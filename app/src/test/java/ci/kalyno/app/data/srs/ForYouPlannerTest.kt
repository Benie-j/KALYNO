package ci.kalyno.app.data.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForYouPlannerTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_700_000_000_000L

    private fun card(id: String, stability: Double, elapsedDays: Int, lapses: Int = 0, relearn: Int = 0) =
        CardWithContext(
            flashcardId = id, subjectId = "maths", coefficient = 1f,
            dueDate = now - day, easeFactor = 2.5f, intervalDays = stability.toInt(), repetitions = 3,
            stability = stability, lastReviewedAt = now - elapsedDays * day,
            lapses = lapses, relearnLeft = relearn
        )

    @Test
    fun fragileLessonComesFirst() {
        val due = listOf(card("a1", 2.0, 10), card("b1", 40.0, 41))
        val ranked = ForYouPlanner.rank(due, mapOf("a1" to "A", "b1" to "B"), now, emptyMap())
        assertEquals(listOf("A", "B"), ranked.map { it.chapterId })
    }

    @Test
    fun relearningCardsAreCountedAndBoosted() {
        val due = listOf(card("a1", 5.0, 6, lapses = 2, relearn = 2), card("b1", 5.0, 6))
        val ranked = ForYouPlanner.rank(due, mapOf("a1" to "A", "b1" to "B"), now, emptyMap())
        assertEquals("A", ranked.first().chapterId)
        assertEquals(1, ranked.first().relearnCount)
        assertTrue(ranked.first().score > ranked.last().score)
    }

    @Test
    fun cardsOutsideTheLessonsAreIgnored() {
        assertTrue(ForYouPlanner.rank(listOf(card("x", 2.0, 5)), emptyMap(), now, emptyMap()).isEmpty())
    }
}
