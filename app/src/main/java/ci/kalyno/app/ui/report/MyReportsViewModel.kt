package ci.kalyno.app.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.reports.ReportEntity
import ci.kalyno.app.data.reports.ReportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** « Mes signalements » : lit la base locale (toujours disponible) et la rafraîchit depuis Supabase. */
class MyReportsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ReportRepository.get(app)
    private val auth = AuthStore(app)

    private val _reports = MutableStateFlow<List<ReportEntity>?>(null)
    /** null = chargement en cours. */
    val reports: StateFlow<List<ReportEntity>?> = _reports.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        viewModelScope.launch { repository.observeAll().collect { _reports.value = it } }
    }

    fun isSignedIn(): Boolean = auth.userId() != null

    /** Renvoie d'abord ce qui attendait du réseau, puis récupère statuts et réponses de l'équipe. */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                repository.syncPending()
                repository.refreshFromServer()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Hors ligne : la liste locale reste affichée
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun markSeen() {
        viewModelScope.launch { repository.markRepliesSeen() }
    }
}
