package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.srs.ReviewRating
import ci.kalyno.app.ml.semantic.EvaluationResult
import ci.kalyno.app.ml.semantic.EvaluationVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DojoGradingTest {

    private val slow = AnswerContext(elapsedMs = 120_000, questionLength = 40)
    private val fast = AnswerContext(elapsedMs = 500, questionLength = 40)

    private fun result(verdict: EvaluationVerdict) =
        EvaluationResult(isValid = verdict == EvaluationVerdict.CORRECT, similarityScore = 0.5f, feedback = "", verdict = verdict)

    @Test fun phraseLonguePasseParLeMoteurSemantique() =
        assertTrue(DojoGrading.useSemantic("La photosynthèse produit du glucose à partir de la lumière"))

    @Test fun motCleResteSurLesMotsCles() {
        assertFalse(DojoGrading.useSemantic("mitochondrie"))
        assertFalse(DojoGrading.useSemantic("3"))
        assertFalse(DojoGrading.useSemantic("deux mots"))
    }

    @Test fun variantesAdminResteSurLesMotsCles() =
        assertFalse(DojoGrading.useSemantic("la photosynthèse | la synthèse chlorophyllienne complète"))

    @Test fun referenceVideNEstPasSemantique() = assertFalse(DojoGrading.useSemantic("   "))

    @Test fun valideParSimilariteDonneAuMieuxHesite() {
        // Une similarité ne certifie pas un fait : jamais « Su » ni « Trop facile », quelle que soit la vitesse
        for (ctx in listOf(slow, fast, slow.copy(hintUsed = true))) {
            val v = DojoGrading.fromSemantic(result(EvaluationVerdict.CORRECT), ctx)!!
            assertTrue(v.correct)
            assertEquals(ReviewRating.HESITE, v.rating)
        }
    }

    @Test fun presqueDonneHesiteEtPartiel() {
        val v = DojoGrading.fromSemantic(result(EvaluationVerdict.PARTIAL), slow)!!
        assertFalse(v.correct)
        assertTrue(v.partial)
        assertEquals(ReviewRating.HESITE, v.rating)
    }

    @Test fun contradictionEtHorsSujetDonnentOublie() {
        for (verdict in listOf(EvaluationVerdict.CONTRADICTION, EvaluationVerdict.INCORRECT)) {
            val v = DojoGrading.fromSemantic(result(verdict), slow)!!
            assertFalse(v.correct)
            assertEquals(ReviewRating.OUBLIE, v.rating)
        }
    }

    @Test fun pannedeMoteurDeclencheLeRepli() =
        assertNull(DojoGrading.fromSemantic(result(EvaluationVerdict.ENGINE_ERROR), slow))

    @Test fun autresVerdictsSontNotes() =
        assertNotNull(DojoGrading.fromSemantic(result(EvaluationVerdict.EMPTY_INPUT), slow))

    // ---- Rattrapage par la leçon ----

    @Test fun reformulationValideeParLaLeconEstRattrapeeAHesite() {
        val v = DojoGrading.fromLesson(result(EvaluationVerdict.CORRECT))!!
        assertTrue(v.correct)
        assertEquals(ReviewRating.HESITE, v.rating)
    }

    @Test fun presqueParLaLeconResteUnePartielleHesite() {
        val v = DojoGrading.fromLesson(result(EvaluationVerdict.PARTIAL))!!
        assertFalse(v.correct)
        assertTrue(v.partial)
    }

    @Test fun contresensEtHorsSujetGardentLesMotsCles() {
        assertNull(DojoGrading.fromLesson(result(EvaluationVerdict.CONTRADICTION)))
        assertNull(DojoGrading.fromLesson(result(EvaluationVerdict.INCORRECT)))
        assertNull(DojoGrading.fromLesson(result(EvaluationVerdict.ENGINE_ERROR)))
    }

    @Test fun nombresEtMotsIsolesNeSontJamaisRattrapes() {
        assertFalse(DojoGrading.canRescueWithLesson("3"))
        assertFalse(DojoGrading.canRescueWithLesson("2,5"))
        assertFalse(DojoGrading.canRescueWithLesson("  "))
        assertTrue(DojoGrading.canRescueWithLesson("mitochondrie"))
    }
}
