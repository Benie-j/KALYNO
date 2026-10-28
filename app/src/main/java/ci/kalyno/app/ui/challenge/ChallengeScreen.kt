package ci.kalyno.app.ui.challenge

import ci.kalyno.app.ui.icons.KalynoSvgIcon

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import ci.kalyno.app.data.Challenge
import ci.kalyno.app.data.ChallengeRepository
import ci.kalyno.app.data.ChallengeResult
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.Question
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.dojo.AnswerContext
import ci.kalyno.app.data.dojo.AnswerGrader
import ci.kalyno.app.data.dojo.CalculatorPolicy
import ci.kalyno.app.data.dojo.ExerciseKind
import ci.kalyno.app.data.dojo.answerText
import ci.kalyno.app.data.dojo.parseIndexList
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.MathText
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.dojo.CalculatorModel
import ci.kalyno.app.ui.dojo.ChoiceRow
import ci.kalyno.app.ui.dojo.DojoMessages
import ci.kalyno.app.ui.dojo.ScientificCalculatorDialog
import ci.kalyno.app.ui.dojo.explanationSteps
import ci.kalyno.app.ui.quiz.FeedbackTone
import ci.kalyno.app.ui.quiz.QuizFeedbackPanel
import ci.kalyno.app.ui.quiz.QuizStickyFooter
import ci.kalyno.app.ui.theme.*
import kotlinx.coroutines.CancellationException

@Composable
fun ChallengeScreen(
    levelId: String,
    repository: ChallengeRepository,
    onExit: () -> Unit,
    onUnavailable: (() -> Unit)? = null
) {
    var attempt by remember { mutableStateOf(0) }
    var result by remember(levelId, attempt) { mutableStateOf<ChallengeResult?>(null) }

    LaunchedEffect(levelId, attempt) {
        result = try {
            repository.firstChallenge(levelId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ChallengeResult.Failed("Impossible de charger le défi.")
        }
    }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        when (val r = result) {
            null -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = KalynoBlue
            )
            is ChallengeResult.Loaded -> {
                // Garde-fou : on n'affiche jamais le contenu d'un autre niveau
                if (r.challenge.levelId == levelId && r.challenge.questions.isNotEmpty()) {
                    ChallengeContent(r.challenge, onExit)
                } else {
                    ComingSoon(levelId, onExit)
                }
            }
            ChallengeResult.Empty -> {
                if (onUnavailable != null) {
                    // Aucune démo publiée : on passe directement au tableau de bord
                    LaunchedEffect(Unit) { onUnavailable() }
                } else {
                    ComingSoon(levelId, onExit)
                }
            }
            is ChallengeResult.Failed -> MessageState(
                iconName = "radio-tower",
                title = "Oups, ça n'a pas chargé",
                text = r.message,
                primaryLabel = "Réessayer",
                onPrimary = { attempt += 1 },
                secondaryLabel = "Retour à l'accueil",
                onSecondary = onUnavailable ?: onExit
            )
        }
    }
}

@Composable
private fun ComingSoon(levelId: String, onExit: () -> Unit) {
    MessageState(
        iconName = "rocket",
        title = "Bientôt disponible",
        text = "Les défis de ${Levels.label(levelId)} arrivent bientôt. Reviens vite !",
        primaryLabel = "Retour à l'accueil",
        onPrimary = onExit
    )
}

@Composable
private fun MessageState(
    iconName: String,
    title: String,
    text: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        KalynoSvgIcon(name = iconName, size = 64.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            color = KalynoText,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(text = text, color = KalynoMuted, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KalynoBlue)
        ) {
            Text(primaryLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (secondaryLabel != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = secondaryLabel,
                color = KalynoBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSecondary)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun ChallengeContent(challenge: Challenge, onExit: () -> Unit) {
    val total = challenge.questions.size
    var index by rememberSaveable { mutableStateOf(0) }
    // Calculatrice du Dojo, conservée pendant tout le défi
    val calculator = remember { CalculatorModel() }

    if (index >= total) {
        // Plus d'écran « Défi terminé » : on enregistre la série et on passe à la suite
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            StreakStore(context.applicationContext).recordCompletion()
            onExit()
        }
    } else {
        key(index) {
            QuestionStep(
                title = challenge.title,
                question = challenge.questions[index],
                index = index,
                total = total,
                calculator = calculator,
                onNext = { index += 1 },
                onExit = onExit
            )
        }
    }
}

/** 0 = pas encore validé, 1 = juste, 2 = incomplet (QCM), 3 = faux. */
private const val OUTCOME_NONE = 0
private const val OUTCOME_CORRECT = 1
private const val OUTCOME_PARTIAL = 2
private const val OUTCOME_WRONG = 3

@Composable
private fun QuestionStep(
    title: String,
    question: Question,
    index: Int,
    total: Int,
    calculator: CalculatorModel,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    val haptic = rememberHaptic()
    val shownAt = remember { SystemClock.elapsedRealtime() }

    var selectedCsv by rememberSaveable { mutableStateOf("") }
    var typed by rememberSaveable { mutableStateOf("") }
    var hintShown by rememberSaveable { mutableStateOf(false) }
    var calculatorUsed by rememberSaveable { mutableStateOf(false) }
    var calculatorOpen by remember { mutableStateOf(false) }
    var outcome by rememberSaveable { mutableStateOf(OUTCOME_NONE) }
    var message by rememberSaveable { mutableStateOf("") }

    val selected = remember(selectedCsv) { parseIndexList(selectedCsv).toSet() }
    val locked = outcome != OUTCOME_NONE
    val isFree = question.kind == ExerciseKind.FREE_TEXT
    val canSubmit = if (isFree) typed.isNotBlank() else selected.isNotEmpty()
    val calculatorSuggested = remember(question.id) {
        CalculatorPolicy.suggested(listOf(question.prompt) + question.choices)
    }

    fun toggle(choice: Int) {
        if (locked) return
        selectedCsv = if (question.kind == ExerciseKind.MULTIPLE_CHOICE) {
            val next = selected.toMutableSet()
            if (!next.add(choice)) next.remove(choice)
            next.sorted().joinToString(",")
        } else {
            choice.toString()
        }
    }

    // Correction objective du Dojo : jamais avant la validation
    fun submit() {
        if (locked || !canSubmit) return
        val context = AnswerContext(
            elapsedMs = SystemClock.elapsedRealtime() - shownAt,
            questionLength = question.prompt.length + question.choices.sumOf { it.length },
            hintUsed = hintShown,
            calculatorUsed = calculatorUsed
        )
        val verdict = if (isFree) {
            AnswerGrader.gradeText(typed, question.acceptedAnswers, context)
        } else {
            AnswerGrader.gradeChoice(selected, question.correctIndices, context)
        }
        outcome = when {
            verdict.correct -> OUTCOME_CORRECT
            verdict.partial -> OUTCOME_PARTIAL
            else -> OUTCOME_WRONG
        }
        message = if (verdict.correct) DojoMessages.congrats() else DojoMessages.encourage()
    }

    val done = index + if (locked) 1 else 0
    val progress by animateFloatAsState(
        targetValue = done.toFloat() / maxOf(total, 1),
        animationSpec = tween(400),
        label = "progress"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onExit),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "x", size = 22.dp, tint = KalynoMuted)
            }
            Spacer(Modifier.width(8.dp))
            FlameProgressBar(progress = progress, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text("$done/$total", color = KalynoMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChallengeQuestionCard(
                title = title,
                question = question,
                outcome = outcome,
                selected = selected,
                typed = typed,
                hintShown = hintShown,
                calculatorSuggested = calculatorSuggested,
                onToggle = { haptic(); toggle(it) },
                onTyped = { if (!locked) typed = it.take(MAX_INPUT) },
                onSubmit = ::submit,
                onShowHint = { hintShown = true },
                onCalculator = {
                    haptic()
                    calculatorUsed = true
                    calculatorOpen = true
                }
            )
        }

        // Footer fixe : la correction et l'explication restent visibles sans scroll
        QuizStickyFooter(
            validated = locked,
            actionLabel = when {
                !locked -> "VALIDER MA RÉPONSE"
                index == total - 1 -> "TERMINER"
                else -> "CONTINUER"
            },
            actionEnabled = locked || canSubmit,
            onAction = { if (locked) onNext() else submit() },
            feedback = if (locked) {
                { ChallengeCorrection(question = question, outcome = outcome, message = message) }
            } else null
        )
    }

    if (calculatorOpen) {
        ScientificCalculatorDialog(
            model = calculator,
            canUseResult = isFree && !locked,
            onUseResult = {
                typed = it.take(MAX_INPUT)
                calculatorOpen = false
            },
            onDismiss = { calculatorOpen = false }
        )
    }
}

private const val MAX_INPUT = 200

@Composable
private fun ChallengeQuestionCard(
    title: String,
    question: Question,
    outcome: Int,
    selected: Set<Int>,
    typed: String,
    hintShown: Boolean,
    calculatorSuggested: Boolean,
    onToggle: (Int) -> Unit,
    onTyped: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowHint: () -> Unit,
    onCalculator: () -> Unit
) {
    val locked = outcome != OUTCOME_NONE

    KalynoCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = 16.dp,
        background = Color.White,
        borderColor = KalynoOutline
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KalynoPill(
                text = when (question.kind) {
                    ExerciseKind.SINGLE_CHOICE -> "UNE BONNE RÉPONSE"
                    ExerciseKind.MULTIPLE_CHOICE -> "PLUSIEURS RÉPONSES"
                    ExerciseKind.FREE_TEXT -> "SAISIE LIBRE"
                }
            )
            Spacer(Modifier.weight(1f))
            if (calculatorSuggested) {
                Text(
                    text = "Calculatrice",
                    color = KalynoAmberDeep,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(KalynoAmberSoft)
                        .clickable(onClick = onCalculator)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (title.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = title, color = KalynoMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(10.dp))
        MathText(
            text = question.prompt,
            color = KalynoText,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // Indice : seulement à la demande
        val hint = question.hint
        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            if (hintShown) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KalynoAmberSoft)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    KalynoSvgIcon(name = "lightbulb", size = 18.dp, tint = KalynoAmberDeep)
                    Spacer(Modifier.width(8.dp))
                    MathText(text = hint, color = KalynoAmberDeep, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                }
            } else if (!locked) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(KalynoAmberSoft)
                        .clickable(onClick = onShowHint)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KalynoSvgIcon(name = "lightbulb", size = 16.dp, tint = KalynoAmberDeep)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Voir un indice",
                        color = KalynoAmberDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (question.kind == ExerciseKind.FREE_TEXT) {
            OutlinedTextField(
                value = typed,
                onValueChange = onTyped,
                modifier = Modifier.fillMaxWidth(),
                enabled = !locked,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                placeholder = { Text("Écris ta réponse ici...", color = KalynoMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedBorderColor = KalynoBlue,
                    unfocusedBorderColor = KalynoOutline,
                    disabledBorderColor = if (outcome == OUTCOME_CORRECT) KalynoCorrect else KalynoDangerBorder,
                    cursorColor = KalynoBlue,
                    focusedTextColor = KalynoText,
                    unfocusedTextColor = KalynoText,
                    disabledTextColor = KalynoText
                )
            )
        } else {
            val multiple = question.kind == ExerciseKind.MULTIPLE_CHOICE
            if (multiple && !locked) {
                Text(text = "Coche toutes les bonnes réponses.", color = KalynoMuted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.choices.forEachIndexed { i, choice ->
                    ChoiceRow(
                        letter = ('A'.code + i).toChar().toString(),
                        text = choice,
                        multiple = multiple,
                        selected = i in selected,
                        locked = locked,
                        // La bonne réponse n'est révélée qu'après la validation
                        isCorrect = locked && i in question.correctIndices,
                        onClick = { onToggle(i) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeCorrection(question: Question, outcome: Int, message: String) {
    val isFree = question.kind == ExerciseKind.FREE_TEXT
    // La bonne réponse : affichée en cas d'erreur, et toujours pour la saisie libre
    val answer = if (outcome != OUTCOME_CORRECT || isFree) {
        if (isFree) question.acceptedAnswers.firstOrNull()
        else answerText(question.choices, question.correctIndices.sorted(), null)
    } else null

    QuizFeedbackPanel(
        tone = when (outcome) {
            OUTCOME_CORRECT -> FeedbackTone.Correct
            OUTCOME_PARTIAL -> FeedbackTone.Partial
            else -> FeedbackTone.Wrong
        },
        message = message,
        detail = if (outcome == OUTCOME_PARTIAL) "Tu n'as pas coché toutes les bonnes réponses." else null,
        correctAnswer = answer,
        alsoAccepted = if (isFree) question.acceptedAnswers.drop(1) else emptyList(),
        explanationSteps = explanationSteps(question.explanation)
    )
}

@Composable
private fun FlameProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "flame")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "flame-scale"
    )

    BoxWithConstraints(modifier = modifier.height(32.dp)) {
        val flameSize = 30.dp
        val barHeight = 14.dp
        val p = progress.coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(7.dp))
                .background(KalynoBlueSoft)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(p)
                .height(barHeight)
                .clip(RoundedCornerShape(7.dp))
                .background(KalynoYellow)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth - flameSize) * p)
                .size(flameSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = "flame", size = 22.dp)
        }
    }
}
