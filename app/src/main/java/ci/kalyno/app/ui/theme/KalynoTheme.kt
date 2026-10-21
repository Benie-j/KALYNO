package ci.kalyno.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KalynoBlue = Color(0xFF2457C5)
val KalynoLightBlue = Color(0xFF4778D8)
val KalynoGreen = Color(0xFF15966A)
val KalynoYellow = Color(0xFFFFC857)
val KalynoPurple = Color(0xFF8457D9)
val KalynoBackground = Color(0xFFF5F8FF)
val KalynoText = Color(0xFF202B45)

private val KalynoColors = lightColorScheme(
    primary = KalynoBlue,
    secondary = KalynoGreen,
    tertiary = KalynoPurple,
    background = KalynoBackground,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = KalynoText,
    onSurface = KalynoText
)

@Composable
fun KalynoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KalynoColors,
        content = content
    )
}
