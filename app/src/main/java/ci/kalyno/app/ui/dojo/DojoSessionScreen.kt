package ci.kalyno.app.ui.dojo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.dashboard.ReviewSession
import ci.kalyno.app.data.dojo.ExerciseKind
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.MathText
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.StreakBadge
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.quiz.FeedbackTone
import ci.kalyno.app.data.dojo.FeynmanParser
import ci.kalyno.app.ui.quiz.KalynoChoiceCard
import ci.kalyno.app.ui.quiz.KalynoQuestionCard
import ci.kalyno.app.ui.quiz.QuizColors
import ci.kalyno.app.ui.quiz.QuizFeedbackPanel
import ci.kalyno.app.ui.quiz.QuizStickyFooter
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrect
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerBorder
import ci.kalyno.app.ui.theme.KalynoDangerSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText
import kotlinx.coroutines.launch
import ci.kalyno.app.ui.report.ReportFlagButton
import ci.kalyno.app.ui.report.ReportLayer
import ci.kalyno.app.ui.report.ReportTarget
import ci.kalyno.app.ui.report.rememberReportLayerState

// ===========================================================================
// SESSION DU DOJO : exercices objectifs (QCM / saisie libre), correction après validation,
// note FSRS calculée automatiquement. Remplace l'ancienne auto-évaluation.
// ===========================================================================

@Composable
fun DojoSessionScreen(
    session: ReviewSession,
    levelId: String?,
    onClose: (completed: Boolean) -> Unit
) {
    val vm: DojoViewModel = viewModel(key = "dojo-${session.id}")
    remember(session.id) { vm.start(session, levelId) }

    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val dayStreak = remember { StreakStore(context.applicationContext).current() }
    val scope = rememberCoroutineScope()
    var calculatorOpen by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val report = rememberReportLayerState()

    // On attend l'écriture des notes dans Room avant de rendre la main au tableau de bord
    fun close(completed: Boolean) {
        if (closing) return
        closing = true
        scope.launch {
            vm.flush()
            onClose(completed)
        }
    }

    BackHandler { close(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .safeDrawingPadding()
    ) {
        if (!state.finished) {
            TopBar(
                done = state.index + if (state.result != null) 1 else 0,
                index = state.index,
                total = state.total,
                dayStreak = dayStreak,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                calculatorSuggested = state.exercise?.calculatorSuggested == true,
                onReport = {
                    state.exercise?.let { ex ->
                        report.open(
                            ReportTarget.dojoExercise(
                                cardId = ex.item.cardId,
                                subjectName = ex.item.subjectName,
                                lessonTitle = ex.lessonTitle ?: ex.item.chapterTitle,
                                question = ex.item.front
                            )
                        )
                    }
                },
                onClose = {
                    haptic()
                    close(false)
                },
                onCalculator = {
                    haptic()
                    vm.markCalculatorUsed()
                    calculatorOpen = true
                }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val exercise = state.exercise
            if (state.finished && state.lockedOut) {
                LockedPane(onClose = { close(false) })
            } else if (state.finished) {
                FinishedPane(
                    total = state.total,
                    correct = state.correctCount,
                    onDone = { close(true) }
                )
            } else if (exercise != null) {
                key(state.index) {
                    ExercisePane(state = state, exercise = exercise, vm = vm)
                }
            }
            ReportLayer(report)
        }
    }

    if (calculatorOpen) {
        ScientificCalculatorDialog(
            model = vm.calculator,
            canUseResult = state.exercise?.kind == ExerciseKind.FREE_TEXT && state.result == null,
            onUseResult = {
                vm.onTyped(it)
                calculatorOpen = false
            },
            onDismiss = { calculatorOpen = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Barre du haut : fermer, progression, compteur, calculatrice, flamme
// ---------------------------------------------------------------------------

@Composable
internal fun TopBar(
    done: Int,
    index: Int,
    total: Int,
    dayStreak: Int,
    modifier: Modifier = Modifier,
    calculatorSuggested: Boolean,
    onReport: () -> Unit,
    onClose: () -> Unit,
    onCalculator: () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else done.toFloat() / total,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "dojoProgress"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(KalynoBlueSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(KalynoBlue)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = KalynoMuted, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${index + 1} / $total",
                color = KalynoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.weight(1f))
            ReportFlagButton(onClick = onReport)
            Spacer(Modifier.width(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (calculatorSuggested) KalynoAmberSoft else KalynoBlueSoft)
                    .clickable(onClick = onCalculator)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(
                    Icons.Rounded.Calculate,
                    contentDescription = null,
                    tint = if (calculatorSuggested) KalynoAmberDeep else KalynoBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Calculatrice",
                    color = if (calculatorSuggested) KalynoAmberDeep else KalynoBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(8.dp))
            StreakBadge(count = dayStreak)
        }
    }
}

// ---------------------------------------------------------------------------
// Un exercice : question -> validation -> correction (jamais avant la validation)
// La rétroaction vit dans un footer fixe : aucun scroll nécessaire pour la voir.
// ---------------------------------------------------------------------------

@Composable
private fun ExercisePane(state: DojoUiState, exercise: DojoExercise, vm: DojoViewModel) {
    val result = state.result
    val isLast = state.index + 1 >= state.total

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320)) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = enter.value
                    translationX = (1f - enter.value) * 48.dp.toPx()
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 12.dp)
        ) {
            QuestionCard(state = state, exercise = exercise, vm = vm)
        }

        QuizStickyFooter(
            validated = result != null,
            actionLabel = when {
                state.evaluating -> "ANALYSE EN COURS..."
                result == null -> "VALIDER MA RÉPONSE"
                isLast -> "VOIR MON BILAN"
                else -> "CONTINUER"
            },
            actionEnabled = result != null || state.canSubmit,
            onAction = { if (result == null) vm.submit() else vm.next() },
            feedback = if (result != null) {
                { DojoFeedback(exercise = exercise, result = result) }
            } else null
        )
    }
}

@Composable
private fun QuestionCard(state: DojoUiState, exercise: DojoExercise, vm: DojoViewModel) {
    val item = exercise.item
    val result = state.result
    val locked = result != null
    val haptic = rememberHaptic()

    val lessonLabel = listOfNotNull(
        item.subjectName.takeIf { it.isNotBlank() },
        item.chapterTitle.takeIf { it.isNotBlank() }
    ).distinct().joinToString(" · ").ifBlank { state.title.orEmpty() }

    KalynoQuestionCard(
        question = item.front,
        lessonLabel = lessonLabel,
        typeLabel = when {
            exercise.promotedToFreeInput -> "Saisie libre · niveau débloqué"
            exercise.kind == ExerciseKind.SINGLE_CHOICE -> "Une bonne réponse"
            exercise.kind == ExerciseKind.MULTIPLE_CHOICE -> "Plusieurs réponses"
            else -> "Saisie libre"
        },
        // Indice : seulement à la demande (la carte reviendra alors plus tôt)
        hint = item.hint,
        hintVisible = state.hintShown,
        onRequestHint = if (!locked) ({ vm.showHint() }) else null
    ) {
        DojoAnswerArea(
            state = state,
            exercise = exercise,
            onToggleChoice = vm::toggleChoice,
            onTyped = vm::onTyped,
            onSubmit = vm::submit,
            onHaptic = haptic
        )
    }
}

/** Conservé pour le Défi : même carte de suggestion que le Dojo. */
@Composable
internal fun ChoiceRow(
    letter: String,
    text: String,
    multiple: Boolean,
    selected: Boolean,
    locked: Boolean,
    isCorrect: Boolean,
    onClick: () -> Unit
) {
    KalynoChoiceCard(
        letter = letter,
        text = text,
        selected = selected,
        locked = locked,
        isCorrect = isCorrect,
        onClick = onClick
    )
}

// ---------------------------------------------------------------------------
// Correction : message, planification FSRS, bonne réponse, explication de l'admin
// ---------------------------------------------------------------------------

@Composable
internal fun DojoFeedback(exercise: DojoExercise, result: DojoResult) {
    val item = exercise.item
    // La bonne réponse : affichée en cas d'erreur, et toujours pour la saisie libre
    val showAnswer = !result.correct || exercise.kind == ExerciseKind.FREE_TEXT

    QuizFeedbackPanel(
        tone = when {
            result.correct -> FeedbackTone.Correct
            result.partial -> FeedbackTone.Partial
            else -> FeedbackTone.Wrong
        },
        message = result.message,
        detail = when {
            result.feedback != null -> result.feedback
            result.partial -> "Tu n'as pas coché toutes les bonnes réponses."
            !result.correct && exercise.kind == ExerciseKind.FREE_TEXT -> "Retiens le mot-clé : il résume l'idée en un mot."
            else -> null
        },
        meta = result.nextReviewDays?.let { days ->
            "Prochaine révision : " + if (days <= 1) "demain" else "dans $days jours"
        },
        correctAnswer = if (showAnswer) (exercise.acceptedAnswers.firstOrNull() ?: item.answer) else null,
        alsoAccepted = exercise.acceptedAnswers.drop(1),
        explanationSteps = explanationSteps(item.explanation),
        feynman = remember(item.explanation) { FeynmanParser.parse(item.explanation) }
    )
}

// ---------------------------------------------------------------------------
// Bilan de session
// ---------------------------------------------------------------------------

@Composable
private fun FinishedPane(total: Int, correct: Int, onDone: () -> Unit) {
    val good = total > 0 && correct * 100 >= total * 60
    val message = remember { if (good) DojoMessages.congrats() else DojoMessages.encourage() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (good) Icons.Rounded.EmojiEvents else Icons.Rounded.FitnessCenter,
            contentDescription = null,
            tint = QuizColors.Primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Session terminée !",
            color = KalynoText,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$correct / $total bonnes réponses",
            color = KalynoMuted,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            color = KalynoText,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryChip(Icons.Rounded.Check, "$correct", QuizColors.CorrectBg, QuizColors.CorrectText)
            SummaryChip(Icons.Rounded.Close, "${total - correct}", QuizColors.WrongBg, QuizColors.WrongText)
        }
        Spacer(Modifier.height(32.dp))
        KalynoPrimaryButton(label = "TERMINER", onClick = onDone)
    }
}

@Composable
private fun SummaryChip(icon: ImageVector, text: String, container: Color, content: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = text, color = content, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Découpe l'explication de l'admin en étapes (une par ligne, sinon une par phrase). */
internal fun explanationSteps(explanation: String?): List<String> {
    if (explanation.isNullOrBlank()) return emptyList()
    val lines = explanation.lines()
        .map { it.trim().trimStart('-', '•', '*').trim() }
        .filter { it.isNotEmpty() }
    if (lines.size > 1) return lines
    val sentences = explanation.trim()
        .split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return if (sentences.size > 1) sentences else listOf(explanation.trim())
}

/** Affiché si aucune carte de la session n'appartient à une leçon terminée : on renvoie vers l'Explorer. */
@Composable
private fun LockedPane(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Leçon à terminer d'abord",
            color = KalynoText,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Les exercices du Dojo se débloquent quand tu appuies sur « Terminer » à la fin d'une leçon.",
            color = KalynoMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(20.dp))
        KalynoPrimaryButton(label = "COMPRIS", onClick = onClose)
    }
}
