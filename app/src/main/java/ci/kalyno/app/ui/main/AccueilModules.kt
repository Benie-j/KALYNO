package ci.kalyno.app.ui.main

import ci.kalyno.app.ui.theme.KalynoSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.dashboard.LastLessonUi
import ci.kalyno.app.data.dashboard.MasteredNotion
import ci.kalyno.app.data.dashboard.MasteryTier
import ci.kalyno.app.data.dashboard.UrgentTopic
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.KalynoRing
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.components.rememberCountUp
import ci.kalyno.app.ui.components.rememberPulse
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
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
    container: Color = KalynoBlueSoft,
    animateIcon: Boolean = false
) {
    // L'icône flotte doucement quand le module réclame l'attention de l'élève
    val bob = rememberPulse(min = 0f, max = 1f, durationMillis = 1100)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .graphicsLayer { translationY = if (animateIcon) -bob * 3.dp.toPx() else 0f }
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

// ---------- Carte Révisions : ce qui t'attend au Dojo, matière par matière ----------

@Composable
internal fun UrgentReviewModule(
    total: Int,
    topics: List<UrgentTopic>,
    loading: Boolean,
    onReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val urgent = total > 0
    val shownTotal = rememberCountUp(total, durationMillis = 900)
    // Le contour respire doucement tant que des révisions attendent
    val glow = rememberPulse(min = 0f, max = 1f, durationMillis = 1400)
    KalynoCard(
        modifier = modifier,
        onClick = if (urgent) onReview else null,
        background = if (urgent) KalynoRetrySoft else KalynoSurface,
        borderColor = if (urgent) KalynoRetry.copy(alpha = 0.3f + 0.3f * glow) else KalynoOutline
    ) {
        ModuleHeader(
            icon = "brain",
            title = "Révisions du Dojo",
            tint = if (urgent) KalynoRetryText else KalynoBlue,
            container = if (urgent) Color.White else KalynoBlueSoft,
            animateIcon = urgent
        )
        Spacer(Modifier.height(14.dp))
        when {
            loading && total == 0 -> SkeletonLines()
            !urgent -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(KalynoCorrectSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        KalynoSvgIcon(name = "check-circle", size = 22.dp, tint = KalynoCorrectText)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tout est à jour",
                            color = KalynoCorrectText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Aucune notion à réviser pour l'instant.",
                            color = KalynoMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = shownTotal.toString(),
                        color = KalynoRetryText,
                        fontSize = 44.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (total == 1) "notion à réviser" else "notions à réviser",
                        color = KalynoRetryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }
                if (topics.isNotEmpty()) {
                    val biggest = topics.maxOf { it.count }.coerceAtLeast(1)
                    Spacer(Modifier.height(12.dp))
                    topics.forEachIndexed { index, topic ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        TopicBar(topic = topic, biggest = biggest)
                    }
                }
                Spacer(Modifier.height(16.dp))
                KalynoPrimaryButton(
                    label = "Réviser maintenant",
                    onClick = onReview,
                    height = 48.dp,
                    container = KalynoRetry,
                    content = KalynoText
                )
            }
        }
    }
}

/** Une matière en attente : libellé, nombre de notions, barre proportionnelle à la plus chargée. */
@Composable
private fun TopicBar(topic: UrgentTopic, biggest: Int) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = topic.label,
                color = KalynoText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
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
        Spacer(Modifier.height(4.dp))
        KalynoProgressBar(
            progress = topic.count.toFloat() / biggest.toFloat(),
            height = 5.dp,
            color = KalynoRetry,
            track = KalynoSurface
        )
    }
}

// ---------- Carte Progression : maîtrise, paliers et rétention (moteur de révision) ----------

@Composable
internal fun ProgressModule(
    masteredCount: Int?,
    retentionScore: Float?,
    masteredNotions: List<MasteredNotion> = emptyList(),
    loading: Boolean,
    modifier: Modifier = Modifier,
    onOpenDetail: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val hasData = masteredCount != null || retentionScore != null
    val tiers = masteredNotions.groupingBy { it.tier }.eachCount()

    KalynoCard(
        modifier = modifier,
        // La carte ouvre toujours la page « Ma progression » (historique complet), même sans statistiques
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onOpenDetail()
        }
    ) {
        ModuleHeader(icon = "target", title = "Ma progression")
        Spacer(Modifier.height(14.dp))
        if (!hasData) {
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
                val shownMastered = rememberCountUp(masteredCount, durationMillis = 900)
                Text(
                    text = shownMastered.toString(),
                    color = KalynoText,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (masteredCount == 1) "notion maîtrisée" else "notions maîtrisées",
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (tiers.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TierCount("check-circle", tiers[MasteryTier.SOLIDE] ?: 0, KalynoCorrectText, KalynoCorrectSoft)
                        TierCount("target", tiers[MasteryTier.AVANCEE] ?: 0, KalynoBlue, KalynoBlueSoft)
                        TierCount("trophy", tiers[MasteryTier.EXPERTE] ?: 0, KalynoAmberDeep, KalynoAmberSoft)
                    }
                }
            }
            if (masteredCount != null && retentionScore != null) {
                Spacer(Modifier.height(14.dp))
            }
            if (retentionScore != null) {
                val score = retentionScore.coerceIn(0f, 1f)
                val shownPercent = rememberCountUp((score * 100).toInt(), durationMillis = 1100)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KalynoRing(progress = score, diameter = 58.dp, strokeWidth = 7.dp) {
                        Text(
                            text = "$shownPercent%",
                            color = KalynoText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "rétention estimée",
                        color = KalynoMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Voir le détail",
                    color = KalynoBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(4.dp))
                KalynoSvgIcon(name = "chevron-right", size = 14.dp, tint = KalynoBlue)
            }
        }
    }
}

/** Nombre de notions d'un palier de maîtrise (solide, avancée, experte) : icône + compte. */
@Composable
private fun TierCount(icon: String, count: Int, tint: Color, soft: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(soft),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = icon, size = 13.dp, tint = tint)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = count.toString(),
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
