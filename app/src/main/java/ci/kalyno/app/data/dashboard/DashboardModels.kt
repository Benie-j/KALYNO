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

data class MissionUi(
    val subjectName: String,
    val subjectEmoji: String,
    val chapterTitle: String,
    val minutes: Int,
    val toDiscover: Int,
    val done: Int,
    val total: Int,
    val items: List<ReviewItem>
) {
    val progress: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
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
    val urgentTotal: Int = 0,
    val urgentTopics: List<UrgentTopic> = emptyList(),
    val urgentItems: List<ReviewItem> = emptyList(),
    val session: ReviewSession? = null
)
