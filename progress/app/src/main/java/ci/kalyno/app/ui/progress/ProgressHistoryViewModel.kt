package ci.kalyno.app.ui.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.dashboard.StudentId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.ZoneId

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

data class ProgressHistoryUiState(
    val loading: Boolean = true,
    val summary: ProgressSummaryUi? = null,
    val subjects: List<String> = emptyList(),
    val filter: HistoryFilter = HistoryFilter(),
    val rows: List<HistoryRow> = emptyList(),
    /** Il reste des tentatives plus anciennes à charger. */
    val canLoadMore: Boolean = false
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

    private val filter = MutableStateFlow(HistoryFilter())
    private val limit = MutableStateFlow(PAGE_SIZE)

    private val timeline = combine(filter, limit) { f, l -> f to l }
        .flatMapLatest { (f, l) -> dao.observeHistory(studentId, f.status.code, f.subject, l) }

    val state: StateFlow<ProgressHistoryUiState> = combine(
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
    }
}
