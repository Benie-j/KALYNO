package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.ExplorerBanner
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerSoft
import java.text.Normalizer

// ---------------------------------------------------------------------------
// Smart Tags : étiquettes choisies par l'admin sur chaque chapitre
// ---------------------------------------------------------------------------

private data class TagStyle(val label: String, val container: Color, val content: Color)

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

/**
 * Valeurs connues ("urgent", "populaire", "nouveau") : style dédié.
 * Toute autre valeur saisie par l'admin s'affiche telle quelle (emoji compris).
 */
private fun tagStyleFor(raw: String): TagStyle {
    val key = Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
    return when (key) {
        "urgent" -> TagStyle("🔴 Urgent", KalynoDangerSoft, KalynoDanger)
        "populaire", "popular", "hot" -> TagStyle("⚡ Populaire", KalynoAmberSoft, KalynoAmberDeep)
        "nouveau", "new" -> TagStyle("✨ Nouveau", KalynoBlueSoft, KalynoBlue)
        else -> TagStyle(raw.trim(), KalynoBlueSoft, KalynoBlue)
    }
}

/** Pastille de Smart Tag ; n'affiche rien si l'admin n'en a pas défini. */
@Composable
fun SmartTagPill(tag: String?, modifier: Modifier = Modifier) {
    val raw = tag?.trim().orEmpty()
    if (raw.isEmpty()) return
    val style = tagStyleFor(raw)
    KalynoPill(
        text = style.label,
        modifier = modifier,
        container = style.container,
        content = style.content,
        fontSize = 11
    )
}

// ---------------------------------------------------------------------------
// Hero Banner : mission ou défi du moment, entièrement piloté par l'admin
// ---------------------------------------------------------------------------

@Composable
fun ExplorerHeroBanner(
    banner: ExplorerBanner,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()

    KalynoCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick?.let { action ->
            {
                haptic()
                action()
            }
        },
        background = KalynoBlue,
        borderColor = KalynoBlue,
        contentPadding = 22.dp
    ) {
        KalynoPill(
            text = "À LA UNE",
            container = KalynoAmberSoft,
            content = KalynoAmberDeep,
            fontSize = 11
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = banner.title,
            color = Color.White,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (!banner.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = banner.subtitle,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        if (onClick != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = banner.buttonLabel?.takeIf { it.isNotBlank() } ?: "Découvrir",
                color = KalynoBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 11.dp)
            )
        }
    }
}
