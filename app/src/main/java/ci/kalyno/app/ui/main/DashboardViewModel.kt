package ci.kalyno.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.dashboard.DashboardRepository
import ci.kalyno.app.data.dashboard.DashboardUiState
import ci.kalyno.app.data.dashboard.ReviewItem
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
import ci.kalyno.app.data.dashboard.DailyQuote
import ci.kalyno.app.data.dashboard.DailyQuotes
import ci.kalyno.app.data.dashboard.FavoriteQuote
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.dashboard.QuoteFavoriteEntity
import ci.kalyno.app.data.dashboard.favoriteKey
import ci.kalyno.app.data.dashboard.ExamSchedule
import ci.kalyno.app.data.srs.SrsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DashboardRepository(app)
    private val streakStore = StreakStore(app)
    private val studentId = StudentId.get(app)
    private val exams = ExamSchedule(app)
    private val quotes = DailyQuotes(app)
    private val favoritesDao = KalynoDatabase.get(app).quoteFavoriteDao()

    private val _state = MutableStateFlow(DashboardUiState(streak = streakStore.current()))
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        // Les favoris viennent de Room : la liste se met à jour toute seule (cœur, bottom sheet).
        viewModelScope.launch {
            favoritesDao.observeAll().collect { rows ->
                _state.update { s ->
                    s.copy(favoriteQuotes = rows.map { FavoriteQuote(it.quoteKey, DailyQuote(it.content, it.author)) })
                }
            }
        }
    }

    private var loadedLevel: String? = null
    private var loadJob: Job? = null

    // Les évaluations sont écrites dans l'ordre, avant tout rafraîchissement
    private val writeLock = Mutex()

    fun load(levelId: String, nickname: String) {
        _state.update { it.copy(nickname = nickname) }
        if (loadedLevel == levelId) return
        loadedLevel = levelId
        // Affichage immédiat avec ce que le téléphone connaît déjà (marche hors connexion)
        val cachedQuote = quotes.cached(levelId)
        _state.update {
            it.copy(
                examDaysLeft = cachedExamDays(levelId),
                examLabel = exams.label(levelId),
                dailyQuote = cachedQuote,
                shuffledQuote = null,
                quoteWeek = quotes.week(levelId)
            )
        }
        refresh(levelId)
        refreshQuote(levelId)
    }

    fun retry() {
        loadedLevel?.let {
            refresh(it)
            refreshQuote(it)
        }
    }

    private fun cachedExamDays(levelId: String): Int? =
        exams.cached(levelId)
            ?.let { SrsEngine.daysUntil(System.currentTimeMillis(), it) }
            ?.takeIf { it >= 0 }

    private fun refreshQuote(levelId: String) {
        viewModelScope.launch {
            val (quote, week) = withContext(Dispatchers.IO) { quotes.refresh(levelId) to quotes.week(levelId) }
            _state.update { it.copy(dailyQuote = quote, quoteWeek = week, quoteReady = true) }
        }
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
                        urgentItems = snapshot.urgentItems,
                        examDaysLeft = snapshot.examDaysLeft,
                        examLabel = exams.label(levelId),
                        masteredCount = snapshot.masteredCount,
                        retentionScore = snapshot.retentionScore,
                        lastLesson = snapshot.lastLesson
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

    /** Pioche une autre citation que celle affichée, parmi celles gardées sur le téléphone. */
    fun shuffleQuote() {
        val level = loadedLevel ?: return
        val current = _state.value.shuffledQuote ?: _state.value.dailyQuote
        val pool = quotes.all(level).filter { it != current }
        if (pool.isEmpty()) return
        _state.update { it.copy(shuffledQuote = pool.random()) }
    }

    /** Épingle ou retire une citation des favoris (écriture Room hors du thread principal). */
    fun toggleFavorite(quote: DailyQuote) {
        val key = quote.favoriteKey()
        val alreadyFavorite = _state.value.favoriteQuotes.any { it.key == key }
        viewModelScope.launch(Dispatchers.IO) {
            if (alreadyFavorite) {
                favoritesDao.delete(key)
            } else {
                favoritesDao.upsert(QuoteFavoriteEntity(key, quote.text, quote.author, System.currentTimeMillis()))
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
                    title = "${mission.subjectEmoji} ${mission.chapterTitle}".trim(),
                    items = mission.items
                )
            )
        }
    }

    /** Lance une session sur une sélection de cartes (ex. un chapitre choisi dans Explorer). */
    fun startCustomSession(title: String?, items: List<ReviewItem>) {
        if (items.isEmpty()) return
        _state.update {
            it.copy(session = ReviewSession(id = UUID.randomUUID().toString(), title = title, items = items))
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
                    repository.rate(studentId, cardId, rating, loadedLevel)
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
