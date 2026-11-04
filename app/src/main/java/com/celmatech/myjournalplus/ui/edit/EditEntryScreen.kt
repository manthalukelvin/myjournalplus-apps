package com.celmatech.myjournalplus.ui.edit

<<<<<<< HEAD
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
=======
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
<<<<<<< HEAD
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
=======
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
<<<<<<< HEAD
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
=======
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celmatech.myjournalplus.data.model.JournalEntry
import com.celmatech.myjournalplus.ui.components.PremiumUpgradeDialog
import com.celmatech.myjournalplus.ui.theme.AppColors
<<<<<<< HEAD
import com.celmatech.myjournalplus.util.Analytics
import com.celmatech.myjournalplus.util.CrashReporting
import com.celmatech.myjournalplus.util.RichStyle
import com.celmatech.myjournalplus.util.RichTextState
import com.google.firebase.auth.FirebaseAuth

/**
 * Immersive full-page editor with true WYSIWYG rich text.
 * Styles are span-based (bold looks bold). Storage stays Markdown-compatible.
 */
=======
import com.celmatech.myjournalplus.util.MarkdownFormat
import com.google.firebase.auth.FirebaseAuth

>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
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
<<<<<<< HEAD
    val rich = remember { RichTextState() }
=======
    var body by remember { mutableStateOf(TextFieldValue("")) }
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
<<<<<<< HEAD
    var isNew by remember { mutableStateOf(entryId.isNullOrBlank()) }

    val saveState by viewModel.saveState.collectAsState()
    val bodyFocus = remember { FocusRequester() }

    // Force recomposition when rich text / selection changes
    val bodyValue = rich.textFieldValue
    val rangesVersion = rich.ranges
=======
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9

    LaunchedEffect(entryId) {
        if (!entryId.isNullOrBlank() && !loaded) {
            try {
                viewModel.loadEntry(uid, entryId)?.let {
                    title = it.title
<<<<<<< HEAD
                    rich.loadMarkdown(it.content)
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

    // Autosave markdown serialization
    LaunchedEffect(title, bodyValue.text, rangesVersion) {
        if (loaded) {
            viewModel.scheduleAutosave(title, rich.toMarkdown())
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushPending(title, rich.toMarkdown())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushPending(title, rich.toMarkdown())
        }
    }

    BackHandler {
        viewModel.flushPending(title, rich.toMarkdown())
        onBack()
    }

    val wordCount = rich.plainWordCount()
=======
                    body = TextFieldValue(it.content, TextRange(it.content.length))
                }
            } catch (_: Exception) { }
            loaded = true
        }
    }

    val plain = MarkdownFormat.plainText(body.text)
    val wordCount = plain.split(Regex("\\s+")).filter { it.isNotBlank() }.size
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
    val freeLimit = 500
    val overLimit = !isPremium && wordCount > freeLimit

    fun requirePremium(action: () -> Unit) {
        if (!isPremium) showPremiumDialog = true else action()
    }

<<<<<<< HEAD
=======
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

>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
<<<<<<< HEAD
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
                    IconButton(onClick = {
                        viewModel.flushPending(title, rich.toMarkdown())
                        onBack()
                    }) {
=======
                    Text(
                        if (entryId.isNullOrBlank()) "New Entry" else "Edit Entry",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
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
<<<<<<< HEAD
                            val markdown = rich.toMarkdown()
=======
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
                            viewModel.save(
                                JournalEntry(
                                    id = entryId ?: "",
                                    uid = uid,
                                    title = title,
<<<<<<< HEAD
                                    content = markdown
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
=======
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
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Primary)
            )
        },
        containerColor = AppColors.ScreenBg
    ) { padding ->
<<<<<<< HEAD
=======
        // imePadding keeps content above keyboard; body field uses weight + internal scroll
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
<<<<<<< HEAD
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
                    if (!isPremium) {
                        TextButton(onClick = { showPremiumDialog = true }) {
                            Text("✦ Premium", color = AppColors.Primary, fontSize = 12.sp)
                        }
=======
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
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
                    }
                }
            }

<<<<<<< HEAD
            // Title
            BasicTextField(
                value = title,
                onValueChange = { title = it.take(200) },
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
=======
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

            // Larger, flexible "Write your thoughts" card — fills remaining space,
            // scrolls internally when content is long, comfortable on any phone size.
            OutlinedTextField(
                value = body,
                onValueChange = { onBodyChange(it) },
                label = { Text("Write your thoughts…") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .defaultMinSize(minHeight = 220.dp),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                textStyle = LocalTextStyle.current.copy(
                    color = AppColors.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                visualTransformation = MarkdownFormat.visualTransformation(AppColors.TextPrimary)
            )

            // Compact footer: word count only (notice texts removed for clean writing UX)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
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
>>>>>>> bb2e8564354b35039192d34a53ed32a370b8b3f9
}
