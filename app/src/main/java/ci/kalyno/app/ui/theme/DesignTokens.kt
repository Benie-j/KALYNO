package ci.kalyno.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Jetons de design complémentaires (en plus de Palette.kt et KalynoTheme.kt).
 * Mode clair : fond #F8FAFC, cartes blanches, Deep Teal #0F766E, accents orange.
 * Mode sombre automatique : mêmes teintes, fonds ardoise profonds, textes clairs.
 */

// Surfaces : cartes, champs, feuilles (blanc en clair, ardoise en sombre)
val KalynoSurface: Color get() = kc(0xFFFFFFFF, 0xFF141E30)
// Puces / fonds neutres (ex. #F1F5F9 en clair)
val KalynoSurfaceVariant: Color get() = kc(0xFFF1F5F9, 0xFF1B2740)

// Accent chaleureux : flammes, séries, trophées, indices
val KalynoAmberSoft: Color get() = kc(0xFFFEF3C7, 0xFF3A2A0B)
val KalynoAmberDeep: Color get() = kc(0xFFB45309, 0xFFFBBF24)
val KalynoAmberBorder: Color get() = kc(0xFFFDE68A, 0xFF6B4E10)
val KalynoHighlight: Color get() = kc(0xFFFFF0B3, 0xFF5B4A12)

// "Oublié" : alerte subtile (jamais agressive)
val KalynoDanger: Color get() = kc(0xFFB91C1C, 0xFFF87171)
val KalynoDangerSoft: Color get() = kc(0xFFFEE2E2, 0xFF3B1618)
val KalynoDangerBorder: Color get() = kc(0xFFFECACA, 0xFF7F1D1D)

// Fonds de bulles / chips colorés
val KalynoSkySoft: Color get() = kc(0xFFE0F2FE, 0xFF0C2A3D)
val KalynoPurpleSoft: Color get() = kc(0xFFEDE9FE, 0xFF2A2147)
val KalynoBlueChipSoft: Color get() = kc(0xFFDBEAFE, 0xFF12284A)

// Squelettes shimmer
val KalynoShimmerBase: Color get() = kc(0xFFE2E8F0, 0xFF1F2B42)
val KalynoShimmerHighlight: Color get() = kc(0xFFF1F5F9, 0xFF2A3854)

// Dégradé des bannières (Deep Teal -> teal plus sombre)
val KalynoTealDark: Color get() = kc(0xFF115E59, 0xFF0F766E)

// Ombre diffuse des cartes
val KalynoShadow: Color get() = kc(0x1F0F172A, 0x66000000)

/** Fond/bordure venant du contenu admin (pensé pour fond clair) : atténué en mode sombre. */
fun adaptContentBackground(c: Color): Color =
    if (!KalynoThemeState.isDark || c.alpha < 0.05f || c.luminance() < 0.5f) c
    else lerp(KalynoSurface, c.copy(alpha = 1f), 0.22f)

/** Couleur de texte venant du contenu admin : les teintes sombres sont éclaircies en mode sombre. */
fun adaptContentText(c: Color): Color =
    if (!KalynoThemeState.isDark || c.luminance() > 0.35f) c
    else lerp(c, Color.White, 0.6f)
