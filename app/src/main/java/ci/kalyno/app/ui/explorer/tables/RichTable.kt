package ci.kalyno.app.ui.explorer.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import ci.kalyno.app.ui.explorer.RichText

/**
 * Contenu contenant des tableaux : le HTML est découpé en morceaux. Les morceaux sans tableau repartent
 * dans RichText (moteur actuel, inchangé) ; chaque tableau est rendu nativement par [RichTable].
 */
@Composable
internal fun RichTextWithTables(
    html: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    fontStyle: FontStyle
) {
    val segments = remember(html) { splitTables(html) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (seg in segments) {
            when (seg) {
                is HtmlSegment.Html -> RichText(
                    seg.html, color, fontSize, lineHeight,
                    fontWeight, fontStyle, allowKatex = allowKatex
                )
                is HtmlSegment.Table -> RichTable(seg.raw, color, fontSize, lineHeight)
            }
        }
    }
}

/**
 * Tableau scolaire natif : lignes/colonnes, cellules fusionnées, bordures fines ou épaisses, en-têtes,
 * alignements, couleurs. Les formules des cellules passent par le moteur JLaTeXMath de RichText
 * (pas de WebView par cellule). Un tableau trop large défile horizontalement dans son cadre,
 * sans jamais élargir la page.
 */
@Composable
internal fun RichTable(raw: String, color: Color, fontSize: TextUnit, lineHeight: TextUnit) {
    val table = remember(raw) { parseHtmlTable(raw) }
    if (table.placed.isEmpty()) return
    val cellSize = fontSize * 0.95f
    val cellLine = if (lineHeight.isSpecified) lineHeight * 0.95f else lineHeight

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        table.caption?.let {
            RichText(it, color, cellSize, cellLine, FontWeight.Bold, FontStyle.Normal, TextAlign.Center, allowKatex = false)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availablePx = constraints.maxWidth
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                TableGrid(table, availablePx) {
                    for (p in table.placed) TableCellView(p, table, color, cellSize, cellLine)
                }
            }
        }
    }
}

@Composable
private fun TableCellView(p: PlacedCell, table: HtmlTable, color: Color, fontSize: TextUnit, lineHeight: TextUnit) {
    val cell = p.cell
    val density = LocalDensity.current
    val lineColorDefault = table.borderColor?.let { Color(it) } ?: color.copy(alpha = 0.85f)
    val tableLinePx = with(density) { table.borderDp.dp.toPx() }
    val cellLinePx = cell.borderDp?.let { with(density) { it.dp.toPx() } }
    val cellLineColor = cell.borderColor?.let { Color(it) }
    val bg = cell.background?.let { Color(it) }
        ?: if (cell.header) color.copy(alpha = 0.07f) else table.background?.let { Color(it) } ?: Color.Transparent
    val textColor = cell.textColor?.let { Color(it) } ?: color
    val h = when (cell.align ?: if (cell.header) CellAlign.CENTER else CellAlign.START) {
        CellAlign.START -> -1f
        CellAlign.CENTER -> 0f
        CellAlign.END -> 1f
    }
    val v = when (cell.vAlign ?: CellVAlign.MIDDLE) {
        CellVAlign.TOP -> -1f
        CellVAlign.MIDDLE -> 0f
        CellVAlign.BOTTOM -> 1f
    }
    val textAlign = when {
        h < 0f -> TextAlign.Start
        h > 0f -> TextAlign.End
        else -> TextAlign.Center
    }
    val pad = (cell.paddingDp ?: 6f).dp
    // Cellule vide (&nbsp; de l'éditeur) : rien à afficher. Texte brut : on décode les entités, comme un paragraphe.
    val shown = when {
        cell.isBlank -> ""
        !cell.html.contains('<') && !cell.html.contains('$') -> plainEntities(cell.html)
        else -> cell.html
    }
    val firstRow = p.row == 0
    val firstCol = p.col == 0

    Box(
        modifier = Modifier
            .background(bg)
            .drawBehind {
                val own = cellLinePx != null && cellLinePx > 0f
                val w = if (own) cellLinePx!! else tableLinePx
                if (w <= 0f) return@drawBehind
                val c = if (own) (cellLineColor ?: lineColorDefault) else lineColorDefault
                val right = size.width - w / 2
                val bottom = size.height - w / 2
                drawLine(c, Offset(right, 0f), Offset(right, size.height), w)
                drawLine(c, Offset(0f, bottom), Offset(size.width, bottom), w)
                if (firstRow || own) drawLine(c, Offset(0f, w / 2), Offset(size.width, w / 2), w)
                if (firstCol || own) drawLine(c, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
            }
            .defaultMinSize(minWidth = 28.dp, minHeight = 30.dp)
            .padding(horizontal = pad, vertical = pad - 1.dp),
        contentAlignment = BiasAlignment(h, v)
    ) {
        RichText(
            html = shown,
            color = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = if (cell.header || cell.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = FontStyle.Normal,
            textAlign = textAlign,
            allowKatex = false
        )
    }
}

/**
 * Grille : largeur de chaque colonne = contenu le plus large (cellules fusionnées comprises). Si le total
 * dépasse la largeur disponible, les colonnes se resserrent (le texte passe à la ligne) jusqu'à un minimum ;
 * en dessous, le tableau garde sa largeur et défile. Hauteur des lignes = cellule la plus haute (rowspan compris).
 */
@Composable
private fun TableGrid(table: HtmlTable, availablePx: Int, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, _ ->
        val placed = table.placed
        val n = table.colCount
        val rows = table.rowCount
        if (placed.isEmpty() || measurables.size != placed.size || n == 0) return@Layout layout(0, 0) {}

        // 1) largeur naturelle de chaque cellule (sans retour à la ligne)
        val natW = IntArray(placed.size) { measurables[it].measure(Constraints()).width }

        // 2) largeur naturelle de chaque colonne
        val natural = IntArray(n)
        val fixed = BooleanArray(n)
        val bounded = availablePx in 1 until Constraints.Infinity
        placed.forEachIndexed { i, p ->
            if (p.colSpan == 1) {
                val explicit = p.cell.widthDp?.dp?.roundToPx()
                    ?: p.cell.widthPercent?.let { if (bounded) availablePx * it / 100 else null }
                if (explicit != null) { natural[p.col] = explicit; fixed[p.col] = true }
                else if (!fixed[p.col]) natural[p.col] = maxOf(natural[p.col], natW[i])
            }
        }
        val order = placed.indices.filter { placed[it].colSpan > 1 }.sortedBy { placed[it].colSpan }
        for (i in order) {
            val p = placed[i]
            val cur = (p.col until p.col + p.colSpan).sumOf { natural[it] }
            if (natW[i] > cur) {
                val extra = natW[i] - cur
                val each = extra / p.colSpan
                for (c in p.col until p.col + p.colSpan) natural[c] += each
                natural[p.col + p.colSpan - 1] += extra - each * p.colSpan
            }
        }

        // 3) largeur finale des colonnes
        val total = natural.sum()
        val target = when {
            !bounded -> total
            table.widthPercent != null -> availablePx * table.widthPercent / 100
            table.widthDp != null -> table.widthDp.dp.roundToPx()
            else -> availablePx
        }
        val widths = natural.copyOf()
        if (total < target) {
            val free = (0 until n).filter { !fixed[it] }.ifEmpty { (0 until n).toList() }
            val extra = target - total
            val each = extra / free.size
            for (c in free) widths[c] += each
            widths[free.last()] += extra - each * free.size
        } else if (total > target) {
            val minCol = 72.dp.roundToPx()
            val mins = IntArray(n) { if (fixed[it]) natural[it] else minOf(natural[it], minCol) }
            val sumMin = mins.sum()
            if (sumMin >= target) {
                for (c in 0 until n) widths[c] = mins[c] // trop étroit : on garde le minimum et le tableau défile
            } else {
                val t = (target - sumMin).toFloat() / (total - sumMin).toFloat()
                for (c in 0 until n) widths[c] = if (fixed[c]) natural[c] else mins[c] + ((natural[c] - mins[c]) * t).toInt()
            }
        }

        // 4) hauteur des lignes
        val cellW = IntArray(placed.size) { i -> (placed[i].col until placed[i].col + placed[i].colSpan).sumOf { widths[it] } }
        val needH = IntArray(placed.size) { i -> measurables[i].measure(Constraints(minWidth = cellW[i], maxWidth = cellW[i])).height }
        val rowH = IntArray(rows)
        placed.forEachIndexed { i, p -> if (p.rowSpan == 1) rowH[p.row] = maxOf(rowH[p.row], needH[i]) }
        val spans = placed.indices.filter { placed[it].rowSpan > 1 }.sortedBy { placed[it].rowSpan }
        for (i in spans) {
            val p = placed[i]
            val cur = (p.row until p.row + p.rowSpan).sumOf { rowH[it] }
            if (needH[i] > cur) rowH[p.row + p.rowSpan - 1] += needH[i] - cur
        }

        // 5) mesure finale et placement
        val xs = IntArray(n + 1); for (c in 0 until n) xs[c + 1] = xs[c] + widths[c]
        val ys = IntArray(rows + 1); for (r in 0 until rows) ys[r + 1] = ys[r] + rowH[r]
        val placeables = placed.mapIndexed { i, p ->
            val h = ys[p.row + p.rowSpan] - ys[p.row]
            measurables[i].measure(Constraints.fixed(cellW[i], h))
        }
        layout(xs[n], ys[rows]) {
            placeables.forEachIndexed { i, pl -> pl.place(xs[placed[i].col], ys[placed[i].row]) }
        }
    }
}

private fun plainEntities(s: String): String = s
    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
