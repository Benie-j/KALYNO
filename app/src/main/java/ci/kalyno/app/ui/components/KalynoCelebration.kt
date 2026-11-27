package ci.kalyno.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoGreen
import ci.kalyno.app.ui.theme.KalynoLightBlue
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoPurple
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Confettis : une explosion unique, aux couleurs de la marque, qui retombe en fondu.
// ---------------------------------------------------------------------------

private class ConfettiPiece(
    val vx: Float,
    val vy: Float,
    val dim: Float,
    val rotation: Float,
    val spin: Float,
    val color: Color,
    val round: Boolean
)

private val ConfettiPalette = listOf(KalynoBlue, KalynoLightBlue, KalynoYellow, KalynoGreen, KalynoPurple)

@Composable
fun ConfettiBurst(
    modifier: Modifier = Modifier,
    count: Int = 80,
    durationMillis: Int = 2800
) {
    val pieces = remember {
        val random = Random(42)
        List(count) {
            // Direction : éventail vers le haut (de -160° à -20°)
            val angle = Math.toRadians((-160.0 + random.nextDouble() * 140.0))
            val speed = 0.55f + random.nextFloat() * 0.9f
            ConfettiPiece(
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat(),
                dim = 6f + random.nextFloat() * 8f,
                rotation = random.nextFloat() * 360f,
                spin = (random.nextFloat() - 0.5f) * 900f,
                color = ConfettiPalette[random.nextInt(ConfettiPalette.size)],
                round = random.nextBoolean()
            )
        }
    }
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        time.animateTo(1f, tween(durationMillis = durationMillis, easing = LinearEasing))
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val p = time.value
        if (p < 1f) {
            val seconds = p * 2.4f
            val originX = size.width * 0.5f
            val originY = size.height * 0.30f
            val gravity = size.height * 1.5f
            val fade = 1f - ((p - 0.65f) / 0.35f).coerceIn(0f, 1f)
            pieces.forEach { piece ->
                val x = originX + piece.vx * size.width * seconds
                val y = originY + piece.vy * size.width * seconds + 0.5f * gravity * seconds * seconds
                val color = piece.color.copy(alpha = fade)
                if (piece.round) {
                    drawCircle(color = color, radius = piece.dim / 2f, center = Offset(x, y))
                } else {
                    rotate(degrees = piece.rotation + piece.spin * seconds, pivot = Offset(x, y)) {
                        drawRect(
                            color = color,
                            topLeft = Offset(x - piece.dim / 2f, y - piece.dim / 3f),
                            size = Size(piece.dim, piece.dim * 0.66f)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Écran de célébration (fin de session Dojo et révisions)
// ---------------------------------------------------------------------------

/**
 * Bilan de fin de session.
 *
 * - [score] (0..1) remplit l'anneau, avec le pourcentage qui monte ; [scoreCaption] le légende.
 * - [good] déclenche les confettis et la vibration de victoire.
 * - [streakBefore] -> [streakAfter] : la série est montrée telle qu'elle sera enregistrée à la fermeture ;
 *   si elle progresse, le compteur s'incrémente sous les yeux de l'élève.
 * - [chips] : le détail (justes / à revoir…), fourni par chaque écran.
 */
@Composable
fun KalynoCelebration(
    good: Boolean,
    title: String,
    subtitle: String,
    message: String?,
    score: Float,
    scoreCaption: String,
    streakBefore: Int,
    streakAfter: Int,
    buttonLabel: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    chips: @Composable () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        if (good) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Le badge « pop » avec un ressort élastique
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(250)
        pop.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (good) ConfettiBurst()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val shownPercent = rememberCountUp((score.coerceIn(0f, 1f) * 100).roundToInt(), 1200)

                KalynoRing(
                    progress = score,
                    diameter = 156.dp,
                    strokeWidth = 13.dp,
                    color = if (good) KalynoBlue else KalynoYellow,
                    track = if (good) KalynoBlueSoft else KalynoAmberSoft
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = pop.value
                                    scaleY = pop.value
                                }
                        ) {
                            KalynoSvgIcon(
                                name = if (good) "trophy" else "dumbbell",
                                size = 30.dp,
                                tint = if (good) KalynoBlue else KalynoAmberDeep
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$shownPercent %",
                            color = KalynoText,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = scoreCaption,
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.entrance(1)
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    text = title,
                    color = KalynoText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.entrance(2)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = KalynoMuted,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.entrance(3)
                )
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = KalynoText,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.entrance(4)
                    )
                }

                if (streakAfter > 0) {
                    Spacer(Modifier.height(20.dp))
                    StreakResult(
                        before = streakBefore,
                        after = streakAfter,
                        modifier = Modifier.entrance(5)
                    )
                }

                Spacer(Modifier.height(20.dp))
                Box(modifier = Modifier.entrance(6)) { chips() }

                Spacer(Modifier.height(28.dp))
                KalynoPrimaryButton(
                    label = buttonLabel,
                    onClick = onDone,
                    modifier = Modifier
                        .entrance(7)
                        .fillMaxWidth()
                )
            }
        }
    }
}

/** Carte de série : le compteur passe de l'ancienne à la nouvelle valeur, la flamme respire. */
@Composable
private fun StreakResult(before: Int, after: Int, modifier: Modifier = Modifier) {
    val grew = after > before
    val shown = remember { Animatable(before.toFloat()) }
    LaunchedEffect(before, after) {
        if (grew) {
            delay(1100)
            shown.animateTo(after.toFloat(), tween(700, easing = FastOutSlowInEasing))
        } else {
            shown.snapTo(after.toFloat())
        }
    }
    val flame = rememberPulse(min = 1f, max = 1.18f, durationMillis = 750)
    val days = shown.value.roundToInt()

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(KalynoAmberSoft)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = flame
                scaleY = flame
            }
        ) {
            KalynoSvgIcon(name = "flame", size = 24.dp, tint = KalynoYellow)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = if (days <= 1) "$days jour d'affilée" else "$days jours d'affilée",
                color = KalynoAmberDeep,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = if (grew) "Série prolongée, bien joué !" else "Série maintenue",
                color = KalynoAmberDeep,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Tuile de statistique (grand chiffre + libellé) ; à placer dans une Row avec Modifier.weight(1f). */
@Composable
fun CelebrationChip(
    label: String,
    count: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    val shown = rememberCountUp(count, durationMillis = 900)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = shown.toString(),
            color = content,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            color = content,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
