package com.celmatech.myjournalplus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.celmatech.myjournalplus.data.local.UserPrefs
import com.celmatech.myjournalplus.data.repository.DeviceRepository
import com.celmatech.myjournalplus.ui.NavGraph
import com.celmatech.myjournalplus.ui.theme.AppColors
import com.celmatech.myjournalplus.ui.theme.MyJournalPlusTheme
import com.celmatech.myjournalplus.util.ReminderScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    private val requestNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var lastPausedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val openTarget = intent?.getStringExtra("open")

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            Thread {
                try {
                    runBlocking {
                        DeviceRepository().registerOrTouch(this@MainActivity, uid)
                        val prefs = UserPrefs(this@MainActivity)
                        if (prefs.reminderEnabled.first()) {
                            ReminderScheduler.scheduleDaily(
                                this@MainActivity,
                                prefs.reminderHour.first(),
                                prefs.reminderMinute.first()
                            )
                        }
                    }
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users").document(uid)
                            .update(
                                "fcmTokens",
                                com.google.firebase.firestore.FieldValue.arrayUnion(token)
                            )
                    }
                    FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                } catch (_: Exception) { }
            }.start()
        }

        setContent {
            val context = LocalContext.current
            val prefs = remember { UserPrefs(context) }
            val themeId by prefs.themeId.collectAsState(initial = "default")
            val pinEnabled by prefs.pinEnabled.collectAsState(initial = false)
            val pinHash by prefs.pinHash.collectAsState(initial = null)
            val pinTimeout by prefs.pinTimeoutMin.collectAsState(initial = 0)
            val lastUnlock by prefs.lastUnlockMs.collectAsState(initial = 0L)
            val scope = rememberCoroutineScope()

            var locked by remember { mutableStateOf(false) }
            var pinInput by remember { mutableStateOf("") }
            var pinError by remember { mutableStateOf(false) }

            fun sha(s: String): String {
                val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
                return d.joinToString("") { "%02x".format(it) }
            }

            fun shouldLock(): Boolean {
                if (!pinEnabled || pinHash.isNullOrBlank()) return false
                if (pinTimeout <= 0) return true
                val elapsed = System.currentTimeMillis() - lastUnlock
                return elapsed > pinTimeout * 60_000L
            }

            LaunchedEffect(pinEnabled, pinHash, pinTimeout, lastUnlock) {
                locked = shouldLock()
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, pinEnabled, pinTimeout) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> lastPausedAt = System.currentTimeMillis()
                        Lifecycle.Event.ON_RESUME -> {
                            if (pinEnabled && !pinHash.isNullOrBlank()) {
                                val away = System.currentTimeMillis() - lastPausedAt
                                if (pinTimeout <= 0 || away > pinTimeout * 60_000L) {
                                    locked = true
                                    pinInput = ""
                                }
                            }
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            MyJournalPlusTheme(themeId = themeId) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        NavGraph(initialOpen = openTarget)

                        if (locked && pinEnabled && !pinHash.isNullOrBlank()) {
                            PinLockOverlay(
                                pinInput = pinInput,
                                pinError = pinError,
                                onPinChange = {
                                    pinInput = it.filter { c -> c.isDigit() }.take(6)
                                    pinError = false
                                },
                                onSubmit = {
                                    if (sha(pinInput) == pinHash) {
                                        scope.launch { prefs.markUnlocked() }
                                        locked = false
                                        pinInput = ""
                                        pinError = false
                                    } else {
                                        pinError = true
                                        pinInput = ""
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinLockOverlay(
    pinInput: String,
    pinError: Boolean,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.ScreenBg),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Enter PIN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AppColors.TextPrimary
                )
                Text(
                    "Your journal is protected",
                    color = AppColors.TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = onPinChange,
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = pinError,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedContainerColor = AppColors.FieldBg,
                        unfocusedContainerColor = AppColors.FieldBg,
                        cursorColor = AppColors.Primary,
                        focusedBorderColor = AppColors.Primary
                    )
                )
                if (pinError) {
                    Text(
                        "Incorrect PIN",
                        color = AppColors.Error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onSubmit,
                    enabled = pinInput.length in 4..6,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text("Unlock", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}
