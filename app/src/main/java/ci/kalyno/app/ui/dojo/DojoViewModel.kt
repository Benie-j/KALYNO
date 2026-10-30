package ci.kalyno.app.ui.dojo

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.SupabaseConfig
import ci.kalyno.app.data.dashboard.DashboardRepository
import ci.kalyno.app.data.dashboard.DojoCardMeta
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.data.dashboard.ReviewSession
import ci.kalyno.app.data.dashboard.StudentId
import ci.kalyno.app.data.dojo.AnswerContext
import ci.kalyno.app.data.dojo.AnswerGrader
import ci.kalyno.app.data.dojo.CalculatorPolicy
import ci.kalyno.app.data.dojo.DojoGrading
import ci.kalyno.app.data.dojo.DojoStagePolicy
import ci.kalyno.app.data.dojo.ExerciseKind
import ci.kalyno.app.data.dojo.LessonReferenceProvider
import ci.kalyno.app.data.dojo.Interleaver
import ci.kalyno.app.data.dojo.mergeCorrectIndices
import ci.kalyno.app.data.lessons.LessonProgressRepository
import ci.kalyno.app.data.dojo.Verdict
import ci.kalyno.app.data.srs.ReviewRating
import ci.kalyno.app.ml.semantic.EvaluationResult
import ci.kalyno.app.ml.semantic.EvaluationVerdict
import ci.kalyno.app.ml.semantic.SemanticEvaluationEngine
import ci.kalyno.app.ml.semantic.SemanticEvaluator
import ci.kalyno.app.ml.semantic.SemanticSettingsRepository
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
    /** Phrase de référence de l'admin : sert à la correction sémantique et à l'affichage de la bonne réponse. */
    val reference: String = item.answer,
    /** Vrai si la saisie est corrigée par le moteur d'embedding (sinon : mots-clés). */
    val useSemantic: Boolean = false,
    /** Vrai si la carte était un QCM et vient de basculer en saisie libre grâce à son niveau. */
    val promotedToFreeInput: Boolean = false,
    /** Passages de la leçon de la carte (vide si la leçon n'est pas en cache sur ce téléphone). */
    val lessonPassages: List<String> = emptyList(),
    /** Leçon parente de la question (relation Room flashcards.lessonId -> catalog_lessons.id) : contexte du passage E5. */
    val lessonTitle: String? = null,
    val lessonSummary: String? = null
) {
    /** Saisie libre corrigée par mots-clés, avec la leçon en renfort pour valider les reformulations. */
    val canRescueWithLesson: Boolean
        get() = requiresFreeInput && !useSemantic && lessonPassages.isNotEmpty() &&
            DojoGrading.canRescueWithLesson(acceptedAnswers.firstOrNull() ?: item.answer)

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
    val nextReviewDays: Int? = null,
    /** Retour pédagogique du moteur sémantique (saisie libre uniquement). */
    val feedback: String? = null,
    /** Proximité sémantique entre 0 et 1, null hors correction sémantique. */
    val similarity: Float? = null
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
    /** Correction sémantique en cours : le bouton de validation est bloqué (pas de double envoi). */
    val evaluating: Boolean = false,
    /** null tant que l'élève n'a pas validé : la correction reste cachée. */
    val result: DojoResult? = null,
    val correctCount: Int = 0,
    val finished: Boolean = false,
    /** Vrai si la session s'est vidée faute de leçon terminée : aucun bravo, aucune série créditée. */
    val lockedOut: Boolean = false
) {
    val canSubmit: Boolean
        get() = !evaluating && when (exercise?.kind) {
            null -> false
            ExerciseKind.FREE_TEXT -> typed.isNotBlank()
            else -> selected.isNotEmpty()
        }
}

class DojoViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DashboardRepository(app)
    private val studentId = StudentId.get(app)
    private val lessonProgress = LessonProgressRepository.get(app)

    /** Moteur ONNX + seuils de l'admin : le ViewModel ne manipule ni ONNX ni Supabase directement. */
    private val semanticEvaluator = SemanticEvaluator(
        app,
        SemanticSettingsRepository(app, SupabaseConfig.BASE_URL, SupabaseConfig.PUBLISHABLE_KEY)
    )
    private var evaluation: Job? = null
    private var lessons: LessonReferenceProvider? = null

    /** Calculatrice conservée pendant toute la session (survit aux rotations d'écran). */
    val calculator = CalculatorModel()

    private val writeLock = Mutex()
    private var lastWrite: Job? = null
    private var sessionId: String? = null
    private var levelId: String? = null
    private var exercises: List<DojoExercise> = emptyList()
    private var shownAt = 0L

    /** Moteur ONNX utilisable sur cet appareil (lu une fois par session, hors thread principal). */
    private var engineOn = false
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
        lessons = LessonReferenceProvider(getApplication(), levelId)
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
            // Texte des leçons depuis le cache hors-ligne : sert à valider les reformulations
            val passages = try {
                lessons?.passagesFor(mixed.map { it.cardId }).orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
            engineOn = withContext(Dispatchers.Default) { semanticEvaluator.isAvailable }
            exercises = mixed.map { buildExercise(it, meta[it.cardId], passages[it.cardId].orEmpty()) }
            show(0)
            if (exercises.any { it.useSemantic || it.canRescueWithLesson }) warmUpSemanticEngine()
        }
    }

    /** Chargement du modèle E5 (ONNX) et des seuils admin en arrière-plan, avant que l'élève n'atteigne une saisie libre. */
    private fun warmUpSemanticEngine() {
        if (!engineOn) return
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { SemanticEvaluationEngine.getInstance(getApplication()) }
            runCatching { semanticEvaluator.refreshSettings() }
            // Réponses officielles de la session : vecteurs prêts avant la première saisie
            runCatching {
                semanticEvaluator.preloadReferences(exercises.filter { it.useSemantic }.map { it.reference }.distinct())
            }
            // Vecteurs des leçons calculés avant la première réponse (une seule fois par leçon)
            val seen = java.util.IdentityHashMap<List<String>, Boolean>()
            exercises.filter { it.canRescueWithLesson }.forEach { ex ->
                if (seen.put(ex.lessonPassages, true) == null) {
                    runCatching { semanticEvaluator.preloadPassages(ex.lessonPassages) }
                }
            }
        }
    }

    /**
     * Choisit l'interface de la carte. Un QCM bascule en saisie libre quand son niveau atteint le seuil
     * ([DojoStagePolicy.requiresFreeInput]) ; une carte « saisie libre » de l'admin l'est dès le départ.
     * Un QCM à réponses multiples ne bascule que si l'admin a écrit une phrase de référence.
     */
    private fun buildExercise(item: ReviewItem, meta: DojoCardMeta?, lessonPassages: List<String>): DojoExercise {
        val indices = mergeCorrectIndices(item.correctIndices, item.correctIndex)
            .filter { it in item.choices.indices }
        val declared = ExerciseKind.resolve(item.kind, item.choices, indices)
        val stage = meta?.stage ?: 0
        val adminReference = meta?.adminReference?.takeIf { it.isNotBlank() }

        val canPromote = when (declared) {
            ExerciseKind.SINGLE_CHOICE -> true
            ExerciseKind.MULTIPLE_CHOICE -> adminReference != null
            ExerciseKind.FREE_TEXT -> false
        }
        val promoted = canPromote && DojoStagePolicy.requiresFreeInput(stage)
        val kind = if (promoted) ExerciseKind.FREE_TEXT else declared

        val reference = adminReference ?: item.answer
        val useSemantic = engineOn && kind == ExerciseKind.FREE_TEXT && DojoGrading.useSemantic(reference)
        return DojoExercise(
            item = item,
            kind = kind,
            correctIndices = indices.toSet(),
            acceptedAnswers = if (useSemantic) listOf(reference) else AnswerGrader.acceptedAnswers(item.answer),
            calculatorSuggested = CalculatorPolicy.suggested(listOf(item.front) + item.choices),
            stage = stage,
            reference = reference,
            useSemantic = useSemantic,
            promotedToFreeInput = promoted,
            lessonPassages = if (engineOn) lessonPassages else emptyList(),
            lessonTitle = meta?.lessonTitle,
            lessonSummary = meta?.lessonSummary
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
                evaluating = false,
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
        if (_state.value.result != null || _state.value.evaluating) return
        val limit = if (_state.value.exercise?.useSemantic == true) MAX_SENTENCE_INPUT else MAX_INPUT
        _state.update { it.copy(typed = value.take(limit)) }
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
     * Valide la réponse : QCM -> comparaison des indices ; saisie libre -> moteur sémantique (ou mots-clés
     * pour une référence courte). Note FSRS automatique, puis écriture dans Room.
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

        if (!exercise.requiresFreeInput) {
            finish(exercise, AnswerGrader.gradeChoice(s.selected, exercise.correctIndices, context), semantic = null)
            return
        }

        if (!exercise.useSemantic) {
            val lexical = AnswerGrader.gradeText(s.typed, exercise.acceptedAnswers, context)
            if (lexical.correct || !exercise.canRescueWithLesson) {
                finish(exercise, lexical, semantic = null)
                return
            }
            // Mots-clés refusés : la leçon peut encore valider une reformulation correcte
            val typed = s.typed
            _state.update { it.copy(evaluating = true) }
            evaluation = viewModelScope.launch {
                val result = semanticEvaluator.evaluateWithLesson(
                    question = exercise.item.front,
                    expectedShort = exercise.acceptedAnswers.firstOrNull() ?: exercise.item.answer,
                    passages = exercise.lessonPassages,
                    studentAnswer = typed
                )
                val rescued = DojoGrading.fromLesson(result)
                finish(
                    exercise,
                    rescued ?: lexical,
                    semantic = result.takeIf { it.verdict != EvaluationVerdict.ENGINE_ERROR }
                )
            }
            return
        }

        val answer = s.typed
        _state.update { it.copy(evaluating = true) }
        evaluation = viewModelScope.launch {
            // query: « réponse officielle » contre query: « saisie de l'élève » (tâche symétrique de la fiche E5)
            val result = semanticEvaluator.evaluate(
                adminReference = exercise.reference,
                studentAnswer = answer,
                question = exercise.item.front
            )

            // Si E5 rejette la réponse, vérifier aussi avec le contenu de la leçon.
            val lessonResult = if (
                exercise.lessonPassages.isNotEmpty() &&
                DojoGrading.canRescueWithLesson(exercise.reference) &&
                result.verdict in setOf(
                    EvaluationVerdict.INCORRECT,
                    EvaluationVerdict.ENGINE_ERROR
                )
            ) {
                semanticEvaluator.evaluateWithLesson(
                    question = exercise.item.front,
                    expectedShort = exercise.item.answer,
                    passages = exercise.lessonPassages,
                    studentAnswer = answer
                )
            } else {
                null
            }

            val rescued = lessonResult?.let { DojoGrading.fromLesson(it) }
            val semanticVerdict = DojoGrading.fromSemantic(result, context)

            val verdict = when {
                rescued != null -> rescued
                semanticVerdict != null -> semanticVerdict
                else -> AnswerGrader.gradeText(
                    answer,
                    AnswerGrader.acceptedAnswers(exercise.item.answer),
                    context
                )
            }

            val feedbackResult = if (rescued != null) lessonResult else result

            finish(
                exercise,
                verdict,
                semantic = feedbackResult?.takeIf {
                    it.verdict != EvaluationVerdict.ENGINE_ERROR
                }
            )

        }
    }

    private fun finish(exercise: DojoExercise, verdict: Verdict, semantic: EvaluationResult?) {
        val message = if (verdict.correct) DojoMessages.congrats() else DojoMessages.encourage()
        _state.update {
            it.copy(
                evaluating = false,
                result = DojoResult(
                    correct = verdict.correct,
                    partial = verdict.partial,
                    rating = verdict.rating,
                    message = message,
                    feedback = semantic?.feedback,
                    similarity = semantic?.similarityScore
                ),
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
        const val MAX_SENTENCE_INPUT = 400
    }
}
