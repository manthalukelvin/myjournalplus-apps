package com.celmatech.myjournalplus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.celmatech.myjournalplus.data.local.UserPrefs
import com.celmatech.myjournalplus.ui.NavGraph
import com.celmatech.myjournalplus.ui.theme.AppColors
import com.celmatech.myjournalplus.ui.theme.MyJournalPlusTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Production PIN lock:
 * - When locked, NavGraph is NOT composed → zero interaction with underlying screens.
 * - Full-screen non-dismissible Dialog as final barrier.
 * - PBKDF2-HMAC-SHA256 with per-device salt (migrates old SHA-256 hashes safely).
 * - Proper lifecycle: lock on background according to timeout, clear PIN input, update unlock timestamp.
 */
class MainActivity : ComponentActivity() {

    private var lastPausedAt: Long = 0L
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openRequest.value = savedInstanceState?.getString("open")
            ?: intent?.getStringExtra("open")
            ?: intent?.data?.getQueryParameter("open")
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val auth = remember { FirebaseAuth.getInstance() }
            var accountUid by remember { mutableStateOf(auth.currentUser?.uid.orEmpty()) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            )
            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener {
                    accountUid = it.currentUser?.uid.orEmpty()
                }
                auth.addAuthStateListener(listener)
                onDispose { auth.removeAuthStateListener(listener) }
            }
            val prefs = remember(accountUid) { UserPrefs(context, accountUid) }
            LaunchedEffect(accountUid) {
                if (accountUid.isNotBlank()) {
                    runCatching { prefs.syncFromCloud() }
                }
            }
            val themeId by prefs.themeId.collectAsState(initial = "default")
            val pinEnabled by prefs.pinEnabled.collectAsState(initial = false)
            val pinHash by prefs.pinHash.collectAsState(initial = null)
            val pinTimeout by prefs.pinTimeoutMin.collectAsState(initial = 0)
            val lastUnlock by prefs.lastUnlockMs.collectAsState(initial = 0L)
            val scope = rememberCoroutineScope()

            LaunchedEffect(accountUid) {
                if (
                    accountUid.isNotBlank() &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            var locked by remember { mutableStateOf(false) }
            var pinInput by remember { mutableStateOf("") }
            var pinError by remember { mutableStateOf(false) }
            var unlockInProgress by remember { mutableStateOf(false) }

            fun shouldLock(): Boolean {
                if (!pinEnabled || pinHash.isNullOrBlank()) return false
                if (pinTimeout <= 0) return true // immediate
                val elapsed = System.currentTimeMillis() - lastUnlock
                return elapsed > pinTimeout * 60_000L
            }

            // React to preference changes
            LaunchedEffect(pinEnabled, pinHash, pinTimeout, lastUnlock) {
                locked = shouldLock()
                if (locked) {
                    pinInput = ""
                    pinError = false
                }
            }

            // Lifecycle: re-lock when returning from background after timeout
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, pinEnabled, pinTimeout) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> {
                            lastPausedAt = System.currentTimeMillis()
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            if (pinEnabled && !pinHash.isNullOrBlank()) {
                                val away = System.currentTimeMillis() - lastPausedAt
                                if (pinTimeout <= 0 || away > pinTimeout * 60_000L) {
                                    locked = true
                                    pinInput = ""
                                    pinError = false
                                }
                            }
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val openParam by openRequest

            MyJournalPlusTheme(themeId = themeId) {
                // CRITICAL: When locked, do NOT compose NavGraph at all.
                // This is the only reliable way to guarantee zero interaction
                // with underlying screens, navigation, text fields, etc.
                if (locked && pinEnabled && !pinHash.isNullOrBlank()) {
                    // Full-screen non-cancellable lock UI
                    Dialog(
                        onDismissRequest = { /* intentionally non-dismissible */ },
                        properties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        PinLockScreen(
                            pinInput = pinInput,
                            pinError = pinError,
                            unlockInProgress = unlockInProgress,
                            onPinChange = {
                                pinInput = it.filter { c -> c.isDigit() }.take(6)
                                pinError = false
                            },
                            onSubmit = {
                                if (unlockInProgress) return@PinLockScreen
                                unlockInProgress = true
                                val enteredPin = pinInput
                                val hash = pinHash
                                val ok = hash != null && PinHasher.verify(enteredPin, hash)
                                if (ok) {
                                    locked = false
                                    pinInput = ""
                                    pinError = false
                                    scope.launch {
                                        prefs.markUnlocked()
                                        // Migrate old SHA-256 hashes to PBKDF2 on successful unlock
                                        if (!hash!!.startsWith("pbkdf2$")) {
                                            val newHash = PinHasher.hash(enteredPin)
                                            prefs.setPinHash(newHash)
                                        }
                                    }
                                } else {
                                    pinError = true
                                    pinInput = ""
                                }
                                unlockInProgress = false
                            }
                        )
                    }
                } else {
                    NavGraph(initialOpen = openParam)
                }
            }

            override fun onNewIntent(intent: Intent) {
                super.onNewIntent(intent)
                setIntent(intent)
                openRequest.value = intent.getStringExtra("open")
                    ?: intent.data?.getQueryParameter("open")
            }
        }
    }
}

/** Secure PIN hashing: PBKDF2-HMAC-SHA256 + migration from legacy SHA-256. */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LEN = 256
    private const val SALT_LEN = 16

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val derived = pbkdf2(pin, salt)
        return "pbkdf2$" + salt.toHex() + "$" + derived.toHex()
    }

    fun verify(pin: String, stored: String): Boolean {
        return try {
            if (stored.startsWith("pbkdf2$")) {
                val parts = stored.split("$")
                if (parts.size != 3) return false
                val salt = parts[1].hexToBytes()
                val expected = parts[2].hexToBytes()
                val actual = pbkdf2(pin, salt)
                actual.contentEquals(expected)
            } else {
                // Legacy plain SHA-256 (hex)
                val d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(pin.toByteArray(Charsets.UTF_8))
                d.joinToString("") { "%02x".format(it) } == stored
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LEN)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) +
                    Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}

@Composable
private fun PinLockScreen(
    pinInput: String,
    pinError: Boolean,
    unlockInProgress: Boolean,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    // Consume all pointer events so nothing underneath can receive them
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF12101A))
            .pointerInput(Unit) { /* consume everything */ },
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth()
                .widthIn(max = 400.dp)
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = AppColors.Primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = AppColors.Primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "MyJournal+ Locked",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter your PIN to continue",
                    color = AppColors.TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = onPinChange,
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    isError = pinError,
                    supportingText = if (pinError) {
                        { Text("Incorrect PIN. Please try again.", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !unlockInProgress
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onSubmit,
                    enabled = pinInput.length >= 4 && !unlockInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    if (unlockInProgress) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Unlock", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
