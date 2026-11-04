package ci.kalyno.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ci.kalyno.app.data.notifications.StudyReminders
import ci.kalyno.app.ui.KalynoApp
import ci.kalyno.app.ui.splash.StartupViewModel
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    /** Prépare les données du premier écran ; le splash système reste affiché tant qu'elles ne sont pas prêtes. */
    private val startup: StartupViewModel by viewModels()

    /** Android 13+ : l'autorisation d'envoyer des notifications se demande à l'exécution. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
    }

    private fun consumeNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(StudyReminders.EXTRA_OPEN_DOJO, false) == true) {
            StudyReminders.openDojoRequested.value = true
            intent.removeExtra(StudyReminders.EXTRA_OPEN_DOJO)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Doit être appelé avant super.onCreate : il installe le splash système
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        val startupState = startup // lance le chargement des données locales dès la création
        // Le splash reste jusqu'à ce que les données soient prêtes, puis s'efface en fondu
        splash.setKeepOnScreenCondition { !startupState.ready.value }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(EXIT_FADE_MS)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { provider.remove() }
                .start()
        }

        // Rappels système (Dojo + révisions urgentes) : sans effet si déjà programmés
        StudyReminders.schedule(this)
        consumeNotificationIntent(intent)

        setContent {
            KalynoTheme {
                val ready by startup.ready.collectAsStateWithLifecycle()

                // L'autorisation de notifier se demande une fois l'accueil visible, jamais par-dessus le splash
                LaunchedEffect(ready) {
                    if (ready) {
                        delay(PERMISSION_DELAY_MS)
                        requestNotificationPermissionIfNeeded()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = KalynoBackground
                ) {
                    KalynoApp()
                }
            }
        }
    }

    private companion object {
        const val EXIT_FADE_MS = 220L
        const val PERMISSION_DELAY_MS = 600L
    }
}
