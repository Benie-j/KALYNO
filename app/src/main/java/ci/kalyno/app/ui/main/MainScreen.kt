package ci.kalyno.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Levels
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.notifications.StudyReminders
import ci.kalyno.app.ui.theme.*
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.ui.dojo.DojoScreen
import ci.kalyno.app.ui.explorer.ExplorerScreen
import ci.kalyno.app.ui.profile.ProfileLinks
import ci.kalyno.app.ui.profile.ProfileScreen
import ci.kalyno.app.ui.profile.appVersionLabel
import ci.kalyno.app.ui.profile.emailSupportOrSoon
import ci.kalyno.app.ui.profile.openLinkOrSoon
import ci.kalyno.app.ui.dojo.DojoSessionScreen
import ci.kalyno.app.ui.progress.ProgressHistoryScreen
import ci.kalyno.app.ui.report.ReportSheetHost
import ci.kalyno.app.ui.report.ReportSnackbarHost
import ci.kalyno.app.ui.report.ReportTarget
import ci.kalyno.app.ui.report.rememberReportLayerState
import ci.kalyno.app.data.reports.ReportRepository
import ci.kalyno.app.ui.report.MyReportsScreen

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
    onLoginClick: () -> Unit,
    signedIn: Boolean = false,
    accountLabel: String? = null,
    onNicknameChange: (String) -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    val viewModel: DashboardViewModel = viewModel(key = "dashboard-$levelId")
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(levelId, nickname) { viewModel.load(levelId, nickname) }

    var tab by rememberSaveable { mutableStateOf(0) }
    var progressOpen by rememberSaveable { mutableStateOf(false) }
    var myReportsOpen by rememberSaveable { mutableStateOf(false) }
    val appContext = LocalContext.current
    val reportRepository = remember { ReportRepository.get(appContext) }
    val unreadReplies by reportRepository.observeUnreadReplies().collectAsStateWithLifecycle(initialValue = 0)
    // Réponses de l'équipe aux signalements : lues à l'ouverture (silencieux hors ligne ou sans compte)
    LaunchedEffect(signedIn) { if (signedIn) runCatching { reportRepository.refreshFromServer() } }
    val reportLayer = rememberReportLayerState()

    // Tap sur une notification (Dojo / révisions urgentes) : on ouvre l'onglet Dojo
    val openDojo by StudyReminders.openDojoRequested
    LaunchedEffect(openDojo) {
        if (openDojo) {
            tab = 1
            StudyReminders.openDojoRequested.value = false
        }
    }
    val session = state.session

    if (session != null) {
        BackHandler { viewModel.closeSession(false) }
        DojoSessionScreen(
            session = session,
            levelId = levelId,
            onClose = { completed -> viewModel.closeSession(completed) }
        )
    } else if (myReportsOpen) {
        BackHandler { myReportsOpen = false }
        MyReportsScreen(onBack = { myReportsOpen = false })
    } else if (progressOpen) {
        BackHandler { progressOpen = false }
        ProgressHistoryScreen(
            retentionScore = state.retentionScore,
            onBack = { progressOpen = false }
        )
    } else {
        BackHandler(enabled = tab != 0) { tab = 0 }

        Scaffold(
            containerColor = KalynoBackground,
            snackbarHost = { ReportSnackbarHost(reportLayer) },
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
            ReportSheetHost(reportLayer)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    0 -> AccueilScreen(
                        state = state,
                        onStartMission = viewModel::startMission,
                        onReviewUrgent = viewModel::startUrgentReview,
                        onRetry = viewModel::retry,
                        onResumeLastLesson = { tab = 2 }, // ouvre l'onglet Explorer
                        onShuffleQuote = viewModel::shuffleQuote,
                        onToggleFavoriteQuote = viewModel::toggleFavorite,
                        onOpenProgress = { progressOpen = true }
                    )
                    1 -> DojoScreen(
                        levelId = levelId,
                        state = state,
                        onStartMission = viewModel::startMission,
                        onReviewUrgent = viewModel::startUrgentReview,
                        onRetry = viewModel::retry
                    )
                    2 -> ExplorerScreen(
                        levelId = levelId,
                        onStartChapter = viewModel::startCustomSession
                    )
                    else -> {
                        val context = LocalContext.current
                        val levelLabel = Levels.label(levelId)
                        val version = context.appVersionLabel()
                        ProfileScreen(
                            levelId = levelId,
                            nickname = nickname,
                            streak = state.streak,
                            userEmail = if (signedIn) (accountLabel ?: "Compte connecté") else null,
                            onNicknameChange = onNicknameChange,
                            onChangeLevel = onChangeLevel,
                            onLoginClick = onLoginClick,
                            onLogoutClick = onLogoutClick,
                            onOpenHelp = { context.openLinkOrSoon(ProfileLinks.HELP_URL) },
                            onContactSupport = {
                                context.emailSupportOrSoon(
                                    subject = "[KALYNO] Question",
                                    body = "\n\n---\nClasse : $levelLabel\n$version"
                                )
                            },
                            onReportIssue = {
                                reportLayer.open(
                                    ReportTarget.settings(levelLabel = levelLabel, versionLabel = version)
                                )
                            },
                            onOpenMyReports = { myReportsOpen = true },
                            unreadReplies = unreadReplies,
                            onOpenTerms = { context.openLinkOrSoon(ProfileLinks.TERMS_URL) },
                            onOpenPrivacy = { context.openLinkOrSoon(ProfileLinks.PRIVACY_URL) }
                        )
                    }
                }
            }
        }
    }
}
