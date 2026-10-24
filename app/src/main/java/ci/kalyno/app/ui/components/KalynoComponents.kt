package ci.kalyno.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoRetryText
import ci.kalyno.app.ui.theme.KalynoShadow
import ci.kalyno.app.ui.theme.KalynoText

// ---------------------------------------------------------------------------
// Retour haptique
// ---------------------------------------------------------------------------

/** Retourne une action qui déclenche une vibration légère (LongPress). */
@Composable
fun rememberHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}

// ---------------------------------------------------------------------------
// Carte flat-design : blanc, coins 24dp, bordure #E2E8F0, ombre diffuse 4dp,
// léger effet de rebond au toucher + ripple quand elle est cliquable.
// ---------------------------------------------------------------------------

@Composable
fun KalynoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    background: Color = Color.White,
    borderColor: Color = KalynoOutline,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = KalynoShadow,
                spotColor = KalynoShadow
            )
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .then(clickable)
            .padding(contentPadding),
        content = content
    )
}

// ---------------------------------------------------------------------------
// Boutons
// ---------------------------------------------------------------------------

/** Bouton principal Deep Teal avec ripple et vibration légère. */
@Composable
fun KalynoPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    height: Dp = 56.dp,
    container: Color = KalynoBlue,
    content: Color = Color.White
) {
    val haptic = rememberHaptic()
    Button(
        onClick = {
            haptic()
            onClick()
        },
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = KalynoOutline,
            disabledContentColor = KalynoMuted
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        )
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Petits éléments
// ---------------------------------------------------------------------------

/** Pastille de texte (badge de matière, type de question, etc.). */
@Composable
fun KalynoPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = KalynoBlueSoft,
    content: Color = KalynoBlue,
    fontSize: Int = 12
) {
    Text(
        text = text,
        color = content,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.4.sp,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** Série de jours : flamme orange sur fond doux. */
@Composable
fun StreakBadge(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "🔥 $count",
        color = KalynoRetryText,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .clip(CircleShape)
            .background(KalynoAmberSoft)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** Titre de section avec sous-titre optionnel. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = KalynoText,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, color = KalynoMuted, fontSize = 13.sp)
        }
    }
}

/** Barre de progression élégante et animée (valeur entre 0 et 1). */
@Composable
fun KalynoProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = KalynoBlue,
    track: Color = KalynoBlueSoft
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// ---------------------------------------------------------------------------
// Shimmer (squelettes de chargement)
// ---------------------------------------------------------------------------

fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
        label = "shimmerOffset"
    )
    background(
        Brush.linearGradient(
            colors = listOf(Color(0xFFE2E8F0), Color(0xFFF1F5F9), Color(0xFFE2E8F0)),
            start = Offset(offset, 0f),
            end = Offset(offset + 400f, 200f)
        )
    )
}

@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    Box(modifier = modifier.clip(shape).shimmer())
}

// ---------------------------------------------------------------------------
// États vides / erreur
// ---------------------------------------------------------------------------

@Composable
fun KalynoEmptyState(
    emoji: String,
    title: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 52.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            color = KalynoText,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            color = KalynoMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun KalynoErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    KalynoCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Oups, connexion impossible",
            color = KalynoAmberDeep,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(text = message, color = KalynoText, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(14.dp))
        KalynoPrimaryButton(label = "RÉESSAYER", onClick = onRetry, height = 48.dp)
    }
}
