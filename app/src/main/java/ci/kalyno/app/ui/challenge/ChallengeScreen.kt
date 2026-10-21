package ci.kalyno.app.ui.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Challenge
import ci.kalyno.app.data.ChallengeRepository
import ci.kalyno.app.data.Feedback
import ci.kalyno.app.ui.theme.*

@Composable
fun ChallengeScreen(levelId: String, repository: ChallengeRepository, onExit: () -> Unit) {
    var challenge by remember { mutableStateOf<Challenge?>(null) }
    LaunchedEffect(levelId) { challenge = repository.firstChallenge(levelId) }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        val loaded = challenge
        if (loaded == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = KalynoBlue)
        } else {
            ChallengeContent(loaded, onExit)
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
        ResultContent(score = score, total = total, onExit = onExit)
        return
    }

    val question = challenge.questions[index]
    val answered = selected != null
    val wasCorrect = selected == question.correctIndex
    val feedback = remember(index, selected) {
        selected?.let { if (it == question.correctIndex) Feedback.success() else Feedback.retry() }
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
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
                color = KalynoYellow,
                trackColor = KalynoBlueSoft
            )
            Spacer(Modifier.width(12.dp))
            Text("${done}/$total", color = KalynoMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(2.dp, KalynoOutline)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "QUESTION ${index + 1}",
                        color = KalynoBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = question.prompt,
                        color = KalynoText,
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
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
                        onClick = {
                            if (selected == null) {
                                selected = i
                                if (i == question.correctIndex) score += 1
                            }
                        }
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
                        text = feedback ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = if (wasCorrect) KalynoCorrectText else KalynoRetryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        selected = null
                        index += 1
                    },
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
private fun ResultContent(score: Int, total: Int, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎉", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text("Défi terminé !", color = KalynoText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("$score / $total", color = KalynoBlue, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
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
