package ci.kalyno.app.ui.explorer

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoEmptyState
import ci.kalyno.app.ui.components.KalynoErrorCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.SectionHeader
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.components.StreakBadge
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.icons.KalynoSvgIcon
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

// ===========================================================================
// ÉCRAN EXPLORER
// ===========================================================================

@Composable
fun ExplorerScreen(
    levelId: String,
    nickname: String,
    streak: Int,
    onStartChapter: (title: String, items: List<ReviewItem>) -> Unit,
    viewModel: ExplorerViewModel = viewModel(key = "explorer-$levelId")
) {
    val state by viewModel.state.collectAsState()
    val ui = state

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showLeaderboard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(levelId) {
        viewModel.load(levelId)
        while (true) {
            delay(AUTO_REFRESH_MS)
            viewModel.refresh(silent = true)
        }
    }

    val start: (ExplorerChapter) -> Unit = { chapter ->
        if (chapter.items.isNotEmpty()) {
            onStartChapter("${chapter.subjectEmoji} ${chapter.title}", chapter.items)
        }
    }
    val open: (ExplorerChapter) -> Unit = { selectedId = it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ExplorerTopBar(
            nickname = nickname,
            streak = streak,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        SearchField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        FilterChips(selected = ui.filter, onSelect = viewModel::onFilterChange)

        if (ui.offline) {
            Text(
                text = "📡 Hors ligne · contenu enregistré sur ton téléphone",
                color = KalynoAmberDeep,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(CircleShape)
                    .background(KalynoAmberSoft)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        val visible = ui.visibleChapters

        when {
            ui.loading -> ExplorerSkeleton()

            ui.error != null -> KalynoErrorCard(
                message = ui.error ?: "",
                onRetry = viewModel::retry,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            ui.chapters.isEmpty() -> KalynoEmptyState(
                emoji = "📚",
                title = "Aucun chapitre publié",
                text = "Les matières et les chapitres apparaîtront ici dès leur publication."
            )

            ui.filter == ExplorerFilter.COMMUNITY -> CommunityBanner(
                progress = ui.subjectProgress,
                levelLabel = Levels.label(levelId),
                onOpenLeaderboard = { showLeaderboard = true },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            visible.isEmpty() -> KalynoEmptyState(
                emoji = "🔍",
                title = "Aucun résultat",
                text = "Essaie un autre mot-clé ou une autre matière."
            )

            else -> when (ui.filter) {
                ExplorerFilter.FOR_YOU -> {
                    ForYouSection(chapters = visible, onStart = start, onOpen = open)
                    NewPathsSection(chapters = visible, onOpen = open)
                    CommunityBanner(
                        progress = ui.subjectProgress,
                        levelLabel = Levels.label(levelId),
                        onOpenLeaderboard = { showLeaderboard = true },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                ExplorerFilter.CHALLENGES -> ChallengesSection(
                    chapters = visible,
                    onStart = start,
                    onOpen = open
                )

                ExplorerFilter.PATHS -> PathsSection(chapters = visible, onOpen = open)

                ExplorerFilter.COMMUNITY -> Unit
            }
        }
    }

    val selected = ui.chapters.firstOrNull { it.id == selectedId }
    if (selected != null) {
        ChapterSheet(
            chapter = selected,
            onDismiss = { selectedId = null },
            onStart = {
                selectedId = null
                start(selected)
            }
        )
    }

    if (showLeaderboard) {
        AlertDialog(
            onDismissRequest = { showLeaderboard = false },
            confirmButton = {
                TextButton(onClick = { showLeaderboard = false }) {
                    Text("OK", color = KalynoBlue, fontWeight = FontWeight.ExtraBold)
                }
            },
            title = { Text("🏆 Classement en direct", fontWeight = FontWeight.ExtraBold) },
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
// TOP BAR / RECHERCHE / FILTRES
// ===========================================================================

@Composable
private fun ExplorerTopBar(nickname: String, streak: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Bonjour 👋", color = KalynoMuted, fontSize = 14.sp)
            Text(
                text = nickname.ifBlank { "Explorateur" },
                color = KalynoText,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StreakBadge(count = streak)
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(46.dp)
                .border(2.5.dp, KalynoBlue, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(KalynoBlueSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = nickname.trim().take(1).uppercase().ifEmpty { "K" },
                color = KalynoBlue,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

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
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        placeholder = { Text("Rechercher un cours, une matière…", color = KalynoMuted) },
        leadingIcon = { Text("🔍", fontSize = 16.sp) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                Text(
                    text = "✕",
                    color = KalynoMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onValueChange("") }
                        .padding(12.dp)
                )
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
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(ExplorerFilter.entries.toList(), key = { it.name }) { filter ->
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
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(container)
                    .border(1.dp, if (isSelected) KalynoBlue else KalynoOutline, CircleShape)
                    .clickable {
                        haptic()
                        onSelect(filter)
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Sélection pour toi",
            subtitle = "Choisie pour ta classe",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        if (picks.isEmpty()) {
            Text(
                text = "Les cartes de tes chapitres arrivent bientôt.",
                color = KalynoMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(picks, key = { it.id }) { chapter ->
                    CourseCard(
                        chapter = chapter,
                        onStart = { onStart(chapter) },
                        onOpen = { onOpen(chapter) },
                        modifier = Modifier.width(272.dp)
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Nouveaux parcours",
            subtitle = "Publiés en direct",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        PathGrid(chapters = latest, onOpen = onOpen, modifier = Modifier.padding(horizontal = 20.dp))
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
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        bySubject.forEach { list ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(title = "${list.first().subjectEmoji} ${list.first().subjectName}")
                PathGrid(chapters = list, onOpen = onOpen)
            }
        }
    }
}

// ===========================================================================
// CARTES
// ===========================================================================

/** Carte de cours : badge matière, titre, progression, durée, bouton "Démarrer". */
@Composable
private fun CourseCard(
    chapter: ExplorerChapter,
    onStart: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    KalynoCard(modifier = modifier, onClick = onOpen) {
        KalynoPill(text = "${chapter.subjectEmoji} ${chapter.subjectName}")
        Spacer(Modifier.height(12.dp))
        Text(
            text = chapter.title,
            color = KalynoText,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(14.dp))
        KalynoProgressBar(progress = chapter.progress)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "⏱️ ${questionsLabel(chapter.questionCount)} • ${chapter.minutes} min",
                color = KalynoMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${(chapter.progress * 100).toInt()}%",
                color = KalynoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(14.dp))
        KalynoPrimaryButton(
            label = "DÉMARRER",
            onClick = onStart,
            enabled = chapter.questionCount > 0,
            height = 48.dp
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        chapters.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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

    KalynoCard(modifier = modifier, onClick = onClick, contentPadding = 16.dp) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tileColor),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = PATH_ICONS[index], size = 28.dp, tint = tint)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = chapter.title,
            color = KalynoText,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (chapter.questionCount > 0) questionsLabel(chapter.questionCount) else "Leçons",
            color = KalynoMuted,
            fontSize = 12.sp
        )
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
                Text("🏆", fontSize = 28.sp)
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
                        Text(subject.emoji, fontSize = 16.sp)
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
// DÉTAIL D'UN CHAPITRE (leçons + démarrage)
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSheet(
    chapter: ExplorerChapter,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KalynoPill(text = "${chapter.subjectEmoji} ${chapter.subjectName}")
            Text(
                text = chapter.title,
                color = KalynoText,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (chapter.questionCount > 0) {
                Text(
                    text = "⏱️ ${questionsLabel(chapter.questionCount)} • ${chapter.minutes} min",
                    color = KalynoMuted,
                    fontSize = 14.sp
                )
                KalynoProgressBar(progress = chapter.progress)
            }

            chapter.capsules.forEach { capsule ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(KalynoBlueSoft)
                        .padding(16.dp)
                ) {
                    Text(
                        text = capsule.title,
                        color = KalynoBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (capsule.minutes > 0) {
                        Text("${capsule.minutes} min de lecture", color = KalynoMuted, fontSize = 12.sp)
                    }
                    if (capsule.body.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = capsule.body,
                            color = KalynoText,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }

            if (chapter.questionCount > 0) {
                Spacer(Modifier.height(4.dp))
                KalynoPrimaryButton(
                    label = "DÉMARRER LES ${chapter.questionCount} QUESTIONS",
                    onClick = onStart
                )
            }
        }
    }
}

// ===========================================================================
// SQUELETTE DE CHARGEMENT
// ===========================================================================

@Composable
private fun ExplorerSkeleton() {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ShimmerBlock(Modifier.width(180.dp).height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(2) {
                ShimmerBlock(
                    modifier = Modifier.weight(1f).height(210.dp),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
        ShimmerBlock(Modifier.width(160.dp).height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(2) {
                ShimmerBlock(
                    modifier = Modifier.weight(1f).height(130.dp),
                    shape = RoundedCornerShape(24.dp)
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
