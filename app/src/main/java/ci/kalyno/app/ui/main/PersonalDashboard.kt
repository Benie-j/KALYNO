package ci.kalyno.app.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.dashboard.DashboardUiState
import ci.kalyno.app.data.dashboard.MissionUi
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.*

/** Dashboard élève : tout le contenu vient de [DashboardUiState] (ViewModel). */
@Composable
fun PersonalDashboard(
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ----- En-tête : inchangé (nom + classe + série) -----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "KALYNO",
                    color = KalynoBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${Levels.emoji(levelId)} ${Levels.label(levelId)}",
                    color = KalynoMuted,
                    fontSize = 13.sp
                )
            }
            Text(
                text = "🔥 ${state.streak}",
                color = KalynoText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(KalynoYellow.copy(alpha = 0.25f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        GreetingCard(nickname = state.nickname)
        MissionCard(state = state, onStart = onStartMission, onRetry = onRetry)
        UrgentCard(state = state, onReview = onReviewUrgent)
    }
}

// ---------- a) Salutation ----------

@Composable
private fun GreetingCard(nickname: String) {
    DashCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(KalynoBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "user-round", size = 28.dp, tint = KalynoBlue)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = if (nickname.isBlank()) "Bonjour !" else "Bonjour, $nickname !",
                color = KalynoText,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------- b) Mission du jour ----------

@Composable
private fun MissionCard(state: DashboardUiState, onStart: () -> Unit, onRetry: () -> Unit) {
    val mission = state.mission

    DashCard {
        CardLabel("🎯 MISSION DU JOUR", KalynoBlue)
        Spacer(Modifier.height(10.dp))

        when {
            mission != null -> MissionContent(mission, onStart)

            state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 3.dp,
                    color = KalynoBlue
                )
                Spacer(Modifier.width(12.dp))
                Text("Préparation de ta mission…", color = KalynoMuted, fontSize = 15.sp)
            }

            state.error != null -> Column {
                Text(state.error, color = KalynoText, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Réessayer", onRetry)
            }

            else -> Text(
                text = "Pas de nouvelle mission pour l'instant. Reviens dès que de nouveaux chapitres seront publiés.",
                color = KalynoMuted,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun MissionContent(mission: MissionUi, onStart: () -> Unit) {
    Text(
        text = "${mission.subjectEmoji} ${mission.subjectName}",
        color = KalynoBlue,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(2.dp))
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
            append(if (mission.toDiscover == 1) "1 carte à découvrir" else "${mission.toDiscover} cartes à découvrir")
            if (mission.minutes > 0) append(" · environ ${mission.minutes} min")
        },
        color = KalynoMuted,
        fontSize = 14.sp
    )

    Spacer(Modifier.height(14.dp))
    LinearProgressIndicator(
        progress = { mission.progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp)),
        color = KalynoBlue,
        trackColor = KalynoBlueSoft
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "${mission.done}/${mission.total} cartes vues",
        color = KalynoMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )

    Spacer(Modifier.height(16.dp))
    PrimaryButton("Démarrer ma Mission", onStart)
}

// ---------- c) Alerte Révisions Urgentes (SRS) ----------

@Composable
private fun UrgentCard(state: DashboardUiState, onReview: () -> Unit) {
    val urgent = state.urgentTotal > 0

    if (!urgent) {
        DashCard(background = KalynoCorrectSoft, stroke = KalynoCorrect) {
            CardLabel("✅ RÉVISIONS URGENTES (SRS)", KalynoCorrectText)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.loading) "Analyse de tes révisions…" else "Rien d'urgent à réviser pour l'instant.",
                color = KalynoCorrectText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }

    DashCard(background = KalynoRetrySoft, stroke = KalynoRetry) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(KalynoRetry),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.urgentTotal.toString(),
                    color = KalynoText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                CardLabel("🚨 ALERTE RÉVISIONS URGENTES ! (SRS)", KalynoRetryText)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (state.urgentTotal == 1) "1 notion à sauver maintenant" else "${state.urgentTotal} notions à sauver maintenant",
                    color = KalynoRetryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (state.urgentTopics.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            state.urgentTopics.forEach { topic ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("•", color = KalynoRetryText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = topic.label,
                        color = KalynoText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "×${topic.count}",
                        color = KalynoRetryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onReview,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KalynoRetry, contentColor = KalynoText)
        ) {
            Text("Réviser Urgemment", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ---------- Éléments communs ----------

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = KalynoBlue, contentColor = Color.White)
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun DashCard(
    background: Color = Color.White,
    stroke: Color = KalynoOutline,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = background,
        border = BorderStroke(2.dp, stroke)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun CardLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp
    )
}
