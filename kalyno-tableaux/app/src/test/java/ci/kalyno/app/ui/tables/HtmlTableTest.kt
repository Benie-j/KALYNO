package ci.kalyno.app.ui.tables

import ci.kalyno.app.ui.explorer.tables.CellAlign
import ci.kalyno.app.ui.explorer.tables.CellVAlign
import ci.kalyno.app.ui.explorer.tables.HtmlSegment
import ci.kalyno.app.ui.explorer.tables.containsTable
import ci.kalyno.app.ui.explorer.tables.parseCssColor
import ci.kalyno.app.ui.explorer.tables.parseHtmlTable
import ci.kalyno.app.ui.explorer.tables.splitTables
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTableTest {

    private val monomes = """
        <h2>Exercice 1</h2>
        <p>Complète le tableau suivant.</p>
        <table border="1">
          <thead><tr><th>Monôme</th><th>Coefficient</th><th>Degré</th></tr></thead>
          <tbody>
            <tr><td>${'$'}7x^2${'$'}</td><td></td><td></td></tr>
            <tr><td>${'$'}x^5${'$'}</td><td></td><td></td></tr>
            <tr><td>${'$'}-\frac{2}{3}x^4${'$'}</td><td></td><td></td></tr>
            <tr><td>${'$'}-x${'$'}</td><td></td><td></td></tr>
          </tbody>
        </table>
        <p>Fin.</p>
    """.trimIndent()

    private fun table(html: String) = parseHtmlTable((splitTables(html).first { it is HtmlSegment.Table } as HtmlSegment.Table).raw)

    // 1. tableau simple à trois colonnes
    @Test fun simpleThreeColumns() {
        val t = table("<table><tr><th>A</th><th>B</th><th>C</th></tr></table>")
        assertEquals(3, t.colCount); assertEquals(1, t.rowCount)
        assertEquals(listOf("A", "B", "C"), t.placed.map { it.cell.html })
        assertTrue(t.placed.all { it.cell.header })
    }

    // 2. plusieurs lignes (thead puis tbody, exemple de la mission)
    @Test fun severalRows() {
        val t = table(monomes)
        assertEquals(5, t.rowCount); assertEquals(3, t.colCount); assertEquals(15, t.placed.size)
        assertEquals(1f, t.borderDp, 0f)
        assertTrue(t.placed.filter { it.row == 0 }.all { it.cell.header })
        assertFalse(t.interactive)
    }

    // 3. cellules vides
    @Test fun emptyCells() {
        val t = table(monomes)
        val empties = t.placed.filter { it.cell.isBlank }
        assertEquals(8, empties.size)
        assertTrue(empties.all { it.col in 1..2 })
    }

    // 4. fractions : le contenu de la cellule reste intact pour RichText / JLaTeXMath
    @Test fun fractionsKeptForRichText() {
        val t = table(monomes)
        assertTrue(t.placed.any { it.cell.html == "${'$'}-\\frac{2}{3}x^4${'$'}" })
        val h = table("""<table><tr><td><span class="frac"><span class="fn">1</span><span class="fd">2</span></span></td></tr></table>""")
        assertTrue(h.placed.single().cell.html.contains("class=\"frac\""))
    }

    // 5. exposants et indices
    @Test fun superscriptsAndSubscripts() {
        val t = table("<table><tr><td>x<sup>2</sup></td><td>u<sub>n</sub></td></tr></table>")
        assertEquals("x<sup>2</sup>", t.placed[0].cell.html)
        assertEquals("u<sub>n</sub>", t.placed[1].cell.html)
    }

    // 6. cellules fusionnées
    @Test fun mergedCells() {
        val t = table("""
            <table border="1">
              <tr><th colspan="2">Titre</th><th rowspan="2">Z</th></tr>
              <tr><td>a</td><td>b</td></tr>
              <tr><td>c</td><td>d</td><td>e</td></tr>
            </table>""")
        assertEquals(3, t.colCount); assertEquals(3, t.rowCount)
        val titre = t.placed.first { it.cell.html == "Titre" }
        assertEquals(0, titre.col); assertEquals(2, titre.colSpan)
        val z = t.placed.first { it.cell.html == "Z" }
        assertEquals(2, z.col); assertEquals(2, z.rowSpan)
        // La ligne 2 n'a que 2 cellules : la colonne 2 est occupée par Z, rien ne se décale.
        assertEquals(listOf(0, 1), t.placed.filter { it.row == 1 }.map { it.col })
        assertEquals(listOf(0, 1, 2), t.placed.filter { it.row == 2 }.map { it.col })
    }

    @Test fun rowspanIsClampedToTableHeight() {
        val t = table("<table><tr><td rowspan=\"9\">x</td></tr></table>")
        assertEquals(1, t.placed.single().rowSpan)
    }

    // 7. couleurs, alignements, bordures épaisses
    @Test fun customColorsAndStyles() {
        val t = table("""
            <table style="border: 3px solid #0F766E; width: 80%">
              <tr><td style="background-color:#FEE2E2; color:#B91C1C; text-align:right; vertical-align:bottom; font-weight:bold; padding:10px">x</td>
                  <td align="center" valign="top" width="120" bgcolor="#EAF0FF">y</td></tr>
            </table>""")
        assertEquals(3f, t.borderDp, 0f); assertEquals(0xFF0F766EL, t.borderColor); assertEquals(80, t.widthPercent)
        val a = t.placed[0].cell
        assertEquals(0xFFFEE2E2L, a.background); assertEquals(0xFFB91C1CL, a.textColor)
        assertEquals(CellAlign.END, a.align); assertEquals(CellVAlign.BOTTOM, a.vAlign)
        assertTrue(a.bold); assertEquals(10f, a.paddingDp!!, 0f)
        val b = t.placed[1].cell
        assertEquals(CellAlign.CENTER, b.align); assertEquals(CellVAlign.TOP, b.vAlign)
        assertEquals(120f, b.widthDp!!, 0f); assertEquals(0xFFEAF0FFL, b.background)
    }

    @Test fun cellBorderOverrideAndNone() {
        val t = table("""<table border="1"><tr><td style="border:2px solid #000">a</td><td>b</td></tr></table>""")
        assertEquals(2f, t.placed[0].cell.borderDp!!, 0f); assertEquals(0xFF000000L, t.placed[0].cell.borderColor)
        assertNull(t.placed[1].cell.borderDp)
        assertEquals(0f, table("""<table style="border:none"><tr><td>a</td></tr></table>""").borderDp, 0f)
        assertEquals(0f, table("<table><tr><td>a</td></tr></table>").borderDp, 0f)
    }

    @Test fun colorParsing() {
        assertEquals(0xFFAABBCCL, parseCssColor("#abc"))
        assertEquals(0xFF102030L, parseCssColor("rgb(16, 32, 48)"))
        assertNull(parseCssColor("n'importe quoi"))
    }

    // 8. tableau large : beaucoup de colonnes + largeurs explicites (la mise en page défile, voir Limitations)
    @Test fun wideTable() {
        val cells = (1..12).joinToString("") { "<td width=\"90\">c$it</td>" }
        val t = table("<table border=\"1\"><tr>$cells</tr></table>")
        assertEquals(12, t.colCount)
        assertTrue(t.placed.all { it.cell.widthDp == 90f })
    }

    // 9. tableau entre deux paragraphes
    @Test fun betweenParagraphs() {
        val parts = splitTables(monomes)
        assertEquals(3, parts.size)
        assertTrue(parts[0] is HtmlSegment.Html && (parts[0] as HtmlSegment.Html).html.contains("Complète"))
        assertTrue(parts[1] is HtmlSegment.Table)
        assertEquals("<p>Fin.</p>", (parts[2] as HtmlSegment.Html).html)
    }

    // 10. plusieurs tableaux dans une même capsule
    @Test fun severalTables() {
        val html = "<p>1</p><table><tr><td>a</td></tr></table><p>2</p><table><tr><td>b</td><td>c</td></tr></table>"
        val parts = splitTables(html)
        assertEquals(listOf(true, false, true, false), parts.map { it is HtmlSegment.Html })
        assertEquals(1, parseHtmlTable((parts[1] as HtmlSegment.Table).raw).colCount)
        assertEquals(2, parseHtmlTable((parts[3] as HtmlSegment.Table).raw).colCount)
    }

    // 11. HTML sans tableau : un seul morceau, texte identique
    @Test fun htmlWithoutTable() {
        val html = "<p>Bonjour <strong>toi</strong> ${'$'}x^2${'$'}</p>"
        assertFalse(containsTable(html))
        assertEquals(listOf<HtmlSegment>(HtmlSegment.Html(html)), splitTables(html))
    }

    // 12. anciennes capsules : texte brut, paragraphes séparés par une ligne vide, formules seules
    @Test fun oldCapsulesUntouched() {
        for (body in listOf("Texte simple", "Premier\n\nSecond", "${'$'}${'$'}a^2+b^2=c^2${'$'}${'$'}", "<ul><li>a</li></ul>", "a < b et c > d")) {
            assertEquals(listOf<HtmlSegment>(HtmlSegment.Html(body)), splitTables(body))
        }
    }

    // 13. restauration depuis le cache : le cache enregistre `body` dans un JSON (put/optString) ; le tableau survit
    @Test fun tableSurvivesJsonCacheRoundTrip() {
        val stored = JSONObject().put("body", monomes).toString()
        val restored = JSONObject(stored).optString("body", "")
        assertEquals(monomes, restored)
        assertEquals(splitTables(monomes), splitTables(restored))
        assertEquals(15, parseHtmlTable((splitTables(restored)[1] as HtmlSegment.Table).raw).placed.size)
    }

    // Robustesse
    @Test fun missingClosingTagsAndSectionOrder() {
        val t = table("<table><tfoot><tr><td>pied</td></tr></tfoot><tbody><tr><td>1<td>2<tr><td>3</tbody><thead><tr><th>tête</th></tr></thead></table>")
        assertEquals("tête", t.placed.first().cell.html)
        assertEquals("pied", t.placed.last().cell.html)
        assertEquals(4, t.rowCount)
        assertEquals(listOf("1", "2"), t.placed.filter { it.row == 1 }.map { it.cell.html })
    }

    @Test fun captionIsExtracted() {
        val t = table("<table><caption>Monômes</caption><tr><td>a</td></tr></table>")
        assertEquals("Monômes", t.caption)
        assertEquals(1, t.placed.size)
    }

    @Test fun unclosedTableIsStillParsed() {
        val parts = splitTables("<p>x</p><table><tr><td>a</td></tr>")
        assertTrue(parts.last() is HtmlSegment.Table)
        assertEquals(1, parseHtmlTable((parts.last() as HtmlSegment.Table).raw).placed.size)
    }

    // Mode B : l'interactivité est explicite, un tableau ordinaire ne devient jamais modifiable
    @Test fun interactivityMustBeExplicit() {
        assertFalse(table(monomes).interactive)
        assertTrue(table(monomes).placed.none { it.cell.editable })
        val t = table("""<table data-kalyno="exercise"><tr><td>${'$'}7x^2${'$'}</td><td data-answer="7"></td><td data-answer="2"></td></tr></table>""")
        assertTrue(t.interactive)
        assertEquals(listOf(false, true, true), t.placed.map { it.cell.editable })
        assertEquals("7", t.placed[1].cell.answer)
        assertNotNull(t.placed[2].cell.answer)
    }
}
