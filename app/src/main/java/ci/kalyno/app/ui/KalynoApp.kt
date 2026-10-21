package ci.kalyno.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import ci.kalyno.app.data.SupabaseChallengeRepository
import ci.kalyno.app.ui.challenge.ChallengeScreen
import ci.kalyno.app.ui.home.HomeScreen
import ci.kalyno.app.ui.login.LoginScreen

@Composable
fun KalynoApp() {
    var levelId by rememberSaveable { mutableStateOf<String?>(null) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    val repository = remember { SupabaseChallengeRepository() }
    val current = levelId

    when {
        showLogin -> {
            BackHandler { showLogin = false }
            LoginScreen(onBack = { showLogin = false })
        }
        current == null -> HomeScreen(
            onLevelSelected = { levelId = it },
            onLoginClick = { showLogin = true }
        )
        else -> {
            BackHandler { levelId = null }
            ChallengeScreen(
                levelId = current,
                repository = repository,
                onExit = { levelId = null }
            )
        }
    }
}
