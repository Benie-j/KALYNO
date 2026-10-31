package ci.kalyno.app.ui.report

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.reports.ReportCategory
import ci.kalyno.app.data.reports.ReportEntity
import ci.kalyno.app.data.reports.ReportSourceType
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Écran « Mes signalements » : historique local, statut suivi par l'équipe et réponses reçues. */
@Composable
fun MyReportsScreen(onBack: () -> Unit, viewModel: MyReportsViewModel = viewModel()) {
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val signedIn = remember { viewModel.isSignedIn() }

    LaunchedEffect(Unit) { viewModel.refresh() }
    // Les réponses restent marquées « Nouveau » pendant la visite, puis comptées comme lues en sortant
    DisposableEffect(Unit) { onDispose { viewModel.markSeen() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar(onBack = onBack, refreshing = refreshing, onRefresh = viewModel::refresh)

        val list = reports
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KalynoBlue)
            }
            list.isEmpty() -> EmptyState(signedIn)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!signedIn) item(key = "guest") { GuestNote() }
                items(list, key = { it.id }) { ReportCard(it) }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = "arrow-left", size = 22.dp, tint = KalynoText, contentDescription = "Retour")
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "Mes signalements",
            color = KalynoText,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(enabled = !refreshing, onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = KalynoBlue)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = KalynoMuted, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(signedIn: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Rien signalé pour l'instant", color = KalynoText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Une erreur dans une leçon ou un exercice ? Touche le drapeau en haut de l'écran pour nous la signaler.",
            color = KalynoMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
        if (!signedIn) {
            Spacer(Modifier.height(12.dp))
            GuestNote()
        }
    }
}

@Composable
private fun GuestNote() {
    Text(
        text = "Connecte-toi à ton compte pour recevoir les réponses de l'équipe à tes signalements.",
        color = KalynoAmberDeep,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KalynoAmberSoft)
            .padding(12.dp)
    )
}

private data class StatusUi(val label: String, val background: Color, val foreground: Color)

private fun statusUi(report: ReportEntity): StatusUi = when {
    report.syncedAt == null -> StatusUi("En attente d'envoi", KalynoAmberSoft, KalynoAmberDeep)
    report.status == "IN_REVIEW" -> StatusUi("En cours d'examen", KalynoBlueSoft, KalynoBlue)
    report.status == "RESOLVED" -> StatusUi("Résolu", KalynoCorrectSoft, KalynoCorrectText)
    report.status == "REJECTED" -> StatusUi("Clôturé", KalynoOutline, KalynoMuted)
    else -> StatusUi("Reçu", KalynoBlueSoft, KalynoBlue)
}

private fun sourceLabel(code: String): String = when (runCatching { ReportSourceType.valueOf(code) }.getOrNull()) {
    ReportSourceType.LESSON -> "Leçon"
    ReportSourceType.EXERCISE -> "Exercice"
    ReportSourceType.SETTINGS -> "Paramètres"
    null -> "Signalement"
}

@Composable
private fun ReportCard(report: ReportEntity) {
    val status = remember(report.status, report.syncedAt) { statusUi(report) }
    val category = ReportCategory.fromCode(report.category)?.label ?: report.category
    val date = remember(report.createdAt) {
        SimpleDateFormat("d MMM yyyy · HH:mm", Locale.FRANCE).format(Date(report.createdAt))
    }
    val reply = report.teamReply?.takeIf { it.isNotBlank() }
    val isNew = reply != null && report.replySeenAt == null

    KalynoCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = sourceLabel(report.sourceType).uppercase(Locale.FRANCE),
                color = KalynoMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = status.label,
                color = status.foreground,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(CircleShape).background(status.background).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(category, color = KalynoText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        report.contextLabel?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, color = KalynoMuted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
        }
        if (report.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(report.description, color = KalynoText, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (report.syncedAt == null) "$date · sera envoyé dès que le réseau revient" else date,
            color = KalynoMuted,
            fontSize = 12.sp
        )

        if (reply != null) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KalynoCorrectSoft)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RÉPONSE DE L'ÉQUIPE",
                        color = KalynoCorrectText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (isNew) {
                        Text(
                            "NOUVEAU",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clip(CircleShape).background(KalynoDanger).padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(reply, color = KalynoText, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

/** Pastille rouge du nombre de réponses non lues (ligne « Mes signalements » des Paramètres). */
@Composable
fun ReportUnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(KalynoDanger),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
