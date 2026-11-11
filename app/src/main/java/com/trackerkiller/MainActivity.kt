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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Neon = Color(0xFF65F0B2)
private val Cyan = Color(0xFF57C7D9)
private val Bg = Color(0xFF090E13)
private val Panel = Color(0xFF14202A)
private val PanelLight = Color(0xFF1B2A36)
private val Muted = Color(0xFF9DB0BE)
private val Warn = Color(0xFFFFC47A)
private val Border = Color(0xFF223040)

data class InstalledApp(val label: String, val packageName: String, val system: Boolean)
data class ScanFinding(val app: InstalledApp, val matches: List<String>)
data class LogEntry(val time: String, val message: String, val icon: String)

private fun timeNow(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun GlowCard(
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, if (highlighted) Neon else Border),
        content = content
    )
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color(0xFF042017)),
        modifier = modifier
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun OutlinedPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Neon),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon),
        modifier = modifier
    ) { Text(text, fontSize = 13.sp) }
}

@Composable
private fun StatChip(icon: String, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(PanelLight)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 12.sp)
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IconButtonCircle(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Neon.copy(alpha = 0.15f))
            .border(1.dp, Neon, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("+", color = Neon, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun HeaderSection() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("مركز الحماية والخصوصية", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("TrackerKiller • 1.0.0 Beta", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun ShizukuEnvironmentCard(
    statusLabel: String,
    isReady: Boolean,
    onRestart: () -> Unit,
    onCheckPermissions: () -> Unit,
    executionMode: String,
    onModeChange: (String) -> Unit,
    shizukuText: String,
    rootText: String,
    onDeveloperOptions: () -> Unit
) {
    GlowCard(highlighted = true) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("تحكم Shizuku والبيئة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(statusLabel, color = if (isReady) Neon else Warn, fontWeight = FontWeight.Bold, fontSize = 13.sp)

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.radialGradient(listOf(Neon.copy(alpha = 0.25f), Color.Transparent))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\uD83D\uDEE1\uFE0F", fontSize = 40.sp)
                }
            }

            Text(
                "إعادة تشغيل البيئة والتحقق من الأذونات المطلوبة لتنفيذ أوامر النظام.",
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            PillButton(text = "إعادة تشغيل البيئة", onClick = onRestart, modifier = Modifier.fillMaxWidth())
            OutlinedPillButton(text = "تحقق من الأذونات", onClick = onCheckPermissions, modifier = Modifier.fillMaxWidth())

            HorizontalDivider(color = Border)

            Text("طريقة التحكم بالنظام", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = executionMode == "shizuku", onClick = { onModeChange("shizuku") }, colors = RadioButtonDefaults.colors(selectedColor = Neon))
                Text("Shizuku", color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.width(12.dp))
                RadioButton(selected = executionMode == "root", onClick = { onModeChange("root") }, colors = RadioButtonDefaults.colors(selectedColor = Neon))
                Text("Root اختياري", color = Color.White, fontSize = 12.sp)
            }
            Text("Shizuku: $shizukuText", color = Muted, fontSize = 11.sp)
            Text("Root: $rootText", color = Muted, fontSize = 11.sp)
            OutlinedPillButton(text = "خيارات المطوّر", onClick = onDeveloperOptions, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DnsFirewallCard(
    vpnActive: Boolean,
    onToggle: (Boolean) -> Unit,
    domainInput: String,
    onDomainInputChange: (String) -> Unit,
    onAddDomain: () -> Unit,
    customCount: Int,
    builtinCount: Int
) {
    GlowCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("جدار حماية DNS المحلي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Switch(checked = vpnActive, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Neon))
            }
            Text(
                "يُعالج استعلامات DNS التقليدية IPv4/UDP فقط في هذا الإصدار. لا يفحص HTTPS ولا يضمن حظر DNS المشفر أو عناوين IP المباشرة.",
                color = Muted, fontSize = 12.sp
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = domainInput, onValueChange = onDomainInputChange,
                    label = { Text("أضف نطاق مخصص (مثال: tracker.com)") }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                )
                IconButtonCircle(onClick = onAddDomain)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatChip("\u2795", "$customCount مخصص")
                StatChip("\uD83D\uDEE1\uFE0F", "$builtinCount مدمج")
                StatChip("\uD83D\uDCF6", "${customCount + builtinCount} إجمالي")
            }
        }
    }
}

@Composable
private fun AppScanCard(
    totalScanned: Int,
    safeCount: Int,
    flaggedCount: Int,
    showSystem: Boolean,
    onShowSystemChange: (Boolean) -> Unit,
    onScan: () -> Unit
) {
    val progress = if (totalScanned > 0) 1f else 0f
    GlowCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("فحص التطبيقات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (totalScanned > 0) "100%" else "0%", color = Neon, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = Neon, trackColor = PanelLight
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatChip("\uD83D\uDCE6", "$totalScanned تطبيق")
                StatChip("\u23F3", "$flaggedCount بانتظار المراجعة")
                StatChip("\u2705", "$safeCount آمنة")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = showSystem, onCheckedChange = onShowSystemChange, colors = SwitchDefaults.colors(checkedTrackColor = Neon))
                Spacer(Modifier.width(8.dp))
                Text("تضمين تطبيقات النظام", color = Color.White, fontSize = 12.sp)
            }
            PillButton(text = "ابدأ فحص جديد", onClick = onScan, modifier = Modifier.fillMaxWidth())
            Text("الفحص يبحث عن أسماء حزم معروفة فقط؛ وجود مكتبة لا يثبت أن التطبيق يتجسس أو يرسل بيانات.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TrendSection(scanned: Int, flagged: Int) {
    GlowCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("نظرة عامة على الحماية", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    if (scanned == 0) "لم يتم إجراء أي فحص بعد." else "$flagged من أصل $scanned تطبيقاً يحتاج مراجعة.",
                    color = Muted, fontSize = 12.sp
                )
            }
            Text("\uD83D\uDCC8", fontSize = 22.sp)
        }
    }
}

@Composable
private fun StatusLogCard(entries: List<LogEntry>) {
    GlowCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("سجل الحالة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            val ordered = entries.asReversed()
            ordered.forEachIndexed { index, entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Panel)
                                .border(1.dp, Neon, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(entry.icon, fontSize = 11.sp) }
                        if (index != ordered.lastIndex) {
                            Box(Modifier.width(1.dp).height(28.dp).background(Border))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.padding(bottom = 10.dp)) {
                        Text(entry.time, color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(entry.message, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Text(
                "حماية النظام لا تعني حذف كود التتبع من ملفات APK. لا تُنفذ أوامر النظام إلا بعد مراجعة التطبيق والعملية المستهدفة.",
                color = Warn, fontSize = 11.sp
            )
        }
    }
}

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
        var statusLog by remember { mutableStateOf(listOf(LogEntry(timeNow(), "جاهز. لم يتم تغيير أي إعداد بالنظام.", "\uD83D\uDD52"))) }
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

        fun pushStatus(msg: String, icon: String = "\uD83D\uDD52") {
            status = msg
            statusLog = (statusLog + LogEntry(timeNow(), msg, icon)).takeLast(12)
        }

        val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                context.startForegroundService(Intent(context, TrackerVpnService::class.java).setAction(TrackerVpnService.ACTION_START))
                vpnActive = true
                pushStatus("تم طلب تشغيل فلترة DNS.", "\uD83D\uDEE1\uFE0F")
            } else pushStatus("لم يتم منح إذن VPN.", "\u26A0\uFE0F")
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

        val isShizukuReady = shizukuText.contains("متصل") && !shizukuText.contains("غير")

        MaterialTheme(colorScheme = darkColorScheme(primary = Neon, secondary = Cyan, background = Bg, surface = Panel, onSurface = Color.White)) {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { HeaderSection() }
                    item {
                        ShizukuEnvironmentCard(
                            statusLabel = if (isShizukuReady) "نشط وجاهز" else shizukuText,
                            isReady = isShizukuReady,
                            onRestart = { refreshPermissions(); pushStatus("تمت إعادة فحص حالة البيئة (Shizuku / Root).", "\u267B\uFE0F") },
                            onCheckPermissions = {
                                refreshPermissions()
                                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(4201)
                                pushStatus("تم التحقق من أذونات Shizuku.", "\uD83D\uDD11")
                            },
                            executionMode = executionMode,
                            onModeChange = { executionMode = it },
                            shizukuText = shizukuText,
                            rootText = rootText,
                            onDeveloperOptions = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                        )
                    }
                    item {
                        DnsFirewallCard(
                            vpnActive = vpnActive,
                            onToggle = { checked ->
                                if (checked) {
                                    val prepare = VpnService.prepare(context)
                                    if (prepare == null) {
                                        context.startForegroundService(Intent(context, TrackerVpnService::class.java).setAction(TrackerVpnService.ACTION_START)); vpnActive = true
                                        pushStatus("تم تشغيل جدار حماية DNS.", "\uD83D\uDEE1\uFE0F")
                                    } else vpnLauncher.launch(prepare)
                                } else {
                                    context.startService(Intent(context, TrackerVpnService::class.java).setAction(TrackerVpnService.ACTION_STOP)); vpnActive = false
                                    pushStatus("تم إيقاف جدار حماية DNS.", "\u23F9\uFE0F")
                                }
                            },
                            domainInput = domainInput,
                            onDomainInputChange = { domainInput = it },
                            onAddDomain = {
                                val d = domainInput.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/')
                                if (d.contains('.') && !d.contains(' ')) {
                                    customDomains = customDomains + d; domainInput = ""
                                    pushStatus("أضيف $d على قائمة TrackerKiller.", "\u2795")
                                }
                            },
                            customCount = customDomains.size,
                            builtinCount = TrackerBlocklist.domains.size
                        )
                    }
                    item {
                        AppScanCard(
                            totalScanned = apps.size,
                            safeCount = (apps.size - findings.size).coerceAtLeast(0),
                            flaggedCount = findings.size,
                            showSystem = showSystem,
                            onShowSystemChange = { showSystem = it },
                            onScan = {
                                apps = loadApps(showSystem)
                                findings = scanApps(apps)
                                pushStatus("اكتمل الفحص: ${apps.size} تطبيقاً، ${findings.size} يحتاج مراجعة.", "\uD83D\uDD0D")
                            }
                        )
                    }
                    item { TrendSection(scanned = apps.size, flagged = findings.size) }
                    item { StatusLogCard(entries = statusLog) }

                    if (findings.isNotEmpty()) {
                        item { SectionLabel("نتائج تحتاج مراجعة") }
                        items(findings, key = { it.app.packageName }) { finding ->
                            GlowCard {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(finding.app.label, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(finding.app.packageName, color = Neon, fontSize = 11.sp)
                                    Text("مؤشرات: ${finding.matches.joinToString()}", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    item {
                        GlowCard {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("إدارة التطبيقات و AppOps", color = Neon, fontWeight = FontWeight.Bold)
                                Text("حمّل التطبيقات ثم اختر تطبيقاً لإدارة عمليات محددة. استخدم التجميد بحذر شديد، خصوصاً مع تطبيقات النظام.", color = Muted, fontSize = 12.sp)
                                PillButton(text = "تحميل قائمة التطبيقات", onClick = { apps = loadApps(showSystem); pushStatus("تم تحميل ${apps.size} تطبيقاً.", "\uD83D\uDCE6") })
                            }
                        }
                    }
                    items(apps.take(150), key = { "app-" + it.packageName }) { app ->
                        GlowCard {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(app.label, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(app.packageName + if (app.system) " • نظام" else " • مستخدم", color = Muted, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedPillButton(text = "تجميد / استعادة", onClick = { selected = app; showFreezeDialog = true }, modifier = Modifier.weight(1f))
                                    OutlinedPillButton(text = "AppOps", onClick = { selected = app; showAppOpsDialog = true; appOpsText = "" }, modifier = Modifier.weight(1f))
                                }
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
                    runCommand("pm list packages -d | grep -F 'package:${app.packageName}' >/dev/null && pm enable --user 0 ${app.packageName} || pm disable-user --user 0 ${app.packageName}") { pushStatus(it, "\uD83D\uDCE6") }
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
                        runCommand("cmd appops set $pkg $op $mode") { appOpsText = it; pushStatus(it, "\u2699\uFE0F") }
                    }
                }) { Text("تطبيق الوضع") } },
                dismissButton = { TextButton(onClick = {
                    val pkg = selected!!.packageName
                    val allowedOps = setOf("RUN_IN_BACKGROUND", "GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES", "SCHEDULE_EXACT_ALARM", "POST_NOTIFICATION", "CAMERA", "RECORD_AUDIO", "FINE_LOCATION", "COARSE_LOCATION", "READ_CLIPBOARD")
                    val op = appOpsName.trim().uppercase()
                    if (op !in allowedOps) appOpsText = "اسم العملية غير موجود في القائمة المسموحة."
                    else runCommand("cmd appops get $pkg $op") { appOpsText = it; pushStatus(it, "\u2699\uFE0F") }
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

    private fun shizukuCommand(command: String): String {
      return try {
        if (!Shizuku.pingBinder()) return "Shizuku غير متصل."
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return "إذن Shizuku غير ممنوح."
        @Suppress("DEPRECATION")
        val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        newProcessMethod.isAccessible = true
        val process = newProcessMethod.invoke(
            null,
            arrayOf("sh", "-c", command),
            null,
            null
        ) as rikka.shizuku.ShizukuRemoteProcess
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val err = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        "Exit=$code\n$out\n$err".take(2500)
      } catch (e: Exception) { "Shizuku error: ${e.message}" }
    }

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
