package com.celmatech.myjournalplus.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celmatech.myjournalplus.data.model.JournalEntry
import com.celmatech.myjournalplus.ui.components.PremiumUpgradeDialog
import com.celmatech.myjournalplus.ui.theme.AppColors
import com.celmatech.myjournalplus.util.Analytics
import com.celmatech.myjournalplus.util.CrashReporting
import com.celmatech.myjournalplus.util.RichStyle
import com.celmatech.myjournalplus.util.RichTextState
import com.google.firebase.auth.FirebaseAuth

/**
 * Immersive full-page editor with true WYSIWYG rich text.
 * Styles are span-based (bold looks bold). Storage stays Markdown-compatible.
 */
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
    val rich = remember { RichTextState() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var isNew by remember { mutableStateOf(entryId.isNullOrBlank()) }

    val saveState by viewModel.saveState.collectAsState()
    val bodyFocus = remember { FocusRequester() }

    // Force recomposition when rich text / selection changes
    val bodyValue = rich.textFieldValue
    val rangesVersion = rich.ranges
    val alignmentData = rich.alignmentData()

    LaunchedEffect(entryId) {
        if (!entryId.isNullOrBlank() && !loaded) {
            try {
                viewModel.loadEntry(uid, entryId)?.let {
                    title = it.title
                    rich.loadMarkdown(it.content, it.alignments)
                }
            } catch (e: Exception) {
                CrashReporting.editorException(e)
            }
            loaded = true
            isNew = false
        } else if (entryId.isNullOrBlank()) {
            viewModel.bindNewEntry(uid)
            loaded = true
            isNew = true
        }
    }

    BackHandler {
        onBack()
    }

    val wordCount = rich.plainWordCount()
    val freeLimit = 500
    val overLimit = !isPremium && wordCount > freeLimit

    fun requirePremium(action: () -> Unit) {
        if (!isPremium) showPremiumDialog = true else action()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isNew) "New Entry" else "Edit Entry",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        SaveStatusLabel(saveState)
                    }
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
                            val markdown = rich.toMarkdown()
                            viewModel.save(
                                JournalEntry(
                                    id = entryId ?: "",
                                    uid = uid,
                                    title = title,
                                    content = markdown,
                                    alignments = alignmentData
                                )
                            ) { ok, msg ->
                                loading = false
                                if (ok) {
                                    if (isNew) Analytics.journalCreated() else Analytics.journalEdited()
                                    if (msg != null) error = msg
                                    onSaved()
                                } else {
                                    error = msg ?: "Save failed"
                                    CrashReporting.journalSaveFailed(Exception(msg ?: "save failed"))
                                }
                            }
                        },
                        enabled = !loading && bodyValue.text.isNotBlank()
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Save, "Save", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Primary)
            )
        },
        containerColor = AppColors.ScreenBg
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            // Formatting toolbar — toggles real styles on selection
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { rich.undo() }, enabled = rich.canUndo) {
                        Icon(
                            Icons.Default.Undo,
                            "Undo",
                            tint = if (rich.canUndo) AppColors.TextPrimary else AppColors.TextMuted
                        )
                    }
                    IconButton(onClick = { rich.redo() }, enabled = rich.canRedo) {
                        Icon(
                            Icons.Default.Redo,
                            "Redo",
                            tint = if (rich.canRedo) AppColors.TextPrimary else AppColors.TextMuted
                        )
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 6.dp)
                            .width(1.dp)
                            .height(22.dp)
                            .background(AppColors.Border)
                    )
                    FormatToggle(
                        label = "B",
                        active = rich.isStyleActive(RichStyle.Bold),
                        bold = true,
                        onClick = { requirePremium { rich.toggleStyle(RichStyle.Bold) } }
                    )
                    FormatToggle(
                        label = "I",
                        active = rich.isStyleActive(RichStyle.Italic),
                        italic = true,
                        onClick = { requirePremium { rich.toggleStyle(RichStyle.Italic) } }
                    )
                    FormatIconToggle(
                        active = rich.isStyleActive(RichStyle.Underline),
                        onClick = { requirePremium { rich.toggleStyle(RichStyle.Underline) } }
                    ) {
                        Icon(Icons.Default.FormatUnderlined, null, tint = it, modifier = Modifier.size(20.dp))
                    }
                    FormatIconToggle(
                        active = rich.isStyleActive(RichStyle.Strike),
                        onClick = { requirePremium { rich.toggleStyle(RichStyle.Strike) } }
                    ) {
                        Icon(Icons.Default.StrikethroughS, null, tint = it, modifier = Modifier.size(20.dp))
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 6.dp)
                            .width(1.dp)
                            .height(22.dp)
                            .background(AppColors.Border)
                    )
                    IconButton(onClick = { requirePremium { rich.applyBullet() } }) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, tint = AppColors.TextPrimary)
                    }
                    IconButton(onClick = { requirePremium { rich.applyNumbered() } }) {
                        Icon(Icons.Default.FormatListNumbered, null, tint = AppColors.TextPrimary)
                    }
                    FormatIconToggle(
                        active = rich.isStyleActive(RichStyle.Highlight),
                        onClick = { requirePremium { rich.toggleStyle(RichStyle.Highlight) } }
                    ) {
                        Icon(Icons.Default.FormatColorText, null, tint = it, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { rich.setAlignment(TextAlign.Start) }) {
                        Icon(Icons.Default.FormatAlignLeft, "Align left", tint = AppColors.TextPrimary)
                    }
                    IconButton(onClick = { rich.setAlignment(TextAlign.Center) }) {
                        Icon(Icons.Default.FormatAlignCenter, "Align center", tint = AppColors.TextPrimary)
                    }
                    IconButton(onClick = { rich.setAlignment(TextAlign.End) }) {
                        Icon(Icons.Default.FormatAlignRight, "Align right", tint = AppColors.TextPrimary)
                    }
                    IconButton(onClick = { rich.setAlignment(TextAlign.Justify) }) {
                        Icon(Icons.Default.FormatAlignJustify, "Justify", tint = AppColors.TextPrimary)
                    }
                    if (!isPremium) {
                        TextButton(onClick = { showPremiumDialog = true }) {
                            Text("✦ Premium", color = AppColors.Primary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Title
            BasicTextField(
                value = title,
                onValueChange = { title = capitalizeSentenceStarts(it).take(200) },
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 28.sp
                ),
                cursorBrush = SolidColor(AppColors.Primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) {
                            Text(
                                "Title",
                                style = TextStyle(
                                    color = AppColors.TextMuted,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        inner()
                    }
                }
            )

            HorizontalDivider(color = AppColors.Border.copy(0.5f), thickness = 0.5.dp)

            // Body — WYSIWYG surface (plain text + styled AnnotatedString render)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                BasicTextField(
                    value = bodyValue,
                    onValueChange = { rich.onValueChange(it) },
                    textStyle = TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = 17.sp,
                        lineHeight = 26.sp
                    ),
                    cursorBrush = SolidColor(AppColors.Primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    visualTransformation = rich.visualTransformation(AppColors.TextPrimary),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .focusRequester(bodyFocus),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize()) {
                            if (bodyValue.text.isEmpty()) {
                                Text(
                                    "Write your thoughts…",
                                    style = TextStyle(
                                        color = AppColors.TextMuted,
                                        fontSize = 17.sp,
                                        lineHeight = 26.sp
                                    )
                                )
                            }
                            inner()
                        }
                    }
                )
            }

            Surface(color = Color.White, shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth()) {
                    if (error != null) {
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$wordCount words" + if (!isPremium) " · $freeLimit free limit" else "",
                            color = if (overLimit) MaterialTheme.colorScheme.error else AppColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!isPremium) {
                            TextButton(onClick = { showPremiumDialog = true }) {
                                Text(
                                    "✦ Unlimited",
                                    color = AppColors.Primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPremiumDialog) {
        PremiumUpgradeDialog(onDismiss = { showPremiumDialog = false })
    }
}

private fun capitalizeSentenceStarts(value: String): String {
    if (value.isEmpty()) return value
    val chars = value.toCharArray()
    var capitalizeNext = true
    for (i in chars.indices) {
        val c = chars[i]
        if (c.isLetter()) {
            if (capitalizeNext) chars[i] = c.uppercaseChar()
            capitalizeNext = false
        } else if (c == '.' || c == '!' || c == '?') {
            capitalizeNext = true
        }
    }
    return String(chars)
}

@Composable
private fun SaveStatusLabel(state: EditViewModel.SaveState) {
    val (text, color) = when (state) {
        EditViewModel.SaveState.Idle -> return
        EditViewModel.SaveState.Saving -> "Saving…" to Color.White.copy(0.85f)
        EditViewModel.SaveState.Saved -> "Saved" to Color.White.copy(0.9f)
        EditViewModel.SaveState.Error -> "Couldn't save" to Color(0xFFFFCDD2)
    }
    Text(text, fontSize = 11.sp, color = color)
}

@Composable
private fun FormatToggle(
    label: String,
    active: Boolean,
    bold: Boolean = false,
    italic: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (active) AppColors.Primary.copy(0.15f) else Color.Transparent
    val fg = if (active) AppColors.Primary else AppColors.TextPrimary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                color = fg,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun FormatIconToggle(
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val bg = if (active) AppColors.Primary.copy(0.15f) else Color.Transparent
    val fg = if (active) AppColors.Primary else AppColors.TextPrimary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { icon(fg) }
    }
}
