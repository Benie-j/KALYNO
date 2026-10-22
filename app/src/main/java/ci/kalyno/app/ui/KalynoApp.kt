package ci.kalyno.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.SupabaseChallengeRepository
import ci.kalyno.app.ui.challenge.ChallengeScreen
import ci.kalyno.app.ui.home.HomeScreen
import ci.kalyno.app.ui.login.LoginScreen
import ci.kalyno.app.ui.main.MainScreen

@Composable
fun KalynoApp() {
    val context = LocalContext.current.applicationContext
    val prefs = remember { AppPrefs(context) }
    val repository = remember { SupabaseChallengeRepository() }

    var level by remember { mutableStateOf(prefs.level) }
    var demoDone by remember { mutableStateOf(prefs.demoDone) }
    // Vrai quand aucune démo n'est publiée : on passe au tableau de bord sans la marquer comme faite
    var skipDemo by remember { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }

    val current = level

    when {
        showLogin -> {
            BackHandler { showLogin = false }
            LoginScreen(onBack = { showLogin = false })
        }
        current == null -> HomeScreen(
            onLevelSelected = {
                prefs.level = it
                level = it
            },
            onLoginClick = { showLogin = true }
        )
        !demoDone && !skipDemo -> ChallengeScreen(
            levelId = current,
            repository = repository,
            onExit = {
                prefs.demoDone = true
                demoDone = true
            },
            onUnavailable = { skipDemo = true }
        )
        else -> MainScreen(
            levelId = current,
            onChangeLevel = {
                prefs.level = null
                level = null
                skipDemo = false
            },
            onLoginClick = { showLogin = true }
        )
    }
}
