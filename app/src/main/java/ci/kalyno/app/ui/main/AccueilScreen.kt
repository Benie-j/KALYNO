package ci.kalyno.app.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.dashboard.DashboardUiState
import ci.kalyno.app.data.dashboard.LastLessonUi
import ci.kalyno.app.data.dashboard.MissionStatus
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.*

/**
 * Écran d'Accueil (Dashboard / Command Center).
 * Règle stricte : aucune mention de classe/niveau ici — c'est le rôle du Profil.
 * Tout le contenu vient de [DashboardUiState] : zéro donnée en dur.
 */
@Composable
fun AccueilScreen(
    state: DashboardUiState,
    onStartMission: () -> Unit,
    onReviewUrgent: () -> Unit,
    onRetry: () -> Unit,
    onResumeLastLesson: (LastLessonUi) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TopBar(streak = state.streak)
        HeroBanner(message = state.heroMessage, examDaysLeft = state.examDaysLeft)
        SrsWidget(state = state, onReview = onReviewUrgent)
        KpiGrid(masteredCount = state.masteredCount, retentionScore = state.retentionScore)
        state.lastLesson?.let { lesson ->
            ResumeCard(lesson = lesson, onClick = { onResumeLastLesson(lesson) })
        }

        // La mission du jour reste disponible via le widget SRS / le Dojo,
        // elle ne s'affiche plus en pleine carte sur l'Accueil (règle "pas de doublon avec le Dojo").
        if (state.error != null) {
            ErrorCard(message = state.error, onRetry = onRetry)
        }
    }
}

// ---------- 1. Top Bar épurée ----------

@Composable
private fun TopBar(streak: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "KALYNO",
            color = KalynoText,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(KalynoAmberSoft)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("🔥", fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = streak.toString(),
                color = KalynoAmberDeep,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ---------- 2. Hero Banner (admin-driven) ----------

@Composable
private fun HeroBanner(message: String?, examDaysLeft: Int?) {
    // Rien à afficher tant que l'admin n'a publié ni message ni date d'examen.
    if (message.isNullOrBlank() && examDaysLeft == null) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(KalynoBlue, KalynoTealDark)))
                .padding(20.dp)
        ) {
            Column {
                if (examDaysLeft != null) {
                    Text(
                        text = if (examDaysLeft == 0) "📅 Jour J !" else "📅 J-$examDaysLeft avant les épreuves",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (!message.isNullOrBlank()) {
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// ---------- 3. Widget Révisions Urgentes SRS ----------

@Composable
private fun SrsWidget(state: DashboardUiState, onReview: () -> Unit) {
    val urgent = state.urgentTotal > 0

    DashCard(
        background = if (urgent) KalynoRetrySoft else KalynoCorrectSoft,
        stroke = if (urgent) KalynoRetry else KalynoCorrect
    ) {
        if (state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 3.dp, color = KalynoBlue)
                Spacer(Modifier.width(12.dp))
                Text("Analyse de tes révisions…", color = KalynoMuted, fontSize = 15.sp)
            }
            return@DashCard
        }

        if (!urgent) {
            Text(
                text = "⚡ Tout est à jour, ton cerveau est au top !",
                color = KalynoCorrectText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            return@DashCard
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(KalynoRetry),
                contentAlignment = Alignment.Center
            ) {
                Text(state.urgentTotal.toString(), color = KalynoText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (state.urgentTotal == 1) "🔥 1 notion à réviser d'urgence" else "🔥 ${state.urgentTotal} notions à réviser d'urgence",
                color = KalynoRetryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onReview,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KalynoRetry, contentColor = KalynoText)
        ) {
            Text("Lancer la session dans le Dojo", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ---------- 4. Grille des KPIs ----------

@Composable
private fun KpiGrid(masteredCount: Int?, retentionScore: Float?) {
    // Tant que le calcul n'est pas branché côté repository, on n'affiche rien
    // plutôt qu'une fausse valeur à 0.
    if (masteredCount == null && retentionScore == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (masteredCount != null) {
            KpiCard(
                modifier = Modifier.weight(1f),
                icon = "check-circle",
                value = masteredCount.toString(),
                label = "Notions maîtrisées"
            )
        }
        if (retentionScore != null) {
            KpiCard(
                modifier = Modifier.weight(1f),
                icon = "brain",
                value = "${(retentionScore * 100).toInt()}%",
                label = "Score de rétention"
            )
        }
    }
}

@Composable
private fun KpiCard(modifier: Modifier = Modifier, icon: String, value: String, label: String) {
    DashCard(modifier = modifier) {
        KalynoSvgIcon(name = icon, size = 22.dp, tint = KalynoBlue)
        Spacer(Modifier.height(10.dp))
        Text(value, color = KalynoText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = KalynoMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------- 5. Reprendre l'aventure ----------

@Composable
private fun ResumeCard(lesson: LastLessonUi, onClick: () -> Unit) {
    DashCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(KalynoBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "book-open", size = 22.dp, tint = KalynoBlue)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Reprendre l'aventure", color = KalynoMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${lesson.subjectName} · ${lesson.chapterTitle}",
                    color = KalynoText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            KalynoSvgIcon(name = "chevron-right", size = 20.dp, tint = KalynoMuted)
        }
    }
}

// ---------- Commun ----------

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    DashCard(background = KalynoDangerSoft, stroke = KalynoDangerBorder) {
        Text(message, color = KalynoDanger, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = KalynoBlue)) {
            Text("Réessayer", color = Color.White)
        }
    }
}

@Composable
private fun DashCard(
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    stroke: Color = KalynoOutline,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = background,
        border = BorderStroke(1.dp, stroke)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}
