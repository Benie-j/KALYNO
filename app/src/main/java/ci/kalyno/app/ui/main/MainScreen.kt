package ci.kalyno.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.SupabaseLearningRepository
import ci.kalyno.app.data.LearningSubject
import ci.kalyno.app.data.LearningCard
import ci.kalyno.app.ui.theme.*
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.ui.review.ReviewSessionScreen

private data class TabItem(val icon: String, val label: String)

private val TABS = listOf(
    TabItem("home", "Accueil"),
    TabItem("brain", "Dojo"),
    TabItem("book-open", "Explorer"),
    TabItem("user-round", "Profil")
)

@Composable
fun MainScreen(
    levelId: String,
    nickname: String,
    onChangeLevel: () -> Unit,
    onLoginClick: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(key = "dashboard-$levelId")
    val state by viewModel.state.collectAsState()
    LaunchedEffect(levelId, nickname) { viewModel.load(levelId, nickname) }

    var tab by rememberSaveable { mutableStateOf(0) }
    val session = state.session

    if (session != null) {
        BackHandler { viewModel.closeSession(false) }
        ReviewSessionScreen(
            session = session,
            onRate = { cardId, rating -> viewModel.rate(cardId, rating) },
            onClose = { completed -> viewModel.closeSession(completed) }
        )
    } else {
        BackHandler(enabled = tab != 0) { tab = 0 }

        Scaffold(
            containerColor = KalynoBackground,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    TABS.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { KalynoSvgIcon(name = item.icon, size = 24.dp, tint = if (tab == index) KalynoBlue else KalynoMuted) },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = KalynoBlue,
                                unselectedTextColor = KalynoMuted,
                                indicatorColor = KalynoBlueSoft
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    0 -> PersonalDashboard(
                        levelId = levelId,
                        state = state,
                        onStartMission = viewModel::startMission,
                        onReviewUrgent = viewModel::startUrgentReview,
                        onRetry = viewModel::retry
                    )
                    1 -> DojoScreen(levelId)
                    2 -> ExplorerScreen(levelId)
                    else -> ProfileScreen(levelId, onChangeLevel, onLoginClick)
                }
            }
        }
    }
}

// ---------- Chargement des contenus Supabase ----------

@Composable
private fun rememberLearningContent(levelId: String): LearningContentState {
    val repository = remember { SupabaseLearningRepository() }
    var subjects by remember(levelId) {
        mutableStateOf<List<LearningSubject>>(emptyList())
    }
    var loading by remember(levelId) { mutableStateOf(true) }
    var error by remember(levelId) { mutableStateOf<String?>(null) }
    var reload by remember(levelId) { mutableIntStateOf(0) }

    LaunchedEffect(levelId, reload) {
        loading = true
        error = null

        try {
            subjects = repository.loadPublishedContent(levelId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            error = exception.message
                ?.takeIf { it.isNotBlank() }
                ?: "Impossible de charger les contenus."
        } finally {
            loading = false
        }
    }

    return LearningContentState(
        subjects = subjects,
        loading = loading,
        error = error,
        retry = { reload++ }
    )
}

private data class LearningContentState(
    val subjects: List<LearningSubject>,
    val loading: Boolean,
    val error: String?,
    val retry: () -> Unit
)

// ---------- Explorer ----------

@Composable
private fun ExplorerScreen(levelId: String) {
    val state = rememberLearningContent(levelId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "📚 Explorer",
            color = KalynoText,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = Levels.label(levelId),
            color = KalynoMuted,
            fontSize = 14.sp
        )

        when {
            state.loading -> LoadingContent()

            state.error != null -> ErrorContent(
                message = state.error,
                onRetry = state.retry
            )

            state.subjects.none { it.chapters.isNotEmpty() } -> EmptyTab(
                emoji = "📚",
                title = "Aucun chapitre publié",
                text = "Les matières et les chapitres apparaîtront ici après leur publication."
            )

            else -> {
                state.subjects
                    .filter { it.chapters.isNotEmpty() }
                    .forEach { subject ->
                        DashCard {
                            Text(
                                text = "${subject.emoji} ${subject.name}",
                                color = KalynoBlue,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Spacer(Modifier.height(10.dp))

                            subject.chapters.forEach { chapter ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = chapter.title,
                                        color = KalynoText,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    chapter.capsules.forEach { capsule ->
                                        Spacer(Modifier.height(8.dp))

                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            color = KalynoBackground
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                Text(
                                                    text = capsule.title,
                                                    color = KalynoBlue,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold
                                                )

                                                if (capsule.minutes > 0) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = "${capsule.minutes} min",
                                                        color = KalynoMuted,
                                                        fontSize = 12.sp
                                                    )
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
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

// ---------- Dojo ----------

@Composable
private fun DojoScreen(levelId: String) {
    val state = rememberLearningContent(levelId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "🧠 Le Dojo",
            color = KalynoText,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = "Cartes de révision · ${Levels.label(levelId)}",
            color = KalynoMuted,
            fontSize = 14.sp
        )

        when {
            state.loading -> LoadingContent()

            state.error != null -> ErrorContent(
                message = state.error,
                onRetry = state.retry
            )

            state.subjects.none { subject ->
                subject.chapters.any { it.cards.isNotEmpty() }
            } -> EmptyTab(
                emoji = "🧠",
                title = "Aucune carte disponible",
                text = "Les cartes de révision apparaîtront ici après la publication des chapitres correspondants."
            )

            else -> {
                state.subjects.forEach { subject ->
                    subject.chapters.forEach { chapter ->
                        chapter.cards.forEach { card ->
                            LearningCardItem(
                                subjectName = subject.name,
                                chapterTitle = chapter.title,
                                card = card
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningCardItem(
    subjectName: String,
    chapterTitle: String,
    card: LearningCard
) {
    var revealed by rememberSaveable(card.id) {
        mutableStateOf(false)
    }

    DashCard {
        Text(
            text = subjectName,
            color = KalynoBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = chapterTitle,
            color = KalynoMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = card.front,
            color = KalynoText,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold
        )

        if (!card.choices.isNullOrEmpty()) {
            Spacer(Modifier.height(12.dp))

            card.choices.forEachIndexed { index, choice ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (
                        revealed && card.correctIndex == index
                    ) {
                        KalynoCorrectSoft
                    } else {
                        KalynoBackground
                    }
                ) {
                    Text(
                        text = "${('A'.code + index).toChar()}. $choice",
                        color = KalynoText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!revealed) {
            SoftButton(
                label = "Voir la réponse",
                onClick = { revealed = true }
            )
        } else {
            val answer = card.correctIndex
                ?.let { index -> card.choices.getOrNull(index) }
                ?: card.back

            if (!answer.isNullOrBlank()) {
                Text(
                    text = "Réponse",
                    color = KalynoCorrectText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = answer,
                    color = KalynoText,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }

            if (!card.explanation.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Explication",
                    color = KalynoBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = card.explanation,
                    color = KalynoText,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            SoftButton(
                label = "Masquer la réponse",
                onClick = { revealed = false }
            )
        }
    }
}

// ---------- États de chargement et d'erreur ----------

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = KalynoBlue)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Chargement des contenus…",
            color = KalynoMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    DashCard {
        Text(
            text = "Impossible de charger les contenus",
            color = KalynoRetryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = message,
            color = KalynoText,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(12.dp))

        SoftButton("Réessayer", onRetry)
    }
}

// ---------- Profil ----------

@Composable
private fun ProfileScreen(
    levelId: String,
    onChangeLevel: () -> Unit,
    onLoginClick: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val streak = remember { StreakStore(context).current() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "👤 Profil",
            color = KalynoText,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )

        DashCard {
            CardLabel("🎓 MA CLASSE", KalynoBlue)
            Spacer(Modifier.height(8.dp))

            Text(
                text = "${Levels.emoji(levelId)} ${Levels.label(levelId)}",
                color = KalynoText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(12.dp))
            SoftButton("Changer de classe", onChangeLevel)
        }

        DashCard {
            CardLabel("🔥 MA SÉRIE", KalynoBlue)
            Spacer(Modifier.height(8.dp))

            Text(
                text = when (streak) {
                    0 -> "Pas de série en cours. Termine un défi pour la lancer !"
                    1 -> "1 jour de suite"
                    else -> "$streak jours de suite"
                },
                color = KalynoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        DashCard {
            CardLabel("☁️ MON COMPTE", KalynoBlue)
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Connecte-toi avec Google pour retrouver ta progression si tu changes de téléphone.",
                color = KalynoMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(12.dp))
            SoftButton("Se connecter avec Google", onLoginClick)
        }
    }
}

// ---------- Éléments communs ----------

@Composable
private fun EmptyTab(
    emoji: String,
    title: String,
    text: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 56.sp)

        Spacer(Modifier.height(12.dp))

        Text(
            text = title,
            color = KalynoText,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = text,
            color = KalynoMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DashCard(
    background: Color = Color.White,
    stroke: Color = KalynoOutline,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = background,
        border = BorderStroke(2.dp, stroke)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

@Composable
private fun CardLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun SoftButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KalynoBlueSoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = KalynoBlue,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
