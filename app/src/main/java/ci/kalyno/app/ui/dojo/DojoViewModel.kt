package ci.kalyno.app.ui.dojo

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.dashboard.DashboardRepository
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.data.dashboard.ReviewSession
import ci.kalyno.app.data.dashboard.StudentId
import ci.kalyno.app.data.dojo.AnswerContext
import ci.kalyno.app.data.dojo.AnswerGrader
import ci.kalyno.app.data.dojo.CalculatorPolicy
import ci.kalyno.app.data.dojo.ExerciseKind
import ci.kalyno.app.data.dojo.Interleaver
import ci.kalyno.app.data.dojo.mergeCorrectIndices
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

/** Un exercice prêt à jouer : tout vient de l'admin (Room / Supabase), rien n'est écrit en dur. */
data class DojoExercise(
    val item: ReviewItem,
    val kind: ExerciseKind,
    val correctIndices: Set<Int>,
    val acceptedAnswers: List<String>,
    val calculatorSuggested: Boolean
) {
    val promptLength: Int get() = item.front.length + item.choices.sumOf { it.length }
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
    val finished: Boolean = false
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

    /** Calculatrice conservée pendant toute la session (survit aux rotations d'écran). */
    val calculator = CalculatorModel()

    private val writeLock = Mutex()
    private var lastWrite: Job? = null
    private var sessionId: String? = null
    private var levelId: String? = null
    private var exercises: List<DojoExercise> = emptyList()
    private var shownAt = 0L

    private val _state = MutableStateFlow(DojoUiState())
    val state: StateFlow<DojoUiState> = _state.asStateFlow()

    fun start(session: ReviewSession, levelId: String?) {
        if (sessionId == session.id) return
        sessionId = session.id
        this.levelId = levelId
        exercises = Interleaver.mix(session.items, seed = session.id.hashCode().toLong())
            .map { buildExercise(it) }
        _state.value = DojoUiState(title = session.title, total = exercises.size)
        if (exercises.isEmpty()) {
            _state.update { it.copy(finished = true) }
        } else {
            show(0)
        }
    }

    private fun buildExercise(item: ReviewItem): DojoExercise {
        val indices = mergeCorrectIndices(item.correctIndices, item.correctIndex)
            .filter { it in item.choices.indices }
        return DojoExercise(
            item = item,
            kind = ExerciseKind.resolve(item.kind, item.choices, indices),
            correctIndices = indices.toSet(),
            acceptedAnswers = AnswerGrader.acceptedAnswers(item.answer),
            calculatorSuggested = CalculatorPolicy.suggested(listOf(item.front) + item.choices)
        )
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

    /** Valide la réponse : correction objective, note FSRS automatique, puis écriture dans Room. */
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
        val verdict = when (exercise.kind) {
            ExerciseKind.FREE_TEXT -> AnswerGrader.gradeText(s.typed, exercise.acceptedAnswers, context)
            else -> AnswerGrader.gradeChoice(s.selected, exercise.correctIndices, context)
        }
        val message = if (verdict.correct) DojoMessages.congrats() else DojoMessages.encourage()

        _state.update {
            it.copy(
                result = DojoResult(verdict.correct, verdict.partial, verdict.rating, message),
                correctCount = it.correctCount + if (verdict.correct) 1 else 0
            )
        }
        persist(exercise.item.cardId, verdict.rating)
    }

    /** SrsEngine (FSRS) recalcule stabilité, difficulté et échéance ; le résultat est écrit dans Room. */
    private fun persist(cardId: String, rating: ReviewRating) {
        lastWrite = viewModelScope.launch {
            writeLock.withLock {
                try {
                    val days = repository.rate(studentId, cardId, rating, levelId)
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
    }
}
