package ci.kalyno.app.data.faq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaqSearchTest {

    private fun item(id: String, category: String, question: String, answer: String = "") =
        FaqItem(id = id, category = category, question = question, answer = answer)

    private val faq = listOf(
        item("1", "Compte", "Comment changer de classe ?", "Va dans le Profil, puis « Changer de classe »."),
        item("2", "Dojo", "Qu'est-ce que le Dojo ?", "Un espace d'entraînement pour les élèves."),
        item("3", "Compte", "Mes progrès sont-ils sauvegardés ?", "Oui, dès que tu te connectes.")
    )

    @Test
    fun fold_removesAccentsAndKeepsLength() {
        val text = "Élève à l'École Œuvre"
        assertEquals("eleve a l'ecole œuvre", FaqSearch.fold(text))
        assertEquals(text.length, FaqSearch.fold(text).length)
    }

    @Test
    fun blankQuery_returnsEverything() {
        assertEquals(faq, FaqSearch.filter(faq, ""))
        assertEquals(faq, FaqSearch.filter(faq, "   "))
    }

    @Test
    fun search_ignoresCaseAndAccents() {
        assertEquals(listOf("2"), FaqSearch.filter(faq, "ENTRAINEMENT").map { it.id })
        assertEquals(listOf("2"), FaqSearch.filter(faq, "eleves").map { it.id })
        assertEquals(listOf("3"), FaqSearch.filter(faq, "progres").map { it.id })
    }

    @Test
    fun search_requiresEveryWord() {
        assertEquals(listOf("1"), FaqSearch.filter(faq, "classe profil").map { it.id })
        assertTrue(FaqSearch.filter(faq, "classe dojo").isEmpty())
    }

    @Test
    fun search_alsoLooksInCategory() {
        assertEquals(listOf("2"), FaqSearch.filter(faq, "dojo").map { it.id })
        assertEquals(listOf("1", "3"), FaqSearch.filter(faq, "compte").map { it.id })
    }

    @Test
    fun noMatch_returnsEmpty() {
        assertTrue(FaqSearch.filter(faq, "zzzz").isEmpty())
    }

    @Test
    fun group_followsFirstAppearanceOrder() {
        val ordered = listOf(
            item("a", "Dojo", "Q1"),
            item("b", "Compte", "Q2"),
            item("c", "Dojo", "Q3")
        )
        val sections = FaqSearch.group(ordered)
        assertEquals(listOf("Dojo", "Compte"), sections.map { it.category })
        assertEquals(listOf("a", "c"), sections[0].items.map { it.id })
    }

    @Test
    fun highlightRanges_findsEveryOccurrence() {
        val ranges = FaqSearch.highlightRanges("Changer de classe, changer de vie", FaqSearch.terms("changer"))
        assertEquals(listOf(0..6, 19..25), ranges)
    }

    @Test
    fun highlightRanges_mergesOverlaps() {
        assertEquals(listOf(0..4), FaqSearch.highlightRanges("Changer", listOf("cha", "hang")))
    }

    @Test
    fun highlightRanges_matchesAccentedText() {
        assertEquals(listOf(0..4), FaqSearch.highlightRanges("élève", FaqSearch.terms("ELEVE")))
    }

    @Test
    fun highlightRanges_emptyWithoutTerms() {
        assertTrue(FaqSearch.highlightRanges("texte", emptyList()).isEmpty())
    }
}
