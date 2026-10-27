package ci.kalyno.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.KalynoApp
import ci.kalyno.app.ui.splash.SplashVersionFooter
import ci.kalyno.app.ui.theme.KalynoTheme
import ci.kalyno.app.ui.theme.KalynoYellow
import ci.kalyno.app.ui.theme.KalynoText
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import android.os.SystemClock
import androidx.compose.runtime.SideEffect
import kotlin.math.sin

private var splashDrawn = false
private var splashStart = 0L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        splashStart = SystemClock.uptimeMillis()
        installSplashScreen().apply {
            setKeepOnScreenCondition { !splashDrawn && SystemClock.uptimeMillis() - splashStart < 400L }
            setOnExitAnimationListener { it.remove() }
        }

        super.onCreate(savedInstanceState)

        setContent {
            KalynoTheme {
                var showHome by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(1_000L)
                    showHome = true
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = KalynoBackground
                ) {
                    if (showHome) {
                        KalynoApp()
                    } else {
                        Box(Modifier.fillMaxSize()) {
                            ComposeSplash()
                            SplashVersionFooter(Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposeSplashOld() {
    val transition = rememberInfiniteTransition(label = "kalyno-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave-phase"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            BookLogo()

            Row(verticalAlignment = Alignment.CenterVertically) {
                "KALYNO".forEachIndexed { index, letter ->
                    val y = sin(phase + index * 0.65f) * 8f

                    Text(
                        text = letter.toString(),
                        color = KalynoBlue,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(
                            top = (12f + y).dp,
                            bottom = (12f - y).dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun BookLogo() {
    Canvas(modifier = Modifier.size(76.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2

        drawCircle(
            color = KalynoBlue,
            radius = radius,
            center = center
        )

        val left = size.width * 0.25f
        val right = size.width * 0.75f
        val top = size.height * 0.28f
        val bottom = size.height * 0.73f
        val middle = size.width / 2

        val book = PathBuilder()
            .moveTo(left, top)
            .cubicTo(size.width * 0.36f, top - 3f, middle - 2f, top + 5f, middle, top + 10f)
            .lineTo(middle, bottom)
            .cubicTo(middle - 9f, bottom - 7f, size.width * 0.36f, bottom - 10f, left, bottom - 5f)
            .close()
            .build()

        drawPath(book, Color.White)

        val rightPage = PathBuilder()
            .moveTo(middle, top + 10f)
            .cubicTo(middle + 9f, top + 2f, size.width * 0.64f, top - 3f, right, top)
            .lineTo(right, bottom - 5f)
            .cubicTo(size.width * 0.64f, bottom - 10f, middle + 9f, bottom - 7f, middle, bottom)
            .close()
            .build()

        drawPath(rightPage, Color(0xFFEAF0FF))
        drawLine(
            color = KalynoYellow,
            start = Offset(middle, top + 10f),
            end = Offset(middle, bottom),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }
}

private class PathBuilder {
    private val path = androidx.compose.ui.graphics.Path()

    fun moveTo(x: Float, y: Float) = apply { path.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) = apply { path.lineTo(x, y) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        apply { path.cubicTo(x1, y1, x2, y2, x3, y3) }
    fun close() = apply { path.close() }
    fun build() = path
}


@Composable
private fun ComposeSplashV1() {
    SideEffect { splashDrawn = true }

    val transition = rememberInfiniteTransition(label = "kalyno-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave-phase"
    )

    var waveOn by remember { mutableStateOf(false) }
    val amplitude by animateFloatAsState(
        targetValue = if (waveOn) 8f else 0f,
        animationSpec = tween(500),
        label = "wave-amplitude"
    )
    LaunchedEffect(Unit) { waveOn = true }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.scale(0.8f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookLogo()

            Row(
                modifier = Modifier.padding(start = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                "KALYNO".forEachIndexed { index, letter ->
                    val y = sin(phase + index * 0.65f) * amplitude

                    Text(
                        text = letter.toString(),
                        color = KalynoBlue,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(
                            top = (12f + y).dp,
                            bottom = (12f - y).dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposeSplash() {
    SideEffect { splashDrawn = true }

    val transition = rememberInfiniteTransition(label = "kalyno-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave-phase"
    )

    var waveOn by remember { mutableStateOf(false) }
    val amplitude by animateFloatAsState(
        targetValue = if (waveOn) 8f else 0f,
        animationSpec = tween(500),
        label = "wave-amplitude"
    )
    LaunchedEffect(Unit) { waveOn = true }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .scale(0.8f)
    ) {
        val centerX = maxWidth / 2

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = centerX - 113.dp)
        ) {
            BookLogo()
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = centerX - 19.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            "KALYNO".forEachIndexed { index, letter ->
                val y = sin(phase + index * 0.65f) * amplitude

                Text(
                    text = letter.toString(),
                    color = KalynoBlue,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(
                        top = (12f + y).dp,
                        bottom = (12f - y).dp
                    )
                )
            }
        }
    }
}
