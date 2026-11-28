package ci.kalyno.app.ui.profile

import ci.kalyno.app.data.badges.StreakGoalState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Levels
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.entrance
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
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow
import androidx.compose.material.icons.filled.Flag
import ci.kalyno.app.ui.report.ReportUnreadBadge

private val SkyBubble = Color(0xFFE0F2FE)
private val SlateBubble = Color(0xFFF1F5F9)

private const val NICKNAME_MIN = 2
private const val NICKNAME_MAX = 16

/**
 * Écran Profil & Paramètres.
 *
 * Écran sans état : toute la navigation sort par callbacks.
 *
 * @param userEmail e-mail du compte connecté. `null` = invité / non connecté : aucun badge de
 *                  synchronisation n'est alors affiché (ni check vert, ni "sauvegarde active").
 * @param onNicknameChange appelé avec le nouveau pseudo, déjà nettoyé (trim, 2 à 16 caractères).
 */
@Composable
fun ProfileScreen(
    levelId: String,
    nickname: String,
    streak: Int,
    userEmail: String?,
    onNicknameChange: (String) -> Unit,
    onChangeLevel: () -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenHelp: () -> Unit,
    onContactSupport: () -> Unit,
    onReportIssue: () -> Unit,
    onOpenMyReports: () -> Unit,
    unreadReplies: Int,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    retentionScore: Float? = null,
    masteredCount: Int? = null,
    onOpenProgress: (() -> Unit)? = null,
    streakGoals: List<StreakGoalState> = emptyList()
) {
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var showNicknameDialog by rememberSaveable { mutableStateOf(false) }
    val isConnected = userEmail != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ----- A. Carte héros : avatar, pseudo, classe, statut du compte -----
        ProfileHero(
            nickname = nickname,
            classLabel = Levels.label(levelId),
            isConnected = isConnected,
            retentionScore = retentionScore,
            onEditNickname = { showNicknameDialog = true },
            modifier = Modifier.entrance(index = 0)
        )

        // ----- B. Les chiffres qui comptent + objectif de série -----
        ProfileStatsRow(
            streak = streak,
            masteredCount = masteredCount,
            retentionScore = retentionScore,
            modifier = Modifier.entrance(index = 1)
        )
        StreakMilestoneCard(streak = streak, goals = streakGoals, modifier = Modifier.entrance(index = 2))
        if (onOpenProgress != null) {
            ProgressLinkCard(onClick = onOpenProgress, modifier = Modifier.entrance(index = 3))
        }

        // ----- C. Mon compte -----
        SettingsGroup(title = "MON COMPTE", modifier = Modifier.entrance(index = 4)) {
            CloudStatusItem(userEmail = userEmail, onLoginClick = onLoginClick)
            SettingsItem(
                icon = Icons.Default.School,
                title = "Changer de classe",
                subtitle = Levels.label(levelId),
                iconBackground = KalynoBlueSoft,
                onClick = onChangeLevel,
                showDivider = false
            )
        }

        // ----- D. Aide & assistance -----
        SettingsGroup(title = "AIDE & ASSISTANCE", modifier = Modifier.entrance(index = 5)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionTile(
                    icon = Icons.Default.Lightbulb,
                    title = "Centre d'aide & FAQ",
                    subtitle = "Comprendre l'Active Recall et le SRS",
                    background = KalynoBlueSoft,
                    onClick = onOpenHelp,
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = "Contacter l'assistance",
                    subtitle = "Échange avec l'équipe KALYNO",
                    background = SkyBubble,
                    onClick = onContactSupport,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(KalynoOutline.copy(alpha = 0.7f))
            )
            SettingsItem(
                icon = Icons.Default.BugReport,
                title = "Signaler un bug ou un contenu",
                subtitle = "Une erreur dans un cours ? Dis-le-nous",
                iconBackground = KalynoAmberSoft,
                iconTint = KalynoAmberDeep,
                onClick = onReportIssue
            )
            SettingsItem(
                icon = Icons.Default.Flag,
                title = "Mes signalements",
                subtitle = if (unreadReplies > 0) {
                    if (unreadReplies == 1) "1 nouvelle réponse de l'équipe" else "$unreadReplies nouvelles réponses de l'équipe"
                } else {
                    "Suis ce que tu as signalé"
                },
                iconBackground = KalynoAmberSoft,
                iconTint = KalynoAmberDeep,
                onClick = onOpenMyReports,
                trailing = if (unreadReplies > 0) ({ ReportUnreadBadge(unreadReplies) }) else null,
                showDivider = false
            )
        }

        // ----- E. Légal (volontairement plus discret) -----
        SettingsGroup(title = "LÉGAL", modifier = Modifier.entrance(index = 6)) {
            SettingsItem(
                icon = Icons.Default.Description,
                title = "Conditions Générales d'Utilisation",
                iconBackground = SlateBubble,
                iconTint = KalynoMuted,
                onClick = onOpenTerms,
                compact = true
            )
            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Politique de confidentialité",
                iconBackground = SlateBubble,
                iconTint = KalynoMuted,
                onClick = onOpenPrivacy,
                showDivider = false,
                compact = true
            )
        }

        // ----- Version de l'application (discrète, lue dans le build) -----
        val version = LocalContext.current.appVersionName()
        if (version.isNotBlank()) {
            Text(
                text = "Version $version",
                color = KalynoMuted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ----- F. Déconnexion (ou connexion pour un invité) -----
        if (isConnected) {
            LogoutButton(label = "Se déconnecter", onClick = { showLogoutDialog = true })
        } else {
            KalynoPrimaryButton(label = "SE CONNECTER", onClick = onLoginClick)
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
 * Carte Cloud, deux états exclusifs :
 * - connecté (`userEmail != null`) : e-mail + icône de synchro + CheckCircle vert ;
 * - invité : "Compte non connecté", icône neutre CloudOff, aucun badge vert, cliquable vers la connexion.
 */
@Composable
private fun CloudStatusItem(userEmail: String?, onLoginClick: () -> Unit) {
    if (userEmail != null) {
        SettingsItem(
            icon = Icons.Default.Sync,
            title = "Synchronisation Cloud",
            subtitle = userEmail,
            iconBackground = KalynoCorrectSoft,
            iconTint = KalynoCorrect,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(KalynoCorrectSoft)
                        .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    PulseDot(color = KalynoCorrect, size = 7.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Actif",
                        color = KalynoCorrect,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        )
    } else {
        SettingsItem(
            icon = Icons.Default.CloudOff,
            title = "Compte non connecté",
            subtitle = "Connecte-toi pour sauvegarder ta progression",
            iconBackground = SlateBubble,
            iconTint = KalynoMuted,
            onClick = onLoginClick,
            trailing = {
                Text(
                    text = "Se connecter",
                    color = KalynoAmberDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(KalynoAmberSoft)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        )
    }
}

// ===========================================================================
// MODALE D'ÉDITION DU PSEUDO
// ===========================================================================

@Composable
private fun NicknameEditDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(current) }
    val clean = text.trim()
    val valid = clean.length in NICKNAME_MIN..NICKNAME_MAX

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("Modifier ton pseudo", fontWeight = FontWeight.ExtraBold, color = KalynoText)
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(NICKNAME_MAX) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Ton pseudo") },
                supportingText = { Text("$NICKNAME_MIN à $NICKNAME_MAX caractères", color = KalynoMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onSave(clean) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KalynoBlue,
                    unfocusedBorderColor = KalynoOutline,
                    cursorColor = KalynoBlue,
                    focusedTextColor = KalynoText,
                    unfocusedTextColor = KalynoText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
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

// ===========================================================================
// DÉCONNEXION : rouge doux, sans agresser
// ===========================================================================

@Composable
private fun LogoutButton(label: String, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressScale(interaction)
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, KalynoDangerBorder, shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button
            ) {
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
