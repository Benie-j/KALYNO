package ci.kalyno.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoLightBlue
import ci.kalyno.app.ui.theme.KalynoTealDark

// ---------------------------------------------------------------------------
// Petites briques "premium" partagées par Explorer et Profil.
// Aucune nouvelle couleur : tout vient de la palette existante.
// ---------------------------------------------------------------------------

/**
 * Clic avec rebond au toucher : la zone rétrécit légèrement puis revient en ressort.
 * L'ordre compte : zoom -> découpe arrondie -> clic (le ripple reste dans la forme).
 * Ajouter le fond / la bordure APRÈS cet appel pour qu'ils suivent le zoom.
 */
fun Modifier.bouncyClick(
    shape: Shape = RoundedCornerShape(16.dp),
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncyScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}

/**
 * Reflet lumineux qui traverse l'élément de temps en temps (bouton d'appel à l'action).
 * Le balayage dure ~45 % du cycle, le reste du temps l'élément est au repos.
 */
fun Modifier.sheen(
    color: Color = Color.White.copy(alpha = 0.25f),
    cycleMillis: Int = 3400
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "sheen")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = cycleMillis, easing = LinearEasing)),
        label = "sheenT"
    )
    drawWithContent {
        drawContent()
        val sweep = (t / 0.45f).coerceAtMost(1f)
        if (sweep < 1f) {
            val band = size.width * 0.35f
            val x = -band + (size.width + band * 2f) * sweep
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, color, Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + band, size.height * 0.5f)
                )
            )
        }
    }
}

/** Flamme vectorielle qui "respire" (série active) ; immobile si [active] est faux. */
@Composable
fun PulsingFlame(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    active: Boolean = true
) {
    val scale = if (active) rememberPulse(min = 1f, max = 1.16f, durationMillis = 780) else 1f
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        KalynoSvgIcon(name = "flame", size = size, tint = tint)
    }
}

/**
 * Fond "aurore" : dégradé Vert Pétrole -> Vert Pétrole foncé, avec deux halos qui dérivent
 * lentement. Sert de scène aux cartes héros (Explorer, Profil).
 */
@Composable
fun KalynoAurora(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
    ) {
        DriftingBlob(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 46.dp, y = (-46).dp)
                .size(170.dp),
            color = Color.White.copy(alpha = 0.07f)
        )
        DriftingBlob(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-36).dp, y = 44.dp)
                .size(130.dp),
            color = KalynoLightBlue.copy(alpha = 0.20f),
            drift = 10.dp,
            durationMillis = 6400
        )
        content()
    }
}
