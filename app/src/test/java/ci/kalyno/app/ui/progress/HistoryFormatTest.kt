package ci.kalyno.app.ui.progress

import ci.kalyno.app.data.progress.DojoAttemptEntity
import ci.kalyno.app.data.srs.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HistoryFormatTest {
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 30)

    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun attempt(id: Long, time: Long, success: Boolean = true, rank: Int = 1, answer: String? = "x") =
        DojoAttemptEntity(
            id = id, studentId = "e", flashcardId = "c1", attemptedAt = time, isSuccess = success,
            attemptNumber = rank, rating = ReviewRating.SU, kind = "FREE_TEXT", question = "Question ?",
            studentAnswer = answer, referenceAnswer = "Réponse", lessonTitle = "Leçon", subjectName = "SVT"
        )

    @Test fun premierCoup() = assertEquals("Réussi du premier coup", HistoryFormat.attemptLine(true, 1))

    @Test fun reussiteApresPlusieursTentatives() {
        assertEquals("Réussi à la 2e tentative · 1 échec avant", HistoryFormat.attemptLine(true, 2))
        assertEquals("Réussi à la 3e tentative · 2 échecs avant", HistoryFormat.attemptLine(true, 3))
    }

    @Test fun echecIndiqueLeRang() = assertEquals("Tentative n°2", HistoryFormat.attemptLine(false, 2))

    @Test fun rangInconnuNInventePasDeChiffre() {
        assertNull(HistoryFormat.attemptLine(true, 0))
        assertNull(HistoryFormat.attemptLine(false, 0))
    }

    @Test fun libellesDeJour() {
        assertEquals("Aujourd'hui", HistoryFormat.dayLabel(today, today))
        assertEquals("Hier", HistoryFormat.dayLabel(today.minusDays(1), today))
        assertEquals("12 octobre 2026", HistoryFormat.dayLabel(LocalDate.of(2026, 10, 12), today))
    }

    @Test fun regroupeParJourDansLOrdreRecu() {
        val rows = HistoryFormat.group(
            listOf(
                attempt(3, at(today, 15)),
                attempt(2, at(today, 9)),
                attempt(1, at(today.minusDays(1), 20))
            ), today, utc
        )
        assertEquals(5, rows.size)
        assertTrue(rows[0] is HistoryRow.Day && (rows[0] as HistoryRow.Day).label == "Aujourd'hui")
        assertTrue(rows[3] is HistoryRow.Day && (rows[3] as HistoryRow.Day).label == "Hier")
        assertEquals(listOf(3L, 2L, 1L), rows.filterIsInstance<HistoryRow.Attempt>().map { it.item.id })
    }

    @Test fun reponseVideEstTraiteeCommeNonConservee() {
        assertNull(HistoryFormat.toUi(attempt(1, at(today, 8), answer = "  "), utc).studentAnswer)
    }

    @Test fun cleDeLigneUniquePourLesListesLazy() {
        val rows = HistoryFormat.group(listOf(attempt(1, at(today, 8)), attempt(2, at(today, 9))), today, utc)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }
}
