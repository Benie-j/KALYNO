package ci.kalyno.app.data.dashboard

/** Réglages pédagogiques de la Mission du Jour (un seul endroit pour les ajuster). */
object SrsConfig {
    /** Nouvelles cartes maximum par jour. */
    const val DAILY_NEW_CARDS = 10

    /** Cartes par session de révision urgente. */
    const val SESSION_SIZE = 10

    /** Au-delà : les nouvelles cartes sont réduites de moitié. */
    const val BACKLOG_SOFT_LIMIT = 10

    /** Au-delà : plus aucune nouvelle carte tant que le retard n'est pas rattrapé. */
    const val BACKLOG_HARD_LIMIT = 20

    const val MAX_TOPICS = 3

    /** Matière jamais étudiée : traitée comme si elle l'était depuis ce nombre de jours. */
    const val NEVER_STUDIED_DAYS = 14

    fun newCardQuota(dueCount: Int): Int = when {
        dueCount > BACKLOG_HARD_LIMIT -> 0
        dueCount > BACKLOG_SOFT_LIMIT -> DAILY_NEW_CARDS / 2
        else -> DAILY_NEW_CARDS
    }
}
