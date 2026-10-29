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

    // ----- Évolution des cartes du Dojo (QCM -> saisie libre) -----

    /** Niveau de mémorisation à partir duquel une carte QCM bascule en saisie libre. */
    const val DOJO_FREE_INPUT_STAGE = 3

    /** Niveau maximal d'une carte (au-delà, le niveau ne monte plus). */
    const val DOJO_MAX_STAGE = 6

    /** Une référence d'au moins ce nombre de mots est corrigée par le moteur sémantique ; en dessous, mots-clés. */
    const val DOJO_SEMANTIC_MIN_WORDS = 3

    /** Matière jamais étudiée : traitée comme si elle l'était depuis ce nombre de jours. */
    const val NEVER_STUDIED_DAYS = 14

    fun newCardQuota(dueCount: Int): Int = when {
        dueCount > BACKLOG_HARD_LIMIT -> 0
        dueCount > BACKLOG_SOFT_LIMIT -> DAILY_NEW_CARDS / 2
        else -> DAILY_NEW_CARDS
    }
}
