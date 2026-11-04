package ci.kalyno.app.ui.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.catalog.CatalogRepository
import ci.kalyno.app.ui.explorer.LearningContentCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Prépare ce dont le premier écran a besoin, pendant que le splash système reste affiché ([ready] = faux).
 * Le splash laisse la place dès que tout est prêt ; il ne dépend jamais du réseau pour un élève déjà inscrit.
 *
 * - toujours : préférences, session, base Room (ouverte, migrations comprises) et libellés des classes ;
 * - élève déjà inscrit : contenu de sa classe lu depuis le cache hors ligne (l'accueil et Explorer s'affichent sans attente) ;
 * - première ouverture : liste des classes à choisir (Room si elle est connue, sinon un seul passage réseau),
 *   pour que l'écran de choix s'ouvre déjà rempli.
 *
 * Garde-fou : après [MAX_WAIT_MS], l'appli s'affiche quoi qu'il arrive (disque lent, réseau coupé).
 */
class StartupViewModel(app: Application) : AndroidViewModel(app) {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { prepare() }
            _ready.value = true
        }
        // Minuterie indépendante : une lecture bloquante ne peut pas retenir le splash au-delà du délai
        viewModelScope.launch {
            delay(MAX_WAIT_MS)
            _ready.value = true
        }
    }

    private suspend fun prepare() {
        val app = getApplication<Application>()
        val level = AppPrefs(app).level
        AuthStore(app).isSignedIn()

        val catalog = CatalogRepository.get(app)
        val levelsKnown = runCatching { catalog.hydrateLabels() }.getOrDefault(false)

        if (level != null) {
            runCatching { LearningContentCache(app).read(level) }
        } else if (!levelsKnown) {
            runCatching { catalog.refreshLevels() }
        }
    }

    private companion object {
        const val MAX_WAIT_MS = 2_500L
    }
}
