package ci.kalyno.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KalynoBlue = Color(0xFF0F766E)
val KalynoLightBlue = Color(0xFF14B8A6)
val KalynoGreen = Color(0xFF15966A)
val KalynoYellow = Color(0xFFF59E0B)
val KalynoPurple = Color(0xFF8457D9)
val KalynoBackground = Color(0xFFF8FAFC)
val KalynoText = Color(0xFF0F172A)

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
