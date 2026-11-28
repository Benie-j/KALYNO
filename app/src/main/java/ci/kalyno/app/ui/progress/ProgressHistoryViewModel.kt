package ci.kalyno.app.ui.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.dashboard.StudentId
import ci.kalyno.app.data.progress.DailyCountRow
import ci.kalyno.app.data.progress.DojoAttemptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

enum class HistoryStatus(val code: Int) { ALL(0), SUCCESS(1), FAILURE(2) }

data class HistoryFilter(val status: HistoryStatus = HistoryStatus.ALL, val subject: String? = null)

data class ProgressSummaryUi(
    val total: Int,
    val successes: Int,
    val failures: Int,
    val learnedCards: Int,
    /** Part de succès parmi toutes les tentatives (0..1) ; null tant qu'il n'y a aucune tentative. */
    val successRate: Float?
)

/** Une fiche apprise (réussie au moins une fois) : recto = question, verso = réponse officielle. */
data class LearnedCardUi(
    val id: Long,
    val question: String,
    val answer: String,
    val lessonTitle: String,
    val subjectName: String,
    /** « Aujourd'hui », « Hier » ou la date : jour de la dernière réussite. */
    val learnedOn: String,
    /** « Du premier coup » ou « Après 2 échecs » ; null si le rang est inconnu (historique ancien). */
    val effort: String?
)

/** Une journée de la semaine glissante (de J-6 à aujourd'hui). */
data class DayBarUi(
    val label: String,
    val total: Int,
    val successes: Int,
    val isToday: Boolean
) {
    val failures: Int get() = total - successes
}

data class ProgressHistoryUiState(
    val loading: Boolean = true,
    val summary: ProgressSummaryUi? = null,
    val subjects: List<String> = emptyList(),
    val filter: HistoryFilter = HistoryFilter(),
    val rows: List<HistoryRow> = emptyList(),
    /** Il reste des tentatives plus anciennes à charger. */
    val canLoadMore: Boolean = false,
    /** Fiches apprises, de la plus récemment réussie à la plus ancienne (bornées). */
    val learned: List<LearnedCardUi> = emptyList(),
    /** Activité des 7 derniers jours, du plus ancien à aujourd'hui. */
    val week: List<DayBarUi> = emptyList()
) {
    /** Aucune tentative du tout (hors filtre) : on affiche l'état vide plutôt qu'une liste vide. */
    val neverPlayed: Boolean get() = !loading && (summary?.total ?: 0) == 0
}

/**
 * Historique d'apprentissage de l'élève : lit uniquement Room (hors-ligne), en flux. La page se met à jour
 * toute seule quand une tentative est enregistrée par le Dojo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = KalynoDatabase.get(app).attemptDao()
    private val studentId = StudentId.get(app)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val filter = MutableStateFlow(HistoryFilter())
    private val limit = MutableStateFlow(PAGE_SIZE)

    private val timeline = combine(filter, limit) { f, l -> f to l }
        .flatMapLatest { (f, l) -> dao.observeHistory(studentId, f.status.code, f.subject, l) }

    private val base: Flow<ProgressHistoryUiState> = combine(
        dao.observeSummary(studentId),
        dao.observeSubjects(studentId),
        filter,
        limit,
        timeline
    ) { summary, subjects, f, l, attempts ->
        ProgressHistoryUiState(
            loading = false,
            summary = ProgressSummaryUi(
                total = summary.total,
                successes = summary.successes,
                failures = summary.total - summary.successes,
                learnedCards = summary.learnedCards,
                successRate = if (summary.total > 0) summary.successes.toFloat() / summary.total else null
            ),
            subjects = subjects,
            filter = f,
            rows = HistoryFormat.group(attempts, LocalDate.now(), ZoneId.systemDefault()),
            canLoadMore = attempts.size >= l
        )
    }

    private val learnedFlow: Flow<List<LearnedCardUi>> =
        dao.observeLearned(studentId, LEARNED_LIMIT).map { list ->
            val today = LocalDate.now(zone)
            list.map { toLearnedUi(it, today, zone) }
        }

    private val weekFlow: Flow<List<DayBarUi>> = run {
        val since = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val offsetMs = zone.rules.getOffset(Instant.now()).totalSeconds * 1000L
        dao.observeDailyCounts(studentId, since, offsetMs).map { rows -> buildWeek(rows, LocalDate.now(zone)) }
    }

    val state: StateFlow<ProgressHistoryUiState> = combine(base, learnedFlow, weekFlow) { b, learned, week ->
        b.copy(learned = learned, week = week)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressHistoryUiState())

    fun selectStatus(status: HistoryStatus) = changeFilter { it.copy(status = status) }

    /** Toucher la discipline déjà choisie la désélectionne. */
    fun selectSubject(subject: String?) = changeFilter { it.copy(subject = if (it.subject == subject) null else subject) }

    fun loadMore() {
        if (state.value.canLoadMore) limit.value = limit.value + PAGE_SIZE
    }

    private fun changeFilter(change: (HistoryFilter) -> HistoryFilter) {
        filter.value = change(filter.value)
        limit.value = PAGE_SIZE
    }

    private companion object {
        const val PAGE_SIZE = 40
        const val LEARNED_LIMIT = 30
    }
}

private fun toLearnedUi(a: DojoAttemptEntity, today: LocalDate, zone: ZoneId): LearnedCardUi {
    val day = Instant.ofEpochMilli(a.attemptedAt).atZone(zone).toLocalDate()
    val effort = when {
        a.attemptNumber <= 0 -> null
        a.attemptNumber == 1 -> "Du premier coup"
        else -> {
            val failures = a.attemptNumber - 1
            "Après $failures ${if (failures == 1) "échec" else "échecs"}"
        }
    }
    return LearnedCardUi(
        id = a.id,
        question = a.question,
        answer = a.referenceAnswer,
        lessonTitle = a.lessonTitle,
        subjectName = a.subjectName,
        learnedOn = HistoryFormat.dayLabel(day, today),
        effort = effort
    )
}

/** Sept jours de J-6 à aujourd'hui ; un jour sans tentative vaut zéro (jamais « inconnu »). */
private fun buildWeek(rows: List<DailyCountRow>, today: LocalDate): List<DayBarUi> {
    val byDay = rows.associateBy { it.day }
    return (6 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val row = byDay[date.toEpochDay()]
        DayBarUi(
            label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH)
                .take(1)
                .uppercase(Locale.FRENCH),
            total = row?.total ?: 0,
            successes = row?.successes ?: 0,
            isToday = back == 0
        )
    }
}
