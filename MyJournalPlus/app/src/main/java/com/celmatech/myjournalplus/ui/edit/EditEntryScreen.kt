package com.celmatech.myjournalplus.ui.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celmatech.myjournalplus.data.model.JournalEntry
import com.celmatech.myjournalplus.ui.components.PremiumUpgradeDialog
import com.celmatech.myjournalplus.ui.theme.AppColors
import com.google.firebase.auth.FirebaseAuth

/**
 * Lightweight style ranges stored alongside plain text so toolbar actions
 * apply real visual formatting instead of inserting markdown markers.
 */
private data class StyleSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val color: Color? = null,
    val center: Boolean = false
)

private fun AnnotatedString.toMarkdown(): String {
    // Serialize styled text as simple markdown/HTML for storage
    if (spanStyles.isEmpty()) return text
    val sb = StringBuilder()
    var i = 0
    val ranges = spanStyles.sortedBy { it.start }
    var cursor = 0
    while (cursor < text.length) {
        val active = ranges.filter { cursor >= it.start && cursor < it.end }
        if (active.isEmpty()) {
            val next = ranges.filter { it.start > cursor }.minByOrNull { it.start }?.start ?: text.length
            sb.append(text.substring(cursor, next))
            cursor = next
        } else {
            val end = active.minOf { it.end }
            val chunk = text.substring(cursor, end)
            var wrapped = chunk
            val style = active.first().item
            if (style.fontWeight == FontWeight.Bold) wrapped = "**$wrapped**"
            if (style.fontStyle == FontStyle.Italic) wrapped = "_${wrapped}_"
            if (style.textDecoration?.contains(TextDecoration.Underline) == true) wrapped = "__${wrapped}__"
            if (style.textDecoration?.contains(TextDecoration.LineThrough) == true) wrapped = "~~$wrapped~~"
            if (style.color != Color.Unspecified && style.color != Color.Black) {
                val hex = String.format("#%06X", 0xFFFFFF and style.color.hashCode()) // fallback
                // Prefer fixed accent when purple
                val c = if (style.color == Color(0xFF7B6CFF)) "#7B6CFF" else null
                if (c != null) wrapped = "<span style='color:$c'>$wrapped</span>"
            }
            sb.append(wrapped)
            cursor = end
        }
    }
    return sb.toString().ifBlank { text }
}

private fun parseStoredContent(raw: String): TextFieldValue {
    // Strip simple markdown markers for editing display; styles applied via toolbar going forward
    val plain = raw
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("_(.+?)_"), "$1")
        .replace(Regex("~~(.+?)~~"), "$1")
        .replace(Regex("</?span[^>]*>"), "")
        .replace(Regex("</?div[^>]*>"), "")
    return TextFieldValue(plain, TextRange(plain.length))
}

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
    // Active style ranges over body.text
    var styles by remember { mutableStateOf(listOf<StyleSpan>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        if (!entryId.isNullOrBlank() && !loaded) {
            try {
                viewModel.loadEntry(uid, entryId)?.let {
                    title = it.title
                    body = parseStoredContent(it.content)
                    styles = emptyList()
                }
            } catch (_: Exception) { }
            loaded = true
        }
    }

    val plain = body.text
    val wordCount = plain.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    val freeLimit = 500
    val overLimit = !isPremium && wordCount > freeLimit

    fun annotatedBody(): AnnotatedString = buildAnnotatedString {
        val t = body.text
        append(t)
        styles.forEach { s ->
            val start = s.start.coerceIn(0, t.length)
            val end = s.end.coerceIn(start, t.length)
            if (start < end) {
                addStyle(
                    SpanStyle(
                        fontWeight = if (s.bold) FontWeight.Bold else null,
                        fontStyle = if (s.italic) FontStyle.Italic else null,
                        textDecoration = when {
                            s.underline && s.strike -> TextDecoration.Underline + TextDecoration.LineThrough
                            s.underline -> TextDecoration.Underline
                            s.strike -> TextDecoration.LineThrough
                            else -> null
                        },
                        color = s.color ?: Color.Unspecified
                    ),
                    start,
                    end
                )
            }
        }
    }

    fun applyStyle(
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        strike: Boolean = false,
        color: Color? = null
    ) {
        if (!isPremium) {
            showPremiumDialog = true
            return
        }
        val sel = body.selection
        val start = sel.min
        val end = sel.max
        if (start == end) {
            // No selection: insert placeholder markers only if nothing selected — apply to word under cursor
            val t = body.text
            var s = start
            var e = start
            while (s > 0 && !t[s - 1].isWhitespace()) s--
            while (e < t.length && !t[e].isWhitespace()) e++
            if (s == e) return
            styles = styles + StyleSpan(s, e, bold = bold, italic = italic, underline = underline, strike = strike, color = color)
            body = body.copy(selection = TextRange(e))
            return
        }
        styles = styles + StyleSpan(start, end, bold = bold, italic = italic, underline = underline, strike = strike, color = color)
        // Keep selection
        body = body.copy(selection = TextRange(start, end))
    }

    fun insertLine(prefix: String) {
        if (!isPremium) {
            showPremiumDialog = true
            return
        }
        val t = body.text
        val pos = body.selection.min
        // Insert at start of current line
        val lineStart = t.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val newText = t.replaceRange(lineStart, lineStart, prefix)
        // Shift existing styles after insertion
        val delta = prefix.length
        styles = styles.map { sp ->
            when {
                sp.start >= lineStart -> sp.copy(start = sp.start + delta, end = sp.end + delta)
                sp.end > lineStart -> sp.copy(end = sp.end + delta)
                else -> sp
            }
        }
        body = TextFieldValue(newText, TextRange(lineStart + prefix.length))
    }

    fun contentForSave(): String {
        // Prefer markdown serialization of styles so content survives storage
        val t = body.text
        if (styles.isEmpty()) return t
        val sb = StringBuilder()
        val sorted = styles.sortedBy { it.start }
        var cursor = 0
        for (sp in sorted) {
            val start = sp.start.coerceIn(0, t.length)
            val end = sp.end.coerceIn(start, t.length)
            if (start > cursor) sb.append(t.substring(cursor, start))
            if (start < end) {
                var chunk = t.substring(start, end)
                if (sp.bold) chunk = "**$chunk**"
                if (sp.italic) chunk = "_${chunk}_"
                if (sp.underline) chunk = "__${chunk}__"
                if (sp.strike) chunk = "~~$chunk~~"
                if (sp.color != null) chunk = "<span style='color:#7B6CFF'>$chunk</span>"
                if (sp.center) chunk = "<div style='text-align:center'>$chunk</div>"
                sb.append(chunk)
            }
            cursor = end
        }
        if (cursor < t.length) sb.append(t.substring(cursor))
        return sb.toString()
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
                title = { Text(if (entryId.isNullOrBlank()) "New Entry" else "Edit Entry", color = Color.White) },
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
                                    content = contentForSave()
                                )
                            ) { ok, msg ->
                                loading = false
                                if (ok) onSaved() else error = msg
                            }
                        },
                        enabled = !loading && body.text.isNotBlank()
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Save, null, tint = Color.White)
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
        ) {
            // Formatting toolbar (Premium) — applies real SpanStyles to selection
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FormatChip("B", FontWeight.Bold) { applyStyle(bold = true) }
                FormatChip("I") { applyStyle(italic = true) }
                FormatChip("U") { applyStyle(underline = true) }
                FormatChip("S") { applyStyle(strike = true) }
                IconButton(onClick = { insertLine("• ") }) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, tint = AppColors.Primary)
                }
                IconButton(onClick = { insertLine("1. ") }) {
                    Icon(Icons.Default.FormatListNumbered, null, tint = AppColors.Primary)
                }
                IconButton(onClick = {
                    if (!isPremium) {
                        showPremiumDialog = true
                        return@IconButton
                    }
                    val sel = body.selection
                    if (sel.min != sel.max) {
                        styles = styles + StyleSpan(sel.min, sel.max, center = true)
                    }
                }) {
                    Icon(Icons.Default.FormatAlignCenter, null, tint = AppColors.Primary)
                }
                IconButton(onClick = {
                    applyStyle(color = Color(0xFF7B6CFF))
                }) {
                    Icon(Icons.Default.FormatColorText, null, tint = AppColors.Primary)
                }
                if (!isPremium) {
                    TextButton(onClick = { showPremiumDialog = true }) {
                        Text("✦ Format = Premium", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary)
                )
                Spacer(Modifier.height(12.dp))
                // Body field shows live styled text via visualTransformation
                OutlinedTextField(
                    value = body,
                    onValueChange = { newVal ->
                        // Adjust style ranges when text is edited
                        val oldLen = body.text.length
                        val newLen = newVal.text.length
                        if (newLen != oldLen) {
                            val delta = newLen - oldLen
                            val changeAt = newVal.selection.min.coerceAtMost(body.selection.min)
                            styles = styles.mapNotNull { sp ->
                                when {
                                    sp.end <= changeAt -> sp
                                    sp.start >= changeAt -> {
                                        val ns = (sp.start + delta).coerceAtLeast(0)
                                        val ne = (sp.end + delta).coerceAtLeast(ns)
                                        if (ns < ne) sp.copy(start = ns, end = ne) else null
                                    }
                                    else -> {
                                        val ne = (sp.end + delta).coerceAtLeast(sp.start)
                                        if (sp.start < ne) sp.copy(end = ne) else null
                                    }
                                }
                            }
                        }
                        body = newVal
                    },
                    label = { Text("Write your thoughts…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary),
                    visualTransformation = StyleVisualTransformation(styles)
                )
                Spacer(Modifier.height(8.dp))
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
                    "Offline OK — saves sync when you are online again.",
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(48.dp))
            }
        }
    }
    if (showPremiumDialog) PremiumUpgradeDialog(onDismiss = { showPremiumDialog = false })
}

/** Applies StyleSpan list as visual SpanStyles without changing the underlying string. */
private class StyleVisualTransformation(
    private val styles: List<StyleSpan>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val built = buildAnnotatedString {
            append(text.text)
            styles.forEach { s ->
                val start = s.start.coerceIn(0, text.length)
                val end = s.end.coerceIn(start, text.length)
                if (start < end) {
                    addStyle(
                        SpanStyle(
                            fontWeight = if (s.bold) FontWeight.Bold else null,
                            fontStyle = if (s.italic) FontStyle.Italic else null,
                            textDecoration = when {
                                s.underline && s.strike -> TextDecoration.Underline + TextDecoration.LineThrough
                                s.underline -> TextDecoration.Underline
                                s.strike -> TextDecoration.LineThrough
                                else -> null
                            },
                            color = s.color ?: Color.Unspecified
                        ),
                        start,
                        end
                    )
                }
            }
        }
        return TransformedText(built, OffsetMapping.Identity)
    }
}

@Composable
private fun FormatChip(label: String, weight: FontWeight = FontWeight.Normal, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontWeight = weight, color = AppColors.TextPrimary) },
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White)
    )
}
