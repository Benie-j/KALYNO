package ci.kalyno.app.data.srs

import ci.kalyno.app.data.dashboard.SrsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.floor

class SrsEngineTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val day = 24 * 60 * 60 * 1000L
    private val hour = 60 * 60 * 1000L
    private val t0 = 1_700_000_000_000L // 14/11/2023 22:13 UTC

    private fun fresh(id: String = "c1") =
        CardProgressEntity(studentId = "s", flashcardId = id, dueDate = t0)

    /** Carte déjà apprise : stabilité [s] jours, difficulté [d], dernière révision il y a [elapsed] jours. */
    private fun learned(s: Double, d: Double, elapsed: Int, id: String = "c1", reps: Int = 3) =
        CardProgressEntity(
            studentId = "s",
            flashcardId = id,
            easeFactor = 2.5f,
            intervalDays = s.toInt(),
            repetitions = reps,
            dueDate = t0,
            lastReviewedAt = t0 - elapsed * day,
            stability = s,
            difficulty = d
        )

    private fun context(due: Long, interval: Int, coefficient: Float) = CardWithContext(
        flashcardId = "x", subjectId = "m", coefficient = coefficient,
        dueDate = due, easeFactor = 2.5f, intervalDays = interval, repetitions = 1,
        stability = interval.toDouble(), lastReviewedAt = due - interval * day
    )

    // ------------------------------------------------------------------ quatre notes

    @Test
    fun nouvelleCarte_su_estDueDansTroisJours() {
        val next = SrsEngine.computeNextProgress(fresh(), ReviewRating.SU, t0, utc)
        assertEquals(3, next.intervalDays)
        assertEquals(3.2602, next.stability, 0.001)
        assertEquals(4.8846, next.difficulty, 0.001)
        assertEquals(SrsEngine.startOfDay(t0, utc) + 3 * day, next.dueDate)
        assertEquals(1, next.repetitions)
        assertEquals(0, next.lapses)
    }

    @Test
    fun nouvelleCarte_lesQuatreNotesDonnentDesIntervallesOrdonnes() {
        val iv = SrsEngine.previewIntervals(fresh(), t0, utc)
        assertEquals(1, iv[0])                  // J'ai oublié : demain
        assertEquals(1, iv[1])                  // J'ai hésité
        assertEquals(3, iv[2])                  // J'ai su
        assertTrue("trop facile: ${iv[3]}", iv[3] in 15..17)
        assertTrue(iv[0] <= iv[1] && iv[1] < iv[2] && iv[2] < iv[3])
    }

    @Test
    fun reussitesSu_donnentDesIntervallesQuiGrandissent() {
        var c = fresh()
        var now = t0
        val intervals = mutableListOf<Int>()
        repeat(5) {
            c = SrsEngine.computeNextProgress(c, ReviewRating.SU, now, utc)
            intervals += c.intervalDays
            now = c.dueDate + 1_000
        }
        assertEquals(3, intervals[0])
        assertTrue("2e intervalle: ${intervals[1]}", intervals[1] in 10..12)
        for (i in 1 until intervals.size) {
            assertTrue("croissance: $intervals", intervals[i] > intervals[i - 1])
        }
    }

    @Test
    fun hesiteProgresseMoinsViteQueSu_etTropFacileEstLePlusRapide() {
        val c = learned(s = 10.0, d = 5.0, elapsed = 10)
        val iv = SrsEngine.previewIntervals(c, t0, utc)
        assertTrue("hésité: ${iv[1]}", iv[1] in 14..16)
        assertTrue("su: ${iv[2]}", iv[2] in 30..34)
        assertTrue("trop facile: ${iv[3]}", iv[3] in 75..86)
        assertTrue(iv[1] < iv[2] && iv[2] < iv[3])
    }

    @Test
    fun oubli_repartAUnJour_baisseLaStabilite_etCompteUnOubli() {
        val c = learned(s = 30.0, d = 5.0, elapsed = 30, reps = 5)
        val next = SrsEngine.computeNextProgress(c, ReviewRating.OUBLIE, t0, utc)
        assertEquals(1, next.intervalDays)
        assertEquals(0, next.repetitions)
        assertEquals(1, next.lapses)
        assertEquals(3.733, next.stability, 0.01)
        assertTrue("difficulté: ${next.difficulty}", next.difficulty > 5.0)
    }

    // ------------------------------------------------------------------ garde-fous

    @Test
    fun revisionEnAvance_gagneMoinsQueLaRevisionAuBonMoment() {
        val early = learned(s = 10.0, d = 5.0, elapsed = 2)
        val onTime = learned(s = 10.0, d = 5.0, elapsed = 10)
        val a = SrsEngine.computeNextProgress(early, ReviewRating.SU, t0, utc)
        val b = SrsEngine.computeNextProgress(onTime, ReviewRating.SU, t0, utc)
        assertTrue("jamais de recul: ${a.intervalDays}", a.intervalDays >= 10)
        assertTrue("moins de crédit: ${a.intervalDays} < ${b.intervalDays}", a.intervalDays < b.intervalDays)
    }

    @Test
    fun revisionLeMemeJour_nEnfleJamaisLaStabilite() {
        val c = learned(s = 10.0, d = 5.0, elapsed = 0)
        val next = SrsEngine.computeNextProgress(c, ReviewRating.SU, t0, utc)
        assertTrue("stabilité: ${next.stability}", next.stability in 10.0..10.5)
        assertTrue("intervalle: ${next.intervalDays}", next.intervalDays in 9..11)
    }

    @Test
    fun carteAncienne_estReprise_sansPerdreSaProgression() {
        // Ancien état SM-2 : 10 jours, facteur 2,5, jamais passé par FSRS (stabilité 0)
        val legacy = CardProgressEntity(
            studentId = "s", flashcardId = "c1", easeFactor = 2.5f, intervalDays = 10, repetitions = 3,
            dueDate = t0, lastReviewedAt = t0 - 10 * day
        )
        val next = SrsEngine.computeNextProgress(legacy, ReviewRating.SU, t0, utc)
        assertEquals(32.85, next.stability, 0.05)
        assertTrue("intervalle: ${next.intervalDays}", next.intervalDays in 30..34)
        assertEquals(4, next.repetitions)
        assertTrue(next.stability > 0.0 && next.difficulty in 1.0..10.0)
    }

    @Test
    fun plafondExamen_limiteLIntervalle() {
        val c = learned(s = 100.0, d = 5.0, elapsed = 100)
        val next = SrsEngine.computeNextProgress(c, ReviewRating.SU, t0, utc, maxIntervalDays = 10)
        assertEquals(10, next.intervalDays)
        assertEquals(SrsEngine.startOfDay(t0, utc) + 10 * day, next.dueDate)
    }

    @Test
    fun plafondExamen_dependDeLaDatePubliee() {
        val today = SrsEngine.dateOf(t0, utc)
        assertEquals(SrsEngine.MAX_INTERVAL_DAYS, SrsEngine.examIntervalCap(t0, null, utc))
        assertEquals(SrsEngine.MAX_INTERVAL_DAYS, SrsEngine.examIntervalCap(t0, today.minusDays(3), utc))
        assertEquals(1, SrsEngine.examIntervalCap(t0, today, utc))
        assertEquals(1, SrsEngine.examIntervalCap(t0, today.plusDays(1), utc))
        assertEquals(1, SrsEngine.examIntervalCap(t0, today.plusDays(2), utc))
        assertEquals(2, SrsEngine.examIntervalCap(t0, today.plusDays(3), utc))
        assertEquals(4, SrsEngine.examIntervalCap(t0, today.plusDays(10), utc))
        assertEquals(44, SrsEngine.examIntervalCap(t0, today.plusDays(90), utc))
    }

    @Test
    fun difficulteStabiliteEtIntervalleRestentBornes() {
        var c = fresh()
        var now = t0
        repeat(40) {
            c = SrsEngine.computeNextProgress(c, ReviewRating.OUBLIE, now, utc)
            now = c.dueDate + 1_000
        }
        assertTrue(c.difficulty <= 10.0 && c.stability >= 0.01)
        assertTrue(c.easeFactor >= 1.3f)

        c = fresh()
        now = t0
        repeat(40) {
            c = SrsEngine.computeNextProgress(c, ReviewRating.TROP_FACILE, now, utc)
            now = c.dueDate + 1_000
        }
        assertTrue(c.difficulty >= 1.0)
        assertTrue(c.easeFactor <= 3.0f)
        assertTrue(c.intervalDays <= SrsEngine.MAX_INTERVAL_DAYS)
    }

    // ------------------------------------------------------------------ priorité du jour

    @Test
    fun urgence_carteFragileDevanceCarteSolide() {
        val fragile = context(due = t0 - 3 * day, interval = 1, coefficient = 3f)
        val solide = context(due = t0 - 3 * day, interval = 60, coefficient = 3f)
        assertTrue(SrsEngine.urgencyScore(fragile, t0) > SrsEngine.urgencyScore(solide, t0))
    }

    @Test
    fun urgence_coefficientElevePese_maisSansEcraserLesAutres() {
        val maths = context(due = t0 - 2 * day, interval = 4, coefficient = 7f)
        val autre = context(due = t0 - 2 * day, interval = 4, coefficient = 1f)
        val ratio = SrsEngine.urgencyScore(maths, t0) / SrsEngine.urgencyScore(autre, t0)
        assertTrue("ratio: $ratio", ratio > 2f && ratio < 3f) // racine de 7, pas 7
    }

    @Test
    fun prioritize_excluLesCartesFutures_etTrieParUrgence() {
        val future = context(due = t0 + day, interval = 5, coefficient = 9f)
        val urgente = context(due = t0 - 5 * day, interval = 2, coefficient = 4f)
        val calme = context(due = t0 - 1 * day, interval = 30, coefficient = 1f)
        val sorted = SrsEngine.prioritize(listOf(calme, future, urgente), t0)
        assertEquals(listOf(urgente, calme), sorted)
    }

    @Test
    fun urgence_carteAncienneSansStabilite_resteCalculable() {
        val legacy = CardWithContext(
            flashcardId = "x", subjectId = "m", coefficient = 2f,
            dueDate = t0 - 2 * day, easeFactor = 2.5f, intervalDays = 4, repetitions = 2
        )
        val score = SrsEngine.urgencyScore(legacy, t0)
        assertTrue("score: $score", score > 0f && score < 2f)
    }

    // ------------------------------------------------------------------ simulation sur 90 jours

    private class Outcome(
        val trials: Int,
        val recalled: Int,
        val reviews: Int,
        val introduced: Int,
        val maxDaily: Int,
        val lastDue: Long,
        val examReadiness: Double,
        val worstReadiness: Double
    )

    /**
     * Un élève simulé révise 90 jours. Sa mémoire réelle suit la courbe de l'oubli du moteur ;
     * un tirage déterministe (suite du nombre d'or) décide s'il se souvient ou non.
     * Il traite au plus 25 cartes dues par jour et découvre les nouvelles selon le quota de l'appli.
     */
    private fun simulate(examDate: LocalDate?, examDay: Int = 90): Outcome {
        val base = SrsEngine.startOfDay(t0, utc)
        val ids = (0 until 60).map { "c$it" }
        val coefficients = floatArrayOf(6f, 4f, 2f)
        val progress = HashMap<String, CardProgressEntity>()
        var introduced = 0
        var counter = 0
        var trials = 0
        var recalled = 0
        var reviews = 0
        var maxDaily = 0
        var lastDue = 0L

        fun elapsed(p: CardProgressEntity, now: Long): Double =
            ChronoUnit.DAYS.between(
                SrsEngine.dateOf(p.lastReviewedAt!!, utc),
                SrsEngine.dateOf(now, utc)
            ).toDouble()

        for (d in 0 until examDay) {
            val now = base + d * day + 9 * hour
            val cap = SrsEngine.examIntervalCap(now, examDate, utc)

            val due = progress.values
                .filter { it.dueDate <= now }
                .map { p ->
                    val index = p.flashcardId.drop(1).toInt()
                    CardWithContext(
                        p.flashcardId, "m${index % 3}", coefficients[index % 3], p.dueDate,
                        p.easeFactor, p.intervalDays, p.repetitions, p.stability, p.lastReviewedAt
                    )
                }
            val queue = SrsEngine.prioritize(due, now).take(DAILY_CAPACITY)

            queue.forEach { card ->
                val p = progress.getValue(card.flashcardId)
                val r = SrsEngine.retrievability(elapsed(p, now), p.stability)
                counter++
                val x = counter * 0.6180339887498949
                val u = x - floor(x)
                val rating = if (u < r) ReviewRating.SU else ReviewRating.OUBLIE
                trials++
                if (rating == ReviewRating.SU) recalled++
                progress[p.flashcardId] = SrsEngine.computeNextProgress(p, rating, now, utc, cap)
            }

            val quota = SrsConfig.newCardQuota(due.size)
            var newToday = 0
            while (introduced < ids.size && newToday < quota) {
                val id = ids[introduced]
                progress[id] = SrsEngine.computeNextProgress(
                    CardProgressEntity(studentId = "s", flashcardId = id, dueDate = now),
                    ReviewRating.SU, now, utc, cap
                )
                introduced++
                newToday++
            }

            reviews += queue.size + newToday
            maxDaily = maxOf(maxDaily, queue.size + newToday)
        }

        val examNow = base + examDay * day + 9 * hour
        val readiness = progress.values.map {
            SrsEngine.retrievability(elapsed(it, examNow), it.stability)
        }
        progress.values.forEach { lastDue = maxOf(lastDue, it.dueDate) }

        return Outcome(
            trials, recalled, reviews, introduced, maxDaily, lastDue,
            readiness.average(), readiness.minOrNull() ?: 0.0
        )
    }

    @Test
    fun simulation90Jours_avecDateExamen_toutEstRevuAvantLeJourJ() {
        val base = SrsEngine.startOfDay(t0, utc)
        val examDate = SrsEngine.dateOf(base, utc).plusDays(90)
        val out = simulate(examDate)

        assertEquals("toutes les cartes sont découvertes", 60, out.introduced)
        assertTrue("aucune carte programmée après le jour J", out.lastDue <= base + 90 * day)
        assertTrue(
            "rétention en révision: ${out.recalled}/${out.trials}",
            out.recalled.toDouble() / out.trials >= 0.88
        )
        assertTrue("prêt le jour J (moyenne): ${out.examReadiness}", out.examReadiness >= 0.93)
        assertTrue("prêt le jour J (pire carte): ${out.worstReadiness}", out.worstReadiness >= 0.85)
        assertTrue("charge totale: ${out.reviews}", out.reviews in 350..900)
        assertTrue(
            "charge maximale en un jour: ${out.maxDaily}",
            out.maxDaily <= DAILY_CAPACITY + SrsConfig.DAILY_NEW_CARDS
        )
    }

    @Test
    fun simulation90Jours_sansDateExamen_lesCartesPartentAuDelaDuJourJ() {
        val base = SrsEngine.startOfDay(t0, utc)
        val sans = simulate(examDate = null)
        val avec = simulate(SrsEngine.dateOf(base, utc).plusDays(90))

        assertTrue("sans plafond, des cartes sont dues après le jour J", sans.lastDue > base + 90 * day)
        assertTrue("le plafond coûte quelques révisions de plus", avec.reviews > sans.reviews)
        assertTrue("mais l'élève est mieux préparé", avec.examReadiness > sans.examReadiness)
    }

    private companion object {
        const val DAILY_CAPACITY = 25
    }
}
