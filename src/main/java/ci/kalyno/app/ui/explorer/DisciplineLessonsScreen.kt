package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoEmptyState
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoText

/**
 * Page "Leçons de la discipline" : s'ouvre au clic sur une mini-carte Discipline d'Explorer.
 * Chaque leçon = un chapitre publié par l'admin pour cette matière.
 */
@Composable
fun DisciplineLessonsScreen(
    discipline: Discipline,
    lessons: List<ExplorerChapter>,
    onBack: () -> Unit,
    onOpenLesson: (ExplorerChapter) -> Unit
) {
    val revealed = remember(discipline.id) { HashSet<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 28.dp)
    ) {
        ExplorerBackButton(onClick = onBack)
        Spacer(Modifier.height(18.dp))

        Text(
            text = "${discipline.emoji} ${discipline.name}",
            color = KalynoText,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (lessons.size) {
                0 -> "Aucune leçon publiée"
                1 -> "1 leçon"
                else -> "${lessons.size} leçons"
            },
            color = KalynoMuted,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(20.dp))

        if (lessons.isEmpty()) {
            KalynoEmptyState(
                emoji = "📭",
                title = "Pas encore de leçon",
                text = "Les leçons de cette discipline apparaîtront ici dès leur publication."
            )
        }

        lessons.forEachIndexed { index, lesson ->
            RevealOnAppear(id = lesson.id, index = index, revealed = revealed, bottomSpace = 14.dp) {
                LessonRowCard(
                    number = index + 1,
                    lesson = lesson,
                    onClick = { onOpenLesson(lesson) }
                )
            }
        }
    }
}

@Composable
private fun LessonRowCard(number: Int, lesson: ExplorerChapter, onClick: () -> Unit) {
    KalynoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KalynoBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    color = KalynoBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.title,
                    color = KalynoText,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val meta = buildList {
                    if (lesson.minutes > 0) add("⏱️ ${lesson.minutes} min")
                    if (lesson.capsules.isNotEmpty()) add("${lesson.capsules.size} blocs")
                    if (lesson.questionCount > 0) add("${lesson.questionCount} questions")
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" · "),
                        color = KalynoMuted,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(text = "›", color = KalynoMuted, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

        if (lesson.questionCount > 0) {
            Spacer(Modifier.height(12.dp))
            KalynoProgressBar(progress = lesson.progress)
        }
    }
}
