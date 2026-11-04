package com.celmatech.myjournalplus.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Lightweight Markdown → AnnotatedString for AI assistant replies.
 * Supports: **bold**, *italic* / _italic_, ~~strike~~, `code`,
 * # / ## / ### headings, - / * / numbered lists, > blockquotes.
 * Does not show raw markers in the final UI.
 */
object AiMarkdown {

    fun toAnnotated(
        raw: String,
        baseColor: Color,
        codeBg: Color = Color(0x147B6CFF)
    ): AnnotatedString {
        if (raw.isBlank()) return AnnotatedString("")
        return buildAnnotatedString {
            val lines = raw.replace("\r\n", "\n").split("\n")
            lines.forEachIndexed { idx, line ->
                appendLineContent(line, baseColor, codeBg)
                if (idx < lines.lastIndex) append("\n")
            }
        }
    }

    private fun AnnotatedString.Builder.appendLineContent(
        line: String,
        baseColor: Color,
        codeBg: Color
    ) {
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length

        when {
            trimmed.startsWith("### ") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = baseColor))
                appendInline(trimmed.removePrefix("### ").trim(), baseColor, codeBg)
                pop()
            }
            trimmed.startsWith("## ") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = baseColor))
                appendInline(trimmed.removePrefix("## ").trim(), baseColor, codeBg)
                pop()
            }
            trimmed.startsWith("# ") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = baseColor))
                appendInline(trimmed.removePrefix("# ").trim(), baseColor, codeBg)
                pop()
            }
            trimmed.startsWith("> ") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor.copy(alpha = 0.85f)))
                append("│ ")
                appendInline(trimmed.removePrefix("> ").trim(), baseColor, codeBg)
                pop()
            }
            trimmed.matches(Regex("^[-*]\\s+.*")) -> {
                append("  • ")
                appendInline(trimmed.replace(Regex("^[-*]\\s+"), ""), baseColor, codeBg)
            }
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val num = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)
                if (num != null) {
                    append("  ${num.groupValues[1]}. ")
                    appendInline(num.groupValues[2], baseColor, codeBg)
                } else {
                    appendInline(trimmed, baseColor, codeBg)
                }
            }
            else -> {
                if (indent > 0) append(" ".repeat(minOf(indent, 4)))
                appendInline(trimmed, baseColor, codeBg)
            }
        }
    }

    private fun AnnotatedString.Builder.appendInline(
        text: String,
        baseColor: Color,
        codeBg: Color
    ) {
        // Order: code, bold, italic, strike
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("`", i) -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBg,
                                fontSize = 13.sp,
                                color = baseColor
                            )
                        )
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                        appendInline(text.substring(i + 2, end), baseColor, codeBg)
                        pop()
                        i = end + 2
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i) {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("*", i) || text.startsWith("_", i) -> {
                    val marker = text[i].toString()
                    // avoid matching ** already handled
                    if (i + 1 < text.length && text[i + 1].toString() == marker) {
                        append(text[i]); i++
                        continue
                    }
                    val end = text.indexOf(marker, i + 1)
                    if (end > i) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
