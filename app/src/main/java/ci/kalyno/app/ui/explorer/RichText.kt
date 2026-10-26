package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.theme.KalynoBlue
import ru.noties.jlatexmath.JLatexMathDrawable

private enum class RKind { P, H, QUOTE, LI, DISPLAY, BOX }
private class RFrac(val num: String, val den: String)
private class RBlock(
    val kind: RKind,
    val text: AnnotatedString,
    val fracs: Map<String, RFrac>,
    val maths: Map<String, String>,
    val boxBorder: Color? = null,
    val boxBackground: Color? = null
)

private val FRAC_RE = Regex(
    """<span class="frac"[^>]*>\s*<span class="fn"[^>]*>(.*?)</span>\s*<span class="fd"[^>]*>(.*?)</span>\s*</span>""",
    RegexOption.DOT_MATCHES_ALL
)
private val MATH_RE = Regex("""\$\$(.+?)\$\$|\$([^$]+?)\$""", RegexOption.DOT_MATCHES_ALL)
private val TAG_RE = Regex("<[^>]*>")
private val SPACES_RE = Regex("\\s+")
private val COLOR_RE = Regex("""(?<![-\w])color\s*:\s*#([0-9a-fA-F]{3,6})""")
private val BG_RE = Regex("""background-color\s*:\s*#([0-9a-fA-F]{3,6})""")
private val BORDER_RE = Regex("""border\s*:\s*\d+px\s+solid\s+#([0-9a-fA-F]{3,6})""")
private const val MARK_OPEN = '\uE000'
private const val MARK_CLOSE = '\uE001'

private fun decode(s: String): String = s
    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
    .replace("\u200B", "")

private fun plain(s: String): String = decode(TAG_RE.replace(s, "")).trim()

// Formule coupée par des balises (couleur, gras...) : on les retire dans le $...$, comme l'aperçu admin.
private val MATH_SPAN_RE = Regex("""\$\$(.{1,800}?)\$\$|\$([^$]{1,800}?)\$""", RegexOption.DOT_MATCHES_ALL)
private val BR_RE = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

private fun unwrapMath(html: String): String {
    if (!html.contains('$')) return html
    return MATH_SPAN_RE.replace(html) { m ->
        val display = m.groupValues[1].isNotEmpty()
        val inner = if (display) m.groupValues[1] else m.groupValues[2]
        if (!inner.contains('<')) {
            m.value
        } else {
            val flat = TAG_RE.replace(BR_RE.replace(inner, " "), "")
            if (display) "\$\$" + flat + "\$\$" else "\$" + flat + "\$"
        }
    }
}

private fun normalizeTex(tex: String): String = tex
    .replace('\u00A0', ' ').replace("\u200B", "")
    .replace('\u2212', '-')
    .replace("\u25A2", "")
    .trim()

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
    val src = FRAC_RE.replace(unwrapMath(html)) { m ->
        fracs.add(RFrac(plain(m.groupValues[1]), plain(m.groupValues[2])))
        "$MARK_OPEN${fracs.size - 1}$MARK_CLOSE"
    }

    val blocks = ArrayList<RBlock>()
    var builder = AnnotatedString.Builder()
    var kind = RKind.P
    var empty = true
    var blockFracs = LinkedHashMap<String, RFrac>()
    var blockMaths = LinkedHashMap<String, String>()
    var mathCount = 0
    var activeBoxBorder: Color? = null
    var activeBoxBackground: Color? = null
    val stack = ArrayList<Boolean>()
    val ordered = ArrayList<Boolean>()
    val counters = ArrayList<Int>()

    fun flush() {
        if (!empty) blocks.add(RBlock(kind, builder.toAnnotatedString(), blockFracs, blockMaths, activeBoxBorder, activeBoxBackground))
        builder = AnnotatedString.Builder()
        blockFracs = LinkedHashMap()
        blockMaths = LinkedHashMap()
        empty = true
        for (j in stack.indices) stack[j] = false
    }

    fun push(style: SpanStyle) {
        builder.pushStyle(style)
        stack.add(true)
    }

    fun appendPlain(text: String) {
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

    fun appendMath(tex: String, display: Boolean) {
        val clean = normalizeTex(tex)
        if (clean.isEmpty()) return
        if (display) {
            flush()
            kind = RKind.DISPLAY
        }
        val id = "m${mathCount++}"
        blockMaths[id] = clean
        builder.appendInlineContent(id, clean)
        empty = false
        if (display) {
            flush()
            kind = RKind.P
        }
    }

    fun appendText(raw: String) {
        val text = decode(raw)
        var last = 0
        for (m in MATH_RE.findAll(text)) {
            appendPlain(text.substring(last, m.range.first))
            val display = m.groupValues[1].isNotEmpty()
            appendMath(if (display) m.groupValues[1] else m.groupValues[2], display)
            last = m.range.last + 1
        }
        appendPlain(text.substring(last))
    }

    fun openTag(name: String, raw: String) {
        when (name) {
            "p" -> { flush(); kind = if (activeBoxBorder != null) RKind.BOX else RKind.P }
            "div" -> {
                val border = BORDER_RE.find(raw)?.let { hexColor(it.groupValues[1]) }
                val background = BG_RE.find(raw)?.let { hexColor(it.groupValues[1]) }
                if (border != null) {
                    flush()
                    activeBoxBorder = border
                    activeBoxBackground = background
                    kind = RKind.BOX
                } else {
                    flush()
                    kind = if (activeBoxBorder != null) RKind.BOX else RKind.P
                }
            }
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
            "s", "strike", "del" -> push(SpanStyle(textDecoration = TextDecoration.LineThrough))
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
            "p", "h1", "h2", "h3", "blockquote", "li" -> { flush(); kind = if (activeBoxBorder != null) RKind.BOX else RKind.P }
            "div" -> { flush(); activeBoxBorder = null; activeBoxBackground = null; kind = RKind.P }
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

private val CASES_RE = Regex("""\\begin\{cases\}(.*?)\\end\{cases\}""", RegexOption.DOT_MATCHES_ALL)

// Formule d'origine d'abord ; si JLaTeXMath la refuse, on tente des écritures équivalentes.
private fun texVariants(tex: String): List<String> {
    val list = ArrayList<String>()
    list.add(tex)
    if (CASES_RE.containsMatchIn(tex)) {
        list.add(CASES_RE.replace(tex) { m ->
            val body = m.groupValues[1]
            val cols = (body.split("\\\\").maxOfOrNull { row -> row.count { it == '&' } } ?: 0) + 1
            "\\left\\{\\begin{array}{" + "l".repeat(cols) + "}" + body + "\\end{array}\\right."
        })
    }
    if (tex.contains("\\dfrac") || tex.contains("\\tfrac")) {
        list.add(tex.replace("\\dfrac", "\\frac").replace("\\tfrac", "\\frac"))
    }
    return list.distinct()
}

private fun buildDrawable(tex: String, sizePx: Float): Pair<JLatexMathDrawable?, Throwable?> {
    var error: Throwable? = null
    for (variant in texVariants(tex)) {
        try {
            return Pair(JLatexMathDrawable.builder(variant).textSize(sizePx).padding(1).build(), null)
        } catch (e: Throwable) {
            if (error == null) error = e
        }
    }
    android.util.Log.w("KalynoMath", "Formule non rendue : $tex", error)
    return Pair(null, error)
}

private fun buildInline(
    block: RBlock,
    color: Color,
    fontSize: TextUnit,
    density: Density
): Map<String, InlineTextContent> {
    val result = LinkedHashMap<String, InlineTextContent>()
    val small = (fontSize.value * 0.82f).sp

    for ((id, f) in block.fracs) {
        val widthEm = maxOf(f.num.length, f.den.length) * 0.56f + 0.6f
        result[id] = InlineTextContent(
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

    val sizePx = with(density) { fontSize.toPx() } * (if (block.kind == RKind.DISPLAY) 1.15f else 1f)
    for ((id, tex) in block.maths) {
        val (drawable, failure) = buildDrawable(tex, sizePx)
        val w = drawable?.intrinsicWidth ?: 0
        val h = drawable?.intrinsicHeight ?: 0
        result[id] = if (drawable != null && w > 0 && h > 0) {
            InlineTextContent(
                Placeholder(
                    width = with(density) { w.toSp() },
                    height = with(density) { h.toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas ->
                        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                        drawable.draw(canvas.nativeCanvas)
                    }
                }
            }
        } else {
            // Formule refusée : texte brut + raison, pour que l'admin voie exactement quoi corriger.
            val reason = failure?.message?.lineSequence()?.firstOrNull()?.take(90) ?: "rendu vide"
            InlineTextContent(
                Placeholder(
                    width = maxOf(tex.length * 0.55f, reason.length * 0.34f).em,
                    height = 2.6.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Column {
                    Text(tex, color = color, fontSize = fontSize, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    Text("⚠ " + reason, color = Color(0xFFB91C1C), fontSize = (fontSize.value * 0.6f).sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
    return result
}

/** Affiche le contenu d'une capsule : HTML simple + formules LaTeX ($...$ et $$...$$), ou texte brut. */
@Composable
fun RichText(
    html: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal
) {
    if (!html.contains('<') && !html.contains('$')) {
        Text(text = html, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight, fontStyle = fontStyle)
        return
    }
    val blocks = remember(html) { parseRich(html) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) RichBlockView(block, color, fontSize, lineHeight, fontWeight, fontStyle)
    }
}

@Composable
private fun RichBlockView(block: RBlock, color: Color, fontSize: TextUnit, lineHeight: TextUnit, fontWeight: FontWeight, fontStyle: FontStyle) {
    val density = LocalDensity.current
    val inline = remember(block, color, fontSize, density) { buildInline(block, color, fontSize, density) }
    val heading = block.kind == RKind.H
    val size = if (heading) (fontSize.value * 1.2f).sp else fontSize
    val lh = if (block.fracs.isNotEmpty() || block.maths.isNotEmpty()) TextUnit.Unspecified else lineHeight
    val body: @Composable () -> Unit = {
        Text(
            text = block.text,
            inlineContent = inline,
            color = color,
            fontSize = size,
            lineHeight = lh,
            fontWeight = if (heading) FontWeight.ExtraBold else fontWeight,
            fontStyle = fontStyle
        )
    }
    when (block.kind) {
        RKind.QUOTE -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(KalynoBlue))
            Box(Modifier.padding(start = 10.dp)) { body() }
        }
        RKind.LI -> Box(Modifier.padding(start = 8.dp)) { body() }
        RKind.DISPLAY -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.horizontalScroll(rememberScrollState())) { body() }
        }
        RKind.BOX -> {
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier.fillMaxWidth()
                    .background(block.boxBackground ?: Color.Transparent, shape)
                    .border(1.dp, block.boxBorder ?: KalynoBlue, shape)
                    .padding(10.dp)
            ) { body() }
        }
        else -> body()
    }
}
