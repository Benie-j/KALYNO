package ci.kalyno.app.ui.faq

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.faq.FaqRepository
import ci.kalyno.app.data.faq.FaqSearch
import ci.kalyno.app.data.faq.FaqSection
import ci.kalyno.app.data.faq.FaqSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class FaqUiState(
    /** Texte tel que tapé dans la barre de recherche. */
    val query: String = "",
    /** Mots de la recherche, normalisés (casse et accents) : sert au surlignage. */
    val terms: List<String> = emptyList(),
    /** Résultat filtré et regroupé par catégorie, dans l'ordre de l'admin. */
    val sections: List<FaqSection> = emptyList(),
    val resultCount: Int = 0,
    /** Nombre de questions stockées en local, hors filtre. */
    val totalCount: Int = 0,
    /** Room a répondu au moins une fois (sinon on affiche un squelette plutôt qu'un faux « vide »). */
    val loaded: Boolean = false,
    /** Une mise à jour depuis Supabase est en cours. */
    val syncing: Boolean = false,
    /** La dernière tentative de mise à jour a échoué (réseau coupé…). Le contenu local reste affiché. */
    val syncFailed: Boolean = false,
    /** Questions dont la réponse est dépliée. */
    val expanded: Set<String> = emptySet()
) {
    val searching: Boolean get() = terms.isNotEmpty()
    val neverSynced: Boolean get() = loaded && totalCount == 0
}

/**
 * Lit uniquement Room (affichage instantané, hors ligne) et lance la mise à jour Supabase en arrière-plan
 * à l'ouverture. Le filtre de recherche s'applique à chaque frappe, en mémoire, hors du fil principal.
 */
class FaqViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = FaqRepository.get(app)

    private data class SyncState(val syncing: Boolean = false, val failed: Boolean = false)

    private val query = MutableStateFlow("")
    private val expanded = MutableStateFlow<Set<String>>(emptySet())
    private val sync = MutableStateFlow(SyncState())

    val state: StateFlow<FaqUiState> = combine(
        repository.observeItems(), query, expanded, sync
    ) { items, q, open, s ->
        val terms = FaqSearch.terms(q)
        val matching = FaqSearch.filter(items, q)
        FaqUiState(
            query = q,
            terms = terms,
            sections = FaqSearch.group(matching),
            resultCount = matching.size,
            totalCount = items.size,
            loaded = true,
            syncing = s.syncing,
            syncFailed = s.failed,
            expanded = open
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FaqUiState())

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        query.value = value.take(MAX_QUERY)
    }

    fun toggle(id: String) {
        expanded.update { if (id in it) it - id else it + id }
    }

    /** Met à jour depuis Supabase ; l'écran se rafraîchit seul via Room. */
    fun refresh() {
        if (sync.value.syncing) return
        sync.value = SyncState(syncing = true)
        viewModelScope.launch {
            val result = repository.refresh()
            sync.value = SyncState(syncing = false, failed = result == FaqSyncResult.FAILED)
        }
    }

    /** À la fermeture de l'écran : la prochaine ouverture repart d'une recherche vide, réponses repliées. */
    fun resetUi() {
        query.value = ""
        expanded.value = emptySet()
    }

    private companion object {
        const val MAX_QUERY = 100
    }
}
