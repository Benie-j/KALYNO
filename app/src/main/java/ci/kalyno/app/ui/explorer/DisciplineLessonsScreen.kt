package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText

private val NODE_SIZE = 40.dp
private val NODE_TOP = 14.dp

/** Une leçon est terminée quand toutes ses cartes ont été découvertes. */
private fun ExplorerChapter.isDone(): Boolean = questionCount > 0 && discovered >= questionCount

/**
 * Page "Leçons de la discipline" : s'ouvre au clic sur une mini-carte Discipline d'Explorer.
 * Chaque leçon = un chapitre publié par l'admin, présenté comme une étape d'un parcours
 * (roadmap) : étapes terminées, étape en cours, étapes à venir.
 */
@Composable
fun DisciplineLessonsScreen(
    discipline: Discipline,
    lessons: List<ExplorerChapter>,
    onBack: () -> Unit,
    onOpenLesson: (ExplorerChapter) -> Unit
) {
    val revealed = remember(discipline.id) { HashSet<String>() }
    // Étape en cours = première leçon pas encore terminée (-1 si tout est terminé)
    val currentIndex = remember(lessons) { lessons.indexOfFirst { !it.isDone() } }

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
                emoji = "🌱",
                title = "Bientôt du nouveau !",
                text = "De nouveaux parcours préparés par tes professeurs arrivent bientôt..."
            )
        }

        lessons.forEachIndexed { index, lesson ->
            RevealOnAppear(id = lesson.id, index = index, revealed = revealed, bottomSpace = 0.dp) {
                RoadmapStep(
                    number = index + 1,
                    lesson = lesson,
                    isCurrent = index == currentIndex,
                    isFirst = index == 0,
                    isLast = index == lessons.lastIndex,
                    aboveDone = index > 0 && lessons[index - 1].isDone(),
                    belowDone = lesson.isDone(),
                    onClick = { onOpenLesson(lesson) }
                )
            }
        }
    }
}

/** Une étape du parcours : rail vertical (pastille + trait) à gauche, carte de la leçon à droite. */
@Composable
private fun RoadmapStep(
    number: Int,
    lesson: ExplorerChapter,
    isCurrent: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    aboveDone: Boolean,
    belowDone: Boolean,
    onClick: () -> Unit
) {
    val done = lesson.isDone()

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // ----- Rail : traits colorés selon l'avancement + pastille numérotée -----
        Box(
            modifier = Modifier
                .width(NODE_SIZE)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2f
                    val center = (NODE_TOP + NODE_SIZE / 2).toPx()
                    val stroke = 3.dp.toPx()
                    if (!isFirst) {
                        drawLine(
                            color = if (aboveDone) KalynoBlue else KalynoOutline,
                            start = Offset(x, 0f),
                            end = Offset(x, center),
                            strokeWidth = stroke
                        )
                    }
                    if (!isLast) {
                        drawLine(
                            color = if (belowDone) KalynoBlue else KalynoOutline,
                            start = Offset(x, center),
                            end = Offset(x, size.height),
                            strokeWidth = stroke
                        )
                    }
                }
        ) {
            val nodeModifier = Modifier
                .padding(top = NODE_TOP)
                .size(NODE_SIZE)
                .align(Alignment.TopCenter)
                .clip(CircleShape)
            when {
                done -> Box(
                    modifier = nodeModifier.background(KalynoBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }

                isCurrent -> Box(
                    modifier = nodeModifier
                        .background(Color.White)
                        .border(3.dp, KalynoBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number.toString(),
                        color = KalynoBlue,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                else -> Box(
                    modifier = nodeModifier.background(KalynoBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number.toString(),
                        color = KalynoMuted,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // ----- Carte de la leçon -----
        Box(modifier = Modifier.weight(1f).padding(bottom = 14.dp)) {
            LessonStepCard(lesson = lesson, onClick = onClick)
        }
    }
}

@Composable
private fun LessonStepCard(lesson: ExplorerChapter, onClick: () -> Unit) {
    KalynoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (!lesson.tag.isNullOrBlank()) {
                    SmartTagPill(lesson.tag)
                    Spacer(Modifier.height(8.dp))
                }
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
