package ci.kalyno.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import ci.kalyno.app.ui.theme.KalynoOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Levels
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrect
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerBorder
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow

// Fonds de bulles d'icônes (doux, jamais criards)
private val SkyBubble = Color(0xFFE0F2FE)
private val SlateBubble = Color(0xFFF1F5F9)

/**
 * Écran Profil & Paramètres ("Settings Hub").
 *
 * Écran sans état : toute la navigation sort par callbacks, MainScreen décide quoi ouvrir.
 *
 * @param userEmail e-mail du compte connecté. `null` = invité / non connecté : aucun statut
 *                  de synchronisation n'est alors affiché (ni check vert, ni "sauvegarde active").
 */
@Composable
fun ProfileScreen(
    levelId: String,
    nickname: String,
    streak: Int,
    userEmail: String?,
    lastSyncMillis: Long = 0L,
    onNicknameChange: (String) -> Unit,
    onChangeLevel: () -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenHelp: () -> Unit,
    onContactSupport: () -> Unit,
    onReportIssue: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var showNicknameDialog by rememberSaveable { mutableStateOf(false) }
    val isConnected = userEmail != null

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // ----- A. En-tête du profil -----
        item {
            ProfileHeader(
                nickname = nickname,
                classLabel = Levels.label(levelId),
                streak = streak,
                onEditNickname = { showNicknameDialog = true }
            )
        }

        // ----- B. Mon compte -----
        item {
            SettingsGroup(title = "MON COMPTE") {
                SettingsItem(
                    icon = Icons.Default.School,
                    title = "Changer de classe",
                    subtitle = Levels.label(levelId),
                    iconBackground = KalynoBlueSoft,
                    onClick = onChangeLevel
                )
                CloudStatusItem(userEmail = userEmail, lastSyncMillis = lastSyncMillis, onLoginClick = onLoginClick)
            }
        }

        // ----- C. Aide & assistance -----
        item {
            SettingsGroup(title = "AIDE & ASSISTANCE") {
                SettingsItem(
                    icon = Icons.Default.Lightbulb,
                    title = "Centre d'aide & FAQ",
                    subtitle = "Comprendre l'Active Recall et le SRS",
                    iconBackground = KalynoBlueSoft,
                    onClick = onOpenHelp
                )
                SettingsItem(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = "Contacter l'assistance",
                    subtitle = "Échange avec l'équipe KALYNO",
                    iconBackground = SkyBubble,
                    onClick = onContactSupport
                )
                SettingsItem(
                    icon = Icons.Default.BugReport,
                    title = "Signaler un bug ou un contenu",
                    subtitle = "Une erreur dans un cours ? Dis-le-nous",
                    iconBackground = KalynoAmberSoft,
                    iconTint = KalynoAmberDeep,
                    onClick = onReportIssue,
                    showDivider = false
                )
            }
        }

        // ----- D. Légal (la version de l'app vit désormais sur le splash) -----
        item {
            SettingsGroup(title = "LÉGAL") {
                SettingsItem(
                    icon = Icons.Default.Description,
                    title = "Conditions Générales d'Utilisation",
                    iconBackground = SlateBubble,
                    iconTint = KalynoMuted,
                    onClick = onOpenTerms
                )
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Politique de confidentialité",
                    iconBackground = SlateBubble,
                    iconTint = KalynoMuted,
                    onClick = onOpenPrivacy,
                    showDivider = false
                )
            }
        }

        // ----- E. Déconnexion (ou connexion pour un invité) -----
        item {
            if (isConnected) {
                LogoutButton(label = "Se déconnecter", onClick = { showLogoutDialog = true })
            } else {
                KalynoPrimaryButton(label = "SE CONNECTER", onClick = onLoginClick)
            }
        }
    }

    if (showNicknameDialog) {
        NicknameEditDialog(
            current = nickname,
            onDismiss = { showNicknameDialog = false },
            onSave = {
                showNicknameDialog = false
                onNicknameChange(it)
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = Color.White,
            title = { Text("Se déconnecter ?", fontWeight = FontWeight.ExtraBold, color = KalynoText) },
            text = {
                Text(
                    "Tu pourras te reconnecter à tout moment pour retrouver ta progression.",
                    color = KalynoMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogoutClick()
                }) {
                    Text("Se déconnecter", color = KalynoDanger, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Annuler", color = KalynoMuted, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ===========================================================================
// CLOUD : jamais de faux statut
// ===========================================================================

/**
 * Carte Cloud, trois états exclusifs :
 * - non connecté : CloudOff gris, aucun badge, cliquable vers la connexion ;
 * - connecté + au moins une synchro réussie : seul cas où le vert est permis ;
 * - connecté sans synchro encore : neutre, sans check.
 */
@Composable
private fun CloudStatusItem(userEmail: String?, lastSyncMillis: Long, onLoginClick: () -> Unit) {
    when {
        userEmail == null -> SettingsItem(
            icon = Icons.Default.CloudOff,
            title = "Compte non connecté",
            subtitle = "Connecte-toi pour sauvegarder ta progression",
            iconBackground = SlateBubble,
            iconTint = KalynoMuted,
            onClick = onLoginClick,
            showDivider = false
        )
        lastSyncMillis > 0L -> SettingsItem(
            icon = Icons.Default.Sync,
            title = "Synchronisation Cloud",
            subtitle = userEmail,
            iconBackground = KalynoCorrectSoft,
            iconTint = KalynoCorrect,
            trailing = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Synchronisation active",
                    tint = KalynoCorrect,
                    modifier = Modifier.size(26.dp)
                )
            },
            showDivider = false
        )
        else -> SettingsItem(
            icon = Icons.Default.Sync,
            title = "Connecté, synchro en attente",
            subtitle = userEmail,
            iconBackground = SlateBubble,
            iconTint = KalynoMuted,
            showDivider = false
        )
    }
}

// ===========================================================================
// EN-TÊTE
// ===========================================================================

@Composable
private fun ProfileHeader(nickname: String, classLabel: String, streak: Int, onEditNickname: () -> Unit) {
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar : bordure Deep Teal + fond teinté + initiale
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .border(3.dp, KalynoBlue, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(KalynoBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = nickname.trim().take(1).uppercase().ifEmpty { "K" },
                    color = KalynoBlue,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nickname.ifBlank { "Élève" },
                    color = KalynoText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                IconButton(onClick = onEditNickname) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Modifier mon pseudo",
                        tint = KalynoBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            KalynoPill(text = classLabel, fontSize = 13)
            Spacer(Modifier.height(14.dp))
            StreakChip(streak)
        }
    }
}

/** Badge de série : flamme vectorielle + accent orange chaleureux. */
@Composable
private fun StreakChip(streak: Int) {
    val label = when (streak) {
        0 -> "Lance ta série aujourd'hui"
        1 -> "1 jour de série"
        else -> "$streak jours de série"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(KalynoAmberSoft)
            .border(1.dp, KalynoYellow, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = null,
            tint = KalynoAmberDeep,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = KalynoAmberDeep,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ===========================================================================
// DÉCONNEXION : rouge doux, sans agresser
// ===========================================================================

@Composable
private fun LogoutButton(label: String, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, KalynoDangerBorder, shape)
            .clickable(role = Role.Button) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = KalynoDanger,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ===========================================================================
// MODALE D'ÉDITION DU PSEUDO
// ===========================================================================

@Composable
private fun NicknameEditDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    val clean = text.trim()
    val valid = clean.length in 2..16

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Modifier ton pseudo", fontWeight = FontWeight.ExtraBold, color = KalynoText) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(16) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                supportingText = { Text("2 à 16 caractères", color = KalynoMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onSave(clean) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KalynoBlue,
                    unfocusedBorderColor = KalynoOutline,
                    cursorColor = KalynoBlue,
                    focusedTextColor = KalynoText,
                    unfocusedTextColor = KalynoText
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(clean) }, enabled = valid) {
                Text(
                    "Enregistrer",
                    color = if (valid) KalynoBlue else KalynoMuted,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = KalynoMuted, fontWeight = FontWeight.Bold)
            }
        }
    )
}
