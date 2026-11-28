package ci.kalyno.app.ui.profile

import ci.kalyno.app.data.badges.StreakGoals
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.DriftingBlob
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.KalynoRing
import ci.kalyno.app.ui.components.rememberCountUp
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.components.rememberPulse
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoLightBlue
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoShadow
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow
import kotlin.math.roundToInt

// ===========================================================================
// Profil v3 : carte héros + chiffres réels + prochain palier de série.
// Palette inchangée. Aucune donnée inventée : tout vient de l'état réel du compte.
// ===========================================================================

// ---------------------------------------------------------------------------
// Carte héros
// ---------------------------------------------------------------------------

/**
 * Carte dégradée Deep Teal : avatar entouré d'un anneau (rétention réelle quand elle est connue),
 * pseudo modifiable, classe, et statut de compte honnête (synchronisé uniquement si connecté).
 */
@Composable
internal fun ProfileHero(
    nickname: String,
    classLabel: String,
    isConnected: Boolean,
    retentionScore: Float?,
    onEditNickname: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = KalynoShadow, spotColor = KalynoShadow)
            .clip(shape)
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
    ) {
        DriftingBlob(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-56).dp),
            color = Color.White.copy(alpha = 0.08f)
        )
        DriftingBlob(
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 40.dp),
            color = KalynoLightBlue.copy(alpha = 0.20f),
            durationMillis = 6400
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KalynoRing(
                progress = retentionScore?.coerceIn(0f, 1f) ?: 0f,
                diameter = 108.dp,
                strokeWidth = 6.dp,
                color = Color.White,
                track = Color.White.copy(alpha = 0.22f)
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = nickname.trim().take(1).uppercase().ifEmpty { "K" },
                        color = KalynoBlue,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Pseudo + crayon : toute la ligne est cliquable
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Modifier mon pseudo") {
                        haptic()
                        onEditNickname()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = nickname.ifBlank { "Élève" },
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                KalynoSvgIcon(name = "pen-line", size = 18.dp, tint = Color.White.copy(alpha = 0.9f))
            }

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroPill(icon = "graduation-cap", text = classLabel)
                if (isConnected) {
                    HeroPill(icon = "check-circle", text = "Compte synchronisé")
                } else {
                    HeroPill(icon = "user-round", text = "Mode invité")
                }
            }
        }
    }
}

@Composable
private fun HeroPill(icon: String, text: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KalynoSvgIcon(name = icon, size = 14.dp, tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Trois chiffres qui comptent
// ---------------------------------------------------------------------------

@Composable
internal fun ProfileStatsRow(
    streak: Int,
    masteredCount: Int?,
    retentionScore: Float?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile(
            icon = "flame",
            iconTint = KalynoAmberDeep,
            iconBackground = KalynoAmberSoft,
            value = streak,
            suffix = "",
            label = if (streak == 1) "jour de série" else "jours de série",
            modifier = Modifier.weight(1f)
        )
        StatTile(
            icon = "brain",
            iconTint = KalynoBlue,
            iconBackground = KalynoBlueSoft,
            value = masteredCount,
            suffix = "",
            label = if (masteredCount == 1) "notion maîtrisée" else "notions maîtrisées",
            modifier = Modifier.weight(1f)
        )
        StatTile(
            icon = "target",
            iconTint = KalynoBlue,
            iconBackground = KalynoBlueSoft,
            value = retentionScore?.let { (it.coerceIn(0f, 1f) * 100f).roundToInt() },
            suffix = "%",
            label = "rétention",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(
    icon: String,
    iconTint: Color,
    iconBackground: Color,
    value: Int?,
    suffix: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val shown = rememberCountUp(value ?: 0)
    KalynoCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        contentPadding = 14.dp
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = icon, size = 18.dp, tint = iconTint)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (value == null) "—" else "$shown$suffix",
            color = KalynoText,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = KalynoMuted,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2
        )
    }
}

// ---------------------------------------------------------------------------
// Prochain palier de série
// ---------------------------------------------------------------------------

/**
 * Objectif concret : « encore N jours » vers le prochain badge de série. Même source que la fiche
 * de série de l'Accueil ([StreakGoals]) : les deux écrans affichent toujours le même objectif.
 */
@Composable
internal fun StreakMilestoneCard(streak: Int, modifier: Modifier = Modifier) {
    val goal = StreakGoals.next(streak)
    val daysLeft = StreakGoals.daysLeft(streak)
    val flame = rememberPulse(min = 1f, max = 1.16f, durationMillis = 800)

    KalynoCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KalynoAmberSoft),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = flame
                        scaleY = flame
                    }
                ) {
                    KalynoSvgIcon(name = "flame", size = 24.dp, tint = KalynoYellow)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (goal != null) "PROCHAIN BADGE" else "BADGES DE SÉRIE",
                    color = KalynoMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = goal?.title ?: "Série de légende",
                    color = KalynoText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (goal != null) {
            KalynoProgressBar(
                progress = StreakGoals.progress(streak),
                height = 8.dp,
                color = KalynoYellow,
                track = KalynoAmberSoft
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    streak == 0 -> "Révise aujourd'hui pour lancer ta série."
                    daysLeft == 1 -> "Encore 1 jour pour débloquer ce badge. Reviens demain !"
                    else -> "Encore $daysLeft jours pour débloquer ce badge. Tu es sur la bonne voie."
                },
                color = KalynoMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        } else {
            Text(
                text = "Tu as débloqué tous les badges de série. Chaque jour de plus est une victoire.",
                color = KalynoMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Accès à « Ma progression »
// ---------------------------------------------------------------------------

@Composable
internal fun ProgressLinkCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    KalynoCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        background = KalynoBlueSoft,
        borderColor = KalynoBlueSoft,
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "history", size = 22.dp, tint = KalynoBlue)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ma progression",
                    color = KalynoText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Retrouve toutes tes cartes maîtrisées",
                    color = KalynoMuted,
                    fontSize = 13.sp
                )
            }
            KalynoSvgIcon(name = "chevron-right", size = 20.dp, tint = KalynoBlue)
        }
    }
}
