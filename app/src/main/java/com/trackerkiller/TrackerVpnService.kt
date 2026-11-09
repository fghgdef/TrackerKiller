package com.trackerkiller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local DNS-only VPN:
 * - Routes only the synthetic DNS server address through the TUN.
 * - Forwards allowed DNS packets to Cloudflare 1.1.1.1.
 * - Replies NXDOMAIN for exact/suffix matches in the blocklist.
 * It does not inspect or decrypt HTTPS traffic.
 */
class TrackerVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopFilter()
            else -> startFilter()
        }
        return START_STICKY
    }

    private fun startFilter() {
        if (running.get()) return
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        try {
            tun = Builder()
                .setSession("TrackerKiller DNS Filter")
                .setMtu(1500)
                .addAddress("10.111.0.1", 32)
                .addDnsServer("10.111.0.2")
                .addRoute("10.111.0.2", 32)
                .establish()
            val fd = tun ?: throw IllegalStateException("تعذر إنشاء واجهة VPN")
            running.set(true)
            worker = Thread { packetLoop(fd) }.also { it.name = "TrackerKiller-DNS"; it.start() }
        } catch (e: Exception) {
            running.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun packetLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)
        while (running.get()) {
            try {
                val length = input.read(buffer)
                if (length <= 0) continue
                val packet = buffer.copyOf(length)
                val parsed = parseDnsUdp(packet) ?: continue
                val name = parsed.first
                val dns = parsed.second
                val response = if (isBlocked(name)) makeNxDomain(dns) else queryUpstream(dns)
                if (response != null) {
                    val reply = buildIpv4UdpReply(packet, response)
                    if (reply != null) output.write(reply)
                }
            } catch (_: Exception) {
                if (running.get()) continue
            }
        }
    }

    private fun parseDnsUdp(ip: ByteArray): Pair<String, ByteArray>? {
        if (ip.size < 28 || (ip[0].toInt() and 0xF0) != 0x40) return null
        val ihl = (ip[0].toInt() and 0x0F) * 4
        if (ip.size < ihl + 8 || ip[9].toInt() != 17) return null
        val dstPort = u16(ip, ihl + 2)
        if (dstPort != 53) return null
        val udpLen = u16(ip, ihl + 4)
        if (udpLen < 8 || ihl + udpLen > ip.size) return null
        val dns = ip.copyOfRange(ihl + 8, ihl + udpLen)
        val name = decodeDnsName(dns) ?: return null
        return name to dns
    }

    private fun decodeDnsName(dns: ByteArray): String? {
        if (dns.size < 17 || u16(dns, 4) < 1) return null
        var p = 12
        val labels = mutableListOf<String>()
        while (p < dns.size) {
            val n = dns[p++].toInt() and 0xff
            if (n == 0) break
            if ((n and 0xC0) != 0 || n > 63 || p + n > dns.size) return null
            labels.add(String(dns, p, n, Charsets.US_ASCII))
            p += n
        }
        return labels.joinToString(".").lowercase().trimEnd('.').takeIf { it.isNotBlank() }
    }

    private fun isBlocked(host: String): Boolean {
        val block = TrackerBlocklist.domains
        return block.any { host == it || host.endsWith(".$it") }
    }

    private fun makeNxDomain(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        val response = query.copyOf()
        val flags = u16(query, 2)
        put16(response, 2, (flags or 0x8000 or 0x0080) and 0xFFF0 or 0x0003)
        put16(response, 6, 0)
        put16(response, 8, 0)
        put16(response, 10, 0)
        return response
    }

    private fun queryUpstream(dns: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                protect(socket)
                socket.soTimeout = 3500
                val data = DatagramPacket(dns, dns.size, InetAddress.getByName("1.1.1.1"), 53)
                socket.send(data)
                val response = ByteArray(4096)
                val packet = DatagramPacket(response, response.size)
                socket.receive(packet)
                response.copyOf(packet.length)
            }
        } catch (_: Exception) { null }
    }

    private fun buildIpv4UdpReply(request: ByteArray, dns: ByteArray): ByteArray? {
        if (request.size < 28) return null
        val ihl = (request[0].toInt() and 0x0F) * 4
        val udpOffset = ihl
        val src = request.copyOfRange(12, 16)
        val dst = request.copyOfRange(16, 20)
        val srcPort = u16(request, udpOffset)
        val dstPort = u16(request, udpOffset + 2)
        val total = ihl + 8 + dns.size
        if (total > 65535) return null
        val out = ByteArray(total)
        out[0] = 0x45
        out[1] = 0
        put16(out, 2, total)
        put16(out, 4, 0)
        put16(out, 6, 0x4000)
        out[8] = 64
        out[9] = 17
        System.arraycopy(dst, 0, out, 12, 4)
        System.arraycopy(src, 0, out, 16, 4)
        put16(out, 10, checksum(out, 0, 20))
        put16(out, 20, dstPort)
        put16(out, 22, srcPort)
        put16(out, 24, 8 + dns.size)
        put16(out, 26, 0)
        System.arraycopy(dns, 0, out, 28, dns.size)
        return out
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length) {
            val hi = data[i].toInt() and 0xff
            val lo = if (i + 1 < offset + length) data[i + 1].toInt() and 0xff else 0
            sum += (hi shl 8) or lo
            i += 2
        }
        while ((sum shr 16) != 0L) sum = (sum and 0xffff) + (sum shr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun u16(b: ByteArray, p: Int) = ((b[p].toInt() and 255) shl 8) or (b[p + 1].toInt() and 255)
    private fun put16(b: ByteArray, p: Int, v: Int) {
        b[p] = (v ushr 8).toByte()
        b[p + 1] = v.toByte()
    }

    private fun stopFilter() {
        running.set(false)
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "TrackerKiller VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this, 9, Intent(this, TrackerVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("TrackerKiller")
            .setContentText("فلترة DNS المحلية مفعّلة")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(Notification.Action.Builder(null, "إيقاف", stop).build())
            .build()
    }

    override fun onDestroy() {
        running.set(false)
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.trackerkiller.START_VPN"
        const val ACTION_STOP = "com.trackerkiller.STOP_VPN"
        private const val CHANNEL_ID = "tracker_vpn"
        private const val NOTIFICATION_ID = 7102
    }
}
