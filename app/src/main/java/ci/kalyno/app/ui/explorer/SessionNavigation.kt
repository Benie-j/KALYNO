package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.LearningSession
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.icons.iconAssetFor
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoText

/**
 * Liste des séances PUBLIÉES d'une leçon. Chaque séance s'ouvre sur son propre écran (jamais empilées),
 * avec son état « terminée » indépendant des autres.
 */
@Composable
fun LessonSessionListScreen(
    chapter: ExplorerChapter,
    sessions: List<LearningSession>,
    completedIds: Set<String>,
    /** Nombre d'exercices déjà débloqués dans cette leçon (0 = bouton d'entraînement masqué). */
    unlockedCount: Int,
    onOpenSession: (String) -> Unit,
    onStartQuestions: () -> Unit,
    onBack: () -> Unit
) {
    val doneCount = sessions.count { it.id in completedIds }
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExplorerBackButton(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            KalynoSvgIcon(name = iconAssetFor(chapter.subjectIconKey), size = 18.dp, tint = KalynoBlue)
            Spacer(Modifier.width(8.dp))
            Text(
                text = chapter.subjectName,
                color = KalynoBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "title") {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = chapter.title,
                        color = KalynoText,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "$doneCount / ${sessions.size} séance(s) terminée(s)",
                        color = KalynoMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            itemsIndexed(sessions, key = { _, s -> s.id }) { index, session ->
                SessionListCard(
                    number = index + 1,
                    session = session,
                    blockCount = chapter.capsules.count { it.sessionId == session.id },
                    completed = session.id in completedIds,
                    onClick = { onOpenSession(session.id) }
                )
            }
            if (unlockedCount > 0) {
                item(key = "train") {
                    KalynoPrimaryButton(label = "S'ENTRAÎNER · $unlockedCount EXERCICES DÉBLOQUÉS", onClick = onStartQuestions)
                }
            }
        }
    }
}

@Composable
private fun SessionListCard(
    number: Int,
    session: LearningSession,
    blockCount: Int,
    completed: Boolean,
    onClick: () -> Unit
) {
    KalynoCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(if (completed) KalynoCorrectSoft else KalynoBlue),
                contentAlignment = Alignment.Center
            ) {
                if (completed) {
                    KalynoSvgIcon(name = "check-circle", size = 20.dp, tint = KalynoCorrectText)
                } else {
                    Text(text = number.toString(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SÉANCE $number" + if (completed) " · TERMINÉE" else "",
                    color = if (completed) KalynoCorrectText else KalynoBlue,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (session.title.isNotBlank()) {
                    Text(
                        text = session.title,
                        color = KalynoText,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(text = "$blockCount bloc(s)", color = KalynoMuted, fontSize = 12.sp)
            }
            KalynoSvgIcon(name = "chevron-right", size = 18.dp, tint = KalynoMuted)
        }
    }
}
