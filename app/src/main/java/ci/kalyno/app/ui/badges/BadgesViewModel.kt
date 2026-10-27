package ci.kalyno.app.ui.badges

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.badges.BadgeDef
import ci.kalyno.app.data.badges.BadgeUiState
import ci.kalyno.app.data.badges.BadgesRepository
import ci.kalyno.app.data.dashboard.StudentId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BadgesUiState(
    val badges: List<BadgeUiState> = emptyList(),
    val pendingReward: BadgeDef? = null
)

class BadgesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = BadgesRepository(app)
    private val studentId = StudentId.get(app)

    private val _state = MutableStateFlow(BadgesUiState())
    val state: StateFlow<BadgesUiState> = _state.asStateFlow()

    /** Recalcule tous les badges ; met en file le premier déblocage frais pour la modale. */
    fun refresh() {
        viewModelScope.launch {
            val (badges, fresh) = repository.refresh(studentId)
            _state.update { it.copy(badges = badges, pendingReward = it.pendingReward ?: fresh.firstOrNull()) }
        }
    }

    fun consumeReward() {
        _state.update { it.copy(pendingReward = null) }
    }
}
