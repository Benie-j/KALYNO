package ci.kalyno.app.ml.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class E5PromptTest {

    @Test fun laSaisieDeLEleveGardeLePrefixeQuery() {
        assertEquals("query: la photosynthèse", E5Prompt.query("  la photosynthèse "))
    }

    @Test fun lePassageCombineLecon_Resume_EtReponseOfficielle() {
        val p = E5Prompt.lessonPassage("La photosynthèse", "Les plantes fabriquent leur matière.", "Elle a lieu dans les chloroplastes")
        assertEquals(
            "passage: Leçon : La photosynthèse - Les plantes fabriquent leur matière. " +
                "Réponse officielle : Elle a lieu dans les chloroplastes",
            p
        )
    }

    @Test fun leResumeDejaPonctueNeDoublePasLePoint() {
        val p = E5Prompt.lessonPassage("Titre", "Résumé.", "Réponse")
        assertTrue(p.contains("Titre - Résumé. Réponse officielle"))
    }

    @Test fun sansResumeLeTitreSuffit() {
        assertEquals("passage: Leçon : Titre. Réponse officielle : R", E5Prompt.lessonPassage("Titre", null, "R"))
    }

    @Test fun sansLeconLeCatalogueNEstPasEncoreSynchronise() {
        assertEquals("passage: Réponse officielle : R", E5Prompt.lessonPassage(null, "  ", "R"))
    }

    @Test fun hasPrefixAccepteQueryEtPassageSeulement() {
        assertTrue(E5Prompt.hasPrefix(E5Prompt.query("x")))
        assertTrue(E5Prompt.hasPrefix(E5Prompt.passage("x")))
        assertTrue(!E5Prompt.hasPrefix("x"))
        assertTrue(!E5Prompt.hasPrefix("query:x")) // l'espace après les deux-points fait partie du préfixe
    }

    @Test fun laReponseOfficielleSurvitAuxTitresEtResumesTropLongs() {
        val p = E5Prompt.lessonPassage("T".repeat(500), "S".repeat(500), "R".repeat(2000))
        assertTrue("passage trop long : ${p.length}", p.length <= 1200) // limite de UnigramTokenizer.MAX_CHARS
        assertTrue(p.contains("Réponse officielle : " + "R".repeat(E5Prompt.MAX_REFERENCE_CHARS)))
    }
}

