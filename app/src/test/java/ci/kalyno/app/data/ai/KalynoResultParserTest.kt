package ci.kalyno.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KalynoResultParserTest {

    private fun grade(verdict: String = "CORRECT", extra: String = "") = """
        {"ok":true,"kind":"grade","result":{"verdict":"$verdict","score":95,"confidence":92,
        "feedback":"Bien vu.","strengths":["Notion comprise"],"errors":[],"missing_elements":[],
        "corrected_answer":"Réponse attendue" $extra}}
    """.trimIndent()

    @Test fun `reponse valide est lue`() {
        val r = KalynoResultParser.parseGradeEnvelope(grade())!!
        assertEquals(KalynoVerdict.CORRECT, r.verdict)
        assertEquals(95, r.score)
        assertEquals(92, r.confidence)
        assertEquals(listOf("Notion comprise"), r.strengths)
        assertFalse(r.uncertain)
    }

    @Test fun `les trois verdicts sont acceptes`() {
        assertEquals(KalynoVerdict.PARTIAL, KalynoResultParser.parseGradeEnvelope(grade("PARTIAL"))!!.verdict)
        assertEquals(KalynoVerdict.INCORRECT, KalynoResultParser.parseGradeEnvelope(grade("INCORRECT"))!!.verdict)
    }

    @Test fun `JSON malforme n'est jamais une correction`() {
        assertNull(KalynoResultParser.parseGradeEnvelope("{ pas du json"))
        assertNull(KalynoResultParser.parseGradeEnvelope(""))
        assertNull(KalynoResultParser.parseGradeEnvelope("""{"ok":true,"kind":"grade"}"""))
    }

    @Test fun `verdict inconnu ou en minuscules est refuse`() {
        assertNull(KalynoResultParser.parseGradeEnvelope(grade("MAYBE")))
        assertNull(KalynoResultParser.parseGradeEnvelope(grade("correct")))
    }

    @Test fun `score et confiance hors bornes ou absents sont refuses`() {
        assertNull(KalynoResultParser.parseGradeEnvelope(grade().replace("\"score\":95", "\"score\":140")))
        assertNull(KalynoResultParser.parseGradeEnvelope(grade().replace("\"confidence\":92", "\"confidence\":-1")))
        assertNull(KalynoResultParser.parseGradeEnvelope(grade().replace("\"score\":95,", "")))
        assertNull(KalynoResultParser.parseGradeEnvelope(grade().replace("\"score\":95", "\"score\":\"haut\"")))
    }

    @Test fun `commentaire vide est refuse`() {
        assertNull(KalynoResultParser.parseGradeEnvelope(grade().replace("Bien vu.", "  ")))
    }

    @Test fun `enveloppe en erreur n'est pas une correction`() {
        assertNull(KalynoResultParser.parseGradeEnvelope("""{"ok":false,"error":{"code":"timeout"}}"""))
    }

    @Test fun `erreur serveur est lue avec son caractere reessayable`() {
        val e = KalynoResultParser.parseError(
            """{"ok":false,"error":{"code":"timeout","message":"Trop long","retryable":true}}"""
        )!!
        assertEquals("timeout", e.code)
        assertTrue(e.retryable)
        assertNull(KalynoResultParser.parseError("pas du json"))
    }

    @Test fun `assistant renvoie le texte ou null`() {
        assertEquals("Salut", KalynoResultParser.parseAssistEnvelope("""{"ok":true,"kind":"assist","result":{"text":" Salut "}}"""))
        assertNull(KalynoResultParser.parseAssistEnvelope("""{"ok":true,"kind":"assist","result":{"text":""}}"""))
        assertNull(KalynoResultParser.parseAssistEnvelope("""{"ok":true,"kind":"grade","result":{}}"""))
    }

    @Test fun `confiance basse signale l'incertitude`() {
        val r = KalynoResultParser.parseGradeEnvelope(grade().replace("\"confidence\":92", "\"confidence\":30"))!!
        assertTrue(r.uncertain)
        assertTrue(r.toDetailText().contains("pas sûr"))
        assertNotNull(r)
    }
}
