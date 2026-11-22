package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.srs.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableExerciseTest {

    private val front = """
        {"type":"kalyno_table_v1","statement":"Complète le tableau.","rows":[
          [{"type":"text","value":"A"},{"type":"text","value":"B"}],
          [{"type":"blank","key":"r2c1"},{"type":"blank","key":"r2c2"}],
          [{"type":"blank","key":"r3c1"},{"type":"blank","key":"r3c2"}]]}
    """.trimIndent()

    private val reference = """
        {"type":"kalyno_table_answers_v1","answers":{
          "r2c1":["un"],"r2c2":["deux","2"],"r3c1":["trois"],"r3c2":["quatre"]}}
    """.trimIndent()

    private val table = TableCodec.parseFront(front)!!
    private val answers = TableCodec.parseAnswers(reference)!!
    private val ctx = AnswerContext(elapsedMs = 60_000, questionLength = 20)

    private fun grade(vararg typed: Pair<String, String>) = TableGrader.grade(table, answers, mapOf(*typed))

    // ---- lecture des données

    @Test fun lectureDuTableau() {
        assertEquals("Complète le tableau.", table.statement)
        assertEquals(2, table.columnCount)
        assertEquals(listOf("r2c1", "r2c2", "r3c1", "r3c2"), table.blankKeys)
        assertTrue(TableCodec.isPlayable(table, answers))
    }

    @Test fun frontNeContientAucuneReponse() {
        for (secret in listOf("un", "deux", "trois", "quatre")) {
            assertFalse("front ne doit pas contenir « $secret »", front.contains("\"$secret\""))
        }
        assertFalse(table.rows.flatten().any { it is TableCell.Fixed && it.text in listOf("deux", "trois", "quatre") })
    }

    @Test fun contenuIllisibleDonneNull() {
        assertNull(TableCodec.parseFront("Une question simple"))
        assertNull(TableCodec.parseFront("{pas du json"))
        assertNull(TableCodec.parseFront("""{"type":"autre","rows":[]}"""))
        assertNull(TableCodec.parseFront("""{"type":"kalyno_table_v1","statement":"x","rows":[]}"""))
        assertNull(TableCodec.parseAnswers(null))
        assertNull(TableCodec.parseAnswers("""{"type":"kalyno_table_answers_v1"}"""))
    }

    @Test fun cleDeCelluleRepeteeRefusee() {
        val dup = """{"type":"kalyno_table_v1","statement":"x","rows":[[{"type":"blank","key":"k"},{"type":"blank","key":"k"}]]}"""
        assertNull(TableCodec.parseFront(dup))
    }

    @Test fun tableauSansReponsePourUneCelluleInjouable() {
        val partial = TableCodec.parseAnswers("""{"type":"kalyno_table_answers_v1","answers":{"r2c1":["un"]}}""")!!
        assertFalse(TableCodec.isPlayable(table, partial))
    }

    // ---- types d'exercice

    @Test fun tableNEstJamaisUneSaisieLibre() {
        assertEquals(ExerciseKind.TABLE, ExerciseKind.resolve("table", emptyList(), emptyList()))
        assertEquals(ExerciseKind.TABLE, ExerciseKind.resolve("TABLE", emptyList(), emptyList()))
    }

    @Test fun anciensTypesInchanges() {
        assertEquals(ExerciseKind.FREE_TEXT, ExerciseKind.resolve("cloze", emptyList(), emptyList()))
        assertEquals(ExerciseKind.FREE_TEXT, ExerciseKind.resolve("flashcard", emptyList(), emptyList()))
        assertEquals(ExerciseKind.SINGLE_CHOICE, ExerciseKind.resolve("qcm", listOf("a", "b"), listOf(0)))
        assertEquals(ExerciseKind.MULTIPLE_CHOICE, ExerciseKind.resolve("qcm", listOf("a", "b", "c"), listOf(0, 1)))
    }

    // ---- correction

    @Test fun toutesBonnes() {
        val g = grade("r2c1" to "un", "r2c2" to "deux", "r3c1" to "trois", "r3c2" to "quatre")
        assertEquals(TableOutcome.GOOD, g.outcome)
        assertEquals(100, g.percent)
        assertTrue(TableGrader.verdict(g, ctx).correct)
    }

    @Test fun toutesMauvaises() {
        val g = grade("r2c1" to "x", "r2c2" to "y", "r3c1" to "z", "r3c2" to "w")
        assertEquals(TableOutcome.BAD, g.outcome)
        assertEquals(0, g.correct)
        val v = TableGrader.verdict(g, ctx)
        assertFalse(v.correct); assertFalse(v.partial); assertEquals(ReviewRating.OUBLIE, v.rating)
    }

    @Test fun troisSurQuatreEstPartielle() {
        val g = grade("r2c1" to "un", "r2c2" to "deux", "r3c1" to "trois", "r3c2" to "faux")
        assertEquals(3, g.correct); assertEquals(4, g.total)
        assertEquals(75, g.percent)
        assertEquals(TableOutcome.PARTIAL, g.outcome)
        assertEquals(CellResult.WRONG, g.cells["r3c2"])
        val v = TableGrader.verdict(g, ctx)
        assertFalse(v.correct); assertTrue(v.partial); assertEquals(ReviewRating.HESITE, v.rating)
    }

    @Test fun celluleVideJamaisCorrecte() {
        val g = grade("r2c1" to "un", "r2c2" to "   ")
        assertEquals(CellResult.CORRECT, g.cells["r2c1"])
        assertEquals(CellResult.EMPTY, g.cells["r2c2"])
        assertEquals(CellResult.EMPTY, g.cells["r3c1"]) // jamais saisie
        assertEquals(1, g.correct)
        assertEquals(2, g.empty)
        assertEquals(TableOutcome.PARTIAL, g.outcome)
    }

    @Test fun toutVideEstMauvais() {
        val g = grade()
        assertEquals(0, g.correct)
        assertEquals(TableOutcome.BAD, g.outcome)
    }

    @Test fun variantesAcceptees() {
        assertEquals(CellResult.CORRECT, grade("r2c2" to "2").cells["r2c2"])
        assertEquals(CellResult.CORRECT, grade("r2c2" to "DEUX").cells["r2c2"])
    }

    @Test fun cellulesFixesEtCleInconnuesIgnorees() {
        val g = grade("A" to "hack", "r9c9" to "x", "r2c1" to "un")
        assertEquals(4, g.total)
        assertEquals(1, g.correct)
        assertFalse(g.cells.containsKey("r9c9"))
    }

    // ---- normalisation

    @Test fun normalisationTolerante() {
        assertEquals("éva poration", TableGrader.normalize("  Éva   Poration. "))
        assertEquals(TableGrader.normalize("0,5"), TableGrader.normalize("0.5"))
        assertEquals(TableGrader.normalize("l\u2019eau"), TableGrader.normalize("l'eau"))
        assertEquals(TableGrader.normalize("a\u00A0b"), TableGrader.normalize("a b"))
    }

    @Test fun reponsesDifferentesNonConfondues() {
        val t = TableCodec.parseFront(front)!!
        val a = TableAnswers(mapOf("r2c1" to listOf("évaporation"), "r2c2" to listOf("où"), "r3c1" to listOf("a"), "r3c2" to listOf("b")))
        assertEquals(CellResult.WRONG, TableGrader.grade(t, a, mapOf("r2c1" to "vaporisation")).cells["r2c1"])
        assertEquals(CellResult.WRONG, TableGrader.grade(t, a, mapOf("r2c1" to "evaporation")).cells["r2c1"]) // pas de faute de frappe tolérée
        assertEquals(CellResult.WRONG, TableGrader.grade(t, a, mapOf("r2c2" to "ou")).cells["r2c2"])
        assertEquals(CellResult.WRONG, TableGrader.grade(t, a, mapOf("r2c1" to "l'évaporation")).cells["r2c1"])
    }

    // ---- historique et synchronisation

    @Test fun tentativeEncodeePuisDecrite() {
        val typed = mapOf("r2c1" to "un", "r2c2" to "faux")
        val json = TableAttempt.encode(typed, grade("r2c1" to "un", "r2c2" to "faux"))
        val text = TableAttempt.describe(json)!!
        assertTrue(text.contains("Ligne 2, colonne 1 : un ✓"))
        assertTrue(text.contains("Ligne 2, colonne 2 : faux ✗"))
        assertTrue(text.contains("(non renseignée)"))
        assertTrue(text.contains("1 / 4"))
    }

    @Test fun ancienneReponseTexteNEstPasUneTentativeDeTableau() {
        assertNull(TableAttempt.describe("photosynthèse"))
        assertNull(TableAttempt.describe(null))
    }

    @Test fun reponsesAttenduesLisibles() {
        val text = TableAttempt.expectedText(table, answers)
        assertTrue(text.contains("Ligne 2, colonne 2 : deux / 2"))
    }

    @Test fun reponseDeCartePourLaSynchronisation() {
        assertEquals("un · deux · trois · quatre", cardAnswerText("table", front, emptyList(), emptyList(), null, reference))
        // tableau sans réponses : carte ignorée (null), jamais jouée comme une saisie libre
        assertNull(cardAnswerText("table", front, emptyList(), emptyList(), null, null))
        assertNull(cardAnswerText("table", "pas un tableau", emptyList(), emptyList(), null, reference))
        // anciens types inchangés
        assertEquals("Paris", cardAnswerText("cloze", "Capitale ?", emptyList(), emptyList(), "Paris", null))
        assertEquals("b", cardAnswerText("qcm", "Q", listOf("a", "b"), listOf(1), null, null))
        assertNotNull(TableCodec.statementOf(front))
        assertEquals("Complète le tableau.", TableCodec.statementOf(front))
    }
}
