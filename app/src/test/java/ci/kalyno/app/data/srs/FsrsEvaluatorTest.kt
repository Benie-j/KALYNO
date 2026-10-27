package ci.kalyno.app.data.srs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import kotlin.math.ln

class FsrsEvaluatorTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val day = 24 * 60 * 60 * 1000L
    private val t0 = 1_700_000_000_000L

    private fun log(rating: ReviewRating, at: Long) = ReviewLogEntity(
        studentId = "s", flashcardId = "a", rating = rating,
        intervalBefore = 0, intervalAfter = 0, reviewedAt = at
    )

    @Test
    fun sansHistorique_rienACompare() {
        val r = FsrsEvaluator.evaluate(emptyList(), zone = utc)
        assertEquals(0, r.reviews)
    }

    @Test
    fun premiereRevisionEtMemeJour_neCompentPas() {
        val logs = listOf(log(ReviewRating.SU, t0), log(ReviewRating.SU, t0 + 60 * 60 * 1000L))
        val r = FsrsEvaluator.evaluate(logs, zone = utc)
        assertEquals(0, r.reviews)
    }

    @Test
    fun oubliApresDixJours_perteEgaleMoinsLogDeLOubli() {
        val first = SrsEngine.computeNextProgress(
            CardProgressEntity(studentId = "s", flashcardId = "a", dueDate = 0L),
            ReviewRating.SU, t0, utc
        )
        val p = SrsEngine.retrievability(10.0, first.stability)
        val logs = listOf(log(ReviewRating.SU, t0), log(ReviewRating.OUBLIE, t0 + 10 * day))
        val r = FsrsEvaluator.evaluate(logs, zone = utc)
        assertEquals(1, r.reviews)
        assertEquals(-ln(1.0 - p), r.logLoss, 1e-9)
    }

    @Test
    fun ordreDesLogsSansEffet() {
        val logs = listOf(
            log(ReviewRating.SU, t0),
            log(ReviewRating.SU, t0 + 3 * day),
            log(ReviewRating.OUBLIE, t0 + 12 * day)
        )
        val a = FsrsEvaluator.evaluate(logs, zone = utc)
        val b = FsrsEvaluator.evaluate(logs.reversed(), zone = utc)
        assertEquals(2, a.reviews)
        assertEquals(a.logLoss, b.logLoss, 1e-12)
    }
}
