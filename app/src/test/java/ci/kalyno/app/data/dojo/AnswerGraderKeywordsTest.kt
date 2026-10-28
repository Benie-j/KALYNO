package ci.kalyno.app.data.dojo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerGraderKeywordsTest {

    private val ctx = AnswerContext(elapsedMs = 60_000, questionLength = 20)
    private val keywords = AnswerGrader.acceptedAnswers("photosynthèse | synthèse chlorophyllienne")

    private fun ok(typed: String, accepted: List<String> = keywords) =
        AnswerGrader.gradeText(typed, accepted, ctx).correct

    @Test fun motCleSeul() = assertTrue(ok("Photosynthese"))
    @Test fun deuxiemeMotCle() = assertTrue(ok("synthèse chlorophyllienne"))
    @Test fun motCleDansUnePhrase() = assertTrue(ok("je crois que c'est la photosynthèse !"))
    @Test fun phraseSansMotCle() = assertFalse(ok("c'est la respiration cellulaire"))
    @Test fun negationRefusee() = assertFalse(ok("ce n'est pas la photosynthèse"))
    @Test fun nombreDansUnePhrase() = assertTrue(ok("la réponse est 3", listOf("3", "trois")))
    @Test fun deuxNombresRefuses() = assertFalse(ok("c'est 13 ou 4", listOf("3")))
    @Test fun nombreExact() = assertFalse(ok("4", listOf("3")))
}
