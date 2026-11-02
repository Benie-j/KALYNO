package ci.kalyno.app.ui.dojo

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.dashboard.DashboardRepository
import ci.kalyno.app.data.dashboard.DojoCardMeta
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.data.dashboard.ReviewSession
import ci.kalyno.app.data.dashboard.StudentId
import ci.kalyno.app.data.dojo.AnswerContext
import ci.kalyno.app.data.dojo.AnswerGrader
import ci.kalyno.app.data.dojo.CalculatorPolicy
import ci.kalyno.app.data.dojo.DojoStagePolicy
import ci.kalyno.app.data.dojo.ExerciseKind
import ci.kalyno.app.data.dojo.Interleaver
import ci.kalyno.app.data.dojo.mergeCorrectIndices
import ci.kalyno.app.data.lessons.LessonProgressRepository
import ci.kalyno.app.data.dojo.Verdict
import ci.kalyno.app.data.srs.ReviewRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.progress.DojoAttemptEntity

/** Un exercice prêt à jouer : tout vient de l'admin (Room / Supabase), rien n'est écrit en dur. */
data class DojoExercise(
    val item: ReviewItem,
    /** Interface réellement affichée : QCM tant que la carte est jeune, FREE_TEXT une fois le seuil atteint. */
    val kind: ExerciseKind,
    val correctIndices: Set<Int>,
    val acceptedAnswers: List<String>,
    val calculatorSuggested: Boolean,
    /** Niveau de répétition espacée de l'élève sur cette carte. */
    val stage: Int = 0,
    /** Réponse de référence de l'admin : affichée comme bonne réponse et conservée dans l'historique. */
    val reference: String = item.answer,
    /** Vrai si la carte était un QCM et vient de basculer en saisie libre grâce à son niveau. */
    val promotedToFreeInput: Boolean = false,
    /** Leçon parente de la question (relation Room flashcards.lessonId -> catalog_lessons.id), pour l'historique. */
    val lessonTitle: String? = null
) {
    /** Règle d'affichage : saisie libre (champ texte) ou QCM (cartes de choix). */
    val requiresFreeInput: Boolean get() = kind == ExerciseKind.FREE_TEXT

    val promptLength: Int
        get() = item.front.length + if (requiresFreeInput) 0 else item.choices.sumOf { it.length }
}

data class DojoResult(
    val correct: Boolean,
    val partial: Boolean,
    val rating: ReviewRating,
    val message: String,
    /** Renseigné quand le moteur FSRS a planifié la prochaine révision. */
    val nextReviewDays: Int? = null
)

data class DojoUiState(
    val title: String? = null,
    val total: Int = 0,
    val index: Int = 0,
    val exercise: DojoExercise? = null,
    val selected: Set<Int> = emptySet(),
    val typed: String = "",
    val hintShown: Boolean = false,
    val calculatorUsed: Boolean = false,
    /** null tant que l'élève n'a pas validé : la correction reste cachée. */
    val result: DojoResult? = null,
    val correctCount: Int = 0,
    val finished: Boolean = false,
    /** Vrai si la session s'est vidée faute de leçon terminée : aucun bravo, aucune série créditée. */
    val lockedOut: Boolean = false
) {
    val canSubmit: Boolean
        get() = when (exercise?.kind) {
            null -> false
            ExerciseKind.FREE_TEXT -> typed.isNotBlank()
            else -> selected.isNotEmpty()
        }
}

class DojoViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DashboardRepository(app)
    private val studentId = StudentId.get(app)
    private val lessonProgress = LessonProgressRepository.get(app)
    private val attempts = KalynoDatabase.get(app).attemptDao()

    /** Calculatrice conservée pendant toute la session (survit aux rotations d'écran). */
    val calculator = CalculatorModel()

    private val writeLock = Mutex()
    private var lastWrite: Job? = null
    private var sessionId: String? = null
    private var levelId: String? = null
    private var exercises: List<DojoExercise> = emptyList()
    private var shownAt = 0L

    private var lastLatencyMs = 0L

    private val _state = MutableStateFlow(DojoUiState())
    val state: StateFlow<DojoUiState> = _state.asStateFlow()

    /**
     * Prépare la session. Deux règles s'appliquent ici, quelle que soit la provenance des cartes
     * (mission, révision urgente, chapitre choisi dans Explorer) :
     *  1. seules les cartes dont la leçon est terminée ([LessonProgressRepository.unlockedCardIds]) sont jouées ;
     *  2. elles sont entrelacées globalement, toutes matières confondues ([Interleaver]), avec une graine
     *     propre à la session : l'ordre change à chaque nouvelle session.
     */
    fun start(session: ReviewSession, levelId: String?) {
        if (sessionId == session.id) return
        sessionId = session.id
        this.levelId = levelId
        _state.value = DojoUiState(title = session.title, total = session.items.size)
        if (session.items.isEmpty()) {
            _state.update { it.copy(finished = true) }
            return
        }
        viewModelScope.launch {
            // Règle stricte : en cas d'échec de lecture, on ne débloque rien plutôt que de contourner la règle
            val unlocked = try {
                lessonProgress.unlockedCardIds(studentId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptySet()
            }
            val allowed = session.items.filter { it.cardId in unlocked }
            val mixed = Interleaver.mix(allowed, seed = session.id.hashCode().toLong())
            if (mixed.isEmpty()) {
                _state.update { it.copy(total = 0, finished = true, lockedOut = true) }
                return@launch
            }
            _state.update { it.copy(total = mixed.size) }

            // Niveau de chaque carte pour CET élève : une seule lecture Room pour toute la session
            val meta = try {
                repository.dojoMeta(studentId, mixed.map { it.cardId })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap() // sans niveau, tout reste en QCM : la session n'est jamais bloquée
            }
            exercises = mixed.map { buildExercise(it, meta[it.cardId]) }
            show(0)
        }
    }

    /**
     * Choisit l'interface de la carte. Un QCM bascule en saisie libre quand son niveau atteint le seuil
     * ([DojoStagePolicy.requiresFreeInput]) ; une carte « saisie libre » de l'admin l'est dès le départ.
     * Seule une carte à réponse courte (mots-clés) bascule : une phrase ne se corrige pas par mots-clés, et un QCM à
     * réponses multiples reste un QCM.
     */
    private fun buildExercise(item: ReviewItem, meta: DojoCardMeta?): DojoExercise {
        val indices = mergeCorrectIndices(item.correctIndices, item.correctIndex)
            .filter { it in item.choices.indices }
        val declared = ExerciseKind.resolve(item.kind, item.choices, indices)
        val stage = meta?.stage ?: 0
        val adminReference = meta?.adminReference?.takeIf { it.isNotBlank() }

        val canPromote = when (declared) {
            ExerciseKind.SINGLE_CHOICE -> isKeywordAnswer(item.answer)
            ExerciseKind.MULTIPLE_CHOICE -> false
            ExerciseKind.FREE_TEXT -> false
        }
        val promoted = canPromote && DojoStagePolicy.requiresFreeInput(stage)
        val kind = if (promoted) ExerciseKind.FREE_TEXT else declared

        val reference = adminReference ?: item.answer
        return DojoExercise(
            item = item,
            kind = kind,
            correctIndices = indices.toSet(),
            acceptedAnswers = AnswerGrader.acceptedAnswers(item.answer),
            calculatorSuggested = CalculatorPolicy.suggested(listOf(item.front) + item.choices),
            stage = stage,
            reference = reference,
            promotedToFreeInput = promoted,
            lessonTitle = meta?.lessonTitle
        )
    }

    /** Réponse « mots-clés » : chaque variante acceptée est courte (un mot, un nombre, une expression). */
    private fun isKeywordAnswer(raw: String): Boolean {
        val variants = AnswerGrader.acceptedAnswers(raw)
        return variants.isNotEmpty() && variants.all { it.split(Regex("\\s+")).size <= MAX_KEYWORD_WORDS }
    }

    private fun show(index: Int) {
        shownAt = SystemClock.elapsedRealtime()
        _state.update {
            it.copy(
                index = index,
                exercise = exercises[index],
                selected = emptySet(),
                typed = "",
                hintShown = false,
                calculatorUsed = false,
                result = null
            )
        }
    }

    fun toggleChoice(index: Int) {
        val s = _state.value
        val exercise = s.exercise ?: return
        if (s.result != null) return
        val next = when (exercise.kind) {
            ExerciseKind.MULTIPLE_CHOICE -> if (index in s.selected) s.selected - index else s.selected + index
            else -> setOf(index)
        }
        _state.update { it.copy(selected = next) }
    }

    fun onTyped(value: String) {
        if (_state.value.result != null) return
        _state.update { it.copy(typed = value.take(MAX_INPUT)) }
    }

    fun showHint() {
        if (_state.value.result != null) return
        _state.update { it.copy(hintShown = true) }
    }

    fun markCalculatorUsed() {
        val s = _state.value
        if (s.result != null || s.calculatorUsed) return
        _state.update { it.copy(calculatorUsed = true) }
    }

    /**
     * Valide la réponse : QCM -> comparaison des indices ; saisie libre -> comparaison aux mots-clés de l'admin.
     * Note FSRS automatique, puis écriture dans Room.
     */
    fun submit() {
        val s = _state.value
        val exercise = s.exercise ?: return
        if (s.result != null || !s.canSubmit) return

        val context = AnswerContext(
            elapsedMs = SystemClock.elapsedRealtime() - shownAt,
            questionLength = exercise.promptLength,
            hintUsed = s.hintShown,
            calculatorUsed = s.calculatorUsed
        )

        lastLatencyMs = context.elapsedMs

        val verdict = if (exercise.requiresFreeInput) {
            AnswerGrader.gradeText(s.typed, exercise.acceptedAnswers, context)
        } else {
            AnswerGrader.gradeChoice(s.selected, exercise.correctIndices, context)
        }
        finish(exercise, verdict)
    }

    private fun finish(exercise: DojoExercise, verdict: Verdict) {
        val answered = _state.value // ce que l'élève a saisi ou coché, avant que l'état ne change
        val message = if (verdict.correct) DojoMessages.congrats() else DojoMessages.encourage()
        _state.update {
            it.copy(
                result = DojoResult(
                    correct = verdict.correct,
                    partial = verdict.partial,
                    rating = verdict.rating,
                    message = message
                ),
                correctCount = it.correctCount + if (verdict.correct) 1 else 0
            )
        }
        persist(exercise.item.cardId, verdict.rating, attemptFor(exercise, answered, verdict))
    }

    /** Copie ce que l'élève a vu et répondu (question, réponse saisie, réponse attendue, leçon, discipline) pour l'historique. */
    private fun attemptFor(exercise: DojoExercise, answered: DojoUiState, verdict: Verdict): DojoAttemptEntity {
        val item = exercise.item
        val free = exercise.kind == ExerciseKind.FREE_TEXT
        val given = if (free) {
            answered.typed.trim()
        } else {
            answered.selected.sorted().mapNotNull { item.choices.getOrNull(it) }.joinToString(" ; ")
        }
        val expected = if (free) {
            exercise.reference.split('|').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" / ")
        } else {
            exercise.correctIndices.sorted().mapNotNull { item.choices.getOrNull(it) }.joinToString(" ; ")
        }
        return DojoAttemptEntity(
            studentId = studentId,
            flashcardId = item.cardId,
            attemptedAt = System.currentTimeMillis(),
            isSuccess = verdict.correct,
            attemptNumber = 1, // recalculé par AttemptDao.record selon les échecs précédents
            rating = verdict.rating,
            kind = exercise.kind.name,
            question = item.front,
            studentAnswer = given.ifBlank { null },
            referenceAnswer = expected.ifBlank { item.answer },
            lessonTitle = exercise.lessonTitle ?: item.chapterTitle,
            subjectName = item.subjectName
        )
    }

    /** SrsEngine (FSRS) recalcule stabilité, difficulté et échéance ; le résultat est écrit dans Room. */
    private fun persist(cardId: String, rating: ReviewRating, attempt: DojoAttemptEntity? = null) {
        lastWrite = viewModelScope.launch {
            writeLock.withLock {
                // Historique de « Ma progression » : une panne ici ne doit jamais empêcher la note FSRS
                try {
                    attempt?.let { attempts.record(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // tentative non enregistrée : la session continue
                }
                try {
                    val days = repository.rate(studentId, cardId, rating, levelId, latencyMs = lastLatencyMs)
                    _state.update { st ->
                        val current = st.result
                        if (current != null && st.exercise?.item?.cardId == cardId) {
                            st.copy(result = current.copy(nextReviewDays = days))
                        } else {
                            st
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Une écriture perdue ne doit pas bloquer la session
                }
            }
        }
    }

    fun next() {
        val s = _state.value
        if (s.result == null) return
        val nextIndex = s.index + 1
        if (nextIndex >= exercises.size) {
            _state.update { it.copy(finished = true, exercise = null) }
        } else {
            show(nextIndex)
        }
    }

    /** À attendre avant de fermer la session : toutes les notes sont écrites dans Room. */
    suspend fun flush() {
        lastWrite?.join()
        writeLock.withLock { }
    }

    private companion object {
        const val MAX_INPUT = 200

        /** Réponse « mots-clés » : au-delà, la carte reste en QCM (une phrase ne se corrige pas par mots-clés). */
        const val MAX_KEYWORD_WORDS = 4
    }
}
