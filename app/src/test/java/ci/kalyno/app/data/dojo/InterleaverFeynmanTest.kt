package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.dashboard.ReviewItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterleaverFeynmanTest {

    private fun item(id: String, subject: String, chapter: String = "c") = ReviewItem(
        cardId = id, subjectName = subject, chapterTitle = chapter, front = "q",
        choices = emptyList(), correctIndex = null, answer = "a", explanation = null
    )

    @Test fun aucuneMatiereDeSuite() {
        val items = List(4) { item("m$it", "A") } + List(3) { item("p$it", "B") } + List(2) { item("f$it", "C") }
        val mixed = Interleaver.mix(items, seed = 7)
        assertEquals(items.map { it.cardId }.toSet(), mixed.map { it.cardId }.toSet())
        assertEquals(items.size, mixed.size)
        mixed.zipWithNext().forEach { (a, b) -> assertFalse(a.subjectName == b.subjectName) }
    }

    @Test fun uneSeuleMatiereAlterneLesChapitres() {
        val items = listOf(item("1", "A", "x"), item("2", "A", "x"), item("3", "A", "y"), item("4", "A", "y"))
        Interleaver.mix(items, seed = 1).zipWithNext().forEach { (a, b) -> assertFalse(a.chapterTitle == b.chapterTitle) }
    }

    @Test fun ordreFsrsConserveDansUnGroupe() {
        val items = listOf(item("a1", "A"), item("b1", "B"), item("a2", "A"), item("b2", "B"))
        val ids = Interleaver.mix(items, seed = 1).filter { it.subjectName == "A" }.map { it.cardId }
        assertEquals(listOf("a1", "a2"), ids)
    }

    @Test fun feynmanBalise() {
        val e = FeynmanParser.parse("En clair : Le courant, c'est de l'eau dans un tuyau.\nAnalogie : un tuyau d'arrosage\nÀ retenir : plus de pression, plus de débit.")
        assertEquals(listOf("Le courant, c'est de l'eau dans un tuyau."), e.steps)
        assertEquals("un tuyau d'arrosage", e.analogy)
        assertEquals("plus de pression, plus de débit.", e.takeaway)
        assertTrue(e.structured)
    }

    @Test fun feynmanSansReperesResteCompatible() {
        val e = FeynmanParser.parse("Première idée. Deuxième idée.")
        assertEquals(2, e.steps.size)
        assertNull(e.analogy)
        assertFalse(e.structured)
    }

    @Test fun ligneAvecDeuxPointsNonReconnue() {
        assertEquals(listOf("Exemple : 2 + 2 = 4"), FeynmanParser.parse("Exemple : 2 + 2 = 4").steps)
    }
}
