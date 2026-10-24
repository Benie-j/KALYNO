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
import ci.kalyno.app.ui.theme.*
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.ui.dojo.DojoScreen
import ci.kalyno.app.ui.explorer.ExplorerScreen
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
    onLoginClick: () -> Unit,
    signedIn: Boolean = false,
    accountLabel: String? = null,
    onLogoutClick: () -> Unit = {}
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
                    1 -> DojoScreen(
                        levelId = levelId,
                        state = state,
                        onStartMission = viewModel::startMission,
                        onReviewUrgent = viewModel::startUrgentReview,
                        onRetry = viewModel::retry
                    )
                    2 -> ExplorerScreen(
                        levelId = levelId,
                        nickname = nickname,
                        streak = state.streak,
                        onStartChapter = viewModel::startCustomSession
                    )
                    else -> ProfileScreen(levelId, onChangeLevel, onLoginClick, signedIn, accountLabel, onLogoutClick)
                }
            }
        }
    }
}

// ---------- Profil ----------

@Composable
private fun ProfileScreen(
    levelId: String,
    onChangeLevel: () -> Unit,
    onLoginClick: () -> Unit,
    signedIn: Boolean,
    accountLabel: String?,
    onLogoutClick: () -> Unit
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

            if (signedIn) {
                Text(
                    text = "✅ Connecté" + (accountLabel?.let { " : $it" } ?: ""),
                    color = KalynoText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ta progression est sauvegardée et retrouvée si tu changes de téléphone.",
                    color = KalynoMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                SoftButton("Se déconnecter", onLogoutClick)
            } else {
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
}

// ---------- Éléments communs ----------

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
