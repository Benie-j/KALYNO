package ci.kalyno.app.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoIconEmptyState
import ci.kalyno.app.ui.components.KalynoErrorCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.SectionHeader
import ci.kalyno.app.ui.components.ShimmerBlock
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

private val PATH_ICONS = listOf("book-open", "brain", "rocket", "dumbbell", "radio-tower", "flame")

// Ombre portée ultra-légère des mini-cartes Discipline
private val ChipShadow = Color(0x140F172A)

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
        when {
            !ui.isCompleted(chapter.id) -> viewModel.onLessonClick(chapter)
            chapter.items.isNotEmpty() -> onStartChapter(chapter.title, chapter.items)
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
                    LessonScreen(
                        chapter = lesson,
                        isCompleted = ui.isCompleted(lesson.id),
                        onComplete = { viewModel.onLessonCompleted(lesson.id) },
                        onBack = { viewModel.onBack() },
                        onStartQuestions = { start(lesson) }
                    )
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
// ACCUEIL EXPLORER (épuré : Disciplines -> recherche -> filtres -> contenu)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll)
            .padding(top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ui.banner?.let { banner ->
            val target = ui.chapters.firstOrNull { it.id == banner.chapterId }
            ExplorerHeroBanner(
                banner = banner,
                onClick = target?.let { chapter -> { onOpenLesson(chapter) } },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        DisciplinesSection(
            disciplines = ui.disciplines,
            pressedId = ui.pressedDisciplineId,
            loading = ui.loading && ui.disciplines.isEmpty(),
            onClick = viewModel::onDisciplineClick
        )

        SearchField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        FilterChips(selected = ui.filter, onSelect = viewModel::onFilterChange)

        if (ui.offline) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
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
            ui.loading -> ExplorerSkeleton()

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

            ui.filter == ExplorerFilter.COMMUNITY -> CommunityBanner(
                progress = ui.subjectProgress,
                levelLabel = Levels.label(levelId),
                onOpenLeaderboard = onOpenLeaderboard,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            visible.isEmpty() -> KalynoIconEmptyState(
                icon = "search",
                title = "Rien trouvé",
                text = "Essaie un autre mot-clé ou une autre matière, ça va sortir."
            )

            else -> when (ui.filter) {
                ExplorerFilter.FOR_YOU -> {
                    ForYouSection(chapters = visible, onStart = onStart, onOpen = onOpenLesson)
                    NewPathsSection(chapters = visible, onOpen = onOpenLesson)
                }

                ExplorerFilter.CHALLENGES -> ChallengesSection(
                    chapters = visible,
                    onStart = onStart,
                    onOpen = onOpenLesson
                )

                ExplorerFilter.PATHS -> PathsSection(chapters = visible, onOpen = onOpenLesson)

                ExplorerFilter.COMMUNITY -> Unit
            }
        }
    }
}

// ===========================================================================
// SECTION "DISCIPLINES" (défilement horizontal)
// ===========================================================================

/**
 * Titre "Disciplines" (#0F172A) + LazyRow de mini-cartes, une par matière publiée par l'admin.
 * [pressedId] = discipline touchée : sa carte passe instantanément en Vert Pétrole.
 */
@Composable
private fun DisciplinesSection(
    disciplines: List<Discipline>,
    pressedId: String?,
    loading: Boolean,
    onClick: (String) -> Unit
) {
    // Rien à montrer (pas de discipline publiée) : on masque toute la section
    if (!loading && disciplines.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "Disciplines",
            subtitle = "Choisis une matière pour voir ses leçons",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (loading) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(3) {
                    ShimmerBlock(
                        modifier = Modifier.width(96.dp).height(36.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(disciplines, key = { it.id }) { discipline ->
                    DisciplineChip(
                        discipline = discipline,
                        selected = discipline.id == pressedId,
                        onClick = { onClick(discipline.id) }
                    )
                }
            }
        }
    }
}

/** Mini-carte : blanc pur + bordure #E2E8F0 + ombre légère ; sélectionnée = #0F766E + texte blanc. */
@Composable
private fun DisciplineChip(discipline: Discipline, selected: Boolean, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val shape = RoundedCornerShape(14.dp)

    // Transition courte (120 ms) : le retour visuel est perçu comme instantané
    val container by animateColorAsState(
        targetValue = if (selected) KalynoBlue else Color.White,
        animationSpec = tween(120),
        label = "disciplineContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) Color.White else KalynoText,
        animationSpec = tween(120),
        label = "disciplineContent"
    )
    val border by animateColorAsState(
        targetValue = if (selected) KalynoBlue else KalynoOutline,
        animationSpec = tween(120),
        label = "disciplineBorder"
    )

    Row(
        modifier = Modifier
            .shadow(1.dp, shape, ambientColor = ChipShadow, spotColor = ChipShadow)
            .background(container, shape)
            .border(1.dp, border, shape)
            .clip(shape)
            .clickable {
                haptic()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KalynoSvgIcon(name = iconAssetFor(discipline.iconKey), size = 16.dp, tint = content)
        Text(
            text = discipline.name,
            color = content,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

// ===========================================================================
// RECHERCHE / FILTRES
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
        modifier = modifier.fillMaxWidth().height(46.dp),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text("Rechercher un cours, une matière…", color = KalynoMuted, fontSize = 14.sp) },
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

@Composable
private fun FilterChips(selected: ExplorerFilter, onSelect: (ExplorerFilter) -> Unit) {
    val haptic = rememberHaptic()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ExplorerFilter.entries.filter { it != ExplorerFilter.COMMUNITY }, key = { it.name }) { filter ->
            val isSelected = filter == selected
            val container by animateColorAsState(
                targetValue = if (isSelected) KalynoBlue else Color.White,
                label = "chipContainer"
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) Color.White else KalynoMuted,
                label = "chipContent"
            )
            Text(
                text = filter.label,
                color = content,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(container)
                    .border(1.dp, if (isSelected) KalynoBlue else KalynoOutline, CircleShape)
                    .clickable {
                        haptic()
                        onSelect(filter)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

// ===========================================================================
// SECTIONS
// ===========================================================================

@Composable
private fun ForYouSection(
    chapters: List<ExplorerChapter>,
    onStart: (ExplorerChapter) -> Unit,
    onOpen: (ExplorerChapter) -> Unit
) {
    // En cours d'abord, puis nouveaux, puis terminés
    val picks = remember(chapters) {
        chapters
            .filter { it.questionCount > 0 }
            .sortedBy {
                when {
                    it.discovered in 1 until it.questionCount -> 0
                    it.discovered == 0 -> 1
                    else -> 2
                }
            }
            .take(10)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "Sélection pour toi",
            subtitle = "Choisie pour ta classe",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (picks.isEmpty()) {
            Text(
                text = "Les cartes de tes chapitres arrivent bientôt.",
                color = KalynoMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(picks, key = { it.id }) { chapter ->
                    CourseCard(
                        chapter = chapter,
                        onStart = { onStart(chapter) },
                        onOpen = { onOpen(chapter) },
                        modifier = Modifier.width(248.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NewPathsSection(chapters: List<ExplorerChapter>, onOpen: (ExplorerChapter) -> Unit) {
    val latest = remember(chapters) { chapters.takeLast(6).reversed() }
    if (latest.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "Nouveaux parcours",
            subtitle = "Publiés en direct",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        PathGrid(chapters = latest, onOpen = onOpen, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun ChallengesSection(
    chapters: List<ExplorerChapter>,
    onStart: (ExplorerChapter) -> Unit,
    onOpen: (ExplorerChapter) -> Unit
) {
    val challenges = remember(chapters) { chapters.filter { it.questionCount > 0 } }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(title = "Défis", subtitle = "Teste-toi chapitre par chapitre")
        if (challenges.isEmpty()) {
            Text("Aucun défi disponible pour le moment.", color = KalynoMuted, fontSize = 14.sp)
        }
        challenges.forEach { chapter ->
            CourseCard(
                chapter = chapter,
                onStart = { onStart(chapter) },
                onOpen = { onOpen(chapter) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PathsSection(chapters: List<ExplorerChapter>, onOpen: (ExplorerChapter) -> Unit) {
    val bySubject = remember(chapters) { chapters.groupBy { it.subjectId }.values.toList() }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        bySubject.forEach { list ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = list.first().subjectName)
                PathGrid(chapters = list, onOpen = onOpen)
            }
        }
    }
}

// ===========================================================================
// CARTES
// ===========================================================================

/**
 * Carte de défi/leçon : deux actions clairement séparées visuellement —
 * "Lire le cours" (théorie, lien secondaire) et "Démarrer l'exercice" (bouton principal).
 * Toucher la carte elle-même équivaut à "Lire le cours" (grande zone de touche).
 */
@Composable
private fun CourseCard(
    chapter: ExplorerChapter,
    onStart: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    KalynoCard(
        modifier = modifier,
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        contentPadding = 14.dp
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KalynoPill(
                text = chapter.subjectName,
                modifier = Modifier.weight(1f, fill = false)
            )
            SmartTagPill(chapter.tag)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = chapter.title,
            color = KalynoText,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        KalynoProgressBar(progress = chapter.progress, height = 6.dp)
        Spacer(Modifier.height(5.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KalynoSvgIcon(name = "layers", size = 13.dp, tint = KalynoMuted)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = questionsLabel(chapter.questionCount),
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(10.dp))
                KalynoSvgIcon(name = "clock", size = 13.dp, tint = KalynoMuted)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${chapter.minutes} min",
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "${(chapter.progress * 100).toInt()}%",
                color = KalynoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(8.dp))

        // ----- Action secondaire : lire la théorie -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KalynoSvgIcon(name = "book-open", size = 16.dp, tint = KalynoBlue)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Lire le cours",
                color = KalynoBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            KalynoSvgIcon(name = "chevron-right", size = 14.dp, tint = KalynoBlue)
        }
        Spacer(Modifier.height(2.dp))

        // ----- Action principale : lancer l'exercice -----
        KalynoPrimaryButton(
            label = "DÉMARRER L'EXERCICE",
            onClick = onStart,
            enabled = chapter.questionCount > 0,
            height = 42.dp
        )
    }
}

/** Grille 2 colonnes (sans lazy : on est déjà dans un scroll vertical). */
@Composable
private fun PathGrid(
    chapters: List<ExplorerChapter>,
    onOpen: (ExplorerChapter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chapters.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { chapter ->
                    PathCard(
                        chapter = chapter,
                        onClick = { onOpen(chapter) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PathCard(chapter: ExplorerChapter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val index = (chapter.id.hashCode() and Int.MAX_VALUE) % PATH_ICONS.size
    val warm = index % 2 == 1
    val tileColor = if (warm) KalynoAmberSoft else KalynoBlueSoft
    val tint = if (warm) KalynoYellow else KalynoBlue

    KalynoCard(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        contentPadding = 12.dp
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tileColor),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = PATH_ICONS[index], size = 22.dp, tint = tint)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = chapter.title,
            color = KalynoText,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (chapter.questionCount > 0) questionsLabel(chapter.questionCount) else "Leçons",
            color = KalynoMuted,
            fontSize = 12.sp
        )
        if (!chapter.tag.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            SmartTagPill(chapter.tag)
        }
    }
}

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

// ===========================================================================
// SQUELETTE DE CHARGEMENT
// ===========================================================================

@Composable
private fun ExplorerSkeleton() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShimmerBlock(Modifier.width(180.dp).height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) {
                ShimmerBlock(
                    modifier = Modifier.weight(1f).height(150.dp),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
        ShimmerBlock(Modifier.width(160.dp).height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) {
                ShimmerBlock(
                    modifier = Modifier.weight(1f).height(96.dp),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
        ShimmerBlock(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            shape = RoundedCornerShape(24.dp)
        )
    }
}

private fun questionsLabel(count: Int): String =
    if (count == 1) "1 Question" else "$count Questions"
