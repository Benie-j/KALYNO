package ci.kalyno.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText

// Ombre très diffuse (elevation 1.dp)
private val CardShadow = Color(0x140F172A)

/** Conteneur blanc : coins 20 dp, bordure #E2E8F0, ombre légère. Le contenu est clippé (ripple arrondi). */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, shape, ambientColor = CardShadow, spotColor = CardShadow)
            .background(Color.White, shape)
            .border(1.dp, KalynoOutline, shape)
            .clip(shape),
        content = content
    )
}

/**
 * Bloc de réglages : titre de section (petites capitales grises) + carte blanche
 * contenant des [SettingsItem].
 */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = KalynoMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        SettingsCard(content = content)
    }
}

/**
 * Une ligne de réglage : icône (emoji) dans une bulle colorée, titre, sous-titre optionnel,
 * puis [trailing] ou une flèche si la ligne est cliquable (ripple + vibration légère).
 *
 * [showDivider] : filet fin sous la ligne (à désactiver sur la dernière ligne du groupe).
 */
@Composable
fun SettingsItem(
    icon: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconBackground: Color = KalynoBlueSoft,
    titleColor: Color = KalynoText,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true
) {
    val haptic = rememberHaptic()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button) {
                            haptic()
                            onClick()
                        }
                    } else {
                        Modifier
                    }
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 18.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = KalynoMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            } else if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "›",
                    color = KalynoMuted,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 70.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(KalynoOutline.copy(alpha = 0.7f))
            )
        }
    }
}
