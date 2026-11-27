package ci.kalyno.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Boîte à outils d'animations de KALYNO.
// Règle d'or : le mouvement guide l'œil et récompense l'élève, il ne ralentit jamais.
// Tout est joué une seule fois à l'apparition (pas de relecture en défilant).
// ---------------------------------------------------------------------------

/**
 * Apparition en cascade : fondu + glissement vers le haut + léger zoom.
 * [index] décale le départ ([stepMillis] par rang, plafonné à 600 ms).
 * À n'utiliser que hors LazyColumn (sinon rejoué à chaque défilement).
 */
fun Modifier.entrance(
    index: Int = 0,
    stepMillis: Int = 70,
    distance: Dp = 22.dp
): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * stepMillis).toLong().coerceAtMost(600L))
        progress.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }
    val distancePx = with(LocalDensity.current) { distance.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * distancePx
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}

/** Compteur qui monte de 0 jusqu'à [target] (et se réajuste en douceur si la valeur change). */
@Composable
fun rememberCountUp(target: Int, durationMillis: Int = 1000): Int {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        anim.animateTo(
            targetValue = target.toFloat(),
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        )
    }
    return anim.value.roundToInt()
}

/** Valeur qui oscille en boucle entre [min] et [max] (flamme qui respire, icône qui flotte). */
@Composable
fun rememberPulse(
    min: Float = 1f,
    max: Float = 1.12f,
    durationMillis: Int = 900
): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseValue"
    )
    return value
}

/** Anneau de progression animé (0..1), avec un contenu centré (pourcentage, icône…). */
@Composable
fun KalynoRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 88.dp,
    strokeWidth: Dp = 9.dp,
    color: Color = KalynoBlue,
    track: Color = KalynoBlueSoft,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        anim.animateTo(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
        )
    }
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (anim.value > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * anim.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** Cercle décoratif qui dérive doucement (fonds de bannières). */
@Composable
fun DriftingBlob(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.08f),
    drift: Dp = 14.dp,
    durationMillis: Int = 5200
) {
    val transition = rememberInfiniteTransition(label = "drift")
    val dx by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftX"
    )
    val dy by transition.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (durationMillis * 1.3f).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftY"
    )
    val driftPx = with(LocalDensity.current) { drift.toPx() }
    Box(
        modifier = modifier
            .offset { IntOffset((dx * driftPx).roundToInt(), (dy * driftPx).roundToInt()) }
            .clip(CircleShape)
            .background(color)
    )
}
