package ci.kalyno.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Levels
import ci.kalyno.app.ui.theme.*

@Composable
fun HomeScreen(onLevelSelected: (String) -> Unit) {
    var bacOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "KALYNO",
            color = KalynoBlue,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "C'est comment élément ?",
                color = KalynoMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Dis-nous, c'est quoi ton combat de cette année ?",
                color = KalynoText,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            ChoiceCard(
                title = "BEPC",
                subtitle = "Classe de 3e",
                selected = false,
                onClick = {
                    bacOpen = false
                    onLevelSelected(Levels.BEPC)
                }
            )
            Spacer(Modifier.height(14.dp))
            ChoiceCard(
                title = "BAC",
                subtitle = "Choisis ta série",
                selected = bacOpen,
                onClick = { bacOpen = !bacOpen }
            )

            AnimatedVisibility(visible = bacOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Levels.bacSeries.forEach { (id, label) ->
                        SeriesChip(
                            label = label,
                            modifier = Modifier.weight(1f),
                            onClick = { onLevelSelected(id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) KalynoBlue else Color.White)
            .border(BorderStroke(2.dp, if (selected) KalynoBlue else KalynoOutline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else KalynoBlue,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = subtitle,
            color = if (selected) Color.White.copy(alpha = 0.85f) else KalynoMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SeriesChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(60.dp)
            .clip(shape)
            .background(KalynoBlueSoft)
            .border(2.dp, KalynoBlue, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = KalynoBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}
