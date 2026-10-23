package ci.kalyno.app.data.dashboard

data class ReviewItem(
    val cardId: String,
    val subjectName: String,
    val chapterTitle: String,
    val front: String,
    val choices: List<String>,
    val correctIndex: Int?,
    val answer: String,
    val explanation: String?
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
    val minutes: Int,
    val doneToday: Int,
    val targetToday: Int,
    val items: List<ReviewItem>
) {
    val progress: Float
        get() = if (targetToday <= 0) 0f else (doneToday.toFloat() / targetToday).coerceIn(0f, 1f)
}

data class UrgentTopic(val label: String, val count: Int)

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
    val session: ReviewSession? = null
)
