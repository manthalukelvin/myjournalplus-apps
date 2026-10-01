package com.celmatech.myjournalplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.celmatech.myjournalplus.data.local.UserPrefs
import com.celmatech.myjournalplus.ui.NavGraph
import com.celmatech.myjournalplus.ui.theme.AppColors
import com.celmatech.myjournalplus.ui.theme.MyJournalPlusTheme
import kotlinx.coroutines.launch
import java.security.MessageDigest

class MainActivity : ComponentActivity() {

    private var lastPausedAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

            val openParam = intent?.getStringExtra("open")
                ?: intent?.data?.getQueryParameter("open")

            MyJournalPlusTheme(themeId = themeId) {
                Box(Modifier.fillMaxSize()) {
                    NavGraph(initialOpen = openParam)

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
                                    locked = false
                                    pinInput = ""
                                    pinError = false
                                    scope.launch { prefs.markUnlocked() }
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
            .background(Color(0xFF1A1523).copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth()
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "App Locked",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AppColors.TextPrimary
                )
                Text(
                    "Enter your PIN to continue",
                    color = AppColors.TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
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
                        { Text("Incorrect PIN", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onSubmit,
                    enabled = pinInput.length >= 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text("Unlock", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}
