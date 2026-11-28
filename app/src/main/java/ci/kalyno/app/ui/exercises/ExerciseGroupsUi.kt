package ci.kalyno.app.ui.exercises

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.entrance
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.icons.iconAssetFor
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrect
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoText
import org.json.JSONObject

// ===========================================================================
// Liste des exercices rangés : Discipline > Leçon > Séance (si disponible)
// Palette inchangée. Les disciplines se replient d'un toucher.
// ===========================================================================

private fun countLabel(count: Int, one: String, many: String): String =
    if (count == 1) "1 $one" else "$count $many"

@Composable
internal fun GroupedExerciseList(
    groups: List<ExerciseSubjectGroup>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    numberOf: (JSONObject) -> Int,
    isCompleted: (JSONObject) -> Boolean,
    onOpen: (JSONObject) -> Unit,
    modifier: Modifier = Modifier
) {
    // Tout est plié par défaut : aucune carte n'est construite tant que la discipline n'est pas dépliée.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        groups.forEachIndexed { index, subject ->
            val isExpanded = subject.key in expanded

            Column(modifier = Modifier.entrance(index + 3, stepMillis = 40)) {
                SubjectHeader(
                    subject = subject,
                    expanded = isExpanded,
                    onToggle = { onToggle(subject.key) }
                )
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(tween(180)) + fadeIn(tween(140)),
                    exit = shrinkVertically(tween(140)) + fadeOut(tween(90))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        subject.lessons.forEach { lesson ->
                            LessonBlock(
                                lesson = lesson,
                                numberOf = numberOf,
                                isCompleted = isCompleted,
                                onOpen = onOpen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectHeader(
    subject: ExerciseSubjectGroup,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val haptic = rememberHaptic()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(240),
        label = "subjectChevron"
    )
    val progress = if (subject.total == 0) 0f else subject.done.toFloat() / subject.total
    val allDone = subject.total > 0 && subject.done == subject.total

    KalynoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            haptic()
            onToggle()
        },
        shape = RoundedCornerShape(22.dp),
        contentPadding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (allDone) KalynoCorrect.copy(alpha = 0.14f) else KalynoBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(
                    name = if (allDone) "check-circle" else iconAssetFor(subject.iconKey),
                    size = 22.dp,
                    tint = if (allDone) KalynoCorrect else KalynoBlue
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.name,
                    color = KalynoText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${countLabel(subject.total, "exercice", "exercices")} · ${subject.done} terminé" +
                        if (subject.done > 1) "s" else "",
                    color = KalynoMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                KalynoProgressBar(
                    progress = progress,
                    height = 5.dp,
                    color = if (allDone) KalynoCorrect else KalynoBlue,
                    track = KalynoBlueSoft
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.graphicsLayer { rotationZ = rotation }) {
                KalynoSvgIcon(name = "chevron-down", size = 20.dp, tint = KalynoBlue)
            }
        }
    }
}

@Composable
private fun LessonBlock(
    lesson: ExerciseLessonGroup,
    numberOf: (JSONObject) -> Int,
    isCompleted: (JSONObject) -> Boolean,
    onOpen: (JSONObject) -> Unit
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        // Fil vertical qui rattache les exercices à leur leçon
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .width(3.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(KalynoBlueSoft)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (lesson.title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KalynoSvgIcon(name = "book-open", size = 15.dp, tint = KalynoBlue)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = lesson.title,
                        color = KalynoText,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    KalynoPill(text = "${lesson.done}/${lesson.total}", fontSize = 11)
                }
            }

            lesson.sessions.forEach { session ->
                if (session.label != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(KalynoBlueSoft)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        KalynoSvgIcon(name = "layers", size = 13.dp, tint = KalynoBlue)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = session.label,
                            color = KalynoBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                session.items.forEach { exercise ->
                    ExerciseCard(
                        number = numberOf(exercise),
                        exercise = exercise,
                        done = isCompleted(exercise),
                        onClick = { onOpen(exercise) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
