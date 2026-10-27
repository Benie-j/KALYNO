package ci.kalyno.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.GoogleLogo
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoRetryText
import ci.kalyno.app.ui.theme.KalynoText

/**
 * Bloc "double voie" partagé par Connexion et Inscription :
 * saisie e-mail -> code reçu, séparateur "OU", bouton Google officiel.
 * Aucune donnée en dur : tout passe par [EmailStage]/[onSendEmail]/[onVerifyCode] fournis par l'appelant.
 */
@Composable
fun AuthMethodSection(
    emailStage: EmailStage?,
    pendingEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    onStartEmailFlow: () -> Unit,
    onSendEmail: (String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onCancelEmailFlow: () -> Unit,
    onGoogleClick: () -> Unit,
    googleLabel: String
) {
    when (emailStage) {
        EmailStage.ENTER_CODE -> EmailCodeStep(
            email = pendingEmail,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onVerify = onVerifyCode,
            onBack = onCancelEmailFlow
        )

        EmailStage.ENTER_EMAIL -> EmailEntryStep(
            isLoading = isLoading,
            errorMessage = errorMessage,
            onSend = onSendEmail,
            onBack = onCancelEmailFlow
        )

        null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ----- Voie 1 : e-mail -----
            SecondaryButton(
                label = "Continuer avec cet e-mail",
                icon = { Icon(Icons.Default.Email, contentDescription = null, tint = KalynoBlue) },
                enabled = !isLoading,
                onClick = onStartEmailFlow
            )

            Spacer(Modifier.height(18.dp))
            OrSeparator()
            Spacer(Modifier.height(18.dp))

            // ----- Voie 2 : Google -----
            GoogleButton(label = googleLabel, isLoading = isLoading, onClick = onGoogleClick)

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(errorMessage, color = KalynoRetryText, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EmailEntryStep(
    isLoading: Boolean,
    errorMessage: String?,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ton.adresse@email.com") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = KalynoMuted) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KalynoBlue,
                unfocusedBorderColor = KalynoOutline,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onSend(email) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KalynoBlue, contentColor = Color.White)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = Color.White)
            } else {
                Text("Recevoir le code", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(10.dp))
            Text(errorMessage, color = KalynoRetryText, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onBack, enabled = !isLoading) {
            Text("Retour", color = KalynoMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmailCodeStep(
    email: String,
    isLoading: Boolean,
    errorMessage: String?,
    onVerify: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Code envoyé à $email",
            color = KalynoMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Code à 6 chiffres") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KalynoBlue,
                unfocusedBorderColor = KalynoOutline,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onVerify(code) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KalynoBlue, contentColor = Color.White)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = Color.White)
            } else {
                Text("Valider le code", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(10.dp))
            Text(errorMessage, color = KalynoRetryText, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onBack, enabled = !isLoading) {
            Text("Changer d'adresse", color = KalynoMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OrSeparator() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(1.dp).background(KalynoOutline))
        Text("  OU  ", color = KalynoMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f).height(1.dp).background(KalynoOutline))
    }
}

@Composable
private fun GoogleButton(label: String, isLoading: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, KalynoOutline, shape)
            .clickable(enabled = !isLoading, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = KalynoBlue)
        } else {
            GoogleLogo(size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(label, color = KalynoText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, KalynoOutline, shape)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(label, color = KalynoText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
