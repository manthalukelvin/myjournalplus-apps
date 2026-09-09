package com.celmatech.myjournalplus.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.celmatech.myjournalplus.R
import com.celmatech.myjournalplus.ui.theme.AppColors

// High-contrast field colors so typed text is always visible
private val FieldContainer = Color(0xFFF8F7FC)
private val FieldText = Color(0xFF1A1523)
private val FieldLabel = Color(0xFF4B4458)
private val CardBg = Color(0xFFFFFBFF)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateSignUp: () -> Unit,
    viewModel: AuthViewModel
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }

    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Navigate when auth succeeds (covers email + Google)
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            onLoginSuccess()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.loginSuccessEvent.collect {
            onLoginSuccess()
        }
    }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (!idToken.isNullOrBlank()) {
                    viewModel.loginWithGoogle(idToken) { onLoginSuccess() }
                } else {
                    viewModel.clearError()
                }
            } catch (e: ApiException) {
                if (e.statusCode != 12501) { // 12501 = user cancelled
                    // leave error handling to VM if needed
                }
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = FieldText,
        unfocusedTextColor = FieldText,
        focusedContainerColor = FieldContainer,
        unfocusedContainerColor = FieldContainer,
        disabledContainerColor = FieldContainer,
        cursorColor = AppColors.Primary,
        focusedBorderColor = AppColors.Primary,
        unfocusedBorderColor = Color(0xFFD1CBE0),
        focusedLabelColor = AppColors.Primary,
        unfocusedLabelColor = FieldLabel,
        focusedLeadingIconColor = AppColors.Primary,
        unfocusedLeadingIconColor = FieldLabel,
        focusedTrailingIconColor = FieldLabel,
        unfocusedTrailingIconColor = FieldLabel
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2D1B69),
                        Color(0xFF4C3B9B),
                        Color(0xFF6B5CE0),
                        Color(0xFF9B8CFF)
                    )
                )
            )
    ) {
        Box(
            Modifier
                .size(220.dp)
                .offset(x = (-60).dp, y = (-40).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(12.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "MyJournal+",
                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("MyJournal+", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "A calm, private space for reflection, mood tracking & growth.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        "Welcome back",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = FieldText
                    )
                    Text("Sign in to continue your journal", color = FieldLabel, fontSize = 14.sp)
                    Spacer(Modifier.height(22.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; viewModel.clearError() },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        textStyle = LocalTextStyle.current.copy(color = FieldText, fontSize = 16.sp)
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; viewModel.clearError() },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            viewModel.login(email, password, onLoginSuccess)
                        }),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        textStyle = LocalTextStyle.current.copy(color = FieldText, fontSize = 16.sp)
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            resetEmail = email
                            viewModel.clearError()
                            viewModel.clearSuccess()
                            showForgotDialog = true
                        }) {
                            Text("Forgot password?", color = AppColors.Primary, fontSize = 13.sp)
                        }
                    }

                    AnimatedVisibility(visible = error != null) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }

                    Button(
                        onClick = {
                            viewModel.clearError()
                            viewModel.login(email, password, onLoginSuccess)
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled = !loading && email.isNotBlank() && password.length >= 6,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE5E7EB))
                        Text("  or  ", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE5E7EB))
                    }
                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.clearError()
                            googleClient.signOut().addOnCompleteListener {
                                googleLauncher.launch(googleClient.signInIntent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !loading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FieldText),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                    ) {
                        Text("G", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF4285F4), modifier = Modifier.padding(end = 10.dp))
                        Text("Continue with Google", fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onNavigateSignUp, modifier = Modifier.fillMaxWidth()) {
                        Text("Don't have an account?  ", color = FieldLabel, fontSize = 14.sp)
                        Text("Sign up", color = AppColors.Primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = {
                showForgotDialog = false
                viewModel.clearSuccess()
                viewModel.clearError()
            },
            title = { Text("Reset password", fontWeight = FontWeight.Bold, color = FieldText) },
            text = {
                Column {
                    Text("Enter your email and we'll send a reset link.", fontSize = 14.sp, color = FieldLabel)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        textStyle = LocalTextStyle.current.copy(color = FieldText)
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    if (successMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(successMessage!!, color = Color(0xFF059669), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                if (successMessage == null) {
                    Button(
                        onClick = { viewModel.sendPasswordReset(resetEmail) },
                        enabled = !loading && resetEmail.contains("@"),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Send link")
                    }
                } else {
                    TextButton(onClick = {
                        showForgotDialog = false
                        viewModel.clearSuccess()
                    }) { Text("Done") }
                }
            },
            dismissButton = {
                if (successMessage == null) {
                    TextButton(onClick = {
                        showForgotDialog = false
                        viewModel.clearError()
                    }) { Text("Cancel") }
                }
            },
            containerColor = CardBg
        )
    }
}
