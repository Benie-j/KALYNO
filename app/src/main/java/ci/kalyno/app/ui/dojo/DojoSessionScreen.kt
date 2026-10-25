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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.StreakBadge
import ci.kalyno.app.ui.components.rememberHaptic
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

    val state by vm.state.collectAsState()
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val dayStreak = remember { StreakStore(context.applicationContext).current() }
    val scope = rememberCoroutineScope()
    var calculatorOpen by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

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
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (!state.finished) {
            TopBar(
                done = state.index + if (state.result != null) 1 else 0,
                index = state.index,
                total = state.total,
                dayStreak = dayStreak,
                calculatorSuggested = state.exercise?.calculatorSuggested == true,
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
            if (state.finished) {
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
private fun TopBar(
    done: Int,
    index: Int,
    total: Int,
    dayStreak: Int,
    calculatorSuggested: Boolean,
    onClose: () -> Unit,
    onCalculator: () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else done.toFloat() / total,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "dojoProgress"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
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
                Text("✕", color = KalynoMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${index + 1} / $total",
                color = KalynoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "🧮 Calculatrice",
                color = if (calculatorSuggested) KalynoAmberDeep else KalynoBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (calculatorSuggested) KalynoAmberSoft else KalynoBlueSoft)
                    .clickable(onClick = onCalculator)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Spacer(Modifier.width(8.dp))
            StreakBadge(count = dayStreak)
        }
    }
}

// ---------------------------------------------------------------------------
// Un exercice : question -> validation -> correction (jamais avant la validation)
// ---------------------------------------------------------------------------

@Composable
private fun ExercisePane(state: DojoUiState, exercise: DojoExercise, vm: DojoViewModel) {
    val result = state.result
    val isLast = state.index + 1 >= state.total

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = enter.value
                translationX = (1f - enter.value) * 48.dp.toPx()
            }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            QuestionCard(state = state, exercise = exercise, vm = vm)

            if (result != null) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(350)) + expandVertically(tween(350))
                ) {
                    CorrectionCard(exercise = exercise, result = result)
                }
            }
        }

        if (result == null) {
            KalynoPrimaryButton(
                label = "VALIDER MA RÉPONSE",
                onClick = vm::submit,
                enabled = state.canSubmit
            )
        } else {
            KalynoPrimaryButton(
                label = if (isLast) "VOIR MON BILAN" else "CONTINUER",
                onClick = vm::next
            )
        }
    }
}

@Composable
private fun QuestionCard(state: DojoUiState, exercise: DojoExercise, vm: DojoViewModel) {
    val item = exercise.item
    val result = state.result
    val locked = result != null
    val haptic = rememberHaptic()

    val cardBackground by animateColorAsState(
        targetValue = when {
            result == null -> Color.White
            result.correct -> KalynoCorrectSoft
            else -> KalynoDangerSoft
        },
        label = "dojoCardBackground"
    )
    val cardBorder by animateColorAsState(
        targetValue = when {
            result == null -> KalynoOutline
            result.correct -> KalynoCorrect
            else -> KalynoDangerBorder
        },
        label = "dojoCardBorder"
    )

    KalynoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 22.dp,
        background = cardBackground,
        borderColor = cardBorder
    ) {
        KalynoPill(
            text = when (exercise.kind) {
                ExerciseKind.SINGLE_CHOICE -> "🎯 UNE BONNE RÉPONSE"
                ExerciseKind.MULTIPLE_CHOICE -> "☑️ PLUSIEURS RÉPONSES"
                ExerciseKind.FREE_TEXT -> "✍️ SAISIE LIBRE"
            }
        )

        val context = listOfNotNull(
            state.title,
            item.subjectName.takeIf { it.isNotBlank() },
            item.chapterTitle.takeIf { it.isNotBlank() }
        ).distinct().joinToString(" · ")
        if (context.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = context, color = KalynoMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = item.front,
            color = KalynoText,
            fontSize = 21.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // Indice : seulement à la demande (la carte reviendra alors plus tôt)
        val hint = item.hint
        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            if (state.hintShown) {
                Text(
                    text = "💡 $hint",
                    color = KalynoAmberDeep,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KalynoAmberSoft)
                        .padding(12.dp)
                )
            } else if (!locked) {
                Text(
                    text = "💡 Voir un indice",
                    color = KalynoAmberDeep,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(KalynoAmberSoft)
                        .clickable { vm.showHint() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        when (exercise.kind) {
            ExerciseKind.FREE_TEXT -> OutlinedTextField(
                value = state.typed,
                onValueChange = vm::onTyped,
                modifier = Modifier.fillMaxWidth(),
                enabled = !locked,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                placeholder = { Text("Écris ta réponse ici...", color = KalynoMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedBorderColor = KalynoBlue,
                    unfocusedBorderColor = KalynoOutline,
                    disabledBorderColor = if (result?.correct == true) KalynoCorrect else KalynoDangerBorder,
                    cursorColor = KalynoBlue,
                    focusedTextColor = KalynoText,
                    unfocusedTextColor = KalynoText,
                    disabledTextColor = KalynoText
                )
            )

            else -> {
                val multiple = exercise.kind == ExerciseKind.MULTIPLE_CHOICE
                if (multiple && !locked) {
                    Text(
                        text = "Coche toutes les bonnes réponses.",
                        color = KalynoMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item.choices.forEachIndexed { i, choice ->
                        ChoiceRow(
                            letter = ('A'.code + i).toChar().toString(),
                            text = choice,
                            multiple = multiple,
                            selected = i in state.selected,
                            locked = locked,
                            // La bonne réponse n'est révélée qu'après la validation
                            isCorrect = locked && i in exercise.correctIndices,
                            onClick = {
                                haptic()
                                vm.toggleChoice(i)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    letter: String,
    text: String,
    multiple: Boolean,
    selected: Boolean,
    locked: Boolean,
    isCorrect: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val wrongPick = locked && selected && !isCorrect

    val container = when {
        isCorrect -> KalynoCorrectSoft
        wrongPick -> KalynoDangerSoft
        selected -> KalynoBlueSoft
        else -> Color.White
    }
    val borderColor = when {
        isCorrect -> KalynoCorrect
        wrongPick -> KalynoDanger
        selected -> KalynoBlue
        else -> KalynoOutline
    }
    val badgeColor = when {
        isCorrect -> KalynoCorrect
        wrongPick -> KalynoDanger
        selected -> KalynoBlue
        else -> KalynoBackground
    }
    val badgeText = when {
        isCorrect -> "✓"
        wrongPick -> "✕"
        else -> letter
    }
    val dimmed = locked && !isCorrect && !wrongPick

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (dimmed) 0.55f else 1f }
            .clip(shape)
            .background(container)
            .border(if (selected || isCorrect) 2.dp else 1.dp, borderColor, shape)
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(if (multiple) RoundedCornerShape(8.dp) else CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badgeText,
                color = if (selected || isCorrect || wrongPick) Color.White else KalynoMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = KalynoText,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = if (selected || isCorrect) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// Correction : message, planification FSRS, bonne réponse, explication de l'admin
// ---------------------------------------------------------------------------

@Composable
private fun CorrectionCard(exercise: DojoExercise, result: DojoResult) {
    val item = exercise.item
    val accent = when {
        result.correct -> KalynoCorrectText
        result.partial -> KalynoAmberDeep
        else -> KalynoDanger
    }
    val tint = when {
        result.correct -> KalynoCorrectSoft
        result.partial -> KalynoAmberSoft
        else -> KalynoDangerSoft
    }

    KalynoCard(modifier = Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(tint)
                .padding(14.dp)
        ) {
            val emoji = when {
                result.correct -> "🎉"
                result.partial -> "🤏"
                else -> "💪"
            }
            Text(
                text = "$emoji ${result.message}",
                color = accent,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold
            )
            if (result.partial) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tu n'as pas coché toutes les bonnes réponses.",
                    color = KalynoText,
                    fontSize = 13.sp
                )
            }
            result.nextReviewDays?.let { days ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "🧠 Prochaine révision : " + if (days <= 1) "demain" else "dans $days jours",
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // La bonne réponse : affichée en cas d'erreur, et toujours pour la saisie libre
        if (!result.correct || exercise.kind == ExerciseKind.FREE_TEXT) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "BONNE RÉPONSE",
                color = KalynoCorrectText,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = exercise.acceptedAnswers.firstOrNull() ?: item.answer,
                color = KalynoCorrect,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KalynoCorrectSoft)
                    .padding(16.dp)
            )
            if (exercise.acceptedAnswers.size > 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Aussi accepté : " + exercise.acceptedAnswers.drop(1).joinToString(" · "),
                    color = KalynoMuted,
                    fontSize = 12.sp
                )
            }
        }

        val steps = explanationSteps(item.explanation)
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "EXPLICATION",
                color = KalynoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                steps.forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        if (steps.size > 1) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(KalynoBlueSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    color = KalynoBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = step,
                            color = KalynoText,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
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
        Text(if (good) "🏆" else "💪", fontSize = 64.sp)
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
            SummaryChip("✅ $correct", KalynoCorrectSoft, KalynoCorrectText)
            SummaryChip("❌ ${total - correct}", KalynoDangerSoft, KalynoDanger)
        }
        Spacer(Modifier.height(32.dp))
        KalynoPrimaryButton(label = "TERMINER", onClick = onDone)
    }
}

@Composable
private fun SummaryChip(text: String, container: Color, content: Color) {
    Text(
        text = text,
        color = content,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

/** Découpe l'explication de l'admin en étapes (une par ligne, sinon une par phrase). */
private fun explanationSteps(explanation: String?): List<String> {
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
