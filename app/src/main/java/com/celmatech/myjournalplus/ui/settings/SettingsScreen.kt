package com.celmatech.myjournalplus.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celmatech.myjournalplus.data.local.UserPrefs
import com.celmatech.myjournalplus.data.model.UserProfile
import com.celmatech.myjournalplus.data.repository.DeviceRepository
import com.celmatech.myjournalplus.data.repository.JournalRepository
import com.celmatech.myjournalplus.ui.components.PremiumBadge
import com.celmatech.myjournalplus.ui.components.PremiumUpgradeDialog
import com.celmatech.myjournalplus.ui.theme.AppColors
import com.celmatech.myjournalplus.util.ReminderScheduler
import com.celmatech.myjournalplus.util.ReminderWorker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: UserProfile?,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPrefs(context) }
    val isPremium = profile?.isPremium == true

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showPremiumUpgrade by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var showReminderTime by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var snack by remember { mutableStateOf<String?>(null) }
    val snackHost = remember { SnackbarHostState() }

    val reminderEnabled by prefs.reminderEnabled.collectAsState(initial = true)
    val reminderHour by prefs.reminderHour.collectAsState(initial = 18)
    val reminderMinute by prefs.reminderMinute.collectAsState(initial = 0)
    val pinEnabled by prefs.pinEnabled.collectAsState(initial = false)
    val pinTimeout by prefs.pinTimeoutMin.collectAsState(initial = 0)
    val themeId by prefs.themeId.collectAsState(initial = "default")

    LaunchedEffect(snack) {
        snack?.let { snackHost.showSnackbar(it); snack = null }
    }

    fun openUrl(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { snack = "Could not open link" }
    }

    fun sha(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Account · Premium · Reminders", fontSize = 11.sp, color = Color.White.copy(0.85f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Primary)
            )
        },
        snackbarHost = { SnackbarHost(snackHost) },
        containerColor = AppColors.ScreenBg
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // Profile
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(AppColors.Primary.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = AppColors.Primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile?.displayName?.ifBlank { "User" } ?: "User", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Text(profile?.email ?: "", color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                    if (isPremium) PremiumBadge()
                }
            }

            Spacer(Modifier.height(20.dp))
            Section("Account")
            SettingsItem(Icons.Default.ManageAccounts, "Account settings", "Email, name, password on the web") {
                openUrl("https://app.myjournalplus.com/settings")
            }
            SettingsItem(Icons.Default.WorkspacePremium, if (isPremium) "Premium active" else "Upgrade to Premium",
                if (isPremium) "All premium features unlocked" else "Unlock PIN, themes, AI, export, devices") {
                if (!isPremium) showPremiumUpgrade = true else snack = "You're already Premium"
            }

            Spacer(Modifier.height(16.dp))
            Section("Daily reminder (real)")
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = AppColors.Primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Journal reminder", color = AppColors.TextPrimary, fontWeight = FontWeight.Medium)
                            Text(
                                "Default 18:00 · now %02d:%02d".format(reminderHour, reminderMinute),
                                color = AppColors.TextSecondary, fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { en ->
                                scope.launch {
                                    prefs.setReminder(en, reminderHour, reminderMinute)
                                    if (en) {
                                        ReminderScheduler.scheduleDaily(context, reminderHour, reminderMinute)
                                        ReminderWorker.enqueue(context)
                                        snack = "Reminder on at %02d:%02d".format(reminderHour, reminderMinute)
                                    } else {
                                        ReminderScheduler.cancel(context)
                                        ReminderWorker.cancel(context)
                                        snack = "Reminder off"
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Primary)
                        )
                    }
                    TextButton(onClick = { showReminderTime = true }) {
                        Text("Change time", color = AppColors.Primary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Section("Premium features")
            SettingsItem(Icons.Default.Lock, "PIN protection", if (pinEnabled) "On · timeout ${pinTimeout}m" else "Lock app with PIN") {
                if (!isPremium) showPremiumUpgrade = true else showPinSetup = true
            }
            SettingsItem(Icons.Default.Palette, "Appearance", "Theme: $themeId") {
                if (!isPremium) showPremiumUpgrade = true else showTheme = true
            }
            SettingsItem(Icons.Default.FileDownload, "Export journal", "PDF / CSV / text share") {
                if (!isPremium) showPremiumUpgrade = true else showExport = true
            }
            SettingsItem(Icons.Default.Devices, "Devices", "Sessions & remote logout") {
                if (!isPremium) showPremiumUpgrade = true else showDevices = true
            }

            Spacer(Modifier.height(16.dp))
            Section("App & support")
            SettingsItem(Icons.Default.SystemUpdate, "Check for updates", "Latest version on the web") {
                openUrl("https://app.myjournalplus.com/updates")
            }
            SettingsItem(Icons.Default.Email, "Contact us", "admin@myjournalplus.com") {
                try {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:admin@myjournalplus.com")))
                } catch (_: Exception) {
                    openUrl("mailto:admin@myjournalplus.com")
                }
            }
            SettingsItem(Icons.Default.Info, "About", "MyJournal+ 1.0.0 · Celma Tech") {
                snack = "MyJournal+ v1.0.0"
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Reminder time
    if (showReminderTime) {
        var h by remember { mutableIntStateOf(reminderHour) }
        var m by remember { mutableIntStateOf(reminderMinute) }
        AlertDialog(
            onDismissRequest = { showReminderTime = false },
            title = { Text("Reminder time", color = AppColors.TextPrimary) },
            text = {
                Column {
                    Text("Hour (0–23)", color = AppColors.TextSecondary)
                    Slider(value = h.toFloat(), onValueChange = { h = it.toInt() }, valueRange = 0f..23f, steps = 22)
                    Text("Minute", color = AppColors.TextSecondary)
                    Slider(value = m.toFloat(), onValueChange = { m = (it.toInt() / 5) * 5 }, valueRange = 0f..55f, steps = 10)
                    Text("Selected: %02d:%02d".format(h, m), fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        prefs.setReminder(true, h, m)
                        ReminderScheduler.scheduleDaily(context, h, m)
                        ReminderWorker.enqueue(context)
                        showReminderTime = false
                        snack = "Reminder set for %02d:%02d".format(h, m)
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showReminderTime = false }) { Text("Cancel") } },
            containerColor = Color.White
        )
    }

    // PIN setup
    if (showPinSetup) {
        AlertDialog(
            onDismissRequest = { showPinSetup = false },
            title = { Text("PIN protection", color = AppColors.TextPrimary) },
            text = {
                Column {
                    Text("4–6 digit PIN. Reset by signing out and using account password.", color = AppColors.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = pinInput, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(value = pinConfirm, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinConfirm = it },
                        label = { Text("Confirm PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Text("Lock after inactivity:", color = AppColors.TextSecondary, fontSize = 13.sp)
                    listOf(0 to "Immediate", 1 to "1 min", 5 to "5 min", 15 to "15 min").forEach { (v, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = pinTimeout == v, onClick = {
                                scope.launch { prefs.setPinTimeout(v) }
                            })
                            Text(label, color = AppColors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length < 4 || pinInput != pinConfirm) {
                        Toast.makeText(context, "PINs must match (4–6 digits)", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    scope.launch {
                        prefs.setPin(true, sha(pinInput))
                        prefs.markUnlocked()
                        showPinSetup = false
                        snack = "PIN enabled — will lock after the timeout you chose"
                    }
                }) { Text("Enable PIN") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        prefs.setPin(false, null)
                        showPinSetup = false
                        snack = "PIN disabled"
                    }
                }) { Text("Disable") }
            },
            containerColor = Color.White
        )
    }

    // Themes — color cards applied app-wide via UserPrefs + MainActivity
    if (showTheme) {
        data class ThemeOpt(val id: String, val label: String, val swatch: Color, val bg: Color)
        val themes = listOf(
            ThemeOpt("default", "Default", Color(0xFF7B6CFF), Color(0xFFF5F3FF)),
            ThemeOpt("midnight", "Midnight", Color(0xFF8B7CFF), Color(0xFF0F0D1A)),
            ThemeOpt("ocean", "Ocean", Color(0xFF0EA5E9), Color(0xFFECFEFF)),
            ThemeOpt("forest", "Forest", Color(0xFF10B981), Color(0xFFECFDF5)),
            ThemeOpt("rose", "Rose", Color(0xFFEC4899), Color(0xFFFDF2F8)),
            ThemeOpt("sunset", "Sunset", Color(0xFFF97316), Color(0xFFFFF7ED)),
            ThemeOpt("slate", "Slate", Color(0xFF64748B), Color(0xFFF8FAFC))
        )
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("Appearance", color = AppColors.TextPrimary) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Theme applies to the whole app immediately.", color = AppColors.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    themes.forEach { opt ->
                        val selected = themeId == opt.id
                        Card(
                            onClick = {
                                scope.launch {
                                    prefs.setTheme(opt.id)
                                    snack = "Theme: ${opt.label}"
                                    showTheme = false
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = opt.bg),
                            border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, opt.swatch) else null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(opt.swatch)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(opt.label, fontWeight = FontWeight.SemiBold, color = if (opt.id == "midnight") Color.White else AppColors.TextPrimary)
                                    Text(opt.id, fontSize = 12.sp, color = if (opt.id == "midnight") Color.White.copy(0.7f) else AppColors.TextSecondary)
                                }
                                if (selected) Icon(Icons.Default.Check, null, tint = opt.swatch)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTheme = false }) { Text("Close") } },
            containerColor = Color.White
        )
    }

    // Devices
    if (showDevices) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val deviceRepo = remember { DeviceRepository() }
        val currentId = remember { deviceRepo.currentDeviceId(context) }
        LaunchedEffect(uid) {
            if (uid != null) {
                try { deviceRepo.registerOrTouch(context, uid) } catch (_: Exception) { }
            }
        }
        val devices by if (uid != null) deviceRepo.devicesFlow(uid, currentId).collectAsState(initial = emptyList())
        else remember { mutableStateOf(emptyList()) }

        AlertDialog(
            onDismissRequest = { showDevices = false },
            title = { Text("Devices", color = AppColors.TextPrimary) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (devices.isEmpty()) Text("Loading this device… If empty, open Settings again while online.", color = AppColors.TextMuted, fontSize = 13.sp)
                    devices.forEach { d ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = AppColors.FieldBg)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(d.name + if (d.isCurrent) " (this device)" else "", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                                Text("${d.platform}", color = AppColors.TextSecondary, fontSize = 12.sp)
                                Text("📍 ${d.approxLocation}", color = AppColors.TextSecondary, fontSize = 12.sp)
                                Text("Last active: ${d.lastActiveLabel}", color = AppColors.TextMuted, fontSize = 11.sp)
                                if (!d.isCurrent && uid != null) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            deviceRepo.remoteLogout(uid, d.deviceId)
                                            snack = "Logged out remote device"
                                        }
                                    }) { Text("Log out device", color = AppColors.Error) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDevices = false }) { Text("Close") } },
            containerColor = Color.White
        )
    }

    // Export journal — write real files then share
    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("Export journal", color = AppColors.TextPrimary) },
            text = {
                Column {
                    Text("Download all entries as a file you can save or share.", color = AppColors.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        scope.launch {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                            val entries = JournalRepository().entriesFlow(uid).first()
                            val csv = buildString {
                                appendLine("Title,Content,Mood,WordCount,CreatedAt")
                                entries.forEach { e ->
                                    fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
                                    appendLine("${esc(e.title)},${esc(e.content.replace("\n", " "))},${esc(e.mood ?: "")},${e.wordCount},${e.createdAt}")
                                }
                            }
                            try {
                                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                val file = File(dir, "MyJournalPlus_export.csv")
                                file.writeText(csv)
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, context.packageName + ".fileprovider", file
                                )
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "MyJournal+ export.csv")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Save / share CSV"))
                            } catch (e: Exception) {
                                // Fallback: share as text
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                    putExtra(Intent.EXTRA_SUBJECT, "MyJournal+ export.csv")
                                }
                                context.startActivity(Intent.createChooser(send, "Export CSV"))
                            }
                            showExport = false
                            snack = "CSV ready — ${entries.size} entries"
                        }
                    }) { Text("Download CSV", color = AppColors.Primary) }

                    TextButton(onClick = {
                        scope.launch {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                            val entries = JournalRepository().entriesFlow(uid).first()
                            val body = buildString {
                                appendLine("MyJournal+ Export")
                                appendLine("=".repeat(40))
                                entries.forEach { e ->
                                    appendLine()
                                    appendLine(e.title.ifBlank { "(Untitled)" })
                                    appendLine("-".repeat(24))
                                    appendLine(e.content)
                                    appendLine()
                                }
                            }
                            try {
                                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                val file = File(dir, "MyJournalPlus_export.txt")
                                file.writeText(body)
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, context.packageName + ".fileprovider", file
                                )
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "MyJournal+ export")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Save / share text"))
                            } catch (e: Exception) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, body)
                                }
                                context.startActivity(Intent.createChooser(send, "Export text"))
                            }
                            showExport = false
                            snack = "Text export ready — ${entries.size} entries"
                        }
                    }) { Text("Download text file", color = AppColors.Primary) }
                }
            },
            confirmButton = { TextButton(onClick = { showExport = false }) { Text("Close") } },
            containerColor = Color.White
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout?", color = AppColors.TextPrimary) },
            text = { Text("You'll need to sign in again.", color = AppColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; onLogout() }) {
                    Text("Logout", color = AppColors.Error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") } },
            containerColor = Color.White
        )
    }

    if (showPremiumUpgrade) PremiumUpgradeDialog(onDismiss = { showPremiumUpgrade = false })
}

@Composable private fun Section(t: String) {
    Text(t, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AppColors.Primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = AppColors.TextPrimary, fontWeight = FontWeight.Medium)
                Text(subtitle, color = AppColors.TextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextMuted)
        }
    }
}
