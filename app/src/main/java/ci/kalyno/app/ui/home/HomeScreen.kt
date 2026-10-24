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
fun HomeScreen(onLevelSelected: (String) -> Unit, onLoginClick: () -> Unit, canLogin: Boolean = true) {
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
            fontSize = 18.sp,
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
            Text("🎯", fontSize = 30.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "C'est comment élément ?",
                color = KalynoMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Dis-nous, c'est quoi ton combat de cette année ?",
                color = KalynoText,
                fontSize = 22.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            ChoiceCard(
                title = "🎒  3e BEPC",
                subtitle = "Classe de 3e",
                selected = false,
                onClick = {
                    bacOpen = false
                    onLevelSelected(Levels.BEPC)
                }
            )
            Spacer(Modifier.height(10.dp))
            ChoiceCard(
                title = "🎓  Terminale",
                subtitle = "Choisis ta série",
                selected = bacOpen,
                onClick = { bacOpen = !bacOpen }
            )

            AnimatedVisibility(visible = bacOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Levels.bacSeries.forEach { (id, label) ->
                        SeriesChip(
                            label = "${Levels.emoji(id)} $label",
                            modifier = Modifier.weight(1f),
                            onClick = { onLevelSelected(id) }
                        )
                    }
                }
            }
        }

        if (canLogin) Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "Je suis un ancien",
                color = KalynoBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(KalynoBlueSoft)
                    .clickable(onClick = onLoginClick)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) KalynoBlue else Color.White)
            .border(BorderStroke(2.dp, if (selected) KalynoBlue else KalynoOutline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else KalynoBlue,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = subtitle,
            color = if (selected) Color.White.copy(alpha = 0.85f) else KalynoMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SeriesChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(KalynoBlueSoft)
            .border(2.dp, KalynoBlue, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = KalynoBlue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}
