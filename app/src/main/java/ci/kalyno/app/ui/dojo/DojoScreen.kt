package ci.kalyno.app.ui.dojo

import ci.kalyno.app.ui.theme.KalynoSurface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.dashboard.DashboardUiState
import ci.kalyno.app.data.dashboard.MissionStatus
import ci.kalyno.app.ui.components.DriftingBlob
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoErrorCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoRing
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.components.StreakBadge
import ci.kalyno.app.ui.components.entrance
import ci.kalyno.app.ui.components.rememberCountUp
import ci.kalyno.app.ui.components.rememberPulse
import ci.kalyno.app.ui.components.shineSweep
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrect
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow
import kotlin.math.roundToInt

/**
 * Onglet Dojo : point d'entrée de la répétition espacée.
 * Les données viennent du [DashboardUiState] (moteur SRS + contenu en ligne, cache Room en secours).
 * La session de cartes est [DojoSessionScreen].
 *
 * Tout ce qui est affiché est réel : aucune statistique inventée (une valeur inconnue s'affiche « — »).
 */
@Composable
fun DojoScreen(
    levelId: String,
    state: DashboardUiState,
    onStartMission: () -> Unit,
    onReviewUrgent: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DojoHeader(
            levelLabel = Levels.label(levelId),
            streak = state.streak,
            modifier = Modifier.entrance(0)
        )

        when {
            state.loading && state.mission == null && state.urgentTotal == 0 -> DojoSkeleton()

            state.error != null && state.mission == null && state.urgentTotal == 0 -> KalynoErrorCard(
                message = state.error ?: "",
                onRetry = onRetry
            )

            else -> {
                Box(Modifier.entrance(1)) { DojoHero(state = state, onReview = onReviewUrgent) }
                Box(Modifier.entrance(2)) {
                    DojoStats(
                        streak = state.streak,
                        mastered = state.masteredCount,
                        retention = state.retentionScore
                    )
                }
                Box(Modifier.entrance(3)) {
                    MissionBlock(state = state, onStart = onStartMission, onReview = onReviewUrgent)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// En-tête
// ---------------------------------------------------------------------------

@Composable
private fun DojoHeader(levelLabel: String, streak: Int, modifier: Modifier = Modifier) {
    // Le cerveau respire doucement : le Dojo est « vivant »
    val breath = rememberPulse(min = 1f, max = 1.08f, durationMillis = 1500)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                }
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark))),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = "brain", size = 26.dp, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Le Dojo",
                color = KalynoText,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Répétition espacée · $levelLabel",
                color = KalynoMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        StreakBadge(count = streak)
    }
}

// ---------------------------------------------------------------------------
// Héros : les notions à sauver (ou la mémoire au beau fixe)
// ---------------------------------------------------------------------------

@Composable
private fun DojoHero(state: DashboardUiState, onReview: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val urgent = state.urgentTotal > 0
    val shownUrgent = rememberCountUp(state.urgentTotal, durationMillis = 1000)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
    ) {
        DriftingBlob(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-56).dp)
                .size(190.dp)
        )
        DriftingBlob(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 46.dp)
                .size(130.dp),
            color = Color.White.copy(alpha = 0.06f),
            durationMillis = 6400
        )

        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KalynoPill(
                    text = "ACTIVE RECALL",
                    container = Color.White.copy(alpha = 0.18f),
                    content = Color.White,
                    fontSize = 11
                )
                if (urgent) {
                    Spacer(Modifier.width(8.dp))
                    UrgentDot()
                }
            }
            Spacer(Modifier.height(16.dp))

            if (urgent) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = shownUrgent.toString(),
                        color = Color.White,
                        fontSize = 64.sp,
                        lineHeight = 64.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = if (state.urgentTotal == 1) "notion" else "notions",
                            color = Color.White,
                            fontSize = 22.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "à sauver",
                            color = KalynoAmberSoft,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Révise-les maintenant : chaque rappel actif ancre la notion plus longtemps.",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                val topics = state.urgentTopics.take(4)
                if (topics.isNotEmpty()) {
                    val maxCount = topics.maxOf { it.count }.coerceAtLeast(1)
                    Spacer(Modifier.height(12.dp))
                    topics.forEachIndexed { index, topic ->
                        TopicRow(
                            label = topic.label,
                            count = topic.count,
                            fraction = topic.count.toFloat() / maxCount,
                            index = index
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                KalynoPrimaryButton(
                    label = "RÉVISER MAINTENANT",
                    onClick = onReview,
                    container = KalynoSurface,
                    content = KalynoBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .shineSweep(color = KalynoBlue.copy(alpha = 0.16f))
                )
            } else {
                CalmHero(loading = state.loading, retention = state.retentionScore)
            }
        }
    }
}

/** Petit point ambré qui pulse : « ça demande ton attention », sans agresser. */
@Composable
private fun UrgentDot() {
    val pulse = rememberPulse(min = 0.6f, max = 1.25f, durationMillis = 800)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .size(8.dp)
                .clip(CircleShape)
                .background(KalynoYellow)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "URGENT",
            color = KalynoAmberSoft,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
private fun TopicRow(label: String, count: Int, fraction: Float, index: Int) {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        fill.animateTo(
            targetValue = fraction.coerceIn(0f, 1f),
            animationSpec = tween(800, delayMillis = 300 + index * 90, easing = FastOutSlowInEasing)
        )
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "×$count",
                color = KalynoAmberSoft,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill.value)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(KalynoAmberSoft)
            )
        }
    }
}

/** Rien d'urgent : l'anneau montre la rétention réelle (ou une coche tant qu'elle n'est pas calculée). */
@Composable
private fun CalmHero(loading: Boolean, retention: Float?) {
    val percent = retention?.let { (it * 100).roundToInt().coerceIn(0, 100) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        KalynoRing(
            progress = retention ?: 1f,
            diameter = 96.dp,
            strokeWidth = 10.dp,
            color = Color.White,
            track = Color.White.copy(alpha = 0.22f)
        ) {
            if (percent != null) {
                Text(
                    text = "$percent%",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            } else {
                KalynoSvgIcon(name = "check-circle", size = 34.dp, tint = Color.White)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (loading) "Analyse de tes révisions…" else "Tout est à jour",
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (percent != null) {
                    "Voici la rétention actuelle de ta mémoire. Lance ta mission du jour pour la nourrir."
                } else {
                    "Rien d'urgent. Lance ta mission du jour pour découvrir de nouvelles cartes."
                },
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bandeau de statistiques réelles
// ---------------------------------------------------------------------------

@Composable
private fun DojoStats(streak: Int, mastered: Int?, retention: Float?) {
    val shownStreak = rememberCountUp(streak, durationMillis = 900)
    val shownMastered = rememberCountUp(mastered ?: 0, durationMillis = 900)
    val retentionPercent = retention?.let { (it * 100).roundToInt().coerceIn(0, 100) }
    val shownRetention = rememberCountUp(retentionPercent ?: 0, durationMillis = 900)

    KalynoCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatCell(
                icon = "flame",
                tint = KalynoAmberDeep,
                container = KalynoAmberSoft,
                value = shownStreak.toString(),
                label = if (streak <= 1) "jour de série" else "jours de série",
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatCell(
                icon = "trophy",
                tint = KalynoBlue,
                container = KalynoBlueSoft,
                value = if (mastered != null) shownMastered.toString() else "—",
                label = "maîtrisées",
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatCell(
                icon = "brain",
                tint = KalynoCorrectText,
                container = KalynoCorrectSoft,
                value = if (retentionPercent != null) "$shownRetention%" else "—",
                label = "rétention",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCell(
    icon: String,
    tint: Color,
    container: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = icon, size = 18.dp, tint = tint)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            color = KalynoText,
            fontSize = 20.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Text(
            text = label,
            color = KalynoMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(48.dp)
            .background(KalynoOutline)
    )
}

// ---------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------

@Composable
private fun SectionTitle(icon: String, title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KalynoBlueSoft),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = icon, size = 20.dp, tint = KalynoBlue)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = KalynoText,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (subtitle != null) {
                Text(text = subtitle, color = KalynoMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MetaLine(questionCount: Int, estimatedMinutes: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KalynoSvgIcon(name = "layers", size = 15.dp, tint = KalynoMuted)
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (questionCount == 1) "1 carte" else "$questionCount cartes",
            color = KalynoMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        if (estimatedMinutes != null) {
            Spacer(Modifier.width(14.dp))
            KalynoSvgIcon(name = "clock", size = 15.dp, tint = KalynoMuted)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$estimatedMinutes min",
                color = KalynoMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MissionBlock(
    state: DashboardUiState,
    onStart: () -> Unit,
    onReview: () -> Unit
) {
    val mission = state.mission

    when {
        // Retard : carte ambrée, douce, jamais punitive
        state.missionStatus == MissionStatus.BACKLOG -> KalynoCard(
            modifier = Modifier.fillMaxWidth(),
            background = KalynoAmberSoft,
            borderColor = KalynoYellow.copy(alpha = 0.45f)
        ) {
            SectionTitle(icon = "target", title = "Mission du jour")
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Tes révisions en retard d'abord ! Tes nouvelles cartes se débloquent dès que tu les as rattrapées.",
                color = KalynoAmberDeep,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
            if (state.urgentTotal > 0) {
                Spacer(Modifier.height(14.dp))
                KalynoPrimaryButton(label = "VOIR MES RÉVISIONS", onClick = onReview)
            }
        }

        mission != null && mission.items.isNotEmpty() -> KalynoCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(icon = "target", title = "Mission du jour", subtitle = "Découvre de nouvelles cartes")
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KalynoRing(
                    progress = mission.progress,
                    diameter = 84.dp,
                    strokeWidth = 9.dp
                ) {
                    Text(
                        text = "${mission.doneToday}/${mission.targetToday}",
                        color = KalynoBlue,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (mission.subjectName.isNotBlank()) {
                        KalynoPill(
                            text = "${mission.subjectEmoji} ${mission.subjectName}".trim(),
                            fontSize = 11
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = mission.chapterTitle,
                        color = KalynoText,
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    MetaLine(
                        questionCount = mission.questionCount,
                        estimatedMinutes = mission.estimatedMinutes
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${mission.doneToday}/${mission.targetToday} cartes découvertes aujourd'hui",
                color = KalynoMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(14.dp))
            KalynoPrimaryButton(
                label = "DÉMARRER MA MISSION",
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .shineSweep()
            )
        }

        state.missionStatus == MissionStatus.DONE_TODAY -> KalynoCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KalynoRing(
                    progress = 1f,
                    diameter = 72.dp,
                    strokeWidth = 8.dp,
                    color = KalynoCorrect,
                    track = KalynoCorrectSoft
                ) {
                    KalynoSvgIcon(name = "check-circle", size = 30.dp, tint = KalynoCorrectText)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mission accomplie !",
                        color = KalynoText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Reviens demain pour de nouvelles cartes.",
                        color = KalynoMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        else -> KalynoCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(icon = "target", title = "Mission du jour")
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Pas de mission pour l'instant. Termine une leçon dans Explorer (bouton « Terminer ») pour débloquer ses exercices.",
                color = KalynoMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun DojoSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ShimmerBlock(Modifier.fillMaxWidth().height(230.dp), RoundedCornerShape(28.dp))
        ShimmerBlock(Modifier.fillMaxWidth().height(96.dp), RoundedCornerShape(24.dp))
        ShimmerBlock(Modifier.fillMaxWidth().height(200.dp), RoundedCornerShape(24.dp))
    }
}
