package ci.kalyno.app.data.srs

import ci.kalyno.app.data.dojo.AnswerGrader
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * Comportement de l'élève autour du moteur FSRS ([SrsEngine]) : Kotlin pur, testable sur la JVM.
 *
 * - Latence : une bonne réponse très lente est un souvenir fragile (note abaissée) ; la latence
 *   moyenne de chaque carte est lissée et conservée.
 * - Lapses : une notion oubliée entre en réapprentissage ciblé (revient vite, jusqu'à N succès).
 *   Une « sangsue » (>= [LEECH_LAPSES] oublis) exige un succès de plus.
 * - Interleaving : une matière délaissée gagne en priorité, ce qui espace les disciplines.
 */
object LearningBehavior {

    /** Au-delà, l'élève était distrait : la mesure est ignorée (ni pénalité, ni moyenne). */
    const val MAX_VALID_LATENCY_MS = 300_000L
    const val LEECH_LAPSES = 4

    private const val SLOW_FACTOR = 6.0
    private const val SLOW_FLOOR_MS = 20_000L
    private const val EMA_ALPHA = 0.3

    private const val RELEARN_STEPS = 2
    private const val LEECH_STEPS = 3
    private const val RELEARN_MAX_INTERVAL_DAYS = 2

    private const val LAPSE_WEIGHT = 0.15
    private const val MAX_LAPSES_COUNTED = 5
    private const val RELEARN_BOOST = 1.5
    private const val NEGLECT_MAX_DAYS = 14
    private const val NEGLECT_BONUS = 0.5

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    // ---- Latence

    /** Temps au-delà duquel une bonne réponse est jugée laborieuse (selon la longueur à lire). */
    fun slowThresholdMs(questionLength: Int): Long =
        max(SLOW_FLOOR_MS, (AnswerGrader.fastThresholdMs(questionLength) * SLOW_FACTOR).toLong())

    fun isSlow(elapsedMs: Long, questionLength: Int): Boolean =
        elapsedMs in slowThresholdMs(questionLength)..MAX_VALID_LATENCY_MS

    private fun smooth(previous: Long, latencyMs: Long): Long = when {
        latencyMs <= 0L || latencyMs > MAX_VALID_LATENCY_MS -> previous
        previous <= 0L -> latencyMs
        else -> (EMA_ALPHA * latencyMs + (1.0 - EMA_ALPHA) * previous).toLong()
    }

    // ---- Rétention

    /** Probabilité de rappel de la carte à l'instant [now] (0 si jamais apprise). */
    fun retrievabilityNow(card: CardProgressEntity, now: Long): Double {
        val last = card.lastReviewedAt ?: return 0.0
        if (card.stability <= 0.0) return 0.0
        return SrsEngine.retrievability(max(0L, now - last) / DAY_MS.toDouble(), card.stability)
    }

    // ---- Après une révision

    private fun stepsFor(lapses: Int) = if (lapses >= LEECH_LAPSES) LEECH_STEPS else RELEARN_STEPS

    /**
     * Complète la progression calculée par FSRS ([after]) avec le comportement de l'élève.
     * Réapprentissage : tant qu'il reste des succès à obtenir, l'intervalle est plafonné
     * à [RELEARN_MAX_INTERVAL_DAYS] jours. « Hésité » ne fait ni avancer ni reculer.
     */
    fun afterReview(
        before: CardProgressEntity,
        after: CardProgressEntity,
        rating: ReviewRating,
        latencyMs: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): CardProgressEntity {
        val failed = rating == ReviewRating.OUBLIE
        val lapsed = after.lapses > before.lapses

        val relearnLeft = when {
            lapsed || (failed && before.relearnLeft > 0) -> stepsFor(after.lapses)
            before.relearnLeft > 0 && rating.grade >= ReviewRating.SU.grade -> before.relearnLeft - 1
            else -> before.relearnLeft
        }

        var interval = after.intervalDays
        var due = after.dueDate
        if (relearnLeft > 0 && !failed && interval > RELEARN_MAX_INTERVAL_DAYS) {
            interval = RELEARN_MAX_INTERVAL_DAYS
            due = SrsEngine.dateOf(now, zone).plusDays(interval.toLong())
                .atStartOfDay(zone).toInstant().toEpochMilli()
        }

        return after.copy(
            intervalDays = interval,
            dueDate = due,
            successCount = before.successCount + if (failed) 0 else 1,
            failCount = before.failCount + if (failed) 1 else 0,
            relearnLeft = relearnLeft,
            avgLatencyMs = smooth(before.avgLatencyMs, latencyMs)
        )
    }

    // ---- Priorité du Dojo

    /**
     * Multiplicateur appliqué au score d'urgence FSRS :
     * lapses (+15 % par oubli, plafonné) x réapprentissage (x1,5) x matière délaissée (jusqu'à x1,5).
     */
    fun priorityBoost(card: CardWithContext, now: Long, subjectLastStudied: Map<String, Long>): Float {
        val lapses = 1.0 + LAPSE_WEIGHT * min(card.lapses, MAX_LAPSES_COUNTED)
        val relearn = if (card.relearnLeft > 0) RELEARN_BOOST else 1.0
        val last = subjectLastStudied[card.subjectId]
        val idleDays = if (last == null) NEGLECT_MAX_DAYS
        else ((now - last) / DAY_MS).toInt().coerceIn(0, NEGLECT_MAX_DAYS)
        val neglect = 1.0 + NEGLECT_BONUS * idleDays / NEGLECT_MAX_DAYS
        return (lapses * relearn * neglect).toFloat()
    }
}
