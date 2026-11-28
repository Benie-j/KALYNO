package ci.kalyno.app.data.badges

/**
 * Objectifs de série : une seule source de vérité pour l'Accueil et le Profil.
 *
 * Les paliers sont ceux des badges de série du [BadgeCatalog] (aujourd'hui 1, 7 et 30 jours) :
 * ajouter un badge de série au catalogue met à jour partout l'objectif affiché, sans liste à
 * maintenir à côté.
 */
object StreakGoals {

    /** Badges de série, du plus proche au plus lointain. */
    val all: List<BadgeDef> by lazy {
        BadgeCatalog.all
            .filter { it.category == BadgeCategory.STREAK }
            .sortedBy { it.targetValue }
    }

    /** Prochain badge à débloquer, ou null si tous le sont déjà. */
    fun next(streak: Int): BadgeDef? = all.firstOrNull { it.targetValue > streak }

    /** Palier déjà atteint le plus récent (0 si aucun). */
    fun previousTarget(streak: Int): Int =
        all.lastOrNull { it.targetValue <= streak }?.targetValue ?: 0

    /** Jours restants avant le prochain badge (0 si tous sont débloqués). */
    fun daysLeft(streak: Int): Int = (next(streak)?.targetValue ?: streak) - streak

    /** Avancement (0..1) entre le dernier palier atteint et le prochain badge. */
    fun progress(streak: Int): Float {
        val goal = next(streak) ?: return 1f
        val from = previousTarget(streak)
        val span = (goal.targetValue - from).coerceAtLeast(1)
        return ((streak - from).toFloat() / span).coerceIn(0f, 1f)
    }
}
