package com.celmatech.myjournalplus.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celmatech.myjournalplus.data.model.Gender
import com.celmatech.myjournalplus.ui.theme.Primary

private val FieldContainer = Color(0xFFF8F7FC)
private val FieldText = Color(0xFF1A1523)
private val FieldLabel = Color(0xFF4B4458)
private val CardBg = Color(0xFFFFFBFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel
) {
    var displayName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(Gender.PREFER_NOT) }
    var ageText by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var genderExpanded by remember { mutableStateOf(false) }

    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    LaunchedEffect(currentUser) {
        if (currentUser != null) onSignUpSuccess()
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = FieldText,
        unfocusedTextColor = FieldText,
        focusedContainerColor = FieldContainer,
        unfocusedContainerColor = FieldContainer,
        cursorColor = Primary,
        focusedBorderColor = Primary,
        unfocusedBorderColor = Color(0xFFD1CBE0),
        focusedLabelColor = Primary,
        unfocusedLabelColor = FieldLabel,
        focusedLeadingIconColor = Primary,
        unfocusedLeadingIconColor = FieldLabel
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2D1B69), Color(0xFF4C3B9B), Color(0xFF7B6CFF))
                )
            )
    ) {
        Box(
            Modifier.size(180.dp).offset((-40).dp, (-20).dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopAppBar(
                title = { Text("Create account", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Join MyJournal+", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Your private space for reflection and growth.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display name") },
                            leadingIcon = { Icon(Icons.Default.Badge, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            textStyle = LocalTextStyle.current.copy(color = FieldText)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                label = { Text("First name") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                textStyle = LocalTextStyle.current.copy(color = FieldText)
                            )
                            OutlinedTextField(
                                value = surname,
                                onValueChange = { surname = it },
                                label = { Text("Surname") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                textStyle = LocalTextStyle.current.copy(color = FieldText)
                            )
                        }
                        Spacer(Modifier.height(10.dp))

                        // Gender dropdown
                        ExposedDropdownMenuBox(
                            expanded = genderExpanded,
                            onExpandedChange = { genderExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = gender.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Gender") },
                                leadingIcon = { Icon(Icons.Default.Wc, null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genderExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                textStyle = LocalTextStyle.current.copy(color = FieldText)
                            )
                            ExposedDropdownMenu(
                                expanded = genderExpanded,
                                onDismissRequest = { genderExpanded = false },
                                containerColor = CardBg
                            ) {
                                Gender.entries.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text(g.label, color = FieldText) },
                                        onClick = {
                                            gender = g
                                            genderExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = ageText,
                            onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) ageText = it },
                            label = { Text("Age") },
                            leadingIcon = { Icon(Icons.Default.Cake, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            textStyle = LocalTextStyle.current.copy(color = FieldText)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; viewModel.clearError() },
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Default.Email, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            textStyle = LocalTextStyle.current.copy(color = FieldText)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; viewModel.clearError() },
                            label = { Text("Password (min 6 characters)") },
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            textStyle = LocalTextStyle.current.copy(color = FieldText)
                        )

                        if (error != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }

                        Spacer(Modifier.height(22.dp))
                        Button(
                            onClick = {
                                viewModel.clearError()
                                val age = ageText.toIntOrNull()
                                val dn = displayName.ifBlank { "$firstName $surname".trim() }
                                viewModel.signUp(
                                    email = email,
                                    password = password,
                                    displayName = dn,
                                    firstName = firstName,
                                    surname = surname,
                                    gender = gender.label,
                                    age = age,
                                    onSuccess = onSignUpSuccess
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            enabled = !loading && email.isNotBlank() && password.length >= 6 && firstName.isNotBlank() && surname.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Create account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
