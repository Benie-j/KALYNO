package ci.kalyno.app.ui.dojo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.dashboard.DashboardUiState
import ci.kalyno.app.data.dashboard.MissionStatus
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoErrorCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.SectionHeader
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.components.StreakBadge
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText

/**
 * Onglet Dojo : point d'entrée de la répétition espacée.
 * Les données viennent du [DashboardUiState] (moteur SRS + contenu en ligne, cache Room en secours).
 * La session de cartes (flip 3D) est [ci.kalyno.app.ui.review.ReviewSessionScreen].
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
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Le Dojo 🧠",
                    color = KalynoText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Répétition espacée · ${Levels.label(levelId)}",
                    color = KalynoMuted,
                    fontSize = 13.sp
                )
            }
            StreakBadge(count = state.streak)
        }

        when {
            state.loading && state.mission == null && state.urgentTotal == 0 -> DojoSkeleton()

            state.error != null && state.mission == null && state.urgentTotal == 0 -> KalynoErrorCard(
                message = state.error ?: "",
                onRetry = onRetry
            )

            else -> {
                UrgentHero(state = state, onReview = onReviewUrgent)
                MissionBlock(state = state, onStart = onStartMission, onReview = onReviewUrgent)
            }
        }
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun UrgentHero(state: DashboardUiState, onReview: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val urgent = state.urgentTotal > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
            .padding(22.dp)
    ) {
        KalynoPill(
            text = "🧠 ACTIVE RECALL",
            container = Color.White.copy(alpha = 0.18f),
            content = Color.White
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = when {
                urgent && state.urgentTotal == 1 -> "1 notion à sauver"
                urgent -> "${state.urgentTotal} notions à sauver"
                state.loading -> "Analyse de tes révisions…"
                else -> "Tout est à jour 🎉"
            },
            color = Color.White,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (urgent) {
                "Révise-les maintenant : chaque rappel actif ancre la notion plus longtemps."
            } else {
                "Rien d'urgent. Lance ta mission du jour pour découvrir de nouvelles cartes."
            },
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        if (state.urgentTopics.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            state.urgentTopics.forEach { topic ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = topic.label,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "×${topic.count}",
                        color = KalynoAmberSoft,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        if (urgent) {
            Spacer(Modifier.height(18.dp))
            KalynoPrimaryButton(
                label = "RÉVISER MAINTENANT",
                onClick = onReview,
                container = Color.White,
                content = KalynoBlue
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

    KalynoCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "🎯 Mission du jour")
        Spacer(Modifier.height(12.dp))

        when {
            state.missionStatus == MissionStatus.BACKLOG -> {
                Text(
                    text = "Tes révisions en retard d'abord ! Tes nouvelles cartes se débloquent dès que tu les as rattrapées.",
                    color = KalynoText,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                if (state.urgentTotal > 0) {
                    Spacer(Modifier.height(14.dp))
                    KalynoPrimaryButton(label = "VOIR MES RÉVISIONS", onClick = onReview)
                }
            }

            mission != null && mission.items.isNotEmpty() -> {
                if (mission.subjectName.isNotBlank()) {
                    KalynoPill(text = "${mission.subjectEmoji} ${mission.subjectName}".trim())
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = mission.chapterTitle,
                    color = KalynoText,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        val n = mission.items.size
                        append(if (n == 1) "⏱️ 1 carte à découvrir" else "⏱️ $n cartes à découvrir")
                        if (mission.minutes > 0) append(" • ${mission.minutes} min")
                    },
                    color = KalynoMuted,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(14.dp))
                KalynoProgressBar(progress = mission.progress)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${mission.doneToday}/${mission.targetToday} cartes découvertes aujourd'hui",
                    color = KalynoMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(16.dp))
                KalynoPrimaryButton(label = "DÉMARRER MA MISSION", onClick = onStart)
            }

            state.missionStatus == MissionStatus.DONE_TODAY -> {
                Text(
                    text = "🎉 Mission accomplie !",
                    color = KalynoText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Reviens demain pour de nouvelles cartes.",
                    color = KalynoMuted,
                    fontSize = 14.sp
                )
                if (mission != null) {
                    Spacer(Modifier.height(14.dp))
                    KalynoProgressBar(progress = mission.progress)
                }
            }

            else -> Text(
                text = "Pas de nouvelle mission pour l'instant. Reviens dès que de nouveaux chapitres seront publiés.",
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
        ShimmerBlock(Modifier.fillMaxWidth().height(190.dp), RoundedCornerShape(24.dp))
        ShimmerBlock(Modifier.fillMaxWidth().height(170.dp), RoundedCornerShape(24.dp))
        ShimmerBlock(Modifier.fillMaxWidth().height(60.dp), RoundedCornerShape(16.dp))
    }
}
