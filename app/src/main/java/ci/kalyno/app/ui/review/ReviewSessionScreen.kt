package ci.kalyno.app.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.data.dashboard.ReviewSession
import ci.kalyno.app.data.srs.ReviewRating
import ci.kalyno.app.ui.theme.*

@Composable
fun ReviewSessionScreen(
    session: ReviewSession,
    onRate: (cardId: String, rating: ReviewRating) -> Unit,
    onClose: (completed: Boolean) -> Unit
) {
    var index by rememberSaveable(session.id) { mutableStateOf(0) }
    val total = session.items.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (index >= total) {
            Finished(total = total, onDone = { onClose(true) })
        } else {
            val item = session.items[index]

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onClose(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = KalynoMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { index.toFloat() / total },
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = KalynoBlue,
                    trackColor = KalynoBlueSoft
                )
                Spacer(Modifier.width(12.dp))
                Text("${index + 1}/$total", color = KalynoMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            CardStep(
                item = item,
                title = session.title,
                onRate = { rating ->
                    onRate(item.cardId, rating)
                    index += 1
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.CardStep(
    item: ReviewItem,
    title: String?,
    onRate: (ReviewRating) -> Unit
) {
    var revealed by rememberSaveable(item.cardId) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(2.dp, KalynoOutline)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = listOfNotNull(title, item.subjectName.takeIf { it.isNotBlank() }, item.chapterTitle.takeIf { it.isNotBlank() })
                        .distinct()
                        .joinToString(" · "),
                    color = KalynoBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = item.front,
                    color = KalynoText,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (item.choices.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    item.choices.forEachIndexed { i, choice ->
                        val good = revealed && item.correctIndex == i
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (good) KalynoCorrectSoft else KalynoBackground
                        ) {
                            Text(
                                text = "${('A'.code + i).toChar()}. $choice",
                                color = KalynoText,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        if (revealed) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = KalynoCorrectSoft,
                border = BorderStroke(2.dp, KalynoCorrect)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("BONNE RÉPONSE", color = KalynoCorrectText, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(item.answer, color = KalynoCorrectText, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
                    if (!item.explanation.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(item.explanation, color = KalynoText, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    if (!revealed) {
        Button(
            onClick = { revealed = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KalynoBlue, contentColor = Color.White)
        ) {
            Text("Voir la réponse", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Text(
            "Comment c'était ?",
            color = KalynoMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RatingButton("J'ai oublié", KalynoRetry, KalynoText, Modifier.weight(1f)) { onRate(ReviewRating.OUBLIE) }
            RatingButton("J'ai hésité", KalynoBlueSoft, KalynoBlue, Modifier.weight(1f)) { onRate(ReviewRating.HESITE) }
            RatingButton("Trop facile", KalynoBlue, Color.White, Modifier.weight(1f)) { onRate(ReviewRating.TROP_FACILE) }
        }
    }
}

@Composable
private fun RatingButton(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ColumnScope.Finished(total: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎉", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("Session terminée !", color = KalynoText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (total == 1) "1 carte révisée" else "$total cartes révisées",
            color = KalynoMuted,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KalynoBlue, contentColor = Color.White)
        ) {
            Text("Retour à l'accueil", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
