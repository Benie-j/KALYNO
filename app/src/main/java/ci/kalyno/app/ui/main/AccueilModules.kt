package ci.kalyno.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import ci.kalyno.app.data.dashboard.DailyQuote
import ci.kalyno.app.data.dashboard.LastLessonUi
import ci.kalyno.app.data.dashboard.UrgentTopic
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoRetry
import ci.kalyno.app.ui.theme.KalynoRetrySoft
import ci.kalyno.app.ui.theme.KalynoRetryText
import ci.kalyno.app.ui.theme.KalynoText

/** En-tête commun des modules : pastille d'icône vectorielle + titre. */
@Composable
internal fun ModuleHeader(
    icon: String,
    title: String,
    tint: Color = KalynoBlue,
    container: Color = KalynoBlueSoft
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = icon, size = 20.dp, tint = tint)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = KalynoMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun SkeletonLines() {
    ShimmerBlock(Modifier.fillMaxWidth().height(14.dp))
    Spacer(Modifier.height(8.dp))
    ShimmerBlock(Modifier.fillMaxWidth(0.8f).height(14.dp))
    Spacer(Modifier.height(8.dp))
    ShimmerBlock(Modifier.fillMaxWidth(0.55f).height(14.dp))
}

// ---------- Bloc 2 : alerte de révision (moteur FSRS, ouvre le Dojo) ----------

@Composable
internal fun UrgentReviewModule(
    total: Int,
    topics: List<UrgentTopic>,
    loading: Boolean,
    onReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val urgent = total > 0
    KalynoCard(
        modifier = modifier,
        onClick = if (urgent) onReview else null,
        background = if (urgent) KalynoRetrySoft else Color.White,
        borderColor = if (urgent) KalynoRetry.copy(alpha = 0.45f) else KalynoOutline
    ) {
        ModuleHeader(
            icon = "brain",
            title = "Révisions du Dojo",
            tint = if (urgent) KalynoRetryText else KalynoBlue,
            container = if (urgent) Color.White else KalynoBlueSoft
        )
        Spacer(Modifier.height(14.dp))
        when {
            loading && total == 0 -> SkeletonLines()
            !urgent -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KalynoSvgIcon(name = "check-circle", size = 22.dp, tint = KalynoCorrectText)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Tout est à jour",
                        color = KalynoCorrectText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Aucune notion à réviser pour l'instant.",
                    color = KalynoMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            else -> {
                Text(
                    text = total.toString(),
                    color = KalynoRetryText,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (total == 1) "notion à réviser" else "notions à réviser",
                    color = KalynoRetryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                topics.take(2).forEach { topic ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = topic.label,
                            color = KalynoText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = topic.count.toString(),
                            color = KalynoRetryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Spacer(Modifier.weight(1f))
                KalynoPrimaryButton(
                    label = "Réviser",
                    onClick = onReview,
                    height = 44.dp,
                    container = KalynoRetry,
                    content = KalynoText
                )
            }
        }
    }
}

// ---------- Bloc 3 : reprendre la dernière leçon ----------

@Composable
internal fun ResumeModule(lesson: LastLessonUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val progress = lesson.progress.coerceIn(0f, 1f)
    KalynoCard(modifier = modifier, onClick = onClick) {
        ModuleHeader(icon = "rocket", title = "Reprendre l'aventure")
        Spacer(Modifier.height(14.dp))
        if (lesson.subjectName.isNotBlank()) {
            Text(
                text = lesson.subjectName,
                color = KalynoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = lesson.chapterTitle,
            color = KalynoText,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KalynoProgressBar(progress = progress, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${(progress * 100).toInt()} %",
                color = KalynoMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------- Bloc 4 : indicateurs de progression ----------

@Composable
internal fun ProgressModule(
    masteredCount: Int?,
    retentionScore: Float?,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    KalynoCard(modifier = modifier) {
        ModuleHeader(icon = "target", title = "Ma progression")
        Spacer(Modifier.height(14.dp))
        if (masteredCount == null && retentionScore == null) {
            if (loading) {
                SkeletonLines()
            } else {
                Text(
                    text = "Tes statistiques apparaîtront après tes premières révisions.",
                    color = KalynoMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            if (masteredCount != null) {
                StatValue(
                    value = masteredCount.toString(),
                    label = if (masteredCount == 1) "notion maîtrisée" else "notions maîtrisées"
                )
            }
            if (masteredCount != null && retentionScore != null) {
                Spacer(Modifier.height(14.dp))
            }
            if (retentionScore != null) {
                val score = retentionScore.coerceIn(0f, 1f)
                StatValue(value = "${(score * 100).toInt()} %", label = "rétention estimée")
                Spacer(Modifier.height(6.dp))
                KalynoProgressBar(progress = score, height = 6.dp)
            }
        }
    }
}

@Composable
private fun StatValue(value: String, label: String) {
    Column {
        Text(text = value, color = KalynoText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = label, color = KalynoMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
