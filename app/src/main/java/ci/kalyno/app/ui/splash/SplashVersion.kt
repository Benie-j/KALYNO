package ci.kalyno.app.ui.splash

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.profile.appVersionName
import ci.kalyno.app.ui.theme.KalynoMuted

/**
 * Version de l'app, discrète, tout en bas du splash Compose.
 * Lue dans le build (versionName) : aucune valeur en dur, s'incrémente avec le projet.
 */
@Composable
fun SplashVersionFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember { context.appVersionName() }
    if (version.isBlank()) return
    Text(
        text = "Version $version",
        color = KalynoMuted.copy(alpha = 0.7f),
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    )
}
