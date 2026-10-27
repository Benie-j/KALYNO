package ci.kalyno.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.LevelInfo
import ci.kalyno.app.ui.components.KalynoErrorCard
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.icons.iconAssetFor
import ci.kalyno.app.ui.icons.levelIconFallback
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoShadow
import ci.kalyno.app.ui.theme.KalynoText

private val CardShape = RoundedCornerShape(24.dp)
private val CardGap = 10.dp

/**
 * Écran de choix du "combat de l'année" : classe (3e) ou série de Terminale.
 * Version compacte : paddings verticaux 12 dp, espacement entre cartes 10 dp,
 * titres en bodyMedium, sous-titres en labelSmall. Aucune classe écrite en dur ici.
 */
@Composable
fun HomeScreen(
    onLevelSelected: (String) -> Unit,
    onLoginClick: () -> Unit,
    canLogin: Boolean = true,
    viewModel: LevelSelectionViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "KALYNO",
            color = KalynoBlue,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(KalynoBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    KalynoSvgIcon(name = "target", size = 18.dp, tint = KalynoBlue)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "C'est comment, élément ?",
                    color = KalynoMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Dis-nous, c'est quoi ton combat de cette année ?",
                color = KalynoText,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Choisis ta classe, on s'occupe de te mettre dans le bain.",
                color = KalynoMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(18.dp))

            when {
                state.loading -> LevelSkeleton()

                state.error != null -> KalynoErrorCard(
                    message = state.error ?: "",
                    onRetry = viewModel::refresh
                )

                else -> state.groups.forEachIndexed { index, group ->
                    if (index > 0) Spacer(Modifier.height(16.dp))
                    LevelGroupSection(group = group, onSelect = onLevelSelected)
                }
            }
        }

        // Bouton texte clair, sans fond : Vert Pétrole #0F766E
        if (canLogin) {
            TextButton(
                onClick = onLoginClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = KalynoBlue)
            ) {
                Text(
                    text = "J'ai déjà un compte",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Groupes et cartes
// ---------------------------------------------------------------------------

@Composable
private fun LevelGroupSection(group: LevelGroup, onSelect: (String) -> Unit) {
    if (group.cycle.isNotBlank()) {
        Text(
            text = group.cycle.uppercase(),
            color = KalynoMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))
    }

    // Une classe seule (ex. la 3e) occupe toute la largeur ; plusieurs séries se rangent par deux.
    if (group.levels.size == 1) {
        LevelWideCard(level = group.levels.first(), onClick = { onSelect(group.levels.first().id) })
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
            group.levels.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                    pair.forEach { level ->
                        LevelSeriesCard(
                            level = level,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(level.id) }
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LevelWideCard(level: LevelInfo, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = cardModifier(onClick).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        LevelIcon(level = level)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = level.label,
                color = KalynoText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold
            )
            if (!level.tagline.isNullOrBlank()) {
                Text(
                    text = level.tagline,
                    color = KalynoMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        KalynoSvgIcon(name = "chevron-right", size = 20.dp, tint = KalynoMuted)
    }
}

@Composable
private fun LevelSeriesCard(level: LevelInfo, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.then(cardModifier(onClick)).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        LevelIcon(level = level)
        Spacer(Modifier.height(8.dp))
        Text(
            text = level.label,
            color = KalynoText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold
        )
        if (!level.tagline.isNullOrBlank()) {
            Text(
                text = level.tagline,
                color = KalynoMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun LevelIcon(level: LevelInfo) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KalynoBlueSoft),
        contentAlignment = Alignment.Center
    ) {
        KalynoSvgIcon(
            name = iconAssetFor(level.iconKey, fallback = levelIconFallback(level.cycle)),
            size = 20.dp,
            tint = KalynoBlue
        )
    }
}

/** Carte blanche : coins 24 dp, bordure #E2E8F0, ombre ultra-légère. */
private fun cardModifier(onClick: () -> Unit): Modifier =
    Modifier
        .fillMaxWidth()
        .shadow(2.dp, CardShape, ambientColor = KalynoShadow, spotColor = KalynoShadow)
        .clip(CardShape)
        .background(Color.White)
        .border(BorderStroke(1.dp, KalynoOutline), CardShape)
        .clickable(onClick = onClick)

@Composable
private fun LevelSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
        ShimmerBlock(modifier = Modifier.fillMaxWidth().height(60.dp), shape = CardShape)
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
            ShimmerBlock(modifier = Modifier.weight(1f).height(90.dp), shape = CardShape)
            ShimmerBlock(modifier = Modifier.weight(1f).height(90.dp), shape = CardShape)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
            ShimmerBlock(modifier = Modifier.weight(1f).height(90.dp), shape = CardShape)
            ShimmerBlock(modifier = Modifier.weight(1f).height(90.dp), shape = CardShape)
        }
    }
}
