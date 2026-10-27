package ci.kalyno.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.auth.EmailStage
import ci.kalyno.app.ui.theme.*

/** Connexion pour un élève qui a déjà un compte (même flux Google que l'inscription). */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onGoogleClick: () -> Unit,
    emailStage: EmailStage? = null,
    pendingEmail: String = "",
    onStartEmailFlow: () -> Unit = {},
    onSendEmail: (String) -> Unit = {},
    onVerifyCode: (String) -> Unit = {},
    onCancelEmailFlow: () -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = KalynoMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "KALYNO",
                color = KalynoBlue,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "👋 Content de te revoir !",
                color = KalynoText,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Connecte-toi avec Google pour retrouver ta progression.",
                color = KalynoMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            ci.kalyno.app.ui.auth.AuthMethodSection(
                emailStage = emailStage,
                pendingEmail = pendingEmail,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onStartEmailFlow = onStartEmailFlow,
                onSendEmail = onSendEmail,
                onVerifyCode = onVerifyCode,
                onCancelEmailFlow = onCancelEmailFlow,
                onGoogleClick = onGoogleClick,
                googleLabel = "Continuer avec Google"
            )
        }
    }
}
