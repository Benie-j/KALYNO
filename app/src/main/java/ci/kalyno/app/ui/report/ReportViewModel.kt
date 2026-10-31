package ci.kalyno.app.ui.report

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.reports.ReportCategory
import ci.kalyno.app.data.reports.ReportDraft
import ci.kalyno.app.data.reports.ReportOutcome
import ci.kalyno.app.data.reports.ReportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ReportFormState(
    val sending: Boolean = false,
    val error: String? = null
)

/**
 * L'envoi vit dans `viewModelScope` : fermer la feuille ou changer d'écran en plein envoi
 * n'annule pas le signalement (de toute façon déjà écrit dans Room avant le réseau).
 */
class ReportViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ReportRepository.get(app)

    private val _state = MutableStateFlow(ReportFormState())
    val state: StateFlow<ReportFormState> = _state.asStateFlow()

    fun reset() {
        if (!_state.value.sending) _state.value = ReportFormState()
    }

    fun submit(
        target: ReportTarget,
        category: ReportCategory,
        description: String,
        onDone: (ReportOutcome) -> Unit
    ) {
        if (_state.value.sending) return // anti double-toucher
        _state.value = ReportFormState(sending = true)
        viewModelScope.launch {
            try {
                val outcome = repository.submit(
                    ReportDraft(
                        sourceType = target.sourceType,
                        sourceId = target.sourceId,
                        category = category,
                        description = description,
                        contextLabel = target.contextLabel
                    )
                )
                _state.update { it.copy(sending = false) }
                onDone(outcome)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Seul cas d'échec : l'écriture locale elle-même (stockage plein…)
                _state.value = ReportFormState(
                    sending = false,
                    error = "Impossible d'enregistrer le signalement. Réessaie dans un instant."
                )
            }
        }
    }
}
