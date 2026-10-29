package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.dashboard.SrsConfig
import ci.kalyno.app.data.srs.ReviewRating

/**
 * Règles d'évolution d'une carte dans le Dojo. Pur Kotlin : testable sans Android.
 *
 * Le niveau ([stage]) est propre à chaque élève (table `card_progress`) :
 * - réussite (Su / Trop facile) : +1 ;
 * - hésitation : le niveau ne bouge pas (on ne monte pas sur une réponse approximative) ;
 * - oubli : on recule, et une carte déjà en saisie libre retombe en QCM pour reconstruire la trace.
 */
object DojoStagePolicy {

    val freeInputStage: Int get() = SrsConfig.DOJO_FREE_INPUT_STAGE
    val maxStage: Int get() = SrsConfig.DOJO_MAX_STAGE

    fun requiresFreeInput(stage: Int): Boolean = stage >= freeInputStage

    fun next(stage: Int, rating: ReviewRating): Int {
        val current = stage.coerceIn(0, maxStage)
        return when (rating) {
            ReviewRating.TROP_FACILE, ReviewRating.SU -> (current + 1).coerceAtMost(maxStage)
            ReviewRating.HESITE -> current
            ReviewRating.OUBLIE ->
                if (requiresFreeInput(current)) freeInputStage - 1 else (current - 1).coerceAtLeast(0)
        }
    }

    /** Repli pour une progression restaurée du cloud (qui ne stocke pas encore le niveau Dojo). */
    fun fromRepetitions(repetitions: Int): Int = repetitions.coerceIn(0, maxStage)
}
