package com.celmatech.myjournalplus.ui.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celmatech.myjournalplus.data.model.JournalEntry
import com.celmatech.myjournalplus.ui.components.PremiumUpgradeDialog
import com.celmatech.myjournalplus.ui.theme.AppColors
import com.celmatech.myjournalplus.util.MarkdownFormat
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(
    entryId: String?,
    isPremium: Boolean,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditViewModel = viewModel()
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        if (!entryId.isNullOrBlank() && !loaded) {
            try {
                viewModel.loadEntry(uid, entryId)?.let {
                    title = it.title
                    body = TextFieldValue(it.content, TextRange(it.content.length))
                }
            } catch (_: Exception) { }
            loaded = true
        }
    }

    val plain = MarkdownFormat.plainText(body.text)
    val wordCount = plain.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    val freeLimit = 500
    val overLimit = !isPremium && wordCount > freeLimit

    fun requirePremium(action: () -> Unit) {
        if (!isPremium) showPremiumDialog = true else action()
    }

    fun onBodyChange(newVal: TextFieldValue) {
        val continued = MarkdownFormat.continueListIfNeeded(body, newVal)
        body = continued ?: newVal
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.TextPrimary,
        unfocusedTextColor = AppColors.TextPrimary,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        cursorColor = AppColors.Primary,
        focusedBorderColor = AppColors.Primary,
        unfocusedBorderColor = AppColors.Border
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (entryId.isNullOrBlank()) "New Entry" else "Edit Entry",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (overLimit) {
                                showPremiumDialog = true
                                return@IconButton
                            }
                            loading = true
                            error = null
                            viewModel.save(
                                JournalEntry(
                                    id = entryId ?: "",
                                    uid = uid,
                                    title = title,
                                    content = body.text
                                )
                            ) { ok, msg ->
                                loading = false
                                if (ok) onSaved() else error = msg
                            }
                        },
                        enabled = !loading && body.text.isNotBlank()
                    ) {
                        if (loading) CircularProgressIndicator(
                            Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp
                        )
                        else Icon(Icons.Default.Save, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Primary)
            )
        },
        containerColor = AppColors.ScreenBg
    ) { padding ->
        // imePadding keeps content above keyboard; body field uses weight + internal scroll
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            // Formatting toolbar (fixed)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FormatChip("B", FontWeight.Bold) {
                    requirePremium { body = MarkdownFormat.wrap(body, "**") }
                }
                FormatChip("I") {
                    requirePremium { body = MarkdownFormat.wrap(body, "_") }
                }
                FormatChip("U") {
                    requirePremium { body = MarkdownFormat.wrap(body, "__") }
                }
                FormatChip("S") {
                    requirePremium { body = MarkdownFormat.wrap(body, "~~") }
                }
                IconButton(onClick = {
                    requirePremium { body = MarkdownFormat.insertBullet(body) }
                }) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, tint = AppColors.Primary)
                }
                IconButton(onClick = {
                    requirePremium { body = MarkdownFormat.insertNumbered(body) }
                }) {
                    Icon(Icons.Default.FormatListNumbered, null, tint = AppColors.Primary)
                }
                IconButton(onClick = {
                    requirePremium { body = MarkdownFormat.wrap(body, "{{c}}", "{{/c}}") }
                }) {
                    Icon(Icons.Default.FormatColorText, null, tint = AppColors.Primary)
                }
                if (!isPremium) {
                    TextButton(onClick = { showPremiumDialog = true }) {
                        Text(
                            "✦ Format = Premium",
                            color = AppColors.Primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary)
            )
            Spacer(Modifier.height(10.dp))

            // Body fills remaining space and scrolls inside the field when keyboard is open
            OutlinedTextField(
                value = body,
                onValueChange = { onBodyChange(it) },
                label = { Text("Write your thoughts…") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary),
                visualTransformation = MarkdownFormat.visualTransformation(AppColors.TextPrimary)
            )

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Select text → B / I / U / S. In a list, press Enter for next • or 2. 3. …",
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "$wordCount words" + if (!isPremium) " / $freeLimit free" else "",
                        color = if (overLimit) MaterialTheme.colorScheme.error else AppColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!isPremium) {
                        TextButton(onClick = { showPremiumDialog = true }) {
                            Text("✦ Unlimited", color = AppColors.Primary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                Text(
                    "Formatting is saved and shown in entry preview.",
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    if (showPremiumDialog) PremiumUpgradeDialog(onDismiss = { showPremiumDialog = false })
}

@Composable
private fun FormatChip(label: String, weight: FontWeight = FontWeight.Normal, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontWeight = weight, color = AppColors.TextPrimary) },
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White)
    )
}
