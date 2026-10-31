package ci.kalyno.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.SupabaseChallengeRepository
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.catalog.CatalogRepository
import ci.kalyno.app.data.auth.findActivity
import ci.kalyno.app.ui.auth.AuthViewModel
import ci.kalyno.app.ui.challenge.ChallengeScreen
import ci.kalyno.app.ui.home.HomeScreen
import ci.kalyno.app.ui.auth.KalynoAuthScreen
import ci.kalyno.app.ui.main.MainScreen
import ci.kalyno.app.ui.onboarding.NicknameScreen
import ci.kalyno.app.ui.onboarding.RegistrationChoiceScreen
import ci.kalyno.app.data.reports.ReportSyncScheduler

@Composable
fun KalynoApp() {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val prefs = remember { AppPrefs(context) }
    val authStore = remember { AuthStore(context) }
    val repository = remember { SupabaseChallengeRepository() }

    // Libellés des classes (Tle A1, Tle A2…) : Room d'abord, puis mise à jour réseau en douceur
    LaunchedEffect(Unit) { runCatching { CatalogRepository.get(context).warmUp() } }

    // Signalements restés hors ligne lors d'une session précédente : on relance leur envoi
    LaunchedEffect(Unit) { runCatching { ReportSyncScheduler.requestIfPending(context) } }

    val authViewModel: AuthViewModel = viewModel()
    val auth by authViewModel.state.collectAsStateWithLifecycle()

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
        val activity = activityContext.findActivity()
        if (activity != null) authViewModel.signInWithGoogle(activity)
        else authViewModel.fail("Impossible d'ouvrir la fenêtre Google (écran introuvable).")
    }

    val current = level
    val name = nickname
    val accountLabel = if (auth.signedIn) (authStore.userEmail() ?: authStore.userName()) else null

    when {
        showLogin -> {
            BackHandler {
                authViewModel.clearError()
                showLogin = false
            }
            KalynoAuthScreen(
                onBack = {
                    authViewModel.clearError()
                    showLogin = false
                },
                onGoogleClick = startGoogle,
                emailStage = auth.emailStage,
                pendingEmail = auth.pendingEmail,
                onStartEmailFlow = authViewModel::startEmailFlow,
                onSendEmail = authViewModel::sendEmailCode,
                onVerifyCode = authViewModel::verifyEmailCode,
                onCancelEmailFlow = authViewModel::cancelEmailFlow,
                isLoading = auth.loading,
                errorMessage = auth.error
            )
        }
        current == null -> HomeScreen(
            onLevelSelected = {
                prefs.level = it
                level = it
            },
            onLoginClick = { showLogin = true },
            canLogin = !auth.signedIn
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
            emailStage = auth.emailStage,
            pendingEmail = auth.pendingEmail,
            onStartEmailFlow = authViewModel::startEmailFlow,
            onSendEmail = authViewModel::sendEmailCode,
            onVerifyCode = authViewModel::verifyEmailCode,
            onCancelEmailFlow = authViewModel::cancelEmailFlow,
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
            onLoginClick = { showLogin = true },
            signedIn = auth.signedIn,
            accountLabel = accountLabel,
            onNicknameChange = {
                prefs.nickname = it
                nickname = it
            },
            onLogoutClick = { authViewModel.signOut() }
        )
    }
}
