package ci.kalyno.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.dashboard.DashboardRepository
import ci.kalyno.app.data.dashboard.DashboardUiState
import ci.kalyno.app.data.dashboard.ReviewSession
import ci.kalyno.app.data.dashboard.StudentId
import ci.kalyno.app.data.srs.ReviewRating
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DashboardRepository(app)
    private val streakStore = StreakStore(app)
    private val studentId = StudentId.get(app)

    private val _state = MutableStateFlow(DashboardUiState(streak = streakStore.current()))
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var loadedLevel: String? = null
    private var loadJob: Job? = null

    // Les évaluations sont écrites dans l'ordre, avant tout rafraîchissement
    private val writeLock = Mutex()

    fun load(levelId: String, nickname: String) {
        _state.update { it.copy(nickname = nickname) }
        if (loadedLevel == levelId) return
        loadedLevel = levelId
        refresh(levelId)
    }

    fun retry() {
        loadedLevel?.let { refresh(it) }
    }

    private fun refresh(levelId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, streak = streakStore.current()) }
            try {
                val snapshot = repository.load(levelId, studentId)
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        streak = streakStore.current(),
                        mission = snapshot.mission,
                        missionStatus = snapshot.missionStatus,
                        urgentTotal = snapshot.urgentTotal,
                        urgentTopics = snapshot.urgentTopics,
                        urgentItems = snapshot.urgentItems
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message?.takeIf { m -> m.isNotBlank() }
                            ?: "Impossible de charger ton tableau de bord."
                    )
                }
            }
        }
    }

    fun startMission() {
        val mission = _state.value.mission ?: return
        if (mission.items.isEmpty()) return
        _state.update {
            it.copy(
                session = ReviewSession(
                    id = UUID.randomUUID().toString(),
                    title = "${mission.subjectEmoji} ${mission.chapterTitle}",
                    items = mission.items
                )
            )
        }
    }

    fun startUrgentReview() {
        val items = _state.value.urgentItems
        if (items.isEmpty()) return
        _state.update {
            it.copy(session = ReviewSession(id = UUID.randomUUID().toString(), title = null, items = items))
        }
    }

    fun rate(cardId: String, rating: ReviewRating) {
        viewModelScope.launch {
            writeLock.withLock {
                try {
                    repository.rate(studentId, cardId, rating)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // une évaluation perdue ne doit pas bloquer la session
                }
            }
        }
    }

    /** Ferme la session ; [completed] = vrai si l'élève est allé jusqu'au bout (met à jour la série). */
    fun closeSession(completed: Boolean) {
        viewModelScope.launch {
            writeLock.withLock { }
            if (completed) streakStore.recordCompletion()
            _state.update { it.copy(session = null) }
            loadedLevel?.let { refresh(it) }
        }
    }
}
