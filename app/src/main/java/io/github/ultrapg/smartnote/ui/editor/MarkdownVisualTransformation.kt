package io.github.ultrapg.smartnote.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Non-destructive visual Markdown transformation: keeps exact UTF-8 character offsets (1:1)
 * while applying rich editorial styling for headings, **bold**, *italic*, `code`, and ~~strike~~.
 */
class MarkdownVisualTransformation(
    private val codeBackground: Color,
    private val syntaxMarkerColor: Color,
    private val headingColor: Color
) : VisualTransformation {

    private val boldRegex = Regex("""\*\*(.+?)\*\*""")
    private val italicRegex = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")
    private val codeRegex = Regex("""`([^`]+)`""")
    private val strikeRegex = Regex("""~~(.+?)~~""")
    private val headingRegex = Regex("""(?m)^(#{1,3})\s+(.+)$""")
    private val bulletRegex = Regex("""(?m)^(\s*[-*•])\s+""")

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val builder = AnnotatedString.Builder(raw)

        headingRegex.findAll(raw).forEach { m ->
            val hashes = m.groups[1]!!
            val content = m.groups[2]!!
            builder.addStyle(SpanStyle(color = syntaxMarkerColor), hashes.range.first, hashes.range.last + 1)
            val size = when (hashes.value.length) {
                1 -> 20.sp
                2 -> 18.sp
                else -> 16.sp
            }
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, fontSize = size, color = headingColor),
                content.range.first,
                content.range.last + 1
            )
        }

        bulletRegex.findAll(raw).forEach { m ->
            val g = m.groups[1]!!
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, color = headingColor),
                g.range.first,
                g.range.last + 1
            )
        }

        boldRegex.findAll(raw).forEach { m ->
            builder.addStyle(SpanStyle(color = syntaxMarkerColor), m.range.first, m.range.first + 2)
            builder.addStyle(SpanStyle(color = syntaxMarkerColor), m.range.last - 1, m.range.last + 1)
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                m.range.first + 2,
                m.range.last - 1
            )
        }

        italicRegex.findAll(raw).forEach { m ->
            builder.addStyle(SpanStyle(color = syntaxMarkerColor), m.range.first, m.range.first + 1)
            builder.addStyle(SpanStyle(color = syntaxMarkerColor), m.range.last, m.range.last + 1)
            builder.addStyle(
                SpanStyle(fontStyle = FontStyle.Italic),
                m.range.first + 1,
                m.range.last
            )
        }

        codeRegex.findAll(raw).forEach { m ->
            builder.addStyle(SpanStyle(color = syntaxMarkerColor), m.range.first, m.range.first + 1)
            builder.addStyle(SpanStyle(color = syntaxMarkerColor), m.range.last, m.range.last + 1)
            builder.addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                m.range.first,
                m.range.last + 1
            )
        }

        strikeRegex.findAll(raw).forEach { m ->
            builder.addStyle(SpanStyle(color = syntaxMarkerColor), m.range.first, m.range.first + 2)
            builder.addStyle(SpanStyle(color = syntaxMarkerColor), m.range.last - 1, m.range.last + 1)
            builder.addStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough),
                m.range.first + 2,
                m.range.last - 1
            )
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
