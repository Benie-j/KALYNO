package ci.kalyno.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.SupabaseChallengeRepository
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.auth.findActivity
import ci.kalyno.app.ui.auth.AuthViewModel
import ci.kalyno.app.ui.challenge.ChallengeScreen
import ci.kalyno.app.ui.home.HomeScreen
import ci.kalyno.app.ui.login.LoginScreen
import ci.kalyno.app.ui.main.MainScreen
import ci.kalyno.app.ui.onboarding.NicknameScreen
import ci.kalyno.app.ui.onboarding.RegistrationChoiceScreen

@Composable
fun KalynoApp() {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val prefs = remember { AppPrefs(context) }
    val authStore = remember { AuthStore(context) }
    val repository = remember { SupabaseChallengeRepository() }

    val authViewModel: AuthViewModel = viewModel()
    val auth by authViewModel.state.collectAsState()

    var level by remember { mutableStateOf(prefs.level) }
    var nickname by remember { mutableStateOf(prefs.nickname) }
    var demoDone by remember { mutableStateOf(prefs.demoDone) }
    // Vrai quand aucune démo n'est publiée : on passe à la suite sans la marquer comme faite
    var skipDemo by remember { mutableStateOf(false) }
    var choiceDone by remember { mutableStateOf(authStore.registrationChoiceDone || authStore.isSignedIn()) }
    var showLogin by rememberSaveable { mutableStateOf(false) }

    // Connexion Google réussie : on enregistre le choix et on referme l'écran de connexion
    LaunchedEffect(auth.signedIn) {
        if (auth.signedIn) {
            choiceDone = true
            showLogin = false
        }
    }

    // Compte retrouvé sur un nouveau téléphone : on reprend classe, pseudo et parcours
    LaunchedEffect(auth.restored) {
        auth.restored?.let {
            level = it.levelId
            nickname = it.nickname
            demoDone = true
            authViewModel.consumeRestored()
        }
    }

    val startGoogle: () -> Unit = {
        activityContext.findActivity()?.let { authViewModel.signInWithGoogle(it) }
    }

    val current = level
    val name = nickname

    when {
        showLogin -> {
            BackHandler {
                authViewModel.clearError()
                showLogin = false
            }
            LoginScreen(
                onBack = {
                    authViewModel.clearError()
                    showLogin = false
                },
                onGoogleClick = startGoogle,
                isLoading = auth.loading,
                errorMessage = auth.error
            )
        }
        current == null -> HomeScreen(
            onLevelSelected = {
                prefs.level = it
                level = it
            },
            onLoginClick = { showLogin = true }
        )
        name == null -> {
            BackHandler {
                prefs.level = null
                level = null
            }
            NicknameScreen(onConfirm = {
                prefs.nickname = it
                nickname = it
            })
        }
        !demoDone && !skipDemo -> ChallengeScreen(
            levelId = current,
            repository = repository,
            onExit = {
                prefs.demoDone = true
                demoDone = true
            },
            onUnavailable = { skipDemo = true }
        )
        !choiceDone -> RegistrationChoiceScreen(
            pseudo = name,
            onGoogleClick = startGoogle,
            onGuestClick = {
                authStore.registrationChoiceDone = true
                choiceDone = true
            },
            isLoading = auth.loading,
            errorMessage = auth.error
        )
        else -> MainScreen(
            levelId = current,
            nickname = name,
            onChangeLevel = {
                prefs.level = null
                level = null
                skipDemo = false
            },
            onLoginClick = { showLogin = true }
        )
    }
}
