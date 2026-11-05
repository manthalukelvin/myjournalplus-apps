package com.celmatech.myjournalplus.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * True WYSIWYG rich-text model for the journal editor.
 *
 * - Editing state is plain text + style ranges (no visible markers).
 * - Rendering uses AnnotatedString (bold looks bold, etc.).
 * - Storage remains Markdown-compatible for existing Firestore documents.
 * - Formatting toggles apply to the current selection and preserve it.
 */
enum class RichStyle {
    Bold, Italic, Underline, Strike, Highlight
}

data class StyleRange(
    val start: Int,
    val end: Int,
    val style: RichStyle
) {
    fun shifted(delta: Int, from: Int): StyleRange? {
        var s = start
        var e = end
        if (e <= from) return this
        if (s >= from) {
            s += delta
            e += delta
        } else if (e > from) {
            e += delta
        }
        if (e <= s || s < 0) return null
        return copy(start = s, end = e)
    }

    fun clipped(len: Int): StyleRange? {
        val s = start.coerceIn(0, len)
        val e = end.coerceIn(0, len)
        return if (e > s) copy(start = s, end = e) else null
    }
}

class RichTextState(
    initialMarkdown: String = ""
) {
    var text by mutableStateOf("")
        private set
    var ranges by mutableStateOf<List<StyleRange>>(emptyList())
        private set
    var selection by mutableStateOf(TextRange.Zero)
    private var lastNonEmptySelection = TextRange.Zero
    var alignments by mutableStateOf<Map<Int, TextAlign>>(emptyMap())
        private set
    private data class Snapshot(
        val text: String,
        val ranges: List<StyleRange>,
        val selection: TextRange,
        val alignments: Map<Int, TextAlign>
    )
    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var historyVersion by mutableStateOf(0)

    val textFieldValue: TextFieldValue
        get() = TextFieldValue(text, selection)

    init {
        val parsed = MarkdownCodec.decode(initialMarkdown)
        text = parsed.first
        ranges = parsed.second
        selection = TextRange(text.length)
        lastNonEmptySelection = TextRange.Zero
        alignments = emptyMap()
    }

    fun loadMarkdown(markdown: String, alignmentData: Map<String, String> = emptyMap()) {
        val parsed = MarkdownCodec.decode(markdown)
        text = parsed.first
        ranges = parsed.second
        selection = TextRange(text.length)
        lastNonEmptySelection = TextRange.Zero
        alignments = alignmentData.mapNotNull { (key, value) ->
            val index = key.toIntOrNull()
            val alignment = runCatching { TextAlign(value.toInt()) }.getOrNull()
            if (index != null && alignment != null) index to alignment else null
        }.toMap()
        undoStack.clear()
        redoStack.clear()
        touchHistory()
    }

    fun alignmentData(): Map<String, String> =
        alignments.mapKeys { it.key.toString() }.mapValues { it.value.value.toString() }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        restore(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        restore(next)
    }

    fun alignmentForSelection(): TextAlign {
        val lines = selectedLineIndexes()
        return lines.firstOrNull()?.let { alignments[it] } ?: TextAlign.Start
    }

    fun setAlignment(alignment: TextAlign) {
        val lines = selectedLineIndexes()
        if (lines.isEmpty()) return
        recordHistory()
        alignments = alignments.toMutableMap().apply {
            lines.forEach { this[it] = alignment }
        }
        redoStack.clear()
        touchHistory()
    }

    /** Markdown string safe for Firestore (backward compatible). */
    fun toMarkdown(): String = MarkdownCodec.encode(text, ranges)

    fun plainWordCount(): Int =
        text.split(Regex("\\s+")).count { it.isNotBlank() }

    fun onValueChange(newValue: TextFieldValue) {
        val oldText = text
        val capitalized = capitalizeSentenceStarts(newValue.text)
        val typedValue = TextFieldValue(capitalized, newValue.selection)
        val continued = continueListIfNeeded(oldText, typedValue)
        val finalValue = continued ?: typedValue
        val newText = finalValue.text
        if (oldText != newText) {
            recordHistory()
            ranges = adjustRanges(oldText, newText, ranges)
            alignments = remapAlignments(oldText, newText, alignments)
            redoStack.clear()
        }
        text = newText
        selection = finalValue.selection
        if (selection.min != selection.max) {
            lastNonEmptySelection = selection
        }
        touchHistory()
    }

    fun toggleStyle(style: RichStyle) {
        val activeSelection = if (selection.min < selection.max) {
            selection
        } else {
            lastNonEmptySelection
        }
        val s = activeSelection.min.coerceIn(0, text.length)
        val e = activeSelection.max.coerceIn(s, text.length)
        if (s == e) {
            return
        }
        recordHistory()
        val covering = ranges.filter { it.style == style && it.start <= s && it.end >= e }
        ranges = if (covering.isNotEmpty()) {
            // Remove style from selection
            val next = mutableListOf<StyleRange>()
            ranges.forEach { r ->
                if (r.style != style) {
                    next.add(r)
                    return@forEach
                }
                // subtract [s,e) from r
                if (r.end <= s || r.start >= e) {
                    next.add(r)
                } else {
                    if (r.start < s) next.add(r.copy(end = s))
                    if (r.end > e) next.add(r.copy(start = e))
                }
            }
            mergeRanges(next)
        } else {
            mergeRanges(ranges + StyleRange(s, e, style))
        }
        // Preserve selection
        selection = TextRange(s, e)
        lastNonEmptySelection = selection
        redoStack.clear()
        touchHistory()
    }

    fun applyBullet() {
        val pos = selection.min
        val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let {
            if (it < 0) 0 else it + 1
        }
        if (text.startsWith("• ", lineStart) || text.startsWith("- ", lineStart)) return
        recordHistory()
        val insert = "• "
        val newText = text.substring(0, lineStart) + insert + text.substring(lineStart)
        ranges = ranges.mapNotNull { it.shifted(insert.length, lineStart)?.clipped(newText.length) }
        text = newText
        selection = TextRange(selection.min + insert.length, selection.max + insert.length)
        lastNonEmptySelection = selection
        redoStack.clear()
        touchHistory()
    }

    fun applyNumbered() {
        recordHistory()
        val pos = selection.min
        val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let {
            if (it < 0) 0 else it + 1
        }
        var n = 1
        if (lineStart > 0) {
            val prevStart = text.lastIndexOf('\n', lineStart - 2).let { if (it < 0) 0 else it + 1 }
            val prevLine = text.substring(prevStart, lineStart).trimEnd('\n')
            val m = Regex("^(\\d+)\\.\\s").find(prevLine)
            if (m != null) n = (m.groupValues[1].toIntOrNull() ?: 0) + 1
        }
        val insert = "$n. "
        val newText = text.substring(0, lineStart) + insert + text.substring(lineStart)
        ranges = ranges.mapNotNull { it.shifted(insert.length, lineStart)?.clipped(newText.length) }
        text = newText
        selection = TextRange(selection.min + insert.length, selection.max + insert.length)
        lastNonEmptySelection = selection
        redoStack.clear()
        touchHistory()
    }

    private fun selectedLineIndexes(): Set<Int> {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val starts = buildList {
            add(0)
            text.forEachIndexed { index, c -> if (c == '\n') add(index + 1) }
        }
        return starts.mapIndexedNotNull { line, lineStart ->
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            if (start == end) {
                if (start in lineStart..lineEnd) line else null
            } else if (lineEnd >= start && lineStart <= end) {
                line
            } else null
        }.toSet()
    }

    private fun snapshot() = Snapshot(text, ranges, selection, alignments)

    private fun recordHistory() {
        undoStack.addLast(snapshot())
        if (undoStack.size > 100) undoStack.removeFirst()
    }

    private fun restore(snapshot: Snapshot) {
        text = snapshot.text
        ranges = snapshot.ranges
        selection = snapshot.selection
        alignments = snapshot.alignments
        lastNonEmptySelection = if (selection.min < selection.max) selection else lastNonEmptySelection
        touchHistory()
    }

    private fun touchHistory() {
        historyVersion++
    }

    private fun remapAlignments(
        oldText: String,
        newText: String,
        old: Map<Int, TextAlign>
    ): Map<Int, TextAlign> {
        val oldLines = oldText.split('\n')
        val newLines = newText.split('\n')
        return old.mapNotNull { (line, alignment) ->
            if (line < newLines.size && line < oldLines.size) line to alignment else null
        }.toMap()
    }

    private fun continueListIfNeeded(
        oldText: String,
        newValue: TextFieldValue
    ): TextFieldValue? {
        val newText = newValue.text
        if (newText.length <= oldText.length) return null
        val pos = newValue.selection.min
        if (pos <= 0 || pos > newText.length || newText[pos - 1] != '\n') return null

        val beforeNewline = pos - 1
        val lineStart = newText.lastIndexOf('\n', (beforeNewline - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        if (lineStart >= beforeNewline) return null
        val previousLine = newText.substring(lineStart, beforeNewline)

        val bullet = Regex("^[•\\-*]\\s+(.*)$").find(previousLine)
        if (bullet != null) {
            if (bullet.groupValues[1].isBlank()) {
                return TextFieldValue(
                    newText.removeRange(lineStart, pos),
                    TextRange(lineStart)
                )
            }
            return TextFieldValue(
                newText.replaceRange(pos, pos, "• "),
                TextRange(pos + 2)
            )
        }

        val numbered = Regex("^(\\d+)\\.\\s+(.*)$").find(previousLine)
        if (numbered != null) {
            if (numbered.groupValues[2].isBlank()) {
                return TextFieldValue(
                    newText.removeRange(lineStart, pos),
                    TextRange(lineStart)
                )
            }
            val next = (numbered.groupValues[1].toIntOrNull() ?: 0) + 1
            val prefix = "$next. "
            return TextFieldValue(
                newText.replaceRange(pos, pos, prefix),
                TextRange(pos + prefix.length)
            )
        }
        return null
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
            } else if (c == '\n' || c == '.' || c == '!' || c == '?') {
                capitalizeNext = true
            }
        }
        return String(chars)
    }

    fun isStyleActive(style: RichStyle): Boolean {
        val s = selection.min
        val e = selection.max
        if (s == e) return false
        return ranges.any { it.style == style && it.start <= s && it.end >= e }
    }

    fun toAnnotated(baseColor: Color): AnnotatedString {
        return buildAnnotatedString {
            if (text.isEmpty()) return@buildAnnotatedString
            // Build style map per character (simple, fine for journal lengths)
            val len = text.length
            val bold = BooleanArray(len)
            val italic = BooleanArray(len)
            val underline = BooleanArray(len)
            val strike = BooleanArray(len)
            val highlight = BooleanArray(len)
            ranges.forEach { r ->
                val a = r.start.coerceIn(0, len)
                val b = r.end.coerceIn(0, len)
                for (i in a until b) {
                    when (r.style) {
                        RichStyle.Bold -> bold[i] = true
                        RichStyle.Italic -> italic[i] = true
                        RichStyle.Underline -> underline[i] = true
                        RichStyle.Strike -> strike[i] = true
                        RichStyle.Highlight -> highlight[i] = true
                    }
                }
            }
            var i = 0
            while (i < len) {
                var j = i + 1
                while (j < len &&
                    bold[j] == bold[i] &&
                    italic[j] == italic[i] &&
                    underline[j] == underline[i] &&
                    strike[j] == strike[i] &&
                    highlight[j] == highlight[i]
                ) j++
                val decorations = mutableListOf<TextDecoration>()
                if (underline[i]) decorations.add(TextDecoration.Underline)
                if (strike[i]) decorations.add(TextDecoration.LineThrough)
                val style = SpanStyle(
                    color = baseColor,
                    fontWeight = if (bold[i]) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (italic[i]) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = when (decorations.size) {
                        0 -> TextDecoration.None
                        1 -> decorations[0]
                        else -> TextDecoration.combine(decorations)
                    },
                    background = if (highlight[i]) Color(0x337B6CFF) else Color.Unspecified,
                    fontSize = 17.sp
                )
                withStyle(style) { append(text.substring(i, j)) }
                i = j
            }
        }
    }

    /** Identity visual transformation that paints rich styles (plain text offsets). */
    fun visualTransformation(baseColor: Color): VisualTransformation =
        VisualTransformation {
            TransformedText(toAnnotated(baseColor), OffsetMapping.Identity)
        }

    private fun adjustRanges(
        oldText: String,
        newText: String,
        oldRanges: List<StyleRange>
    ): List<StyleRange> {
        // Find first difference
        var start = 0
        val minLen = minOf(oldText.length, newText.length)
        while (start < minLen && oldText[start] == newText[start]) start++
        var oldEnd = oldText.length
        var newEnd = newText.length
        while (oldEnd > start && newEnd > start &&
            oldText[oldEnd - 1] == newText[newEnd - 1]
        ) {
            oldEnd--; newEnd--
        }
        val deleted = oldEnd - start
        val inserted = newEnd - start
        val delta = inserted - deleted
        if (deleted == 0 && inserted == 0) return oldRanges

        val next = mutableListOf<StyleRange>()
        for (r in oldRanges) {
            when {
                r.end <= start -> next.add(r)
                r.start >= oldEnd -> {
                    val shifted = r.copy(start = r.start + delta, end = r.end + delta)
                    shifted.clipped(newText.length)?.let { next.add(it) }
                }
                else -> {
                    // Overlaps edit region — clip out deleted part
                    val s = r.start
                    val e = r.end
                    if (s < start && e > oldEnd) {
                        // Span covers edit: keep left, extend through insertion
                        next.add(StyleRange(s, start + inserted + (e - oldEnd), r.style))
                    } else if (s < start) {
                        next.add(StyleRange(s, start, r.style))
                    } else if (e > oldEnd) {
                        val ns = start + inserted
                        next.add(StyleRange(ns, ns + (e - oldEnd), r.style))
                    }
                    // else fully inside deleted region → drop
                }
            }
        }
        return mergeRanges(next.mapNotNull { it.clipped(newText.length) })
    }

    private fun mergeRanges(list: List<StyleRange>): List<StyleRange> {
        if (list.isEmpty()) return emptyList()
        val byStyle = list.groupBy { it.style }
        val out = mutableListOf<StyleRange>()
        byStyle.forEach { (style, group) ->
            val sorted = group.sortedBy { it.start }
            var cur = sorted.first()
            for (i in 1 until sorted.size) {
                val n = sorted[i]
                if (n.start <= cur.end) {
                    cur = cur.copy(end = maxOf(cur.end, n.end))
                } else {
                    out.add(cur)
                    cur = n
                }
            }
            out.add(cur)
        }
        return out.sortedWith(compareBy({ it.start }, { it.end }))
    }
}

/**
 * Encode/decode between plain+ranges and existing Markdown storage format:
 * **bold**  _italic_  __underline__  ~~strike~~  {{c}}highlight{{/c}}
 */
object MarkdownCodec {

    fun encode(text: String, ranges: List<StyleRange>): String {
        if (text.isEmpty()) return ""
        if (ranges.isEmpty()) return text

        // Event-based insertion of markers
        data class Ev(val pos: Int, val open: Boolean, val marker: String, val prio: Int)
        val events = mutableListOf<Ev>()
        ranges.forEach { r ->
            val (open, close, prio) = when (r.style) {
                RichStyle.Bold -> Triple("**", "**", 1)
                RichStyle.Italic -> Triple("_", "_", 2)
                RichStyle.Underline -> Triple("__", "__", 3)
                RichStyle.Strike -> Triple("~~", "~~", 4)
                RichStyle.Highlight -> Triple("{{c}}", "{{/c}}", 5)
            }
            events.add(Ev(r.start, true, open, prio))
            events.add(Ev(r.end, false, close, prio))
        }
        // Opens before closes at same pos; higher prio opens first; closes reverse
        events.sortWith(
            compareBy<Ev> { it.pos }
                .thenBy { if (it.open) 0 else 1 }
                .thenBy { if (it.open) it.prio else -it.prio }
        )

        val sb = StringBuilder()
        var cursor = 0
        for (ev in events) {
            if (ev.pos > cursor) {
                sb.append(text.substring(cursor, ev.pos.coerceAtMost(text.length)))
                cursor = ev.pos.coerceAtMost(text.length)
            }
            sb.append(ev.marker)
        }
        if (cursor < text.length) sb.append(text.substring(cursor))
        return sb.toString()
    }

    fun decode(markdown: String): Pair<String, List<StyleRange>> {
        if (markdown.isEmpty()) return "" to emptyList()

        val plain = StringBuilder()
        val ranges = mutableListOf<StyleRange>()
        // Active open markers: style -> plain start index
        val openAt = mutableMapOf<RichStyle, Int>()

        var i = 0
        while (i < markdown.length) {
            when {
                markdown.startsWith("{{c}}", i) -> {
                    openAt[RichStyle.Highlight] = plain.length
                    i += 5
                }
                markdown.startsWith("{{/c}}", i) -> {
                    openAt.remove(RichStyle.Highlight)?.let { start ->
                        ranges.add(StyleRange(start, plain.length, RichStyle.Highlight))
                    }
                    i += 6
                }
                markdown.startsWith("**", i) -> {
                    if (openAt.containsKey(RichStyle.Bold)) {
                        openAt.remove(RichStyle.Bold)?.let { start ->
                            ranges.add(StyleRange(start, plain.length, RichStyle.Bold))
                        }
                    } else {
                        openAt[RichStyle.Bold] = plain.length
                    }
                    i += 2
                }
                markdown.startsWith("__", i) -> {
                    if (openAt.containsKey(RichStyle.Underline)) {
                        openAt.remove(RichStyle.Underline)?.let { start ->
                            ranges.add(StyleRange(start, plain.length, RichStyle.Underline))
                        }
                    } else {
                        openAt[RichStyle.Underline] = plain.length
                    }
                    i += 2
                }
                markdown.startsWith("~~", i) -> {
                    if (openAt.containsKey(RichStyle.Strike)) {
                        openAt.remove(RichStyle.Strike)?.let { start ->
                            ranges.add(StyleRange(start, plain.length, RichStyle.Strike))
                        }
                    } else {
                        openAt[RichStyle.Strike] = plain.length
                    }
                    i += 2
                }
                markdown[i] == '_' -> {
                    if (openAt.containsKey(RichStyle.Italic)) {
                        openAt.remove(RichStyle.Italic)?.let { start ->
                            ranges.add(StyleRange(start, plain.length, RichStyle.Italic))
                        }
                    } else {
                        openAt[RichStyle.Italic] = plain.length
                    }
                    i += 1
                }
                markdown[i] == '*' &&
                    (i + 1 >= markdown.length || markdown[i + 1] != '*') &&
                    (openAt.containsKey(RichStyle.Italic) ||
                        i + 1 >= markdown.length ||
                        !markdown[i + 1].isWhitespace()) -> {
                    if (openAt.containsKey(RichStyle.Italic)) {
                        openAt.remove(RichStyle.Italic)?.let { start ->
                            ranges.add(StyleRange(start, plain.length, RichStyle.Italic))
                        }
                    } else {
                        openAt[RichStyle.Italic] = plain.length
                    }
                    i += 1
                }
                else -> {
                    plain.append(markdown[i])
                    i++
                }
            }
        }
        return plain.toString() to ranges
    }
}
