package ci.kalyno.app.data.dashboard

import ci.kalyno.app.data.srs.MasteredCardRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteryCalculatorTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun row(id: String, reps: Int, chapitre: String = "Fractions", subject: String = "maths", stability: Double = 10.0, last: Long? = now) =
        MasteredCardRow(
            flashcardId = id,
            subjectId = subject,
            subjectName = subject.uppercase(),
            chapitre = chapitre,
            question = "Question $id",
            repetitions = reps,
            stability = stability,
            lastReviewedAt = last
        )

    @Test fun paliersSuiventLesSeuils() {
        assertNull(MasteryCalculator.tierOf(0))
        assertNull(MasteryCalculator.tierOf(2))
        assertEquals(MasteryTier.SOLIDE, MasteryCalculator.tierOf(3))
        assertEquals(MasteryTier.SOLIDE, MasteryCalculator.tierOf(4))
        assertEquals(MasteryTier.AVANCEE, MasteryCalculator.tierOf(5))
        assertEquals(MasteryTier.EXPERTE, MasteryCalculator.tierOf(8))
    }

    @Test fun uneCarteSousLeSeuilNEstJamaisAffichee() {
        val cards = MasteryCalculator.cards(listOf(row("a", 2), row("b", 3)), now)
        assertEquals(listOf("b"), cards.map { it.cardId })
    }

    @Test fun lesCartesSontListeesIndividuellement() {
        val rows = listOf(row("a", 3), row("b", 6), row("c", 9), row("d", 4, chapitre = "Verbes", subject = "francais"))
        val cards = MasteryCalculator.cards(rows, now)
        assertEquals(4, cards.size)
        assertEquals(listOf("c", "b", "d", "a"), cards.map { it.cardId })
        assertEquals("Question c", cards.first().question)
        assertEquals("Verbes", cards.first { it.cardId == "d" }.chapterTitle)
    }

    @Test fun nombreDeNotionsDifferentDuNombreDeCartes() {
        val rows = listOf(row("a", 3), row("b", 6), row("d", 4, chapitre = "Verbes", subject = "francais"))
        assertEquals(3, MasteryCalculator.cards(rows, now).size)
        val notions = MasteryCalculator.notions(rows, now)
        assertEquals(2, notions.size)
        assertEquals(2, notions.first { it.label == "Fractions" }.masteredCards)
    }

    @Test fun retentionAbsenteSansStabiliteOuSansRevision() {
        val cards = MasteryCalculator.cards(listOf(row("a", 3, stability = 0.0), row("b", 3, last = null), row("c", 3)), now)
        assertNull(cards.first { it.cardId == "a" }.score)
        assertNull(cards.first { it.cardId == "b" }.score)
        assertNotNull(cards.first { it.cardId == "c" }.score)
    }

    @Test fun retentionDiminueAvecLeTemps() {
        val recent = MasteryCalculator.cards(listOf(row("a", 3, last = now)), now).first().score!!
        val old = MasteryCalculator.cards(listOf(row("a", 3, last = now - 30 * day)), now).first().score!!
        assertTrue(old < recent)
    }
}
