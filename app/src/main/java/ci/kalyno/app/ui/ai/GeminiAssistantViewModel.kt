package ci.kalyno.app.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.ai.AiExerciseContext
import ci.kalyno.app.data.ai.AiGateway
import ci.kalyno.app.data.ai.AiPhase
import ci.kalyno.app.data.ai.ChatTurn
import ci.kalyno.app.data.ai.GeminiException
import ci.kalyno.app.data.ai.GeminiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Conversation avec Gemini : onglet indépendant (sans contexte) ou aide d'un exercice (avec contexte). */
class GeminiAssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val gateway: AiGateway = GeminiRepository.get(app)
    private var nextId = 0L

    private val _state = MutableStateFlow(GeminiUiState(signedIn = gateway.isAvailable()))
    val state: StateFlow<GeminiUiState> = _state.asStateFlow()

    /** Dernier message utilisateur sans réponse, pour « Réessayer ». */
    private var pending: String? = null

    /** Met à jour le contexte (ex. l'exercice vient d'être validé) sans toucher à la conversation. */
    fun setContext(context: AiExerciseContext?) {
        _state.update { it.copy(context = context, signedIn = gateway.isAvailable()) }
    }

    fun onInput(value: String) {
        _state.update { it.copy(input = value.take(MAX_INPUT)) }
    }

    fun send(text: String = _state.value.input) {
        val message = text.trim()
        val s = _state.value
        if (message.isEmpty() || s.busy || !gateway.isAvailable()) return
        _state.update {
            it.copy(
                messages = it.messages + ChatMessage(nextId++, true, message),
                input = "",
                phase = AiPhase.LOADING,
                error = null
            )
        }
        pending = message
        request(message, retrying = false)
    }

    fun retry() {
        val message = pending ?: return
        if (_state.value.busy) return
        _state.update { it.copy(phase = AiPhase.RETRYING, error = null) }
        request(message, retrying = true)
    }

    private fun request(message: String, retrying: Boolean) {
        val s = _state.value
        // Historique limité : les derniers tours seulement, sans le message en cours
        val history = s.messages.dropLast(1).takeLast(MAX_HISTORY).map { ChatTurn(it.fromUser, it.text) }
        viewModelScope.launch {
            try {
                val reply = gateway.assist(message, s.context, history)
                pending = null
                _state.update {
                    it.copy(
                        messages = it.messages + ChatMessage(nextId++, false, reply),
                        phase = AiPhase.SUCCESS,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeminiException) {
                _state.update { it.copy(phase = AiPhase.ERROR, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(phase = AiPhase.ERROR, error = "Erreur inattendue. Réessaie.") }
            }
        }
    }

    private companion object {
        const val MAX_INPUT = 1_200
        const val MAX_HISTORY = 10
    }
}
