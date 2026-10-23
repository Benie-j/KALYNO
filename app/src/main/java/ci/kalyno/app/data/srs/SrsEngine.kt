package ci.kalyno.app.data.srs

import java.util.concurrent.TimeUnit

/**
 * Moteur de calcul SRS (inspiré SM-2), séparé de Room et d'Android
 * pour rester simple à tester unitairement.
 */
object SrsEngine {

    private const val MIN_EASE_FACTOR = 1.3f
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * Calcule la nouvelle progression d'une carte après une révision.
     *
     * Règles (cahier des charges §4) :
     * - OUBLIE      : on repart de 0 (intervalle 1 jour), l'ease factor baisse.
     * - HESITE      : l'intervalle progresse modérément (x1.2), l'ease factor stable.
     * - TROP_FACILE : l'intervalle est multiplié par l'ease factor (x2.5 au départ),
     *                 et l'ease factor augmente légèrement (comme SM-2 classique).
     */
    fun computeNextProgress(
        current: CardProgressEntity,
        rating: ReviewRating,
        now: Long = System.currentTimeMillis()
    ): CardProgressEntity {
        val newEase: Float
        val newInterval: Int
        val newRepetitions: Int

        when (rating) {
            ReviewRating.OUBLIE -> {
                newEase = (current.easeFactor - 0.2f).coerceAtLeast(MIN_EASE_FACTOR)
                newInterval = 1
                newRepetitions = 0
            }
            ReviewRating.HESITE -> {
                newEase = current.easeFactor // inchangé : on ne pénalise pas une hésitation
                newInterval = when {
                    current.intervalDays <= 0 -> 1
                    else -> (current.intervalDays * 1.2f).toInt().coerceAtLeast(current.intervalDays + 1)
                }
                newRepetitions = current.repetitions + 1
            }
            ReviewRating.TROP_FACILE -> {
                newEase = current.easeFactor + 0.15f
                newInterval = when {
                    current.intervalDays <= 0 -> 1
                    current.repetitions == 0 -> 6 // 1re réussite franche : saut classique SM-2
                    else -> (current.intervalDays * current.easeFactor).toInt()
                        .coerceAtLeast(current.intervalDays + 1)
                }
                newRepetitions = current.repetitions + 1
            }
        }

        val newDueDate = now + newInterval * DAY_MS

        return current.copy(
            easeFactor = newEase,
            intervalDays = newInterval,
            repetitions = newRepetitions,
            dueDate = newDueDate,
            lastReviewedAt = now
        )
    }

    /**
     * Score d'urgence d'une carte (cahier des charges §3).
     *
     * Score = (retard en jours + 1) × coefficient de la matière
     *
     * - Une carte pas encore en retard a un retard de 0 → score = 1 × coefficient.
     * - Une carte en retard de 5 jours en Maths (coef 7) pèse plus
     *   qu'une carte en retard de 20 jours en une matière à coef 1.
     */
    fun urgencyScore(card: CardWithContext, now: Long = System.currentTimeMillis()): Float {
        val lateDays = TimeUnit.MILLISECONDS.toDays(now - card.dueDate)
            .coerceAtLeast(0) // une carte pas encore due compte comme 0 jour de retard
        return (lateDays + 1) * card.coefficient
    }

    /**
     * Trie une liste de cartes par score d'urgence décroissant.
     * Les cartes pas encore dues (dueDate future) sont exclues :
     * elles ne doivent pas apparaître dans la Mission du Jour.
     */
    fun prioritize(cards: List<CardWithContext>, now: Long = System.currentTimeMillis()): List<CardWithContext> {
        return cards
            .filter { it.dueDate <= now }
            .sortedByDescending { urgencyScore(it, now) }
    }
}
