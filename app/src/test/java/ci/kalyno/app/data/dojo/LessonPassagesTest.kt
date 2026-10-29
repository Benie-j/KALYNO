package ci.kalyno.app.data.dojo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
