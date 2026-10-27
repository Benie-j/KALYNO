package ci.kalyno.app.ui.share

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ci.kalyno.app.data.dashboard.DailyQuote
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrect
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoPurple
import ci.kalyno.app.ui.theme.KalynoShadow
import ci.kalyno.app.ui.theme.KalynoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val NeutralChip = Color(0xFFF1F5F9)
private val FacebookBlue = Color(0xFF2563EB)
private val FacebookSoft = Color(0xFFDBEAFE)
private val PurpleSoft = Color(0xFFEDE9FE)

private data class ShareAction(
    val id: String,
    val label: String,
    val icon: String,
    val tint: Color,
    val container: Color,
    val needsImage: Boolean,
    val doneLabel: String? = null,
    val onClick: () -> Unit
)

/**
 * Bottom sheet de partage "façon Spotify" : aperçu de la carte KALYNO générée à la volée,
 * puis une grille d'actions (applications installées uniquement + galerie + copie du texte).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuoteShareSheet(quote: DailyQuote, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var bitmap by remember(quote) { mutableStateOf<Bitmap?>(null) }
    var file by remember(quote) { mutableStateOf<File?>(null) }
    // Retour visuel court sur une tuile : (identifiant, succès ?)
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // Génération à la volée : dessin hors thread principal, puis écriture dans le cache
    LaunchedEffect(quote) {
        val rendered = withContext(Dispatchers.Default) { QuoteCardRenderer.render(context, quote) }
        bitmap = rendered
        file = withContext(Dispatchers.IO) {
            runCatching { QuoteShareActions.writeCache(context, rendered) }.getOrNull()
        }
    }
    LaunchedEffect(status) {
        if (status != null) {
            delay(1800)
            status = null
        }
    }

    fun flash(id: String, ok: Boolean) {
        status = id to ok
        haptic.performHapticFeedback(if (ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }

    val shareVia: (String, List<String>) -> Unit = { id, packages ->
        file?.let { f ->
            val ok = QuoteShareActions.shareImage(context, QuoteShareActions.uriFor(context, f), packages)
            if (!ok) flash(id, false)
        }
    }

    val saveToGallery: () -> Unit = {
        bitmap?.let { bmp ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) { QuoteShareActions.saveToGallery(context, bmp) }
                flash("gallery", ok)
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveToGallery() else flash("gallery", false)
    }

    val whatsappPackages = remember { listOf("com.whatsapp", "com.whatsapp.w4b") }
    val smsPackage = remember { QuoteShareActions.defaultSmsPackage(context) }
    val hasWhatsapp = remember { QuoteShareActions.isInstalled(context, *whatsappPackages.toTypedArray()) }
    val hasInstagram = remember { QuoteShareActions.isInstalled(context, "com.instagram.android") }
    val hasFacebook = remember { QuoteShareActions.isInstalled(context, "com.facebook.katana") }
    val hasSnapchat = remember { QuoteShareActions.isInstalled(context, "com.snapchat.android") }

    val actions = buildList {
        if (hasWhatsapp) {
            add(ShareAction("whatsapp", "WhatsApp", "message-circle", KalynoCorrect, KalynoCorrectSoft, true) {
                shareVia("whatsapp", whatsappPackages)
            })
        }
        if (hasInstagram) {
            add(ShareAction("instagram", "Instagram", "instagram", KalynoPurple, PurpleSoft, true) {
                shareVia("instagram", listOf("com.instagram.android"))
            })
        }
        if (hasFacebook) {
            add(ShareAction("facebook", "Facebook", "facebook", FacebookBlue, FacebookSoft, true) {
                shareVia("facebook", listOf("com.facebook.katana"))
            })
        }
        if (hasSnapchat) {
            add(ShareAction("snapchat", "Snapchat", "ghost", KalynoAmberDeep, KalynoAmberSoft, true) {
                shareVia("snapchat", listOf("com.snapchat.android"))
            })
        }
        if (smsPackage != null) {
            add(ShareAction("sms", "SMS", "message-square", KalynoBlue, KalynoBlueSoft, true) {
                shareVia("sms", listOf(smsPackage))
            })
        }
        add(ShareAction("more", "Autres apps", "share", KalynoMuted, NeutralChip, true) {
            file?.let { f ->
                if (!QuoteShareActions.chooser(context, QuoteShareActions.uriFor(context, f))) flash("more", false)
            }
        })
        add(ShareAction("gallery", "Galerie", "download", KalynoBlue, KalynoBlueSoft, true, doneLabel = "Enregistré") {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                saveToGallery()
            }
        })
        add(ShareAction("copy", "Copier le texte", "copy", KalynoMuted, NeutralChip, false, doneLabel = "Copié") {
            flash("copy", QuoteShareActions.copyText(context, quote))
        })
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp
    val previewHeight = (screenHeight * 0.42f).coerceIn(240f, 460f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Partager la citation",
                color = KalynoText,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Une carte KALYNO prête à publier.",
                color = KalynoMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(18.dp))

            // Aperçu de la carte générée
            val previewShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .height(previewHeight)
                    .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                    .shadow(14.dp, previewShape, ambientColor = KalynoShadow, spotColor = KalynoShadow)
                    .clip(previewShape)
                    .background(NeutralChip)
            ) {
                Crossfade(targetState = bitmap, label = "sharePreview") { bmp ->
                    if (bmp == null) {
                        ShimmerBlock(Modifier.fillMaxSize(), shape = previewShape)
                    } else {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Aperçu de la carte de citation KALYNO",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 480.dp) 6 else 4
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    actions.chunked(columns).forEach { rowActions ->
                        Row(Modifier.fillMaxWidth()) {
                            rowActions.forEach { action ->
                                ShareTile(
                                    action = action,
                                    enabled = !action.needsImage || file != null,
                                    result = status?.takeIf { it.first == action.id }?.second,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        action.onClick()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowActions.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareTile(
    action: ShareAction,
    enabled: Boolean,
    result: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (result) {
        true -> action.doneLabel ?: action.label
        false -> "Échec"
        null -> action.label
    }
    val icon = when (result) {
        true -> if (action.doneLabel != null) "check" else action.icon
        false -> "x"
        null -> action.icon
    }
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClickLabel = action.label, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(action.container),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = icon, size = 26.dp, tint = action.tint)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = KalynoText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
