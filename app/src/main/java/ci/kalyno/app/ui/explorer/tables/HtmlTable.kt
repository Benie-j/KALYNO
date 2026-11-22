package ci.kalyno.app.ui.explorer.tables

/*
 * Modèle + analyseur de tableaux HTML pour le moteur de rendu pédagogique de KALYNO.
 *
 * Ce fichier est du Kotlin pur (aucune classe Android, aucun Compose) : il est testable en JVM.
 * Le contenu des cellules reste du HTML (texte, <sup>, <sub>, fractions, $...$) : c'est RichText
 * qui le rend, on ne réinvente aucun moteur de formules.
 */

sealed class HtmlSegment {
    /** Morceau de HTML sans tableau (rendu par RichText comme avant). */
    data class Html(val html: String) : HtmlSegment()

    /** Un tableau complet, de <table ...> à </table>. */
    data class Table(val raw: String) : HtmlSegment()
}

enum class CellAlign { START, CENTER, END }
enum class CellVAlign { TOP, MIDDLE, BOTTOM }

data class TableCell(
    val html: String,
    val header: Boolean,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    val align: CellAlign? = null,
    val vAlign: CellVAlign? = null,
    /** Couleurs au format ARGB (0xFFRRGGBB). */
    val background: Long? = null,
    val textColor: Long? = null,
    val bold: Boolean = false,
    val widthDp: Float? = null,
    val widthPercent: Int? = null,
    val paddingDp: Float? = null,
    /** Bordure propre à la cellule (remplace celle du tableau) ; null = celle du tableau. */
    val borderDp: Float? = null,
    val borderColor: Long? = null,
    /** Réponse attendue (attribut data-answer) : réservé au mode exercice, sans effet en mode document. */
    val answer: String? = null
) {
    val isBlank: Boolean get() = html.replace(TAGS, "").replace("&nbsp;", "").isBlank()
    /** Cellule à compléter, uniquement dans un tableau déclaré « exercice ». */
    val editable: Boolean get() = answer != null
}

data class PlacedCell(val row: Int, val col: Int, val cell: TableCell) {
    val rowSpan: Int get() = cell.rowSpan
    val colSpan: Int get() = cell.colSpan
}

data class HtmlTable(
    val caption: String?,
    val placed: List<PlacedCell>,
    val rowCount: Int,
    val colCount: Int,
    val borderDp: Float,
    val borderColor: Long?,
    val widthDp: Float?,
    val widthPercent: Int?,
    val background: Long?,
    /** Vrai seulement si l'auteur l'a demandé : <table data-kalyno="exercise">. */
    val interactive: Boolean
)

private val TAGS = Regex("<[^>]*>")
private val TABLE_OPEN = Regex("<table\\b", RegexOption.IGNORE_CASE)
private val TABLE_TAG = Regex("<(/?)table\\b[^>]*>", RegexOption.IGNORE_CASE)
private val STRUCT_TAG = Regex(
    "<(/?)(table|thead|tbody|tfoot|caption|tr|th|td)\\b([^>]*)>",
    RegexOption.IGNORE_CASE
)

fun containsTable(html: String): Boolean = TABLE_OPEN.containsMatchIn(html)

private fun MutableList<HtmlSegment>.addHtml(piece: String) {
    if (piece.isBlank()) return
    // Reste de balises fermantes / <br> / paragraphes vides sans texte ni formule : on l'ignore.
    if (!piece.contains('$') && TAGS.replace(piece, "").replace("&nbsp;", "").isBlank()) return
    add(HtmlSegment.Html(piece.trim()))
}

/**
 * Découpe un contenu en morceaux « HTML sans tableau » et « tableau », dans l'ordre.
 * Sans tableau, renvoie exactement un morceau Html contenant le texte d'origine (aucun changement de rendu).
 */
fun splitTables(html: String): List<HtmlSegment> {
    if (!containsTable(html)) return listOf(HtmlSegment.Html(html))
    val out = ArrayList<HtmlSegment>()
    var cursor = 0
    var depth = 0
    var start = -1
    for (m in TABLE_TAG.findAll(html)) {
        if (m.groupValues[1].isEmpty()) {
            if (depth == 0) start = m.range.first
            depth++
        } else if (depth > 0) {
            depth--
            if (depth == 0) {
                out.addHtml(html.substring(cursor, start))
                out.add(HtmlSegment.Table(html.substring(start, m.range.last + 1)))
                cursor = m.range.last + 1
            }
        }
    }
    if (depth > 0 && start >= 0) { // tableau jamais refermé : on le prend jusqu'à la fin
        out.addHtml(html.substring(cursor, start))
        out.add(HtmlSegment.Table(html.substring(start)))
        cursor = html.length
    }
    out.addHtml(html.substring(cursor))
    return out
}

// --------------------------------------------------------------------------------------------
// Attributs et styles
// --------------------------------------------------------------------------------------------

private fun attr(attrs: String, name: String): String? {
    val re = Regex("(?<![\\w-])" + Regex.escape(name) + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE)
    val m = re.find(attrs) ?: return null
    return (m.groups[1] ?: m.groups[2] ?: m.groups[3])?.value?.trim()
}

internal fun parseStyle(style: String?): Map<String, String> {
    if (style.isNullOrBlank()) return emptyMap()
    val map = LinkedHashMap<String, String>()
    for (decl in style.split(';')) {
        val i = decl.indexOf(':')
        if (i <= 0) continue
        map[decl.substring(0, i).trim().lowercase()] = decl.substring(i + 1).trim().lowercase()
    }
    return map
}

private val NAMED_COLORS = mapOf(
    "black" to 0xFF000000, "white" to 0xFFFFFFFF, "red" to 0xFFDC2626, "blue" to 0xFF2457C5,
    "green" to 0xFF15966A, "gray" to 0xFF808080, "grey" to 0xFF808080, "orange" to 0xFFF59E0B,
    "yellow" to 0xFFFFE08A, "purple" to 0xFF8457D9, "pink" to 0xFFDB2777
)
private val HEX_RE = Regex("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\\b")
private val RGB_RE = Regex("rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})")

/** #RGB, #RRGGBB, rgb(r,g,b) ou quelques noms usuels -> ARGB opaque ; null si non reconnu. */
fun parseCssColor(value: String?): Long? {
    val v = value?.trim()?.lowercase() ?: return null
    HEX_RE.find(v)?.let { m ->
        val h = m.groupValues[1]
        val full = if (h.length == 3) h.map { "$it$it" }.joinToString("") else h
        return 0xFF000000L or full.toLong(16)
    }
    RGB_RE.find(v)?.let { m ->
        val (r, g, b) = m.destructured
        fun c(s: String) = s.toInt().coerceIn(0, 255).toLong()
        return 0xFF000000L or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
    return NAMED_COLORS[v]
}

private val LENGTH_RE = Regex("^(\\d+(?:\\.\\d+)?)\\s*(px|pt)?$")

/** "2px", "2", "1.5pt", "thin|medium|thick" -> dp ; null si non reconnu. */
internal fun parseLengthDp(value: String?): Float? {
    val v = value?.trim()?.lowercase() ?: return null
    when (v) { "thin" -> return 1f; "medium" -> return 2f; "thick" -> return 3f }
    val m = LENGTH_RE.find(v) ?: return null
    val n = m.groupValues[1].toFloat()
    return if (m.groupValues[2] == "pt") n * 1.333f else n
}

private val BORDER_WIDTH_TOKEN = Regex("(?<![\\w#.])(\\d+(?:\\.\\d+)?(?:px|pt)?|thin|medium|thick)(?![\\w#])")

/** Résultat d'un « border: 2px solid #000 ». Largeur 0 si none/hidden. */
internal fun parseBorderShorthand(value: String?): Pair<Float?, Long?> {
    val v = value?.trim()?.lowercase() ?: return Pair(null, null)
    if (v == "none" || v == "hidden" || v.startsWith("none ") || v.startsWith("hidden ")) return Pair(0f, null)
    val withoutColors = HEX_RE.replace(RGB_RE.replace(v, ""), "")
    val width = BORDER_WIDTH_TOKEN.find(withoutColors)?.let { parseLengthDp(it.groupValues[1]) }
    return Pair(width, parseCssColor(v))
}

private fun parseAlign(value: String?): CellAlign? = when (value?.trim()?.lowercase()) {
    "left", "start" -> CellAlign.START
    "center", "middle" -> CellAlign.CENTER
    "right", "end" -> CellAlign.END
    else -> null
}

private fun parseVAlign(value: String?): CellVAlign? = when (value?.trim()?.lowercase()) {
    "top" -> CellVAlign.TOP
    "middle", "center" -> CellVAlign.MIDDLE
    "bottom" -> CellVAlign.BOTTOM
    else -> null
}

private data class Width(val dp: Float?, val percent: Int?)

private fun parseWidth(raw: String?): Width {
    val v = raw?.trim()?.lowercase() ?: return Width(null, null)
    if (v.endsWith("%")) {
        val p = v.dropLast(1).trim().toFloatOrNull()?.toInt()
        return Width(null, p?.coerceIn(1, 100))
    }
    return Width(parseLengthDp(v), null)
}

private fun borderFromStyle(style: Map<String, String>): Triple<Float?, Long?, Boolean> {
    var width: Float? = null
    var color: Long? = null
    style["border"]?.let { val (w, c) = parseBorderShorthand(it); width = w; color = c }
    style["border-width"]?.let { parseLengthDp(it.split(' ').first())?.let { w -> width = w } }
    style["border-color"]?.let { parseCssColor(it)?.let { c -> color = c } }
    return Triple(width, color, width != null || color != null)
}

private fun buildCell(tag: String, attrs: String, inner: String): TableCell {
    val style = parseStyle(attr(attrs, "style"))
    val w = parseWidth(style["width"] ?: attr(attrs, "width"))
    val (bw, bc, _) = borderFromStyle(style)
    val weight = style["font-weight"]
    return TableCell(
        html = inner.trim(),
        header = tag == "th",
        rowSpan = (attr(attrs, "rowspan")?.toIntOrNull() ?: 1).coerceIn(1, 100),
        colSpan = (attr(attrs, "colspan")?.toIntOrNull() ?: 1).coerceIn(1, 50),
        align = parseAlign(style["text-align"] ?: attr(attrs, "align")),
        vAlign = parseVAlign(style["vertical-align"] ?: attr(attrs, "valign")),
        background = parseCssColor(style["background-color"] ?: style["background"] ?: attr(attrs, "bgcolor")),
        textColor = parseCssColor(style["color"]),
        bold = weight == "bold" || weight == "bolder" || (weight?.toIntOrNull() ?: 0) >= 600,
        widthDp = w.dp,
        widthPercent = w.percent,
        paddingDp = parseLengthDp(style["padding"]?.split(' ')?.first()),
        borderDp = bw,
        borderColor = bc,
        answer = attr(attrs, "data-answer")
    )
}

// --------------------------------------------------------------------------------------------
// Analyse d'un tableau
// --------------------------------------------------------------------------------------------

private enum class Section { HEAD, BODY, FOOT }

/**
 * Reconstruit la structure : lignes, colonnes, cellules fusionnées (rowspan/colspan), en-têtes.
 * Tolère les balises fermantes manquantes (</td>, </tr>) et l'absence de thead/tbody.
 * Les tableaux imbriqués ne sont pas pris en charge.
 */
fun parseHtmlTable(raw: String): HtmlTable {
    val first = TABLE_TAG.find(raw)
    val tableAttrs = first?.value?.removePrefix("<")?.removeSuffix(">") ?: ""
    val tStyle = parseStyle(attr(tableAttrs, "style"))

    val head = ArrayList<MutableList<TableCell>>()
    val body = ArrayList<MutableList<TableCell>>()
    val foot = ArrayList<MutableList<TableCell>>()
    var section = Section.BODY
    var row: MutableList<TableCell>? = null
    var cellTag: String? = null
    var cellAttrs = ""
    var cellStart = 0
    var captionStart = -1
    var caption: String? = null

    fun closeCell(end: Int) {
        val tag = cellTag ?: return
        val r = row ?: ArrayList<TableCell>().also { row = it }
        r.add(buildCell(tag, cellAttrs, raw.substring(cellStart, maxOf(cellStart, end))))
        cellTag = null
    }
    fun closeRow() {
        val r = row ?: return
        if (r.isNotEmpty()) when (section) {
            Section.HEAD -> head.add(r)
            Section.BODY -> body.add(r)
            Section.FOOT -> foot.add(r)
        }
        row = null
    }
    fun closeCaption(end: Int) {
        if (captionStart < 0) return
        caption = raw.substring(captionStart, maxOf(captionStart, end)).trim().ifBlank { null }
        captionStart = -1
    }

    var seenOpen = false
    for (m in STRUCT_TAG.findAll(raw)) {
        val closing = m.groupValues[1].isNotEmpty()
        val tag = m.groupValues[2].lowercase()
        val attrs = m.groupValues[3]
        when (tag) {
            "table" -> {
                if (closing) { closeCell(m.range.first); closeRow(); closeCaption(m.range.first) }
                else if (!seenOpen) seenOpen = true
            }
            "thead", "tbody", "tfoot" -> {
                closeCell(m.range.first); closeRow(); closeCaption(m.range.first)
                section = if (closing) Section.BODY else when (tag) {
                    "thead" -> Section.HEAD
                    "tfoot" -> Section.FOOT
                    else -> Section.BODY
                }
            }
            "caption" -> {
                closeCell(m.range.first); closeRow()
                if (closing) closeCaption(m.range.first) else captionStart = m.range.last + 1
            }
            "tr" -> {
                closeCell(m.range.first); closeCaption(m.range.first); closeRow()
                if (!closing) row = ArrayList()
            }
            "td", "th" -> {
                closeCell(m.range.first); closeCaption(m.range.first)
                if (!closing) {
                    if (row == null) row = ArrayList()
                    cellTag = tag; cellAttrs = attrs; cellStart = m.range.last + 1
                }
            }
        }
    }
    closeCell(raw.length); closeRow(); closeCaption(raw.length)

    // HTML : <thead> d'abord, <tbody>, puis <tfoot>, quel que soit l'ordre d'écriture.
    val rows = (head + body + foot).filter { it.isNotEmpty() }

    // Placement sur la grille (rowspan/colspan).
    val occupied = HashSet<Int>()
    fun key(r: Int, c: Int) = r * 1024 + c
    val placed = ArrayList<PlacedCell>()
    var colCount = 0
    rows.forEachIndexed { r, cells ->
        var c = 0
        for (cell in cells) {
            while (key(r, c) in occupied) c++
            val rs = cell.rowSpan.coerceAtMost(rows.size - r).coerceAtLeast(1)
            val cs = cell.colSpan.coerceAtMost(1000 - c).coerceAtLeast(1)
            val fitted = if (rs != cell.rowSpan || cs != cell.colSpan) cell.copy(rowSpan = rs, colSpan = cs) else cell
            for (dr in 0 until rs) for (dc in 0 until cs) occupied.add(key(r + dr, c + dc))
            placed.add(PlacedCell(r, c, fitted))
            c += cs
        }
        colCount = maxOf(colCount, c)
    }

    // Bordure du tableau : border="1" et/ou style (border, border-width, border-color).
    var borderDp = attr(tableAttrs, "border")?.let { if (it.isEmpty()) 1f else it.toFloatOrNull() } ?: 0f
    var borderColor: Long? = null
    val (sw, sc, _) = borderFromStyle(tStyle)
    if (sw != null) borderDp = sw
    if (sc != null) borderColor = sc
    val w = parseWidth(tStyle["width"] ?: attr(tableAttrs, "width"))

    return HtmlTable(
        caption = caption,
        placed = placed,
        rowCount = rows.size,
        colCount = colCount,
        borderDp = borderDp,
        borderColor = borderColor,
        widthDp = w.dp,
        widthPercent = w.percent,
        background = parseCssColor(tStyle["background-color"] ?: attr(tableAttrs, "bgcolor")),
        interactive = attr(tableAttrs, "data-kalyno")?.lowercase() == "exercise"
    )
}
