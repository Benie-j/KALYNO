package ci.kalyno.app.data.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class SrsEngineTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 24 * 60 * 60 * 1000L
    private val t0 = 1_700_000_000_000L // 14/11/2023 22:13 UTC

    private fun card(
        interval: Int = 0,
        reps: Int = 0,
        ease: Float = 2.5f,
        due: Long = t0,
        last: Long? = null
    ) = CardProgressEntity(
        studentId = "s",
        flashcardId = "c1",
        easeFactor = ease,
        intervalDays = interval,
        repetitions = reps,
        dueDate = due,
        lastReviewedAt = last
    )

    private fun context(due: Long, interval: Int, coefficient: Float) = CardWithContext(
        flashcardId = "x", subjectId = "m", coefficient = coefficient,
        dueDate = due, easeFactor = 2.5f, intervalDays = interval, repetitions = 1
    )

    @Test
    fun nouvelleCarteFacile_estDueLeLendemainMatin() {
        val next = SrsEngine.computeNextProgress(card(), ReviewRating.TROP_FACILE, t0, utc)
        assertEquals(1, next.intervalDays)
        assertEquals(SrsEngine.startOfDay(t0, utc) + day, next.dueDate)
        assertTrue(next.dueDate - t0 < day)
    }

    @Test
    fun reussitesFaciles_donnent1Puis6Puis15environ() {
        var c = card()
        var now = t0
        val intervals = mutableListOf<Int>()
        repeat(3) {
            c = SrsEngine.computeNextProgress(c, ReviewRating.TROP_FACILE, now, utc)
            intervals += c.intervalDays
            now = c.dueDate + 1_000
        }
        assertEquals(1, intervals[0])
        assertEquals(6, intervals[1])
        assertTrue("3e intervalle: ${intervals[2]}", intervals[2] in 15..19)
    }

    @Test
    fun easeFactorEtIntervalleSontPlafonnes() {
        var c = card()
        var now = t0
        repeat(40) {
            c = SrsEngine.computeNextProgress(c, ReviewRating.TROP_FACILE, now, utc)
            now = c.dueDate + 1_000
        }
        assertTrue(c.easeFactor <= 3.0f)
        assertTrue(c.intervalDays <= SrsEngine.MAX_INTERVAL_DAYS)
    }

    @Test
    fun plafondExamen_limiteLIntervalle() {
        val c = card(interval = 20, reps = 4, last = t0 - 20 * day, due = t0)
        val next = SrsEngine.computeNextProgress(c, ReviewRating.TROP_FACILE, t0, utc, maxIntervalDays = 10)
        assertEquals(10, next.intervalDays)
    }

    @Test
    fun oubli_repartAUnJour_etBaisseLEase_sansDescendreSous13() {
        val c = card(interval = 30, reps = 5, ease = 1.4f, last = t0 - 30 * day)
        val next = SrsEngine.computeNextProgress(c, ReviewRating.OUBLIE, t0, utc)
        assertEquals(1, next.intervalDays)
        assertEquals(0, next.repetitions)
        assertEquals(1.3f, next.easeFactor, 0.0001f)
    }

    @Test
    fun hesitation_faitProgresserSansChangerLEase() {
        val c = card(interval = 10, reps = 3, last = t0 - 10 * day)
        val next = SrsEngine.computeNextProgress(c, ReviewRating.HESITE, t0, utc)
        assertTrue("intervalle: ${next.intervalDays}", next.intervalDays in 11..13)
        assertEquals(2.5f, next.easeFactor, 0.0001f)
        assertEquals(4, next.repetitions)
    }

    @Test
    fun carteOubliee_puisFacile_saute_a6_puis_a15environ() {
        val lapsed = SrsEngine.computeNextProgress(
            card(interval = 20, reps = 4, last = t0 - 20 * day), ReviewRating.OUBLIE, t0, utc
        )
        val first = SrsEngine.computeNextProgress(lapsed, ReviewRating.TROP_FACILE, lapsed.dueDate + 1_000, utc)
        assertEquals(6, first.intervalDays)
        val second = SrsEngine.computeNextProgress(first, ReviewRating.TROP_FACILE, first.dueDate + 1_000, utc)
        assertTrue("intervalle: ${second.intervalDays}", second.intervalDays in 13..17)
    }

    @Test
    fun revisionEnAvance_neFaitJamaisReculer() {
        val c = card(interval = 10, reps = 3, last = t0 - 2 * day, due = t0 + 8 * day)
        val next = SrsEngine.computeNextProgress(c, ReviewRating.TROP_FACILE, t0, utc)
        assertTrue("intervalle: ${next.intervalDays}", next.intervalDays in 10..11)
    }

    @Test
    fun urgence_carteFragileDevanceCarteSolide() {
        val fragile = context(due = t0 - 3 * day, interval = 1, coefficient = 3f)
        val solide = context(due = t0 - 3 * day, interval = 60, coefficient = 3f)
        assertTrue(SrsEngine.urgencyScore(fragile, t0) > SrsEngine.urgencyScore(solide, t0))
    }

    @Test
    fun urgence_coefficientElevePese() {
        val maths = context(due = t0 - 2 * day, interval = 4, coefficient = 7f)
        val autre = context(due = t0 - 2 * day, interval = 4, coefficient = 1f)
        assertTrue(SrsEngine.urgencyScore(maths, t0) > SrsEngine.urgencyScore(autre, t0))
    }

    @Test
    fun prioritize_excluLesCartesFutures_etTrieParUrgence() {
        val future = context(due = t0 + day, interval = 5, coefficient = 9f)
        val urgente = context(due = t0 - 5 * day, interval = 2, coefficient = 4f)
        val calme = context(due = t0 - 1 * day, interval = 30, coefficient = 1f)
        val sorted = SrsEngine.prioritize(listOf(calme, future, urgente), t0)
        assertEquals(listOf(urgente, calme), sorted)
    }
}
