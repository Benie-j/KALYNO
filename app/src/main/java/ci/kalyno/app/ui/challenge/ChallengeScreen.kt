package ci.kalyno.app.ui.challenge

import ci.kalyno.app.ui.icons.KalynoSvgIcon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Challenge
import ci.kalyno.app.data.ChallengeRepository
import ci.kalyno.app.data.ChallengeResult
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.Feedback
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.Question
import ci.kalyno.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// Temps entre l'affichage du message et le retournement de la carte
private const val FLIP_DELAY_MS = 1100L
private const val FLIP_DURATION_MS = 600

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
    var score by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }

    if (index >= total) {
        val context = LocalContext.current
        val streak = remember { StreakStore(context.applicationContext).recordCompletion() }
        ResultContent(score = score, total = total, streak = streak, onExit = onExit)
    } else {
        QuestionContent(
            question = challenge.questions[index],
            index = index,
            total = total,
            selected = selected,
            onSelect = { choice ->
                if (selected == null) {
                    selected = choice
                    if (choice == challenge.questions[index].correctIndex) score += 1
                }
            },
            onNext = {
                selected = null
                index += 1
            },
            onExit = onExit
        )
    }
}

@Composable
private fun QuestionContent(
    question: Question,
    index: Int,
    total: Int,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    val answered = selected != null
    val wasCorrect = selected == question.correctIndex
    val feedback = remember(index, selected) {
        selected?.let { if (it == question.correctIndex) Feedback.success() else Feedback.retry() }
    }

    // 1) le message apparaît, 2) la carte se retourne, 3) le bouton Suivant apparaît
    var flipped by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index, selected) {
        if (selected != null) {
            delay(FLIP_DELAY_MS)
            flipped = true
        }
    }

    val done = index + if (answered) 1 else 0
    val progress by animateFloatAsState(
        targetValue = done.toFloat() / maxOf(total, 1),
        animationSpec = tween(400),
        label = "progress"
    )

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onExit),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = KalynoMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                .padding(top = 20.dp)
        ) {
            key(index) {
                FlipCard(flipped = flipped, question = question, index = index)
            }

            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.choices.forEachIndexed { i, text ->
                    OptionRow(
                        letter = ('A' + i).toString(),
                        text = text,
                        isCorrect = i == question.correctIndex,
                        isSelected = selected == i,
                        answered = answered,
                        onClick = { onSelect(i) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        AnimatedVisibility(visible = feedback != null) {
            Column {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (wasCorrect) KalynoCorrectSoft else KalynoRetrySoft
                ) {
                    Text(
                        text = (if (wasCorrect) "Correct : " else "À revoir : ") + (feedback ?: ""),
                        modifier = Modifier.padding(16.dp),
                        color = if (wasCorrect) KalynoCorrectText else KalynoRetryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                AnimatedVisibility(visible = flipped) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onNext,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KalynoBlue)
                        ) {
                            Text(
                                text = if (index == total - 1) "Voir mon résultat" else "Suivant",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
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

@Composable
private fun FlipCard(flipped: Boolean, question: Question, index: Int) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(FLIP_DURATION_MS),
        label = "flip"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
    ) {
        if (rotation <= 90f) {
            CardFace(
                label = "QUESTION ${index + 1}",
                labelColor = KalynoBlue,
                background = Color.White,
                stroke = KalynoOutline
            ) {
                Text(
                    text = question.prompt,
                    color = KalynoText,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        } else {
            // Verso : on le re-retourne pour qu'il ne soit pas affiché en miroir
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                CardFace(
                    label = "BONNE RÉPONSE",
                    labelColor = KalynoCorrectText,
                    background = KalynoCorrectSoft,
                    stroke = KalynoCorrect
                ) {
                    Text(
                        text = question.choices[question.correctIndex],
                        color = KalynoCorrectText,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    question.explanation?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(text = it, color = KalynoText, fontSize = 16.sp, lineHeight = 23.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFace(
    label: String,
    labelColor: Color,
    background: Color,
    stroke: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp),
        shape = RoundedCornerShape(24.dp),
        color = background,
        border = BorderStroke(2.dp, stroke)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun OptionRow(
    letter: String,
    text: String,
    isCorrect: Boolean,
    isSelected: Boolean,
    answered: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val showCorrect = answered && isCorrect
    val showWrong = answered && isSelected && !isCorrect
    val background = when {
        showCorrect -> KalynoCorrectSoft
        showWrong -> KalynoRetrySoft
        else -> Color.White
    }
    val stroke = when {
        showCorrect -> KalynoCorrect
        showWrong -> KalynoRetry
        else -> KalynoOutline
    }
    val badge = when {
        showCorrect -> KalynoCorrect
        showWrong -> KalynoRetry
        else -> KalynoBlueSoft
    }
    val badgeText = if (showCorrect || showWrong) Color.White else KalynoBlue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (answered && !showCorrect && !showWrong) 0.55f else 1f)
            .clip(shape)
            .background(background)
            .border(2.dp, stroke, shape)
            .clickable(enabled = !answered, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(badge),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, color = badgeText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            color = KalynoText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
        if (showCorrect) {
            Text("✓", color = KalynoCorrect, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ResultContent(score: Int, total: Int, streak: Int, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        KalynoSvgIcon(name = resultIcon(score, total), size = 64.dp)
        Spacer(Modifier.height(12.dp))
        Text("Défi terminé !", color = KalynoText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("$score / $total", color = KalynoBlue, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = KalynoYellow.copy(alpha = 0.3f)
        ) {
            Text(
                text = if (streak <= 1) "Série lancée ! Reviens demain pour la garder." else "Série : $streak jours de suite !",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = KalynoText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Continue comme ça, on est ensemble !",
            color = KalynoMuted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KalynoBlue)
        ) {
            Text("Retour à l'accueil", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun resultIcon(score: Int, total: Int): String = when {
    total > 0 && score * 10 >= total * 8 -> "trophy"
    total > 0 && score * 2 >= total -> "party-popper"
    else -> "dumbbell"
}
