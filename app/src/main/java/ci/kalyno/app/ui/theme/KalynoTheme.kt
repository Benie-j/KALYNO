package ci.kalyno.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Mode clair / sombre automatique (suit le réglage du téléphone).
 * L'état est observable : tous les jetons de couleur ci-dessous lisent [isDark],
 * donc l'interface se met à jour sans qu'aucun écran n'ait à être modifié.
 */
object KalynoThemeState {
    var isDark by mutableStateOf(false)
}

/** Choisit la couleur selon le mode courant (clair, sombre). */
internal fun kc(light: Long, dark: Long): Color =
    Color(if (KalynoThemeState.isDark) dark else light)

// Palette de marque (clair -> sombre)
val KalynoBlue: Color get() = kc(0xFF0F766E, 0xFF0D9488)
val KalynoLightBlue: Color get() = kc(0xFF14B8A6, 0xFF2DD4BF)
val KalynoGreen: Color get() = kc(0xFF15966A, 0xFF22B07D)
val KalynoYellow: Color get() = kc(0xFFF59E0B, 0xFFF59E0B)
val KalynoPurple: Color get() = kc(0xFF8457D9, 0xFFA78BFA)
val KalynoBackground: Color get() = kc(0xFFF8FAFC, 0xFF0B1220)
val KalynoText: Color get() = kc(0xFF0F172A, 0xFFF1F5F9)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    secondary = Color(0xFF15966A),
    tertiary = Color(0xFF8457D9),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0D9488),
    secondary = Color(0xFF22B07D),
    tertiary = Color(0xFFA78BFA),
    background = Color(0xFF0B1220),
    surface = Color(0xFF141E30),
    surfaceVariant = Color(0xFF1B2740),
    onPrimary = Color.White,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF263349)
)

@Composable
fun KalynoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Écrit avant que les enfants ne lisent les jetons (même valeur = aucun effet)
    KalynoThemeState.isDark = dark
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
