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
}
