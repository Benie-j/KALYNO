package ci.kalyno.app.data.badges

/** Un objectif de série : le badge visé, s'il est atteint, et à quelle date il l'a été. */
data class StreakGoalState(
    val def: BadgeDef,
    val reached: Boolean,
    /** Date de déblocage enregistrée ; null si l'objectif vient d'être atteint ou n'est pas encore enregistré. */
    val reachedAt: Long?
)

/**
 * Objectifs de série : une seule source de vérité pour l'Accueil et le Profil.
 *
 * Les paliers sont ceux des badges de série du [BadgeCatalog] (aujourd'hui 1, 7 et 30 jours) :
 * ajouter un badge de série au catalogue met à jour partout les objectifs affichés, sans liste à
 * maintenir à côté. Un objectif atteint le reste même si la série est ensuite interrompue
 * (il est lu dans les déblocages enregistrés).
 */
object StreakGoals {

    /** Badges de série, du plus proche au plus lointain. */
    val all: List<BadgeDef> by lazy {
        BadgeCatalog.all
            .filter { it.category == BadgeCategory.STREAK }
            .sortedBy { it.targetValue }
    }

    /**
     * État de chaque objectif. [unlockedAt] = déblocages enregistrés (id du badge -> date) ; à défaut,
     * un objectif est considéré atteint dès que la série en cours le couvre.
     */
    fun states(streak: Int, unlockedAt: Map<String, Long> = emptyMap()): List<StreakGoalState> =
        all.map { def ->
            val at = unlockedAt[def.id]
            StreakGoalState(
                def = def,
                reached = at != null || streak >= def.targetValue,
                reachedAt = at
            )
        }

    /** Prochain objectif à atteindre, ou null si tous le sont. */
    fun next(states: List<StreakGoalState>): BadgeDef? = states.firstOrNull { !it.reached }?.def

    /** Jours restants avant [goal] (0 si tous les objectifs sont atteints). */
    fun daysLeft(streak: Int, goal: BadgeDef?): Int =
        if (goal == null) 0 else (goal.targetValue - streak).coerceAtLeast(0)

    /** Avancement (0..1) depuis le dernier palier couvert par la série jusqu'à [goal]. */
    fun progress(streak: Int, goal: BadgeDef?): Float {
        if (goal == null) return 1f
        val from = all
            .lastOrNull { it.targetValue <= streak && it.targetValue < goal.targetValue }
            ?.targetValue ?: 0
        val span = (goal.targetValue - from).coerceAtLeast(1)
        return ((streak - from).toFloat() / span).coerceIn(0f, 1f)
    }
}
