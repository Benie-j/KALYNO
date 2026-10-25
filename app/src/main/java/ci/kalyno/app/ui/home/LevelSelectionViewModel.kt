package ci.kalyno.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.LevelInfo
import ci.kalyno.app.data.catalog.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un groupe d'examen (BEPC, BAC…) et ses classes/séries, dans l'ordre publié par l'admin. */
data class LevelGroup(val cycle: String, val levels: List<LevelInfo>)

data class LevelSelectionState(
    val loading: Boolean = true,
    val groups: List<LevelGroup> = emptyList(),
    /** Renseigné uniquement s'il n'y a rien à afficher (ni réseau, ni Room). */
    val error: String? = null
)

/**
 * Les classes viennent de Room (affichage immédiat, même hors ligne) ;
 * le réseau les met à jour en arrière-plan. Aucune série n'est écrite en dur.
 */
class LevelSelectionViewModel(app: Application) : AndroidViewModel(app) {

    private val catalog = CatalogRepository.get(app)

    private val _state = MutableStateFlow(LevelSelectionState())
    val state: StateFlow<LevelSelectionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            catalog.observeLevels().collect { levels ->
                _state.update {
                    it.copy(
                        groups = group(levels),
                        // Dès qu'on a des classes en local, on n'attend plus le réseau
                        loading = it.loading && levels.isEmpty(),
                        error = if (levels.isEmpty()) it.error else null
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { if (it.groups.isEmpty()) it.copy(loading = true, error = null) else it }
            try {
                catalog.refreshLevels()
                _state.update { it.copy(loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    if (it.groups.isNotEmpty()) {
                        it.copy(loading = false)
                    } else {
                        it.copy(loading = false, error = "Impossible de charger les classes. Vérifie ta connexion.")
                    }
                }
            }
        }
    }

    private fun group(levels: List<LevelInfo>): List<LevelGroup> =
        levels.groupBy { it.cycle }
            .map { (cycle, list) -> LevelGroup(cycle, list.sortedBy { it.position }) }
            .sortedBy { group -> group.levels.minOf { it.position } }
}
