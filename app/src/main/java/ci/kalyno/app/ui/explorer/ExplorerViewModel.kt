package ci.kalyno.app.ui.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.ExplorerBanner
import ci.kalyno.app.data.LearningCapsule
import ci.kalyno.app.data.LearningCard
import ci.kalyno.app.data.LearningSubject
import ci.kalyno.app.data.SupabaseLearningRepository
import ci.kalyno.app.data.catalog.CatalogRepository
import ci.kalyno.app.data.catalog.CatalogTree
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.data.dashboard.StudentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val items: List<ReviewItem>,
    /** Smart Tag admin ("urgent", "populaire"…), null si absent. */
    val tag: String? = null,
    /** Icône vectorielle de la discipline (clé admin), null = icône par défaut. */
    val subjectIconKey: String? = null,
    /** Thème (chapitre global) de la leçon, null tant que l'admin ne l'a pas rangée. */
    val themeId: String? = null
) {
    val questionCount: Int get() = items.size
    val progress: Float
        get() = if (items.isEmpty()) 0f else (discovered.toFloat() / items.size).coerceIn(0f, 1f)
}

data class SubjectProgress(val name: String, val iconKey: String?, val progress: Float)

/** Une discipline (= matière publiée par l'admin) affichée en mini-carte dans Explorer. */
data class Discipline(
    val id: String,
    val name: String,
    val iconKey: String?,
    val lessonCount: Int
)

/** Un thème (chapitre global) d'une discipline : il regroupe des leçons. */
data class ExplorerTheme(
    val id: String,
    val disciplineId: String,
    val title: String,
    val lessonCount: Int
)

/**
 * Navigation interne à l'onglet Explorer (aucune dépendance à un NavHost).
 * [depth] sert à choisir le sens de l'animation de transition.
 */
sealed interface ExplorerRoute {
    val depth: Int

    data object Home : ExplorerRoute {
        override val depth = 0
    }

    data class DisciplineLessons(val disciplineId: String) : ExplorerRoute {
        override val depth = 1
    }

    data class LessonDetail(val lessonId: String) : ExplorerRoute {
        override val depth = 2
    }
}

data class ExplorerUiState(
    val loading: Boolean = true,
    /** Renseigné uniquement s'il n'y a rien à afficher (ni réseau, ni cache). */
    val error: String? = null,
    /** Vrai quand on affiche le cache faute de réseau. */
    val offline: Boolean = false,
    val chapters: List<ExplorerChapter> = emptyList(),
    val query: String = "",
    val filter: ExplorerFilter = ExplorerFilter.FOR_YOU,
    /** Disciplines publiées (ordre défini par l'admin), lues dans Room. */
    val disciplines: List<Discipline> = emptyList(),
    /** Thèmes publiés de toutes les disciplines (ordre défini par l'admin), lus dans Room. */
    val themes: List<ExplorerTheme> = emptyList(),
    /** Discipline touchée : se colore immédiatement, avant l'ouverture de sa page. */
    val pressedDisciplineId: String? = null,
    val openDisciplineId: String? = null,
    val openLessonId: String? = null,
    /** Bannière « à la une » publiée par l'admin (null = rien à afficher). */
    val banner: ExplorerBanner? = null
) {
    val route: ExplorerRoute
        get() = when {
            openLessonId != null -> ExplorerRoute.LessonDetail(openLessonId)
            openDisciplineId != null -> ExplorerRoute.DisciplineLessons(openDisciplineId)
            else -> ExplorerRoute.Home
        }

    fun themesOf(disciplineId: String): List<ExplorerTheme> =
        themes.filter { it.disciplineId == disciplineId }

    /** Leçons (= chapitres publiés) d'une discipline, dans l'ordre de l'admin. */
    fun lessonsOf(disciplineId: String): List<ExplorerChapter> =
        chapters.filter { it.subjectId == disciplineId }

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
                    iconKey = list.first().subjectIconKey,
                    progress = if (total == 0) 0f else done.toFloat() / total
                )
            }
}

// ---------------------------------------------------------------------------
// ViewModel (online-first : réseau d'abord, cache JSON en secours)
// ---------------------------------------------------------------------------

/** Durée du retour visuel (carte verte) avant d'ouvrir la page de la discipline. */
private const val PRESS_FEEDBACK_MS = 140L

class ExplorerViewModel(app: Application) : AndroidViewModel(app) {

    private val remote = SupabaseLearningRepository()
    private val cache = LearningContentCache(app)
    private val catalog = CatalogRepository.get(app)
    private val dao = KalynoDatabase.get(app).dashboardDao()
    private val prefs = AppPrefs(app)
    private val studentId = StudentId.get(app)

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    private var levelId: String? = null
    private var job: Job? = null
    private var treeJob: Job? = null

    /** Premier appel : charge tout. Appels suivants (retour sur l'onglet) : rafraîchit en douceur. */
    fun load(level: String) {
        if (levelId == level) {
            refresh(silent = true)
            return
        }
        levelId = level
        _state.value = ExplorerUiState()
        observeTree(level)
        refresh(silent = false)
    }

    fun retry() = refresh(silent = false)

    /** Disciplines et thèmes viennent de Room : affichage immédiat, même sans réseau. */
    private fun observeTree(level: String) {
        treeJob?.cancel()
        treeJob = viewModelScope.launch {
            catalog.observeTree(level).collect { tree ->
                _state.update { sanitized(it.copy(disciplines = tree.toDisciplines(), themes = tree.toThemes())) }
            }
        }
    }

    private fun CatalogTree.toDisciplines(): List<Discipline> =
        subjects.map { Discipline(it.id, it.name, it.iconKey, it.lessonCount) }

    private fun CatalogTree.toThemes(): List<ExplorerTheme> =
        themes.map { ExplorerTheme(it.id, it.subjectId, it.title, it.lessonCount) }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onFilterChange(value: ExplorerFilter) = _state.update { it.copy(filter = value) }

    // ----- Navigation Explorer -> Discipline -> Leçon -----

    /**
     * Micro-interaction : la carte devient verte instantanément, puis la page de la discipline
     * s'ouvre après un très court délai (le temps de percevoir le retour visuel).
     */
    fun onDisciplineClick(id: String) {
        val st = _state.value
        if (st.pressedDisciplineId != null || st.openDisciplineId != null) return
        _state.update { it.copy(pressedDisciplineId = id) }
        viewModelScope.launch {
            delay(PRESS_FEEDBACK_MS)
            _state.update { if (it.pressedDisciplineId == id) it.copy(openDisciplineId = id) else it }
        }
    }

    fun onLessonClick(chapter: ExplorerChapter) {
        prefs.saveLastLesson(
            subjectName = chapter.subjectName,
            subjectEmoji = chapter.subjectEmoji,
            chapterTitle = chapter.title,
            progress = chapter.progress
        )
        _state.update { it.copy(openLessonId = chapter.id) }
    }

    /** Gère le retour système / flèche. Renvoie false si on est déjà sur l'accueil Explorer. */
    fun onBack(): Boolean {
        val st = _state.value
        return when {
            st.openLessonId != null -> {
                _state.update { it.copy(openLessonId = null) }
                true
            }
            st.openDisciplineId != null || st.pressedDisciplineId != null -> {
                _state.update { it.copy(openDisciplineId = null, pressedDisciplineId = null) }
                true
            }
            else -> false
        }
    }

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
                    // Première ouverture après la mise à jour : on amorce Room avec l'ancien cache
                    withContext(Dispatchers.IO) { runCatching { catalog.storeTree(level, cached) } }
                    _state.update { sanitized(it.copy(chapters = chapters, loading = false)) }
                }
            }

            // 2) Réseau : source de vérité (synchronisée avec l'admin)
            try {
                val subjects = remote.loadPublishedContent(level)
                val banner = remote.loadBanner(level)
                withContext(Dispatchers.IO) {
                    cache.write(level, subjects)
                    runCatching { catalog.storeTree(level, subjects) }
                }
                val chapters = withContext(Dispatchers.IO) { toChapters(subjects) }
                _state.update {
                    sanitized(
                        it.copy(
                            loading = false,
                            error = null,
                            offline = false,
                            chapters = chapters,
                            banner = banner
                        )
                    )
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

    /** Si l'admin dépublie la leçon / discipline ouverte, on revient proprement en arrière. */
    private fun sanitized(st: ExplorerUiState): ExplorerUiState {
        var next = st
        if (next.openLessonId != null && next.chapters.none { it.id == next.openLessonId }) {
            next = next.copy(openLessonId = null)
        }
        if (next.openDisciplineId != null && next.disciplines.none { it.id == next.openDisciplineId }) {
            next = next.copy(openDisciplineId = null, pressedDisciplineId = null)
        }
        return next
    }

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
                    items = items,
                    tag = chapter.tag,
                    subjectIconKey = subject.iconKey,
                    themeId = chapter.themeId
                )
            }
        }
    }

    private fun toItem(card: LearningCard, subject: String, chapter: String): ReviewItem? {
        val indices = ci.kalyno.app.data.dojo.mergeCorrectIndices(card.correctIndices, card.correctIndex)
        val answer = ci.kalyno.app.data.dojo.answerText(card.choices, indices, card.back)
        if (card.front.isBlank() || answer.isNullOrBlank()) return null
        return ReviewItem(
            cardId = card.id,
            subjectName = subject,
            chapterTitle = chapter,
            front = card.front,
            choices = card.choices,
            correctIndex = card.correctIndex ?: indices.firstOrNull(),
            answer = answer,
            explanation = card.explanation,
            kind = card.kind,
            correctIndices = indices
        )
    }
}
