package ci.kalyno.app.ui.profile

import ci.kalyno.app.ui.theme.kc
import ci.kalyno.app.ui.theme.KalynoSurface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText

// Ombre très diffuse (elevation 1.dp)
private val CardShadow: Color get() = kc(0x140F172A, 0x66000000)

// ---------------------------------------------------------------------------
// Micro-interactions
// ---------------------------------------------------------------------------

/** Rebond ressort quand l'élément est pressé (à placer avant clip/clickable). */
fun Modifier.pressScale(
    interaction: MutableInteractionSource,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Point « en direct » : un halo grandit et s'efface en boucle autour d'un point plein. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "pulseDot")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseDotP"
    )
    Box(modifier = modifier.size(size * 2.4f), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size * (1f + 1.3f * p))
                .graphicsLayer { alpha = 1f - p }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.55f))
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// ---------------------------------------------------------------------------
// Conteneurs
// ---------------------------------------------------------------------------

/** Conteneur blanc : coins 20 dp, bordure #E2E8F0, ombre légère. Le contenu est clippé (ripple arrondi). */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, shape, ambientColor = CardShadow, spotColor = CardShadow)
            .background(KalynoSurface, shape)
            .border(1.dp, KalynoOutline, shape)
            .clip(shape),
        content = content
    )
}

/** Bloc de réglages : titre de section (liseré teal + petites capitales) + carte blanche. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(KalynoBlue)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = KalynoMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
        }
        SettingsCard(content = content)
    }
}

// ---------------------------------------------------------------------------
// Lignes et tuiles
// ---------------------------------------------------------------------------

/**
 * Une ligne de réglage : bulle d'icône, titre, sous-titre optionnel, puis [trailing] ou une flèche
 * qui glisse légèrement au toucher. [compact] : version réduite pour les infos secondaires (légal).
 */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconBackground: Color = KalynoBlueSoft,
    iconTint: Color = KalynoBlue,
    titleColor: Color = KalynoText,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    compact: Boolean = false
) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val nudge by animateDpAsState(
        targetValue = if (pressed) 5.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chevronNudge"
    )
    val bubble = if (compact) 34.dp else 42.dp

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            role = Role.Button
                        ) {
                            haptic()
                            onClick()
                        }
                    } else {
                        Modifier
                    }
                )
                .heightIn(min = if (compact) 54.dp else 68.dp)
                .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(bubble)
                    .clip(RoundedCornerShape(if (compact) 10.dp else 13.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = KalynoMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            } else if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = KalynoMuted,
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = nudge)
                )
            }
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .padding(start = if (compact) 64.dp else 72.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(KalynoOutline.copy(alpha = 0.7f))
            )
        }
    }
}

/** Grande tuile d'action (icône en haut, texte en bas) : rebond au toucher + ripple. */
@Composable
fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = KalynoBlueSoft,
    iconBackground: Color = KalynoSurface,
    iconTint: Color = KalynoBlue
) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .height(136.dp)
            .pressScale(interaction)
            .clip(shape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button
            ) {
                haptic()
                onClick()
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Column {
            Text(
                text = title,
                color = KalynoText,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = KalynoMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
