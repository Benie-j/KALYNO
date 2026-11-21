package ci.kalyno.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.ai.AiPhase
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerSoft
import ci.kalyno.app.ui.theme.KalynoMuted

/** Indicateur de correction en cours, ou erreur avec « Réessayer » (partagé par le Dojo et le Challenge). */
@Composable
fun GradingStatus(grading: GradingState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when {
        grading.busy -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.padding(top = 8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = KalynoBlue)
            Text(
                if (grading.phase == AiPhase.RETRYING) "Nouvelle tentative…" else "Kalyno corrige ta réponse…",
                color = KalynoMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        grading.failed -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(KalynoDangerSoft)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                (grading.error ?: "La correction a échoué.") + " Ta réponse est conservée.",
                color = KalynoDanger,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Réessayer",
                color = KalynoDanger,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onRetry).padding(start = 12.dp)
            )
        }
    }
}
