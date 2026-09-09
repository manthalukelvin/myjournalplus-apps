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

    /** Editor — full source kept (cursor OK), styles overlaid. */
    fun visualTransformation(baseColor: Color = Color(0xFF1A1523)): VisualTransformation =
        VisualTransformation { text ->
            val styled = buildAnnotatedString {
                append(text.text)
                applyStylesOnRaw(text.text, baseColor)
            }
            TransformedText(styled, OffsetMapping.Identity)
        }

    private fun AnnotatedString.Builder.applyStylesOnRaw(raw: String, baseColor: Color) {
        Regex("\\*\\*(.+?)\\*\\*", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor), m.range.first + 2, m.range.last)
        }
        Regex("~~(.+?)~~", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor), m.range.first + 2, m.range.last)
        }
        Regex("__(.+?)__", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = baseColor), m.range.first + 2, m.range.last)
        }
        Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)").findAll(raw).forEach { m ->
            addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor), m.range.first + 1, m.range.last)
        }
        Regex("\\{\\{c\\}\\}(.+?)\\{\\{/c\\}\\}", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            val innerStart = m.range.first + 5
            val innerEnd = m.range.last - 5
            if (innerStart < innerEnd) addStyle(SpanStyle(color = Accent), innerStart, innerEnd)
        }
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
                    } else { append(line[i]); i++ }
                }
                line.startsWith("~~", i) -> {
                    val end = line.indexOf("~~", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor)) {
                            append(line.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(line[i]); i++ }
                }
                line.startsWith("__", i) -> {
                    val end = line.indexOf("__", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = baseColor)) {
                            append(line.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(line[i]); i++ }
                }
                line.startsWith("_", i) && (i + 1 >= line.length || line[i + 1] != '_') -> {
                    val end = line.indexOf('_', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                            append(line.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(line[i]); i++ }
                }
                line.startsWith("{{c}}", i) -> {
                    val end = line.indexOf("{{/c}}", i + 5)
                    if (end > i) {
                        withStyle(SpanStyle(color = Accent)) {
                            append(line.substring(i + 5, end))
                        }
                        i = end + 6
                    } else { append(line[i]); i++ }
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
