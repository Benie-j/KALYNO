package ci.kalyno.app.ui.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.LearningCapsule
import ci.kalyno.app.data.LearningCard
import ci.kalyno.app.data.LearningSubject
import ci.kalyno.app.data.SupabaseLearningRepository
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.data.dashboard.StudentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Modèle d'UI
// ---------------------------------------------------------------------------

enum class ExplorerFilter(val label: String) {
    FOR_YOU("Pour Toi"),
    CHALLENGES("Défis"),
    COMMUNITY("Communauté"),
    PATHS("Parcours")
}

/** Un chapitre publié par l'admin, avec la progression de l'élève. */
data class ExplorerChapter(
    val id: String,
    val subjectId: String,
    val subjectName: String,
    val subjectEmoji: String,
    val title: String,
    val minutes: Int,
    val discovered: Int,
    val capsules: List<LearningCapsule>,
    val items: List<ReviewItem>
) {
    val questionCount: Int get() = items.size
    val progress: Float
        get() = if (items.isEmpty()) 0f else (discovered.toFloat() / items.size).coerceIn(0f, 1f)
}

data class SubjectProgress(val name: String, val emoji: String, val progress: Float)

data class ExplorerUiState(
    val loading: Boolean = true,
    /** Renseigné uniquement s'il n'y a rien à afficher (ni réseau, ni cache). */
    val error: String? = null,
    /** Vrai quand on affiche le cache faute de réseau. */
    val offline: Boolean = false,
    val chapters: List<ExplorerChapter> = emptyList(),
    val query: String = "",
    val filter: ExplorerFilter = ExplorerFilter.FOR_YOU
) {
    val visibleChapters: List<ExplorerChapter>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return chapters
            return chapters.filter {
                it.title.contains(q, ignoreCase = true) || it.subjectName.contains(q, ignoreCase = true)
            }
        }

    val subjectProgress: List<SubjectProgress>
        get() = chapters
            .groupBy { it.subjectId }
            .values
            .map { list ->
                val total = list.sumOf { it.questionCount }
                val done = list.sumOf { it.discovered }
                SubjectProgress(
                    name = list.first().subjectName,
                    emoji = list.first().subjectEmoji,
                    progress = if (total == 0) 0f else done.toFloat() / total
                )
            }
}

// ---------------------------------------------------------------------------
// ViewModel (online-first : réseau d'abord, cache JSON en secours)
// ---------------------------------------------------------------------------

class ExplorerViewModel(app: Application) : AndroidViewModel(app) {

    private val remote = SupabaseLearningRepository()
    private val cache = LearningContentCache(app)
    private val dao = KalynoDatabase.get(app).dashboardDao()
    private val studentId = StudentId.get(app)

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    private var levelId: String? = null
    private var job: Job? = null

    /** Premier appel : charge tout. Appels suivants (retour sur l'onglet) : rafraîchit en douceur. */
    fun load(level: String) {
        if (levelId == level) {
            refresh(silent = true)
            return
        }
        levelId = level
        _state.value = ExplorerUiState()
        refresh(silent = false)
    }

    fun retry() = refresh(silent = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onFilterChange(value: ExplorerFilter) = _state.update { it.copy(filter = value) }

    fun refresh(silent: Boolean) {
        val level = levelId ?: return
        job?.cancel()
        job = viewModelScope.launch {
            if (!silent && _state.value.chapters.isEmpty()) {
                _state.update { it.copy(loading = true, error = null) }
            }

            // 1) Affichage immédiat depuis le cache (si on n'a encore rien)
            if (_state.value.chapters.isEmpty()) {
                val cached = withContext(Dispatchers.IO) { cache.read(level) }
                if (cached != null && cached.isNotEmpty()) {
                    val chapters = withContext(Dispatchers.IO) { toChapters(cached) }
                    _state.update { it.copy(chapters = chapters, loading = false) }
                }
            }

            // 2) Réseau : source de vérité (synchronisée avec l'admin)
            try {
                val subjects = remote.loadPublishedContent(level)
                withContext(Dispatchers.IO) { cache.write(level, subjects) }
                val chapters = withContext(Dispatchers.IO) { toChapters(subjects) }
                _state.update {
                    it.copy(loading = false, error = null, offline = false, chapters = chapters)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { st ->
                    if (st.chapters.isNotEmpty()) {
                        st.copy(loading = false, offline = true)
                    } else {
                        st.copy(
                            loading = false,
                            error = e.message?.takeIf { m -> m.isNotBlank() }
                                ?: "Impossible de charger les contenus."
                        )
                    }
                }
            }
        }
    }

    // ----- Mapping contenu -> UI -----

    private suspend fun toChapters(subjects: List<LearningSubject>): List<ExplorerChapter> {
        val introduced = runCatching { dao.progressedCardIds(studentId).toHashSet() }
            .getOrDefault(hashSetOf())

        return subjects.flatMap { subject ->
            subject.chapters.map { chapter ->
                val items = chapter.cards.mapNotNull { toItem(it, subject.name, chapter.title) }
                val capsuleMinutes = chapter.capsules.sumOf { it.minutes }
                ExplorerChapter(
                    id = chapter.id,
                    subjectId = subject.id,
                    subjectName = subject.name,
                    subjectEmoji = subject.emoji,
                    title = chapter.title,
                    minutes = if (capsuleMinutes > 0) capsuleMinutes
                    else (items.size * 1.5).roundToInt().coerceAtLeast(if (items.isEmpty()) 0 else 1),
                    discovered = items.count { it.cardId in introduced },
                    capsules = chapter.capsules,
                    items = items
                )
            }
        }
    }

    private fun toItem(card: LearningCard, subject: String, chapter: String): ReviewItem? {
        val answer = card.correctIndex?.let { card.choices.getOrNull(it) } ?: card.back
        if (card.front.isBlank() || answer.isNullOrBlank()) return null
        return ReviewItem(
            cardId = card.id,
            subjectName = subject,
            chapterTitle = chapter,
            front = card.front,
            choices = card.choices,
            correctIndex = card.correctIndex,
            answer = answer,
            explanation = card.explanation
        )
    }
}
