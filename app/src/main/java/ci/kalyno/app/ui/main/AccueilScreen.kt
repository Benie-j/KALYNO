package ci.kalyno.app.ui.main

import ci.kalyno.app.ui.components.rememberHaptic
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.StreakLogic
import ci.kalyno.app.data.badges.BadgeCatalog
import ci.kalyno.app.data.badges.BadgeCategory
import ci.kalyno.app.data.badges.BadgeDef
import ci.kalyno.app.data.dashboard.DailyQuote
import ci.kalyno.app.data.dashboard.DashboardUiState
import ci.kalyno.app.data.dashboard.LastLessonUi
import ci.kalyno.app.ui.components.DriftingBlob
import ci.kalyno.app.ui.components.KalynoErrorCard
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.entrance
import ci.kalyno.app.ui.components.rememberCountUp
import ci.kalyno.app.ui.components.rememberPulse
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow

/**
 * Les trois modules de la grille, dans l'ordre d'affichage. Sur téléphone, Révisions occupe toute
 * la largeur (contenu riche) ; Progression et Citation se partagent une ligne. La reprise de la
 * dernière leçon est portée par la carte « Ta session du jour » (SessionHero).
 */
private enum class HomeModule(val fullWidth: Boolean) {
    URGENT(true),
    PROGRESS(false),
    QUOTE(false)
}

/** Répartit les modules en lignes : [columns] colonnes, sauf les modules pleine largeur sur téléphone. */
private fun homeRows(modules: List<HomeModule>, columns: Int, wide: Boolean): List<List<HomeModule>> {
    val rows = mutableListOf<List<HomeModule>>()
    val pending = mutableListOf<HomeModule>()
    fun flush() {
        if (pending.isNotEmpty()) {
            rows.add(pending.toList())
            pending.clear()
        }
    }
    modules.forEach { module ->
        if (module.fullWidth && !wide) {
            flush()
            rows.add(listOf(module))
        } else {
            pending.add(module)
            if (pending.size == columns) flush()
        }
    }
    flush()
    return rows
}

/**
 * Écran d'Accueil (Command Center).
 *
 * - Adaptatif : 2 modules par ligne, 1 seule colonne sous 340 dp, contenu centré et plafonné
 *   à 840 dp sur tablette / pliable pour ne jamais s'étirer.
 * - Aucune mention de classe ou de niveau (rôle du Profil), aucun emoji, aucune donnée en dur :
 *   tout vient de [DashboardUiState] (série, révisions, examen, citation, message du coach publiés
 *   par l'admin) ou du catalogue de badges (paliers de série). Seules les phrases du coach ont un
 *   repli écrit dans l'appli, utilisé quand l'admin n'en a publié aucune.
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
    onToggleFavoriteQuote: (DailyQuote) -> Unit = {},
    onOpenProgress: () -> Unit = {},
    nickname: String = "",
    onOpenExplorer: () -> Unit = {}
) {
    // Détail de la série : s'ouvre en touchant la flamme à côté de la salutation
    var showStreak by rememberSaveable { mutableStateOf(false) }
    if (showStreak) {
        StreakDetailsSheet(
            streak = state.streak,
            doneToday = state.streakDoneToday,
            onDismiss = { showStreak = false }
        )
    }

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
                HomeTopBar(
                    streak = state.streak,
                    nickname = nickname.ifBlank { state.nickname },
                    onStreakClick = { showStreak = true },
                    modifier = Modifier.entrance(0)
                )

                SessionHero(
                    state = state,
                    onReviewUrgent = onReviewUrgent,
                    onResume = onResumeLastLesson,
                    onExplore = onOpenExplorer,
                    modifier = Modifier.entrance(1)
                )

                AnimatedVisibility(
                    visible = state.examDaysLeft != null,
                    enter = fadeIn() + expandVertically()
                ) {
                    state.examDaysLeft?.let { days ->
                        ExamStrip(
                            daysLeft = days,
                            label = state.examLabel,
                            coachLine = coachLine(state),
                            modifier = Modifier.entrance(2)
                        )
                    }
                }

                if (state.examDaysLeft == null) {
                    state.heroMessage?.takeIf { it.isNotBlank() }?.let {
                        CoachNote(text = it, modifier = Modifier.entrance(2))
                    }
                }

                state.error?.let { KalynoErrorCard(message = it, onRetry = onRetry) }

                val modules = buildList {
                    add(HomeModule.URGENT)
                    add(HomeModule.PROGRESS)
                    if (state.dailyQuote != null || !state.quoteReady) add(HomeModule.QUOTE)
                }

                // Grille maison : modules pleine largeur ou par paires, de même hauteur dans une ligne.
                homeRows(modules, columns, wide).forEach { rowModules ->
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
                                .entrance(3 + modules.indexOf(module))
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
                                HomeModule.PROGRESS -> ProgressModule(
                                    masteredCount = state.masteredCount,
                                    retentionScore = state.retentionScore,
                                    masteredNotions = state.masteredNotions,
                                    loading = state.loading,
                                    onOpenDetail = onOpenProgress,
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

// ---------- 1. En-tête : avatar + date + salutation + série ----------

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour in 5..11 -> "Bonjour"
        hour in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
}

@Composable
private fun HomeTopBar(
    streak: Int,
    nickname: String,
    onStreakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val streakLabel = if (streak <= 1) "Série de $streak jour" else "Série de $streak jours"
    val haptic = rememberHaptic()
    val chipInteraction = remember { MutableInteractionSource() }
    val chipPressed by chipInteraction.collectIsPressedAsState()
    val chipScale by animateFloatAsState(
        targetValue = if (chipPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "streakChipScale"
    )
    val shownStreak = rememberCountUp(streak, durationMillis = 900)
    // La flamme respire quand une série est en cours ; immobile sinon
    val pulse = rememberPulse(min = 1f, max = 1.16f, durationMillis = 800)
    val flameScale = if (streak > 0) pulse else 1f
    // Date du jour, calculée sur le téléphone (jamais écrite en dur)
    val dateLabel = remember {
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
            .uppercase(Locale.FRENCH)
    }
    val initial = nickname.trim().take(1).uppercase()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(KalynoBlue),
            contentAlignment = Alignment.Center
        ) {
            if (initial.isEmpty()) {
                KalynoSvgIcon(name = "user-round", size = 22.dp, tint = Color.White)
            } else {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLabel,
                color = KalynoMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (nickname.isBlank()) greeting() else "${greeting()}, $nickname",
                color = KalynoText,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = chipScale
                    scaleY = chipScale
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = "$streakLabel. Voir le détail"
                    role = Role.Button
                }
                .clip(CircleShape)
                .background(KalynoAmberSoft)
                .border(1.dp, KalynoYellow.copy(alpha = 0.35f), CircleShape)
                .clickable(
                    interactionSource = chipInteraction,
                    indication = null,
                    onClickLabel = "Voir le détail de ma série"
                ) {
                    haptic()
                    onStreakClick()
                }
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = flameScale
                    scaleY = flameScale
                }
            ) {
                KalynoSvgIcon(name = "flame", size = 20.dp, tint = KalynoYellow)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = shownStreak.toString(),
                color = KalynoAmberDeep,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ---------- 2. Hero : la session du jour (le déclencheur d'habitude) ----------

@Composable
private fun SessionHero(
    state: DashboardUiState,
    onReviewUrgent: () -> Unit,
    onResume: (LastLessonUi) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val urgent = state.urgentTotal
    val lastLesson = state.lastLesson
    val shownUrgent = rememberCountUp(urgent, durationMillis = 900)

    val title: String
    val subtitle: String
    val cta: String
    val action: () -> Unit
    when {
        urgent > 0 -> {
            title = if (urgent == 1) "1 notion t'attend" else "$shownUrgent notions t'attendent"
            subtitle = "Une session courte pour les ancrer durablement en mémoire."
            cta = "Réviser maintenant"
            action = onReviewUrgent
        }
        lastLesson != null -> {
            title = "Tout est à jour"
            subtitle = "Enchaîne avec « ${lastLesson.chapterTitle} »" +
                (lastLesson.sessionTitle?.let { " · $it" } ?: "") + "."
            cta = "Reprendre l'aventure"
            action = { onResume(lastLesson) }
        }
        else -> {
            title = "Tout est à jour"
            subtitle = "C'est le bon moment pour attaquer un nouveau chapitre."
            cta = "Explorer les chapitres"
            action = onExplore
        }
    }

    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
    ) {
        Box(modifier = Modifier.matchParentSize()) {
            DriftingBlob(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 56.dp, y = (-56).dp)
                    .size(190.dp),
                color = Color.White.copy(alpha = 0.09f)
            )
            DriftingBlob(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-40).dp, y = 44.dp)
                    .size(130.dp),
                color = Color.White.copy(alpha = 0.06f),
                durationMillis = 6800
            )
        }
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "TA SESSION DU JOUR",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(18.dp))
            KalynoPrimaryButton(
                label = cta,
                onClick = action,
                height = 52.dp,
                container = Color.White,
                content = KalynoBlue
            )
        }
    }
}

// ---------- 3. Compte à rebours (date et intitulé publiés par l'admin) ----------

@Composable
private fun ExamStrip(
    daysLeft: Int,
    label: String?,
    coachLine: String?,
    modifier: Modifier = Modifier
) {
    val shown = rememberCountUp(daysLeft, durationMillis = 1000)
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, KalynoOutline, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(KalynoBlueSoft)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (daysLeft == 0) "Jour J" else "J-$shown",
                color = KalynoBlue,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (!label.isNullOrBlank()) label else "avant les épreuves",
                color = KalynoText,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!coachLine.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = coachLine,
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Message du coach publié par l'admin, affiché seul quand aucune date d'examen n'est publiée. */
@Composable
private fun CoachNote(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, KalynoOutline, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(KalynoBlueSoft),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = "sparkles", size = 20.dp, tint = KalynoBlue)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = KalynoText,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Message du coach : priorité au texte publié par l'admin ([DashboardUiState.heroMessage], table
 * `home_messages`). À défaut, une phrase générée côté client selon l'activité de l'élève — pas une
 * donnée éducative, juste une tournure motivante en nouchi léger, jamais la même à chaque relance
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
