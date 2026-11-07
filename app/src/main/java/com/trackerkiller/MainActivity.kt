package com.trackerkiller

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private val Neon = Color(0xFF65F0B2)
private val Bg = Color(0xFF090E13)
private val Panel = Color(0xFF14202A)
private val Muted = Color(0xFF9DB0BE)

data class InstalledApp(val label: String, val packageName: String, val system: Boolean)
data class ScanFinding(val app: InstalledApp, val matches: List<String>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TrackerKillerApp() }
    }

    @Composable
    private fun TrackerKillerApp() {
        val context = LocalContext.current
        var shizukuText by remember { mutableStateOf("جارٍ فحص Shizuku…") }
        var rootText by remember { mutableStateOf("لم يتم فحص الروت") }
        var executionMode by remember { mutableStateOf("shizuku") }
        var status by remember { mutableStateOf("جاهز. لم يتم تغيير أي إعداد بالنظام.") }
        var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
        var findings by remember { mutableStateOf<List<ScanFinding>>(emptyList()) }
        var selected by remember { mutableStateOf<InstalledApp?>(null) }
        var showSystem by remember { mutableStateOf(false) }
        var showFreezeDialog by remember { mutableStateOf(false) }
        var showAppOpsDialog by remember { mutableStateOf(false) }
        var appOpsText by remember { mutableStateOf("") }
        var appOpsValue by remember { mutableStateOf("ignore") }
        var appOpsName by remember { mutableStateOf("RUN_IN_BACKGROUND") }
        var domainInput by remember { mutableStateOf("") }
        var customDomains by remember { mutableStateOf(setOf<String>()) }
        var vpnActive by remember { mutableStateOf(false) }

        val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                context.startForegroundService(Intent(context, TrackerVpnService::class.java).setAction(TrackerVpnService.ACTION_START))
                vpnActive = true
                status = "تم طلب تشغيل فلترة DNS."
            } else status = "لم يتم منح إذن VPN."
        }

        fun refreshPermissions() {
            shizukuText = shizukuStatus()
            lifecycleScope.launch {
                rootText = withContext(Dispatchers.IO) { rootStatus() }
            }
        }
        fun runCommand(command: String, onDone: (String) -> Unit) {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    when (executionMode) {
                        "root" -> rootCommand(command)
                        else -> shizukuCommand(command)
                    }
                }
                onDone(result)
            }
        }

        MaterialTheme(colorScheme = darkColorScheme(primary = Neon, secondary = Color(0xFF57C7D9), background = Bg, surface = Panel, onSurface = Color.White)) {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("TRACKERKILLER  •  1.0.0 BETA", color = Neon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("مركز الحماية والخصوصية", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Shizuku + Root اختياري • DNS Firewall • AppOps • فحص التطبيقات", color = Muted, fontSize = 12.sp)
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("طريقة التحكم بالنظام", color = Neon, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = executionMode == "shizuku", onClick = { executionMode = "shizuku" })
                                    Text("Shizuku", color = Color.White)
                                    Spacer(Modifier.width(12.dp))
                                    RadioButton(selected = executionMode == "root", onClick = { executionMode = "root" })
                                    Text("Root اختياري", color = Color.White)
                                }
                                Text("Shizuku: $shizukuText", color = Muted, fontSize = 12.sp)
                                Text("Root: $rootText", color = Muted, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        refreshPermissions()
                                        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(4201)
                                    }) { Text("فحص / إذن Shizuku") }
                                    OutlinedButton(onClick = { refreshPermissions() }) { Text("تحديث الحالة") }
                                }
                                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) { Text("خيارات المطوّر") }
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("جدار حماية DNS المحلي", color = Neon, fontWeight = FontWeight.Bold)
                                Text("الحالة: ${if (vpnActive) "تم طلب التشغيل" else "متوقف"}", color = if (vpnActive) Neon else Muted)
                                Text("يُعالج استعلامات DNS التقليدية IPv4/UDP فقط في هذا الإصدار. لا يفحص HTTPS ولا يضمن حظر DNS المشفر أو عناوين IP المباشرة.", color = Muted, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        val prepare = VpnService.prepare(context)
                                        if (prepare == null) {
                                            context.startForegroundService(Intent(context, TrackerVpnService::class.java).setAction(TrackerVpnService.ACTION_START)); vpnActive = true
                                        } else vpnLauncher.launch(prepare)
                                    }, modifier = Modifier.weight(1f)) { Text("تشغيل") }
                                    OutlinedButton(onClick = {
                                        context.startService(Intent(context, TrackerVpnService::class.java).setAction(TrackerVpnService.ACTION_STOP)); vpnActive = false
                                    }, modifier = Modifier.weight(1f)) { Text("إيقاف") }
                                }
                                Text("نطاقات الحظر المدمجة: ${TrackerBlocklist.domains.size}", color = Muted, fontSize = 12.sp)
                                OutlinedTextField(value = domainInput, onValueChange = { domainInput = it }, label = { Text("إضافة نطاق مخصص") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Button(onClick = {
                                    val d = domainInput.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/')
                                    if (d.contains('.') && !d.contains(' ')) { customDomains = customDomains + d; domainInput = ""; status = "أضيف $d إلى قائمة الجلسة. يلزم ربط القائمة بالخدمة قبل تطبيقها فعلياً." }
                                }) { Text("إضافة للقائمة") }
                                if (customDomains.isNotEmpty()) Text("المخصصة لهذه الجلسة: ${customDomains.joinToString()}", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("فحص التطبيقات ومؤشرات التتبع", color = Neon, fontWeight = FontWeight.Bold)
                                Text("الفحص يبحث عن أسماء حزم معروفة فقط؛ وجود مكتبة لا يثبت أن التطبيق يتجسس أو يرسل بيانات.", color = Muted, fontSize = 12.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                                    Text("تضمين تطبيقات النظام", color = Color.White, fontSize = 13.sp)
                                }
                                Button(onClick = {
                                    apps = loadApps(showSystem)
                                    findings = scanApps(apps)
                                    status = "اكتمل الفحص: ${apps.size} تطبيقاً، ${findings.size} تطبيقاً يحتوي مؤشرات مكتبات معروفة."
                                }) { Text("فحص التطبيقات الآن") }
                                Text("عدد التطبيقات المفحوصة: ${apps.size} • نتائج تحتاج مراجعة: ${findings.size}", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    items(findings, key = { it.app.packageName }) { finding ->
                        Card {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(finding.app.label, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(finding.app.packageName, color = Neon, fontSize = 11.sp)
                                Text("مؤشرات: ${finding.matches.joinToString()}", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("إدارة التطبيقات و AppOps", color = Neon, fontWeight = FontWeight.Bold)
                                Text("حمّل التطبيقات ثم اختر تطبيقاً لإدارة عمليات محددة. استخدم التجميد بحذر شديد، خصوصاً مع تطبيقات النظام.", color = Muted, fontSize = 12.sp)
                                Button(onClick = { apps = loadApps(showSystem); status = "تم تحميل ${apps.size} تطبيقاً." }) { Text("تحميل قائمة التطبيقات") }
                            }
                        }
                    }
                    items(apps.take(150), key = { "app-" + it.packageName }) { app ->
                        Card {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(app.label, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(app.packageName + if (app.system) " • نظام" else " • مستخدم", color = Muted, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { selected = app; showFreezeDialog = true }, modifier = Modifier.weight(1f)) { Text("تجميد / استعادة") }
                                    OutlinedButton(onClick = { selected = app; showAppOpsDialog = true; appOpsText = "" }, modifier = Modifier.weight(1f)) { Text("AppOps") }
                                }
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("سجل الحالة", color = Neon, fontWeight = FontWeight.Bold)
                                Text(status, color = Color.White, fontSize = 12.sp)
                                Text("حماية النظام لا تعني حذف كود التتبع من ملفات APK. لا تُنفذ أوامر النظام إلا بعد مراجعة التطبيق والعملية المستهدفة.", color = Color(0xFFFFC47A), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showFreezeDialog && selected != null) {
            AlertDialog(onDismissRequest = { showFreezeDialog = false }, title = { Text("تجميد أو استعادة التطبيق") },
                text = { Text("${selected!!.label}\nالتجميد يعطل التطبيق للمستخدم الحالي وقد يسبب أعطالاً. لا تستخدمه على الهاتف أو System UI أو Launcher أو تطبيقات الاتصال والطوارئ.") },
                confirmButton = { TextButton(onClick = {
                    val app = selected!!
                    runCommand("pm list packages -d | grep -F 'package:${app.packageName}' >/dev/null && pm enable --user 0 ${app.packageName} || pm disable-user --user 0 ${app.packageName}") { status = it }
                    showFreezeDialog = false
                }) { Text("تبديل الحالة") } },
                dismissButton = { TextButton(onClick = { showFreezeDialog = false }) { Text("إلغاء") } })
        }
        if (showAppOpsDialog && selected != null) {
            AlertDialog(onDismissRequest = { showAppOpsDialog = false }, title = { Text("إدارة AppOps") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(selected!!.packageName, fontSize = 11.sp)
                        OutlinedTextField(value = appOpsName, onValueChange = { appOpsName = it }, label = { Text("اسم العملية") }, singleLine = true)
                        OutlinedTextField(value = appOpsValue, onValueChange = { appOpsValue = it }, label = { Text("الوضع: allow / ignore / default") }, singleLine = true)
                        Text("عمليات مقترحة: RUN_IN_BACKGROUND, SYSTEM_ALERT_WINDOW, GET_USAGE_STATS, REQUEST_INSTALL_PACKAGES. ليست كل العمليات مدعومة على كل إصدار.", fontSize = 11.sp, color = Muted)
                        if (appOpsText.isNotBlank()) Text(appOpsText, fontSize = 10.sp)
                    }
                },
                confirmButton = { TextButton(onClick = {
                    val pkg = selected!!.packageName
                    val allowedOps = setOf("RUN_IN_BACKGROUND", "GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES", "SCHEDULE_EXACT_ALARM", "POST_NOTIFICATION", "CAMERA", "RECORD_AUDIO", "FINE_LOCATION", "COARSE_LOCATION", "READ_CLIPBOARD")
                    val allowedModes = setOf("allow", "ignore", "default", "deny", "foreground")
                    val op = appOpsName.trim().uppercase()
                    val mode = appOpsValue.trim().lowercase()
                    if (op !in allowedOps || mode !in allowedModes) {
                        appOpsText = "عملية أو وضع غير مسموح به. اختر عملية من القائمة المكتوبة ووضعاً معروفاً."
                    } else {
                        runCommand("cmd appops set $pkg $op $mode") { appOpsText = it; status = it }
                    }
                }) { Text("تطبيق الوضع") } },
                dismissButton = { TextButton(onClick = {
                    val pkg = selected!!.packageName
                    val allowedOps = setOf("RUN_IN_BACKGROUND", "GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES", "SCHEDULE_EXACT_ALARM", "POST_NOTIFICATION", "CAMERA", "RECORD_AUDIO", "FINE_LOCATION", "COARSE_LOCATION", "READ_CLIPBOARD")
                    val op = appOpsName.trim().uppercase()
                    if (op !in allowedOps) appOpsText = "اسم العملية غير موجود في القائمة المسموحة."
                    else runCommand("cmd appops get $pkg $op") { appOpsText = it; status = it }
                }) { Text("قراءة الحالة") } })
        }
    }

    private fun loadApps(includeSystem: Boolean): List<InstalledApp> {
        @Suppress("DEPRECATION")
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNull { info ->
            val sys = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystem && sys) return@mapNotNull null
            val label = try { packageManager.getApplicationLabel(info).toString() } catch (_: Exception) { info.packageName }
            InstalledApp(label, info.packageName, sys)
        }.sortedBy { it.label.lowercase() }
    }

    private fun scanApps(apps: List<InstalledApp>): List<ScanFinding> {
        val indicators = listOf("com.google.android.gms.ads", "com.google.firebase.analytics", "com.appsflyer", "com.adjust.sdk", "com.facebook", "io.branch", "com.amplitude", "com.mixpanel", "com.applovin", "com.unity3d.ads", "com.vungle", "com.chartboost")
        return apps.mapNotNull { app ->
            val info = try { packageManager.getPackageInfo(app.packageName, PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS) } catch (_: Exception) { null }
            val names = mutableListOf<String>()
            info?.services?.forEach { names.add(it.name) }
            info?.receivers?.forEach { names.add(it.name) }
            info?.providers?.forEach { names.add(it.name) }
            val hits = indicators.filter { key -> app.packageName.contains(key, true) || names.any { it.contains(key, true) } }
            if (hits.isEmpty()) null else ScanFinding(app, hits.distinct())
        }
    }

    private fun shizukuStatus(): String = try {
        when { !Shizuku.pingBinder() -> "غير متصل"; Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "متصل، الإذن غير ممنوح"; else -> "متصل والإذن ممنوح" }
    } catch (e: Exception) { "خطأ: ${e.message}" }

    private fun shizukuCommand(command: String): String = try {
        if (!Shizuku.pingBinder()) return "Shizuku غير متصل."
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return "إذن Shizuku غير ممنوح."
        @Suppress("DEPRECATION")
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val err = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        "Exit=$code\n$out\n$err".take(2500)
    } catch (e: Exception) { "Shizuku error: ${e.message}" }

    private fun rootStatus(): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code == 0 && out.contains("uid=0")) "متاح وتم منح صلاحية root" else "غير متاح أو لم تمنح صلاحية root ${err.take(100)}"
    } catch (_: Exception) { "لا يوجد root أو لا يمكن الوصول إلى su" }

    private fun rootCommand(command: String): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        "Root exit=$code\n$out\n$err".take(2500)
    } catch (e: Exception) { "Root error: ${e.message}" }
}
