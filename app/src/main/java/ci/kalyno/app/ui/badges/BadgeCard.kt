package ci.kalyno.app.ui.badges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.badges.BadgeUiState
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText

@Composable
fun BadgeCard(state: BadgeUiState, modifier: Modifier = Modifier) {
    val unlocked = state.isUnlocked
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (unlocked) KalynoBlue.copy(alpha = 0.4f) else KalynoOutline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).let { if (unlocked) it else it },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        if (unlocked) KalynoAmberSoft else Color(0xFFF1F5F9),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (unlocked) {
                    Text(state.def.emoji, fontSize = 26.sp)
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = KalynoMuted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.def.title,
                color = if (unlocked) KalynoText else KalynoMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            if (!unlocked) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (state.currentProgress.toFloat() / state.def.targetValue).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = KalynoBlue,
                    trackColor = KalynoOutline
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${state.currentProgress} / ${state.def.targetValue}",
                    color = KalynoMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
