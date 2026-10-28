package ci.kalyno.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.dashboard.DailyQuote
import ci.kalyno.app.data.dashboard.DashboardUiState
import ci.kalyno.app.data.dashboard.LastLessonUi
import ci.kalyno.app.ui.components.KalynoErrorCard
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow

/** Les quatre modules stratégiques de la grille, dans l'ordre d'affichage. */
private enum class HomeModule { QUOTE, URGENT, RESUME, PROGRESS }

/**
 * Écran d'Accueil (Command Center).
 *
 * - Adaptatif : 2 modules par ligne, 1 seule colonne sous 340 dp, contenu centré et plafonné
 *   à 840 dp sur tablette / pliable pour ne jamais s'étirer.
 * - Aucune mention de classe ou de niveau (rôle du Profil), aucun emoji, aucune donnée en dur :
 *   tout vient de [DashboardUiState].
 *
 * [onStartMission] est conservé pour ne pas toucher à MainScreen ; l'Accueil ne l'utilise plus.
 */
@Composable
fun AccueilScreen(
    state: DashboardUiState,
    onStartMission: () -> Unit,
    onReviewUrgent: () -> Unit,
    onRetry: () -> Unit,
    onResumeLastLesson: (LastLessonUi) -> Unit,
    onShuffleQuote: () -> Unit = {},
    onToggleFavoriteQuote: (DailyQuote) -> Unit = {}
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
    ) {
        val columns = if (maxWidth < 340.dp) 1 else 2
        val wide = maxWidth >= 600.dp
        val gap = if (wide) 18.dp else 14.dp
        val sidePadding = if (wide) 32.dp else 20.dp
        // Largeur utile d'une carte : décide si le footer de la citation affiche le bouton shuffle
        val cardInnerWidth = (minOf(maxWidth, 840.dp) - sidePadding * 2 - gap * (columns - 1)) / columns - 36.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                HomeTopBar(streak = state.streak)

                AnimatedVisibility(
                    visible = state.examDaysLeft != null,
                    enter = fadeIn() + expandVertically()
                ) {
                    state.examDaysLeft?.let { days ->
                        ExamHero(
                            daysLeft = days,
                            label = state.examLabel,
                            coachLine = coachLine(state),
                            wide = wide
                        )
                    }
                }

                state.error?.let { KalynoErrorCard(message = it, onRetry = onRetry) }

                val modules = buildList {
                    if (state.dailyQuote != null || !state.quoteReady) add(HomeModule.QUOTE)
                    add(HomeModule.URGENT)
                    if (state.lastLesson != null) add(HomeModule.RESUME)
                    add(HomeModule.PROGRESS)
                }

                // Grille maison : lignes de [columns] modules, de même hauteur dans une ligne.
                modules.chunked(columns).forEach { rowModules ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        rowModules.forEach { module ->
                            val slot = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                            when (module) {
                                HomeModule.QUOTE -> QuoteModule(
                                    state = state,
                                    onShuffle = onShuffleQuote,
                                    onToggleFavorite = onToggleFavoriteQuote,
                                    showShuffle = cardInnerWidth >= 140.dp,
                                    modifier = slot
                                )
                                HomeModule.URGENT -> UrgentReviewModule(
                                    total = state.urgentTotal,
                                    topics = state.urgentTopics,
                                    loading = state.loading,
                                    onReview = onReviewUrgent,
                                    modifier = slot
                                )
                                HomeModule.RESUME -> state.lastLesson?.let { lesson ->
                                    ResumeModule(
                                        lesson = lesson,
                                        onClick = { onResumeLastLesson(lesson) },
                                        modifier = slot
                                    )
                                }
                                HomeModule.PROGRESS -> ProgressModule(
                                    masteredCount = state.masteredCount,
                                    retentionScore = state.retentionScore,
                                    masteredNotions = state.masteredNotions,
                                    urgentTopics = state.urgentTopics,
                                    loading = state.loading,
                                    modifier = slot
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- 1. Top bar épurée ----------

@Composable
private fun HomeTopBar(streak: Int) {
    val streakLabel = if (streak <= 1) "Série de $streak jour" else "Série de $streak jours"
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
                .semantics(mergeDescendants = true) { contentDescription = streakLabel }
                .clip(CircleShape)
                .background(KalynoAmberSoft)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            KalynoSvgIcon(name = "flame", size = 18.dp, tint = KalynoYellow)
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

// ---------- 2. Hero : compte à rebours (date et intitulé publiés par l'admin) ----------

@Composable
private fun ExamHero(daysLeft: Int, label: String?, coachLine: String?, wide: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
    ) {
        // Cercle décoratif discret (n'agrandit pas la bannière)
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 48.dp, y = (-48).dp)
                    .size(if (wide) 220.dp else 160.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
        Column(modifier = Modifier.padding(if (wide) 32.dp else 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KalynoSvgIcon(name = "calendar", size = 16.dp, tint = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "COMPTE À REBOURS",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (daysLeft == 0) "Jour J" else "J-$daysLeft",
                color = Color.White,
                fontSize = if (wide) 56.sp else 44.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (daysLeft > 0) {
                Text(
                    text = "avant les épreuves",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!label.isNullOrBlank()) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (!coachLine.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KalynoSvgIcon(name = "message-circle", size = 16.dp, tint = KalynoYellow)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = coachLine,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * Message du coach : priorité au texte publié par l'admin ([DashboardUiState.heroMessage]).
 * À défaut, une phrase générée côté client selon l'activité de l'élève — pas une donnée
 * éducative, juste une tournure motivante en nouchi léger, jamais la même à chaque relance
 * d'écran pour un même état (choisie sur le jour courant).
 */
private fun coachLine(state: DashboardUiState): String? {
    state.heroMessage?.takeIf { it.isNotBlank() }?.let { return it }

    val pool = when {
        state.urgentTotal >= 10 ->
            listOf(
                "Y'a du taf qui t'attend au Dojo, mais tu gères ça tranquille !",
                "Le cerveau est chaud, il faut juste le nourrir un peu, on y va !"
            )
        state.urgentTotal > 0 ->
            listOf(
                "Petit coup au Dojo et c'est réglé, on avance doucement mais sûrement !",
                "Quelques notions à revoir, rien de sorcier, tu connais la musique !"
            )
        state.streak >= 7 ->
            listOf(
                "Ça fait des jours que tu tiens, respect, continue comme ça !",
                "Série solide, le cerveau commence à kiffer la routine !"
            )
        else ->
            listOf(
                "Tout est à jour, profite pour avancer dans l'Explorer !",
                "Rien d'urgent pour le moment, c'est le bon moment pour explorer un nouveau chapitre !"
            )
    }
    if (pool.isEmpty()) return null
    val dayIndex = (System.currentTimeMillis() / 86_400_000L).toInt()
    return pool[((dayIndex % pool.size) + pool.size) % pool.size]
}
