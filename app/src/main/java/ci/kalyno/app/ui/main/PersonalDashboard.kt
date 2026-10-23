package ci.kalyno.app.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.ui.theme.*

/**
 * Dashboard élève : salutation personnalisée, série, mission du jour,
 * révisions urgentes (SRS) et maîtrise du programme.
 */
@Composable
fun PersonalDashboard(
    levelId: String,
    nickname: String,
    urgentCount: Int = 0,
    mission: String? = null,
    masteryPercent: Int = 0
) {
    val context = LocalContext.current.applicationContext
    val streak = remember { StreakStore(context).current() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                text = "🔥 $streak",
                color = KalynoText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(KalynoYellow.copy(alpha = 0.25f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        Text(
            text = "Bonjour, $nickname !",
            color = KalynoText,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )

        MissionCard(mission)
        UrgentReviewCard(urgentCount)
        MasteryCard(levelId, masteryPercent)
    }
}

@Composable
private fun MissionCard(mission: String?) {
    DashCard {
        CardLabel("🎯 MISSION DU JOUR", KalynoBlue)
        Spacer(Modifier.height(8.dp))
        Text(
            text = mission
                ?: "Ta prochaine étape apparaîtra ici dès que les premiers chapitres seront publiés.",
            color = KalynoText,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun UrgentReviewCard(urgentCount: Int) {
    val calm = urgentCount == 0
    val textColor = if (calm) KalynoCorrectText else KalynoRetryText

    DashCard(
        background = if (calm) KalynoCorrectSoft else KalynoRetrySoft,
        stroke = if (calm) KalynoCorrect else KalynoRetry
    ) {
        CardLabel(
            if (calm) "✅ RÉVISIONS URGENTES (SRS)" else "🚨 RÉVISIONS URGENTES (SRS)",
            textColor
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                calm -> "Rien d'urgent à réviser pour l'instant."
                urgentCount == 1 -> "1 notion à réviser en urgence"
                else -> "$urgentCount notions à réviser en urgence"
            },
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MasteryCard(levelId: String, percent: Int) {
    val value = percent.coerceIn(0, 100)

    DashCard {
        CardLabel("📈 MAÎTRISE DU PROGRAMME", KalynoBlue)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$value %",
            color = KalynoBlue,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "du programme ${Levels.label(levelId)}",
            color = KalynoMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(KalynoBlueSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value / 100f)
                    .fillMaxHeight()
                    .background(KalynoBlue)
            )
        }
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
