package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.theme.KalynoBlue

private enum class RKind { P, H, QUOTE, LI }
private class RFrac(val num: String, val den: String)
private class RBlock(val kind: RKind, val text: AnnotatedString, val fracs: Map<String, RFrac>)

private val FRAC_RE = Regex(
    """<span class="frac"[^>]*>\s*<span class="fn"[^>]*>(.*?)</span>\s*<span class="fd"[^>]*>(.*?)</span>\s*</span>""",
    RegexOption.DOT_MATCHES_ALL
)
private val TAG_RE = Regex("<[^>]*>")
private val SPACES_RE = Regex("\\s+")
private val COLOR_RE = Regex("""(?<![-\w])color\s*:\s*#([0-9a-fA-F]{3,6})""")
private val BG_RE = Regex("""background-color\s*:\s*#([0-9a-fA-F]{3,6})""")
private const val MARK_OPEN = '\uE000'
private const val MARK_CLOSE = '\uE001'

private fun decode(s: String): String = s
    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
    .replace("\u200B", "")

private fun plain(s: String): String = decode(TAG_RE.replace(s, "")).trim()

private fun hexColor(h: String): Color? {
    val full = when (h.length) {
        3 -> h.map { "$it$it" }.joinToString("")
        6 -> h
        else -> return null
    }
    return try {
        Color(android.graphics.Color.parseColor("#$full"))
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun parseRich(html: String): List<RBlock> {
    val fracs = ArrayList<RFrac>()
    val src = FRAC_RE.replace(html) { m ->
        fracs.add(RFrac(plain(m.groupValues[1]), plain(m.groupValues[2])))
        "$MARK_OPEN${fracs.size - 1}$MARK_CLOSE"
    }

    val blocks = ArrayList<RBlock>()
    var builder = AnnotatedString.Builder()
    var kind = RKind.P
    var empty = true
    var blockFracs = LinkedHashMap<String, RFrac>()
    val stack = ArrayList<Boolean>()
    val ordered = ArrayList<Boolean>()
    val counters = ArrayList<Int>()

    fun flush() {
        if (!empty) blocks.add(RBlock(kind, builder.toAnnotatedString(), blockFracs))
        builder = AnnotatedString.Builder()
        blockFracs = LinkedHashMap()
        empty = true
        for (j in stack.indices) stack[j] = false
    }

    fun push(style: SpanStyle) {
        builder.pushStyle(style)
        stack.add(true)
    }

    fun appendText(raw: String) {
        val text = decode(raw)
        var k = 0
        while (k < text.length) {
            if (text[k] == MARK_OPEN) {
                val close = text.indexOf(MARK_CLOSE, k)
                val idx = if (close > k) text.substring(k + 1, close).toIntOrNull() else null
                if (idx != null && idx in fracs.indices) {
                    val id = "f$idx"
                    blockFracs[id] = fracs[idx]
                    builder.appendInlineContent(id, "(${fracs[idx].num})/(${fracs[idx].den})")
                    empty = false
                }
                k = if (close > k) close + 1 else k + 1
            } else {
                val nextMark = text.indexOf(MARK_OPEN, k).let { if (it < 0) text.length else it }
                var piece = SPACES_RE.replace(text.substring(k, nextMark), " ")
                if (empty) piece = piece.trimStart()
                if (piece.isNotEmpty()) {
                    builder.append(piece)
                    if (piece.isNotBlank()) empty = false
                }
                k = nextMark
            }
        }
    }

    fun openTag(name: String, raw: String) {
        when (name) {
            "p", "div" -> { flush(); kind = RKind.P }
            "h1", "h2", "h3" -> { flush(); kind = RKind.H }
            "blockquote" -> { flush(); kind = RKind.QUOTE }
            "ul" -> { ordered.add(false); counters.add(0) }
            "ol" -> { ordered.add(true); counters.add(0) }
            "li" -> {
                flush()
                kind = RKind.LI
                if (counters.isNotEmpty()) counters[counters.lastIndex] = counters.last() + 1
                val n = counters.lastOrNull() ?: 1
                builder.append(if (ordered.lastOrNull() == true) "$n. " else "• ")
            }
            "br" -> builder.append("\n")
            "strong", "b" -> push(SpanStyle(fontWeight = FontWeight.Bold))
            "em", "i" -> push(SpanStyle(fontStyle = FontStyle.Italic))
            "u" -> push(SpanStyle(textDecoration = TextDecoration.Underline))
            "sup" -> push(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em))
            "sub" -> push(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em))
            "mark" -> push(SpanStyle(background = Color(0xFFFFF0B3)))
            "span", "font" -> {
                val color = COLOR_RE.find(raw)?.let { hexColor(it.groupValues[1]) }
                val bg = BG_RE.find(raw)?.let { hexColor(it.groupValues[1]) }
                if (color != null || bg != null) {
                    push(SpanStyle(color = color ?: Color.Unspecified, background = bg ?: Color.Unspecified))
                } else {
                    stack.add(false)
                }
            }
            else -> stack.add(false)
        }
    }

    fun closeTag(name: String) {
        when (name) {
            "p", "div", "h1", "h2", "h3", "blockquote", "li" -> { flush(); kind = RKind.P }
            "ul", "ol" -> {
                if (ordered.isNotEmpty()) ordered.removeAt(ordered.lastIndex)
                if (counters.isNotEmpty()) counters.removeAt(counters.lastIndex)
            }
            "br" -> {}
            else -> if (stack.isNotEmpty()) {
                val pushed = stack.removeAt(stack.lastIndex)
                if (pushed) builder.pop()
            }
        }
    }

    var i = 0
    while (i < src.length) {
        if (src[i] == '<') {
            val end = src.indexOf('>', i)
            if (end < 0) {
                appendText(src.substring(i))
                break
            }
            val raw = src.substring(i + 1, end).trim()
            i = end + 1
            val closing = raw.startsWith("/")
            val name = raw.removePrefix("/").trimEnd('/').trim().takeWhile { it.isLetterOrDigit() }.lowercase()
            if (closing) closeTag(name) else openTag(name, raw)
        } else {
            val next = src.indexOf('<', i).let { if (it < 0) src.length else it }
            appendText(src.substring(i, next))
            i = next
        }
    }
    flush()
    return blocks
}

private fun buildInline(fracs: Map<String, RFrac>, color: Color, fontSize: TextUnit): Map<String, InlineTextContent> =
    fracs.mapValues { (_, f) ->
        val widthEm = maxOf(f.num.length, f.den.length) * 0.56f + 0.6f
        val small = (fontSize.value * 0.82f).sp
        InlineTextContent(
            Placeholder(
                width = widthEm.em,
                height = 2.4.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(f.num, color = color, fontSize = small, lineHeight = small, maxLines = 1, softWrap = false)
                Box(Modifier.fillMaxWidth().height(1.dp).background(color))
                Text(f.den, color = color, fontSize = small, lineHeight = small, maxLines = 1, softWrap = false)
            }
        }
    }

/** Affiche le contenu d'une capsule : HTML simple (gras, couleurs, exposants, fractions...) ou texte brut. */
@Composable
fun RichText(
    html: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal
) {
    if (!html.contains('<')) {
        Text(text = html, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight)
        return
    }
    val blocks = remember(html) { parseRich(html) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) RichBlockView(block, color, fontSize, lineHeight, fontWeight)
    }
}

@Composable
private fun RichBlockView(block: RBlock, color: Color, fontSize: TextUnit, lineHeight: TextUnit, fontWeight: FontWeight) {
    val inline = remember(block, color, fontSize) { buildInline(block.fracs, color, fontSize) }
    val heading = block.kind == RKind.H
    val size = if (heading) (fontSize.value * 1.2f).sp else fontSize
    val lh = if (block.fracs.isNotEmpty()) TextUnit.Unspecified else lineHeight
    val body: @Composable () -> Unit = {
        Text(
            text = block.text,
            inlineContent = inline,
            color = color,
            fontSize = size,
            lineHeight = lh,
            fontWeight = if (heading) FontWeight.ExtraBold else fontWeight
        )
    }
    when (block.kind) {
        RKind.QUOTE -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(KalynoBlue))
            Box(Modifier.padding(start = 10.dp)) { body() }
        }
        RKind.LI -> Box(Modifier.padding(start = 8.dp)) { body() }
        else -> body()
    }
}
