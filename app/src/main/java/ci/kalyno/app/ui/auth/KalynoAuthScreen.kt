package ci.kalyno.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.icons.GoogleLogoVector
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoRetryText
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoTheme

private val Surface = Color.White
private val ButtonShape = RoundedCornerShape(24.dp)
private val FieldShape = RoundedCornerShape(24.dp)

/**
 * Écran unifié Connexion / Inscription (le compte est créé à la première connexion).
 *
 * Mêmes paramètres que l'ancien LoginScreen : remplacement direct dans KalynoApp.
 * - Voie 1 : Google (Credential Manager -> Supabase)
 * - Voie 2 : e-mail + code à 6 chiffres (téléphones partagés)
 */
@Composable
fun KalynoAuthScreen(
    onGoogleClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    emailStage: EmailStage? = null,
    pendingEmail: String = "",
    @Suppress("UNUSED_PARAMETER") onStartEmailFlow: () -> Unit = {},
    onSendEmail: (String) -> Unit = {},
    onVerifyCode: (String) -> Unit = {},
    onCancelEmailFlow: () -> Unit = {},
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        // Barre supérieure : retour optionnel
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = KalynoMuted)
                }
            }
        }

        // Zone centrale (scrollable si le clavier réduit l'espace)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val minHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AuthHeader()
                    Spacer(Modifier.height(40.dp))

                    if (emailStage == EmailStage.ENTER_CODE) {
                        CodeStep(
                            email = pendingEmail,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onVerify = onVerifyCode,
                            onChangeEmail = onCancelEmailFlow
                        )
                    } else {
                        GoogleSignInButton(isLoading = isLoading, onClick = onGoogleClick)
                        Spacer(Modifier.height(24.dp))
                        OrDivider()
                        Spacer(Modifier.height(24.dp))
                        EmailField(isLoading = isLoading, onSubmit = onSendEmail)
                        ErrorText(errorMessage)
                    }
                }
            }
        }

        LegalFooter(onTermsClick = onTermsClick, onPrivacyClick = onPrivacyClick)
    }
}

// ------------------------------------------------------------------ En-tête

@Composable
private fun AuthHeader() {
    Text(
        text = "KALYNO",
        color = KalynoBlue,
        fontSize = 40.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 4.sp
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = "L'appli des élèves qui cartonnent au BEPC et au BAC",
        color = KalynoText,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}

// ------------------------------------------------------------ Bouton Google

@Composable
private fun GoogleSignInButton(isLoading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(ButtonShape)
            .background(Surface)
            .border(BorderStroke(1.dp, KalynoOutline), ButtonShape)
            .clickable(enabled = !isLoading, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = KalynoBlue
            )
        } else {
            Icon(
                imageVector = GoogleLogoVector,
                contentDescription = null,
                tint = Color.Unspecified, // conserve les 4 couleurs officielles
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Continuer avec Google",
                color = KalynoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------------------------------------------------------------- Séparateur

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(1.dp).background(KalynoOutline))
        Text(
            text = "ou",
            color = KalynoMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Box(Modifier.weight(1f).height(1.dp).background(KalynoOutline))
    }
}

// --------------------------------------------------------------- Champ e-mail

@Composable
private fun EmailField(isLoading: Boolean, onSubmit: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val canSend = email.isNotBlank() && !isLoading
    val submit = {
        if (canSend) {
            focus.clearFocus()
            onSubmit(email)
        }
    }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Saisissez votre e-mail", color = KalynoMuted) },
        leadingIcon = {
            Icon(Icons.Outlined.Email, contentDescription = null, tint = KalynoMuted)
        },
        trailingIcon = {
            IconButton(
                onClick = submit,
                enabled = canSend,
                modifier = Modifier.padding(end = 6.dp).size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = KalynoBlue,
                    contentColor = Color.White,
                    disabledContainerColor = KalynoOutline,
                    disabledContentColor = KalynoMuted
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = KalynoBlue
                    )
                } else {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Recevoir le code",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { submit() }),
        colors = authFieldColors()
    )
}

// ------------------------------------------------------------- Étape du code

@Composable
private fun CodeStep(
    email: String,
    isLoading: Boolean,
    errorMessage: String?,
    onVerify: (String) -> Unit,
    onChangeEmail: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val canVerify = code.length >= 4 && !isLoading
    val verify = {
        if (canVerify) {
            focus.clearFocus()
            onVerify(code)
        }
    }

    Text(
        text = "Vérifiez votre boîte mail",
        color = KalynoText,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Code envoyé à $email",
        color = KalynoMuted,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(20.dp))

    OutlinedTextField(
        value = code,
        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Code à 6 chiffres", color = KalynoMuted) },
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { verify() }),
        colors = authFieldColors()
    )
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = verify,
        enabled = canVerify,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = KalynoBlue,
            contentColor = Color.White,
            disabledContainerColor = KalynoOutline,
            disabledContentColor = KalynoMuted
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = Color.White)
        } else {
            Text("Valider le code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    ErrorText(errorMessage)
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onChangeEmail, enabled = !isLoading) {
        Text("Changer d'adresse", color = KalynoMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ------------------------------------------------------------------- Footer

@Composable
private fun LegalFooter(onTermsClick: () -> Unit, onPrivacyClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "En continuant, vous acceptez nos",
            color = KalynoMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegalLink("conditions d'utilisation", onTermsClick)
            Text("  et  ", color = KalynoMuted, fontSize = 12.sp)
            LegalLink("politique de confidentialité", onPrivacyClick)
        }
    }
}

@Composable
private fun LegalLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = KalynoMuted,
        fontSize = 12.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)
    )
}

// ------------------------------------------------------------------ Communs

@Composable
private fun ErrorText(message: String?) {
    if (message == null) return
    Spacer(Modifier.height(12.dp))
    Text(
        text = message,
        color = KalynoRetryText,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = KalynoBlue,
    unfocusedBorderColor = KalynoOutline,
    focusedContainerColor = Surface,
    unfocusedContainerColor = Surface,
    cursorColor = KalynoBlue,
    focusedLeadingIconColor = KalynoBlue,
    focusedTextColor = KalynoText,
    unfocusedTextColor = KalynoText
)

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun KalynoAuthScreenPreview() {
    KalynoTheme { KalynoAuthScreen(onGoogleClick = {}, onBack = {}) }
}
