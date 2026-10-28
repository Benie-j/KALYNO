package ci.kalyno.app.data.dashboard

data class ReviewItem(
    val cardId: String,
    val subjectName: String,
    val chapterTitle: String,
    val front: String,
    val choices: List<String>,
    val correctIndex: Int?,
    val answer: String,
    val explanation: String?,
    /** Indice optionnel affiché dans un bloc orange (null tant que le back-end n'en fournit pas). */
    val hint: String? = null,
    /** Type d'exercice publié par l'admin (SINGLE_CHOICE, MULTIPLE_CHOICE, FREE_TEXT) ; null = déduit des données. */
    val kind: String? = null,
    /** Indices des bonnes réponses (plusieurs possibles) ; vide = on utilise [correctIndex]. */
    val correctIndices: List<Int> = emptyList()
)

enum class MissionStatus {
    /** Des cartes à découvrir aujourd'hui. */
    READY,

    /** Quota du jour atteint. */
    DONE_TODAY,

    /** Trop de révisions en retard : on rattrape d'abord. */
    BACKLOG,

    /** Rien à proposer (aucun contenu publié ou tout est déjà découvert). */
    EMPTY
}

/** Mission du jour choisie par l'algorithme. [items] est vide quand le quota du jour est atteint. */
data class MissionUi(
    val subjectName: String,
    val subjectEmoji: String,
    val chapterTitle: String,
    val doneToday: Int,
    val targetToday: Int,
    val items: List<ReviewItem>
) {
    val progress: Float
        get() = if (targetToday <= 0) 0f else (doneToday.toFloat() / targetToday).coerceIn(0f, 1f)

    /** Nombre de cartes de CET exercice (jamais le nombre de blocs de la leçon). */
    val questionCount: Int get() = items.size

    /**
     * Durée estimée de RÉSOLUTION de l'exercice (≈35 s/carte), pas la durée de lecture
     * de la leçon dont ces cartes sont issues. Null si l'exercice est vide.
     */
    val estimatedMinutes: Int?
        get() = items.size.takeIf { it > 0 }
            ?.let { kotlin.math.ceil(it * SECONDS_PER_CARD / 60.0).toInt().coerceAtLeast(1) }

    private companion object {
        const val SECONDS_PER_CARD = 35.0
    }
}

data class UrgentTopic(val label: String, val count: Int)

/**
 * Palier de maîtrise d'une notion, calculé par le moteur SRS à partir du nombre de
 * répétitions réussies d'affilée. Purement dérivé des données : jamais saisi à la main.
 */
enum class MasteryTier {
    /** Notion tout juste validée par le moteur (seuil de maîtrise franchi). */
    SOLIDE,

    /** Notion revue avec succès à plusieurs reprises : la mémorisation se consolide. */
    AVANCEE,

    /** Notion ancrée durablement : haut niveau de répétitions réussies. */
    EXPERTE
}

/**
 * Une notion (regroupée par chapitre) que le moteur a jugée maîtrisée par l'élève.
 * [masteredCards] est le nombre de cartes de ce chapitre validées par le moteur ;
 * [score] est la probabilité de rétention moyenne (courbe de l'oubli FSRS), 0..1.
 */
data class MasteredNotion(
    val key: String,
    val label: String,
    val domain: String,
    val masteredCards: Int,
    val tier: MasteryTier,
    val score: Float?
)

data class ReviewSession(
    val id: String,
    val title: String?,
    val items: List<ReviewItem>
)

data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val nickname: String = "",
    val streak: Int = 0,
    val mission: MissionUi? = null,
    val missionStatus: MissionStatus = MissionStatus.EMPTY,
    val urgentTotal: Int = 0,
    val urgentTopics: List<UrgentTopic> = emptyList(),
    val urgentItems: List<ReviewItem> = emptyList(),
    /** Jours avant l'examen publié par l'admin ; null tant qu'aucune date n'est publiée. */
    val examDaysLeft: Int? = null,
    val session: ReviewSession? = null,
    /** Message de motivation publie par l'admin (banniere heros). Null = rien a afficher. */
    val heroMessage: String? = null,
    /** Nombre de notions (chapitres) maitrisees = masteredNotions.size. Null = pas encore calcule. */
    val masteredCount: Int? = null,
    /** Détail des notions maîtrisées, regroupées par chapitre, piloté par le moteur SRS. */
    val masteredNotions: List<MasteredNotion> = emptyList(),
    /** Score global de retention (0..1), derive de l'ease factor moyen des cartes. */
    val retentionScore: Float? = null,
    /** Derniere lecon consultee dans l'Explorer, pour "Reprendre l'aventure". */
    val lastLesson: LastLessonUi? = null,
    /** Intitulé de l'examen publié par l'admin (compte à rebours de l'Accueil). */
    val examLabel: String? = null,
    /** Citation du jour publiée par l'admin ; null tant qu'aucune n'est disponible. */
    val dailyQuote: DailyQuote? = null,
    /** Faux tant que la citation n'a pas fini de se charger (affiche un squelette). */
    val quoteReady: Boolean = false,
    /** Citation piochée par l'élève (shuffle) ; remplace celle du jour tant que l'écran reste ouvert. */
    val shuffledQuote: DailyQuote? = null,
    /** Les 7 derniers jours de citations (aujourd'hui en premier), pour la bottom sheet. */
    val quoteWeek: List<QuoteDay> = emptyList(),
    /** Citations épinglées par l'élève (Room), de la plus récente à la plus ancienne. */
    val favoriteQuotes: List<FavoriteQuote> = emptyList()
)

data class LastLessonUi(
    val subjectName: String,
    val chapterTitle: String,
    val progress: Float
)
