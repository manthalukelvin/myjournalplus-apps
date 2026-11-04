package com.celmatech.myjournalplus.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Journal body markdown stored as plain string with markers.
 * **bold**  _italic_  __underline__  ~~strike~~  {{c}}color{{/c}}
 * Lists: "• " and "1. " "2. " (auto-continue on Enter).
 *
 * Editor visual transformation hides markers and applies real styles so the
 * user never sees raw ** / _ / ~~ / {{c}} while typing or selecting.
 * Preview (toAnnotated) also strips markers and keeps styles intact.
 */
object MarkdownFormat {

    private val Accent = Color(0xFF7B6CFF)

    /** Preview — markers removed, styles applied. */
    fun toAnnotated(raw: String, baseColor: Color = Color(0xFF374151)): AnnotatedString {
        if (raw.isBlank()) return AnnotatedString("(No content)")
        return buildAnnotatedString {
            val lines = raw.split('\n')
            lines.forEachIndexed { idx, line ->
                appendInlineHideMarkers(line, baseColor)
                if (idx < lines.lastIndex) append("\n")
            }
        }
    }

    /**
     * Editor VisualTransformation that HIDES markdown markers and applies
     * the corresponding styles. Cursor mapping stays correct via custom OffsetMapping.
     */
    fun visualTransformation(baseColor: Color = Color(0xFF1A1523)): VisualTransformation =
        VisualTransformation { text ->
            val raw = text.text
            val (styled, mapping) = buildHiddenMarkers(raw, baseColor)
            TransformedText(styled, mapping)
        }

    /**
     * Builds an AnnotatedString without the marker characters and an OffsetMapping
     * so original (source) offsets map to the displayed (transformed) offsets.
     */
    private fun buildHiddenMarkers(raw: String, baseColor: Color): Pair<AnnotatedString, OffsetMapping> {
        data class Seg(val startSrc: Int, val endSrc: Int, val style: SpanStyle?)

        val segments = mutableListOf<Seg>()
        var i = 0
        while (i < raw.length) {
            when {
                raw.startsWith("**", i) -> {
                    val end = raw.indexOf("**", i + 2)
                    if (end > i) {
                        // skip markers, keep content bold
                        segments += Seg(i + 2, end, SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                        i = end + 2
                    } else {
                        segments += Seg(i, i + 1, SpanStyle(color = baseColor))
                        i++
                    }
                }
                raw.startsWith("~~", i) -> {
                    val end = raw.indexOf("~~", i + 2)
                    if (end > i) {
                        segments += Seg(i + 2, end, SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor))
                        i = end + 2
                    } else {
                        segments += Seg(i, i + 1, SpanStyle(color = baseColor))
                        i++
                    }
                }
                raw.startsWith("__", i) -> {
                    val end = raw.indexOf("__", i + 2)
                    if (end > i) {
                        segments += Seg(i + 2, end, SpanStyle(textDecoration = TextDecoration.Underline, color = baseColor))
                        i = end + 2
                    } else {
                        segments += Seg(i, i + 1, SpanStyle(color = baseColor))
                        i++
                    }
                }
                raw.startsWith("{{c}}", i) -> {
                    val end = raw.indexOf("{{/c}}", i + 5)
                    if (end > i) {
                        segments += Seg(i + 5, end, SpanStyle(color = Accent))
                        i = end + 6
                    } else {
                        segments += Seg(i, i + 1, SpanStyle(color = baseColor))
                        i++
                    }
                }
                raw.startsWith("_", i) && (i + 1 >= raw.length || raw[i + 1] != '_') -> {
                    // single _italic_
                    val end = raw.indexOf('_', i + 1)
                    if (end > i) {
                        segments += Seg(i + 1, end, SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                        i = end + 1
                    } else {
                        segments += Seg(i, i + 1, SpanStyle(color = baseColor))
                        i++
                    }
                }
                else -> {
                    // plain run until next potential marker
                    var j = i + 1
                    while (j < raw.length) {
                        val c = raw[j]
                        if (c == '*' || c == '~' || c == '_' || (c == '{' && raw.startsWith("{{c}}", j))) break
                        j++
                    }
                    segments += Seg(i, j, SpanStyle(color = baseColor))
                    i = j
                }
            }
        }

        val annotated = buildAnnotatedString {
            segments.forEach { seg ->
                if (seg.style != null) {
                    withStyle(seg.style) {
                        append(raw.substring(seg.startSrc, seg.endSrc))
                    }
                } else {
                    append(raw.substring(seg.startSrc, seg.endSrc))
                }
            }
        }

        // Build source -> transformed and reverse maps
        // source indices that are kept (content) map to consecutive transformed indices
        val srcToTrans = IntArray(raw.length + 1) { -1 }
        val transToSrc = mutableListOf<Int>()
        var t = 0
        segments.forEach { seg ->
            for (s in seg.startSrc until seg.endSrc) {
                srcToTrans[s] = t
                transToSrc += s
                t++
            }
        }
        srcToTrans[raw.length] = t
        // fill gaps (markers) with nearest
        var last = 0
        for (s in 0..raw.length) {
            if (srcToTrans[s] >= 0) last = srcToTrans[s]
            else srcToTrans[s] = last
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, raw.length)
                return srcToTrans[o].coerceIn(0, annotated.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (transToSrc.isEmpty()) return 0
                val o = offset.coerceIn(0, transToSrc.size)
                return if (o >= transToSrc.size) raw.length else transToSrc[o]
            }
        }
        return annotated to mapping
    }

    private fun AnnotatedString.Builder.appendInlineHideMarkers(line: String, baseColor: Color) {
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("**", i) -> {
                    val end = line.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                            append(line.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        withStyle(SpanStyle(color = baseColor)) { append(line[i]) }
                        i++
                    }
                }
                line.startsWith("~~", i) -> {
                    val end = line.indexOf("~~", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor)) {
                            append(line.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        withStyle(SpanStyle(color = baseColor)) { append(line[i]) }
                        i++
                    }
                }
                line.startsWith("__", i) -> {
                    val end = line.indexOf("__", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = baseColor)) {
                            append(line.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        withStyle(SpanStyle(color = baseColor)) { append(line[i]) }
                        i++
                    }
                }
                line.startsWith("{{c}}", i) -> {
                    val end = line.indexOf("{{/c}}", i + 5)
                    if (end > i) {
                        withStyle(SpanStyle(color = Accent)) {
                            append(line.substring(i + 5, end))
                        }
                        i = end + 6
                    } else {
                        withStyle(SpanStyle(color = baseColor)) { append(line[i]) }
                        i++
                    }
                }
                line.startsWith("_", i) && (i + 1 >= line.length || line[i + 1] != '_') -> {
                    val end = line.indexOf('_', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                            append(line.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        withStyle(SpanStyle(color = baseColor)) { append(line[i]) }
                        i++
                    }
                }
                else -> {
                    withStyle(SpanStyle(color = baseColor)) { append(line[i]) }
                    i++
                }
            }
        }
    }

    fun plainText(raw: String): String =
        raw.replace(Regex("\\*\\*|__|~~|\\{\\{c\\}\\}|\\{\\{/c\\}\\}|_"), "")

    fun wrap(value: TextFieldValue, open: String, close: String = open): TextFieldValue {
        val t = value.text
        val s = value.selection.min
        val e = value.selection.max
        val selected = if (s < e) t.substring(s, e) else ""
        if (selected.startsWith(open) && selected.endsWith(close) &&
            selected.length >= open.length + close.length
        ) {
            val inner = selected.removePrefix(open).removeSuffix(close)
            val newText = t.replaceRange(s, e, inner)
            return TextFieldValue(newText, androidx.compose.ui.text.TextRange(s, s + inner.length))
        }
        val inserted = open + selected + close
        val newText = t.replaceRange(s, e, inserted)
        val cursor = if (selected.isEmpty()) s + open.length else s + inserted.length
        return TextFieldValue(newText, androidx.compose.ui.text.TextRange(cursor))
    }

    fun continueListIfNeeded(oldVal: TextFieldValue, newVal: TextFieldValue): TextFieldValue? {
        val old = oldVal.text
        val neu = newVal.text
        if (neu.length < old.length) return null
        val pos = newVal.selection.min
        if (pos <= 0 || pos > neu.length) return null
        if (neu[pos - 1] != '\n') return null
        if (neu.length - old.length > 4) return null

        val beforeNl = pos - 1
        val lineStart = neu.lastIndexOf('\n', (beforeNl - 1).coerceAtLeast(0)).let {
            if (it < 0) 0 else it + 1
        }
        if (lineStart >= beforeNl) return null
        val prevLine = neu.substring(lineStart, beforeNl)

        val bullet = Regex("^([•\\-*])\\s+(.*)$").find(prevLine)
        if (bullet != null) {
            val content = bullet.groupValues[2]
            if (content.isBlank()) {
                val newText = neu.removeRange(lineStart, pos)
                return TextFieldValue(newText, androidx.compose.ui.text.TextRange(lineStart))
            }
            val insert = "• "
            return TextFieldValue(neu.replaceRange(pos, pos, insert), androidx.compose.ui.text.TextRange(pos + insert.length))
        }
        val num = Regex("^(\\d+)\\.\\s+(.*)$").find(prevLine)
        if (num != null) {
            val content = num.groupValues[2]
            if (content.isBlank()) {
                val newText = neu.removeRange(lineStart, pos)
                return TextFieldValue(newText, androidx.compose.ui.text.TextRange(lineStart))
            }
            val next = (num.groupValues[1].toIntOrNull() ?: 0) + 1
            val insert = "$next. "
            return TextFieldValue(neu.replaceRange(pos, pos, insert), androidx.compose.ui.text.TextRange(pos + insert.length))
        }
        return null
    }

    fun insertBullet(value: TextFieldValue): TextFieldValue {
        val t = value.text
        val pos = value.selection.min
        val lineStart = t.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val rest = t.substring(lineStart)
        if (rest.startsWith("• ") || rest.startsWith("- ") || rest.startsWith("* ")) return value
        val prefix = "• "
        return TextFieldValue(t.replaceRange(lineStart, lineStart, prefix), androidx.compose.ui.text.TextRange(lineStart + prefix.length))
    }

    fun insertNumbered(value: TextFieldValue): TextFieldValue {
        val t = value.text
        val pos = value.selection.min
        val lineStart = t.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        var n = 1
        if (lineStart > 0) {
            val prevStart = t.lastIndexOf('\n', lineStart - 2).let { if (it < 0) 0 else it + 1 }
            val prevLine = t.substring(prevStart, lineStart).trimEnd('\n')
            val m = Regex("^(\\d+)\\.\\s").find(prevLine)
            if (m != null) n = (m.groupValues[1].toIntOrNull() ?: 0) + 1
        }
        val rest = t.substring(lineStart)
        if (Regex("^\\d+\\.\\s").containsMatchIn(rest)) return value
        val prefix = "$n. "
        return TextFieldValue(t.replaceRange(lineStart, lineStart, prefix), androidx.compose.ui.text.TextRange(lineStart + prefix.length))
    }
}
