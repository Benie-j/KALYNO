package ci.kalyno.app.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoIconEmptyState
import ci.kalyno.app.ui.components.KalynoErrorCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.SectionHeader
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.components.entrance
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.icons.iconAssetFor
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow
import kotlinx.coroutines.delay

/** Rafraîchissement silencieux tant que l'onglet est visible (contenu publié en direct par l'admin). */
private const val AUTO_REFRESH_MS = 120_000L

// ===========================================================================
// ROUTEUR EXPLORER : Accueil -> Liste des leçons -> Leçon
// ===========================================================================

/**
 * Point d'entrée de l'onglet Explorer.
 *
 * Plus de salutation ni d'icône de profil : l'écran s'ouvre directement sur la section
 * "Disciplines". La navigation (état dans [ExplorerViewModel]) glisse horizontalement
 * entre les trois écrans et gère le bouton retour système.
 */
@Composable
fun ExplorerScreen(
    levelId: String,
    onStartChapter: (title: String, items: List<ReviewItem>) -> Unit,
    viewModel: ExplorerViewModel = viewModel(key = "explorer-$levelId")
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()

    var showLeaderboard by rememberSaveable { mutableStateOf(false) }
    // Hissé ici pour retrouver la même position de scroll en revenant de la liste des leçons
    val homeScroll = rememberScrollState()

    // Leçon demandée par l'Accueil (« Reprendre l'aventure ») : ouverte dès que les leçons sont chargées
    val requestedLessonId by ExplorerDeepLink.openLessonId
    LaunchedEffect(requestedLessonId, ui.chapters, ui.loading) {
        val id = requestedLessonId ?: return@LaunchedEffect
        val chapter = ui.chapters.firstOrNull { it.id == id }
        if (chapter != null) {
            viewModel.onLessonClick(chapter)
            ExplorerDeepLink.openLessonId.value = null
        } else if (!ui.loading) {
            // Leçon introuvable (dépubliée) : on abandonne la demande
            ExplorerDeepLink.openLessonId.value = null
        }
    }

    LaunchedEffect(levelId) {
        viewModel.load(levelId)
        while (true) {
            delay(AUTO_REFRESH_MS)
            viewModel.refresh(silent = true)
        }
    }

    // Retour système : Leçon -> Liste -> Accueil Explorer (puis MainScreen reprend la main)
    BackHandler(enabled = ui.route != ExplorerRoute.Home) { viewModel.onBack() }

    // Les questions d'une leçon ne sont jouables qu'après « Terminer » ; sinon on ouvre la leçon à lire
    val start: (ExplorerChapter) -> Unit = { chapter ->
        // Exercices jouables : toute la leçon si elle est terminée, sinon ceux des séances terminées
        val unlocked = ui.unlockedItems(chapter)
        when {
            unlocked.isNotEmpty() -> onStartChapter(chapter.title, unlocked)
            !ui.isCompleted(chapter.id) -> viewModel.onLessonClick(chapter)
            else -> Unit
        }
    }

    AnimatedContent(
        targetState = ui.route,
        transitionSpec = {
            val forward = targetState.depth > initialState.depth
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(300)) { it / 4 * dir } + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally(tween(300)) { -it / 6 * dir } + fadeOut(tween(180)))
        },
        label = "explorerRoute"
    ) { route ->
        when (route) {
            ExplorerRoute.Home -> ExplorerHome(
                levelId = levelId,
                ui = ui,
                viewModel = viewModel,
                scroll = homeScroll,
                onStart = start,
                onOpenLesson = { viewModel.onLessonClick(it) },
                onOpenLeaderboard = { showLeaderboard = true }
            )

            is ExplorerRoute.DisciplineLessons -> {
                val discipline = ui.disciplines.firstOrNull { it.id == route.disciplineId }
                if (discipline != null) {
                    DisciplineLessonsScreen(
                        discipline = discipline,
                        themes = ui.themesOf(discipline.id),
                        lessons = ui.lessonsOf(discipline.id),
                        onBack = { viewModel.onBack() },
                        onOpenLesson = { viewModel.onLessonClick(it) }
                    )
                }
            }

            is ExplorerRoute.LessonDetail -> {
                val lesson = ui.chapters.firstOrNull { it.id == route.lessonId }
                if (lesson != null) {
                    val visibleSessions = lesson.availableSessions()
                    val openSession = visibleSessions.firstOrNull { it.id == ui.openSessionId }
                    when {
                        // Leçon sans séance publiée : fonctionnement historique inchangé
                        visibleSessions.isEmpty() -> LessonScreen(
                            chapter = lesson,
                            isCompleted = ui.isCompleted(lesson.id),
                            onComplete = { viewModel.onLessonCompleted(lesson.id) },
                            onBack = { viewModel.onBack() },
                            onStartQuestions = { start(lesson) }
                        )
                        // Une séance : ses blocs seuls, son propre « Terminer »
                        openSession != null -> {
                            val sessionItems = lesson.itemsOfSession(openSession.id)
                            LessonScreen(
                                chapter = lesson,
                                isCompleted = ui.isSessionCompleted(openSession.id),
                                onComplete = { viewModel.onSessionCompleted(openSession.id) },
                                onBack = { viewModel.onBack() },
                                onStartQuestions = { onStartChapter(lesson.title, sessionItems) },
                                sessionId = openSession.id,
                                questionCount = sessionItems.size
                            )
                        }
                        // Liste des séances publiées de la leçon
                        else -> LessonSessionListScreen(
                            chapter = lesson,
                            sessions = visibleSessions,
                            completedIds = ui.completedSessionIds,
                            unlockedCount = ui.unlockedItems(lesson).size,
                            onOpenSession = { viewModel.onSessionClick(it) },
                            onStartQuestions = { start(lesson) },
                            onBack = { viewModel.onBack() }
                        )
                    }
                }
            }
        }
    }

    if (showLeaderboard) {
        AlertDialog(
            onDismissRequest = { showLeaderboard = false },
            confirmButton = {
                TextButton(onClick = { showLeaderboard = false }) {
                    Text("OK", color = KalynoBlue, fontWeight = FontWeight.ExtraBold)
                }
            },
            title = { Text("Classement en direct", fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "Le classement inter-classes s'ouvrira avec le premier tournoi publié par ton école. " +
                        "Continue à t'entraîner : ta progression comptera !"
                )
            },
            containerColor = Color.White
        )
    }
}

// ===========================================================================
// ACCUEIL EXPLORER v3 : Mission du jour -> Recherche -> Disciplines (liste verticale) -> Résultats
// ===========================================================================

@Composable
private fun ExplorerHome(
    levelId: String,
    ui: ExplorerUiState,
    viewModel: ExplorerViewModel,
    scroll: ScrollState,
    onStart: (ExplorerChapter) -> Unit,
    onOpenLesson: (ExplorerChapter) -> Unit,
    onOpenLeaderboard: () -> Unit
) {
    val context = LocalContext.current
    // Lue à chaque retour sur l'accueil Explorer : reflète la série réelle du compte
    val streak = remember { StreakStore(context.applicationContext).current() }
    val searching = ui.query.isNotBlank()

    val overallProgress = remember(ui.chapters) {
        val total = ui.chapters.sumOf { it.questionCount }
        if (total == 0) 0f else ui.chapters.sumOf { it.discovered }.toFloat() / total
    }
    val progressByDiscipline = remember(ui.chapters) {
        ui.chapters.groupBy { it.subjectId }.mapValues { (_, list) ->
            val total = list.sumOf { it.questionCount }
            if (total == 0) 0f else list.sumOf { it.discovered }.toFloat() / total
        }
    }
    val picks = ui.forYou.orEmpty()
    val dueTotal = picks.sumOf { it.dueCount }
    // Mission du jour : la leçon réellement consultée en dernier, jamais « la première du catalogue »
    val lastConsultedId = remember(ui.chapters, ui.route) { viewModel.lastConsultedLessonId() }
    val continueChapter = remember(ui.chapters, lastConsultedId) {
        ui.chapters.firstOrNull { it.id == lastConsultedId && !it.isFinished() }
            ?: ui.chapters.firstOrNull { it.discovered > 0 && !it.isFinished() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll)
            .padding(top = 12.dp, bottom = 28.dp)
    ) {
        ExplorerTopBar(onOpenLeaderboard = onOpenLeaderboard, modifier = Modifier.entrance(index = 0))
        Spacer(Modifier.height(16.dp))

        // Mission du jour : masquée pendant une recherche pour laisser la place aux résultats
        AnimatedVisibility(visible = !searching && ui.chapters.isNotEmpty()) {
            DailyMissionHero(
                streak = streak,
                progress = overallProgress,
                dueCount = dueTotal,
                topPick = picks.firstOrNull(),
                continueChapter = continueChapter,
                onReview = { onStart(it.reviewChapter) },
                onContinue = onOpenLesson,
                modifier = Modifier.padding(bottom = 18.dp).entrance(index = 1)
            )
        }

        AnimatedVisibility(visible = !searching && ui.banners.isNotEmpty()) {
            HeroBannerCarousel(
                banners = ui.banners,
                chapters = ui.chapters,
                onOpenLesson = onOpenLesson,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }

        // Recherche : au-dessus des disciplines, pour retrouver un cours ou une leçon
        SearchField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp).entrance(index = 2)
        )

        AnimatedVisibility(visible = !searching && (ui.loading || ui.disciplines.isNotEmpty())) {
            DisciplineList(
                disciplines = ui.disciplines,
                progressById = progressByDiscipline,
                pressedId = ui.pressedDisciplineId,
                loading = ui.loading && ui.disciplines.isEmpty(),
                onClick = viewModel::onDisciplineClick,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }

        if (ui.offline) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .clip(CircleShape)
                    .background(KalynoAmberSoft)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                KalynoSvgIcon(name = "wifi-off", size = 14.dp, tint = KalynoAmberDeep)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Pas de réseau, mais ton contenu est gardé sur ton téléphone",
                    color = KalynoAmberDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val visible = ui.visibleChapters

        when {
            // Le chargement est déjà montré par la liste des disciplines
            ui.loading -> Unit

            ui.error != null -> KalynoErrorCard(
                message = ui.error ?: "",
                onRetry = viewModel::retry,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            ui.chapters.isEmpty() -> KalynoIconEmptyState(
                icon = "book-open",
                title = "Ça arrive bientôt",
                text = "Tes profs préparent de nouveaux parcours pour toi. Reviens vite, on est ensemble."
            )

            visible.isEmpty() -> KalynoIconEmptyState(
                icon = "search",
                title = "Rien trouvé",
                text = "Essaie un autre mot-clé ou une autre matière, ça va sortir."
            )

            searching -> SearchResultsBlock(chapters = visible, onStart = onStart, onOpen = onOpenLesson)

            else -> Unit
        }
    }
}

// ===========================================================================
// RECHERCHE
// ===========================================================================

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(52.dp),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        shape = RoundedCornerShape(18.dp),
        placeholder = { Text("Rechercher un cours ou une leçon…", color = KalynoMuted, fontSize = 14.sp) },
        leadingIcon = { KalynoSvgIcon(name = "search", size = 18.dp, tint = KalynoMuted) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onValueChange("") }
                        .padding(10.dp)
                ) {
                    KalynoSvgIcon(name = "x", size = 16.dp, tint = KalynoMuted)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = KalynoBlue,
            unfocusedBorderColor = KalynoOutline,
            cursorColor = KalynoBlue,
            focusedTextColor = KalynoText,
            unfocusedTextColor = KalynoText
        )
    )
}

// ===========================================================================
// COMMUNAUTÉ (conservée ; accessible via le trophée de l'en-tête)
// ===========================================================================


/** Bannière "Tournoi Inter-Classes" : mini-graphique de progression par matière + accès classement. */
@Composable
private fun CommunityBanner(
    progress: List<SubjectProgress>,
    levelLabel: String,
    onOpenLeaderboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val haptic = rememberHaptic()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(KalynoAmberSoft),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "trophy", size = 28.dp, tint = KalynoAmberDeep)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Tournoi Inter-Classes",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Représente ta classe · $levelLabel",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            }
        }

        if (progress.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "TA PROGRESSION",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                progress.take(5).forEach { subject ->
                    val barHeight by animateDpAsState(
                        targetValue = 8.dp + 56.dp * subject.progress.coerceIn(0f, 1f),
                        label = "bar"
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.92f))
                        )
                        Spacer(Modifier.height(6.dp))
                        KalynoSvgIcon(name = iconAssetFor(subject.iconKey), size = 18.dp, tint = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "VOIR LE CLASSEMENT",
            color = KalynoBlue,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .clickable {
                    haptic()
                    onOpenLeaderboard()
                }
                .padding(vertical = 15.dp),
            textAlign = TextAlign.Center
        )
    }
}

