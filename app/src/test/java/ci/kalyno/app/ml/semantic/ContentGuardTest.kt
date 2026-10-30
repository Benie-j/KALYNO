package ci.kalyno.app.ml.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentGuardTest {
    private val ref = "La photosynthèse a lieu dans les chloroplastes des cellules végétales"
    private val question = "Où se déroule la photosynthèse ?"

    @Test fun uneReformulationQuiReprendLesTermesClesEstCouverte() {
        val c = ContentGuard.coverage(ref, "ça se passe dans les chloroplastes des cellules végétales", question)
        assertTrue("couverture $c", c >= ContentGuard.MIN_COVERAGE)
    }

    @Test fun unTermeRemplaceParUnVoisinEstRefuse() {
        // « mitochondries » est sémantiquement tout proche de « chloroplastes » pour E5 : seul le terme exact les distingue
        val c = ContentGuard.coverage(ref, "elle a lieu dans les mitochondries", question)
        assertTrue("couverture $c", c < ContentGuard.MIN_COVERAGE)
    }

    @Test fun lesMotsDeLaQuestionNeComptentPasCommeTermesPropres() {
        val c = ContentGuard.coverage(ref, "la photosynthèse se déroule quelque part", question)
        assertTrue("couverture $c", c < ContentGuard.MIN_COVERAGE)
    }

    @Test fun accentsPluriels_EtRadicauxSontTolerés() {
        assertTrue(ContentGuard.same("chloroplastes", "chloroplaste"))
        assertTrue(ContentGuard.same("vegetales", "vegetale"))
        assertTrue(ContentGuard.same("oxygene", "dioxygene"))
    }

    @Test fun lesNombresSeComparentExactement() {
        assertTrue(!ContentGuard.same("1960", "1975"))
        assertTrue(ContentGuard.same("1960", "1960"))
    }

    @Test fun uneReferenceSansTermePropreNEstPasBloquee() {
        assertEquals(1f, ContentGuard.coverage("C'est le cas", "oui", ""), 0.001f)
    }
}
