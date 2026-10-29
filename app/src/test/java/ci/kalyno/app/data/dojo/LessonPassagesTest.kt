package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.LearningCapsule
import ci.kalyno.app.data.LearningChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonPassagesTest {

    @Test fun retireLeHtmlEtGardeLeTexte() {
        val out = LessonPassages.split("<p>La mitochondrie produit l'énergie de la cellule.</p><p>Elle possède sa propre ADN circulaire.</p>")
        assertEquals(2, out.size)
        assertFalse(out.any { '<' in it })
    }

    @Test fun lesFormulesLatexRestentLisiblesAvecLeursChiffres() {
        val out = LessonPassages.split("Dans un triangle rectangle, \$a^{2} + b^{2} = c^{2}\$ relie les trois côtés du triangle.")
        assertEquals(1, out.size)
        assertTrue("a2 + b2 = c2" in out[0])
        assertFalse('$' in out[0])
    }

    @Test fun ignoreLesTitresTropCourts() =
        assertTrue(LessonPassages.split("Introduction\n\nAstuce").isEmpty())

    @Test fun decoupeUnLongParagrapheEnPassagesBornes() {
        val sentence = "La photosynthèse transforme l'énergie lumineuse en énergie chimique dans les chloroplastes."
        val out = LessonPassages.split(List(8) { sentence }.joinToString(" "))
        assertTrue(out.size > 1)
        assertTrue(out.all { it.length <= LessonPassages.MAX_PASSAGE_CHARS })
    }

    @Test fun separeLesParagraphesParLignesVides() {
        val out = LessonPassages.split("Premier paragraphe assez long pour compter.\n\nSecond paragraphe assez long pour compter.")
        assertEquals(2, out.size)
    }

    @Test fun texteVideDonneListeVide() = assertTrue(LessonPassages.split("   ").isEmpty())

    private fun chapter(vararg capsules: LearningCapsule) =
        LearningChapter(id = "l1", title = "La photosynthèse", capsules = capsules.toList(), cards = emptyList())

    private fun capsule(kind: String, body: String) =
        LearningCapsule(id = kind + body.length, title = "", body = body, minutes = 1, cards = emptyList(), kind = kind)

    @Test fun lResumeVientDuBlocIntroEnPriorite() {
        val c = chapter(capsule("text", "Un texte quelconque assez long pour compter."), capsule("intro", "<p>Les plantes fabriquent leur matière.</p>"))
        assertEquals("Les plantes fabriquent leur matière.", LessonPassages.summaryOf(c))
    }

    @Test fun sansIntroLePremierTexteSertDeResume() {
        val c = chapter(capsule("tip", "Astuce"), capsule("text", "Le texte de la leçon explique le cycle."))
        assertEquals("Le texte de la leçon explique le cycle.", LessonPassages.summaryOf(c))
    }

    @Test fun lResumeEstBorneEtCoupeEnFinDePhrase() {
        val long = "Première phrase complète sur la leçon. " + "Un mot ".repeat(80)
        val summary = LessonPassages.summaryOf(chapter(capsule("intro", long)))!!
        assertTrue(summary.length <= LessonPassages.MAX_SUMMARY_CHARS + 1)
    }

    @Test fun uneLeconSansTexteNAPasDeResume() = assertNull(LessonPassages.summaryOf(chapter()))
}
