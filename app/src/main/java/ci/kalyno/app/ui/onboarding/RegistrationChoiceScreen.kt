package ci.kalyno.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.theme.KalynoBackground   // #F8FAFC
import ci.kalyno.app.ui.theme.KalynoBlue         // #0F766E (Deep Teal)
import ci.kalyno.app.ui.theme.KalynoMuted        // ardoise adoucie (sous-titre)
import ci.kalyno.app.ui.theme.KalynoOutline      // bordure fine de la carte
import ci.kalyno.app.ui.theme.KalynoRetryText    // message d'erreur (ton orangé)
import ci.kalyno.app.ui.theme.KalynoText         // #0F172A (Ardoise)
import ci.kalyno.app.ui.theme.KalynoTheme
import ci.kalyno.app.ui.theme.KalynoYellow       // #F59E0B (accent orange)

/**
 * Écran de choix d'inscription, affiché juste après le test de découverte.
 *
 * @param pseudo        pseudo choisi par l'élève (affiché dans le message de félicitation)
 * @param onGoogleClick appelé quand l'élève choisit « S'inscrire avec Google »
 * @param onGuestClick  appelé quand l'élève choisit « Continuer en tant qu'invité »
 * @param isLoading     vrai pendant la connexion Google (désactive les boutons)
 * @param errorMessage  message d'erreur éventuel à afficher sous le bouton Google
 */
@Composable
fun RegistrationChoiceScreen(
    pseudo: String,
    onGoogleClick: () -> Unit,
    onGuestClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp) // reste lisible sur tablette
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, KalynoOutline)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()) // petits écrans / grande police
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(KalynoYellow.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🏆", fontSize = 44.sp)
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = if (pseudo.isBlank()) "Félicitation !" else "Félicitation $pseudo !",
                    style = MaterialTheme.typography.headlineSmall, // équivalent M3 de h5 (24sp)
                    color = KalynoText,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Bienvenue parmi nous !",
                    style = MaterialTheme.typography.titleMedium,
                    color = KalynoMuted,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Tu t'inscris pour garder ta progression ou tu veux continuer en privé ?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = KalynoText,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(28.dp))

                // ----- Bouton principal : Google -----
                Button(
                    onClick = onGoogleClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KalynoBlue,
                        contentColor = Color.White,
                        disabledContainerColor = KalynoBlue.copy(alpha = 0.6f),
                        disabledContentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White
                        )
                    } else {
                        GoogleGlyph()
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (isLoading) "Connexion…" else "S'inscrire avec Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = KalynoRetryText,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ----- Bouton secondaire : invité (discret) -----
                TextButton(
                    onClick = onGuestClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = KalynoText)
                ) {
                    Text(
                        text = "Continuer en tant qu'invité",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Pastille blanche avec un « G » : évite d'ajouter une dépendance d'icônes.
 * Pour la version finale, remplace-la par le logo officiel de Google (charte de marque Google).
 */
@Composable
private fun GoogleGlyph() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(width = 1.5.dp, color = Color.White, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun RegistrationChoiceScreenPreview() {
    KalynoTheme {
        RegistrationChoiceScreen(
            pseudo = "Aya",
            onGoogleClick = {},
            onGuestClick = {}
        )
    }
}
