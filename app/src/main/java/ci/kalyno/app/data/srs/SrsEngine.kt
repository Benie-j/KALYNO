package ci.kalyno.app.data.srs

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Moteur de calcul SRS (inspiré SM-2), séparé de Room et d'Android
 * pour rester testable unitairement.
 */
object SrsEngine {

    private const val MIN_EASE_FACTOR = 1.3f
    private const val MAX_EASE_FACTOR = 3.0f
    private const val FIRST_JUMP_DAYS = 6
    const val MAX_INTERVAL_DAYS = 365
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Début de journée (00:00) dans le fuseau donné. */
    fun startOfDay(timeMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = timeMillis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun startOfDayPlus(timeMillis: Long, days: Int, zone: TimeZone): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = startOfDay(timeMillis, zone)
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

    /**
     * Calcule la nouvelle progression d'une carte après une révision.
     *
     * Règles (cahier des charges §4) :
     * - OUBLIE      : on repart de 0 (intervalle 1 jour), l'ease factor baisse.
     * - HESITE      : l'intervalle progresse modérément (x1.2), l'ease factor stable.
     * - TROP_FACILE : 1 jour, puis 6 jours, puis intervalle x ease factor (l'ease augmente).
     *
     * Garde-fous :
     * - ease factor borné entre 1,3 et 3,0 ; intervalle borné par [maxIntervalDays]
     *   (permet de plafonner selon la date d'examen) ;
     * - échéance calée sur le début de journée (la carte apparaît le matin du jour prévu) ;
     * - révision avant l'échéance : l'intervalle ne diminue jamais, mais ne gagne pas de crédit complet ;
     * - léger étalement déterministe des longs intervalles pour éviter que toutes les cartes
     *   tombent le même jour.
     */
    fun computeNextProgress(
        current: CardProgressEntity,
        rating: ReviewRating,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        maxIntervalDays: Int = MAX_INTERVAL_DAYS
    ): CardProgressEntity {
        val cap = maxIntervalDays.coerceAtLeast(1)
        val scheduled = current.intervalDays.coerceAtLeast(0)
        val early = current.lastReviewedAt != null && now < current.dueDate
        val reference = referenceInterval(current, now, zone)

        val newEase: Float
        val newInterval: Int
        val newRepetitions: Int

        when (rating) {
            ReviewRating.OUBLIE -> {
                newEase = (current.easeFactor - 0.2f).coerceIn(MIN_EASE_FACTOR, MAX_EASE_FACTOR)
                newInterval = 1.coerceAtMost(cap)
                newRepetitions = 0
            }
            ReviewRating.HESITE -> {
                newEase = current.easeFactor.coerceIn(MIN_EASE_FACTOR, MAX_EASE_FACTOR)
                val grown = if (reference <= 0) 1 else max(reference + 1, (reference * 1.2f).toInt())
                newInterval = finalizeInterval(grown, current, early, cap)
                newRepetitions = current.repetitions + 1
            }
            ReviewRating.TROP_FACILE -> {
                newEase = (current.easeFactor + 0.15f).coerceIn(MIN_EASE_FACTOR, MAX_EASE_FACTOR)
                val grown = when {
                    scheduled <= 0 -> 1
                    scheduled < FIRST_JUMP_DAYS -> FIRST_JUMP_DAYS
                    else -> max(reference + 1, (reference * current.easeFactor).roundToInt())
                }
                newInterval = finalizeInterval(grown, current, early, cap)
                newRepetitions = current.repetitions + 1
            }
        }

        return current.copy(
            easeFactor = newEase,
            intervalDays = newInterval,
            repetitions = newRepetitions,
            dueDate = startOfDayPlus(now, newInterval, zone),
            lastReviewedAt = now
        )
    }

    /** Intervalle de référence : une révision en avance ne compte que pour le temps réellement écoulé. */
    private fun referenceInterval(current: CardProgressEntity, now: Long, zone: TimeZone): Int {
        val scheduled = current.intervalDays.coerceAtLeast(0)
        val last = current.lastReviewedAt ?: return scheduled
        if (now >= current.dueDate) return scheduled
        val elapsed = ((startOfDay(now, zone) - startOfDay(last, zone)) / DAY_MS.toDouble()).roundToInt()
        return min(scheduled, max(1, elapsed))
    }

    private fun finalizeInterval(grown: Int, current: CardProgressEntity, early: Boolean, cap: Int): Int {
        val spreadOut = withSpread(grown, current.flashcardId)
        val floor = if (early) current.intervalDays else current.intervalDays + 1
        return max(spreadOut, floor).coerceIn(1, cap)
    }

    private fun withSpread(interval: Int, cardId: String): Int {
        val spread = when {
            interval >= 30 -> 2
            interval >= 7 -> 1
            else -> 0
        }
        if (spread == 0) return interval
        return interval + (Math.floorMod(cardId.hashCode(), 2 * spread + 1) - spread)
    }

    /**
     * Score d'urgence d'une carte.
     *
     * Score = coefficient × (1 + retard en jours / intervalle en jours)
     *
     * - Le rapport retard/intervalle mesure le risque d'oubli : 3 jours de retard sur une carte
     *   à 1 jour d'intervalle (fragile) pèsent bien plus que sur une carte à 60 jours (solide).
     * - Le coefficient de la matière à l'examen reste le multiplicateur d'importance.
     */
    fun urgencyScore(card: CardWithContext, now: Long = System.currentTimeMillis()): Float {
        val lateDays = ((now - card.dueDate) / DAY_MS.toFloat()).coerceAtLeast(0f)
        val ratio = lateDays / card.intervalDays.coerceAtLeast(1)
        return card.coefficient * (1f + ratio)
    }

    /**
     * Trie les cartes dues par score d'urgence décroissant (à égalité : la plus ancienne échéance d'abord).
     * Les cartes pas encore dues sont exclues.
     */
    fun prioritize(cards: List<CardWithContext>, now: Long = System.currentTimeMillis()): List<CardWithContext> {
        return cards
            .filter { it.dueDate <= now }
            .map { it to urgencyScore(it, now) }
            .sortedWith(
                compareByDescending<Pair<CardWithContext, Float>> { it.second }
                    .thenBy { it.first.dueDate }
            )
            .map { it.first }
    }
}
