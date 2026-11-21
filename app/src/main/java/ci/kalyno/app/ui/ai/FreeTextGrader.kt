package ci.kalyno.app.ui.ai

import ci.kalyno.app.data.ai.AiExerciseContext
import ci.kalyno.app.data.ai.AiGateway
import ci.kalyno.app.data.ai.AiPhase
import ci.kalyno.app.data.ai.GeminiException
import ci.kalyno.app.data.ai.GradeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Logique de correction sémantique partagée par le Dojo et le Challenge (aucune duplication).
 * - une seule correction à la fois : un second appel pendant le chargement est ignoré ;
 * - en cas d'échec, rien n'est jugé : l'appelant garde la réponse et propose « Réessayer » ;
 * - [onGraded] n'est appelé qu'avec une correction entièrement validée.
 */
class FreeTextGrader(
    private val gateway: AiGateway,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(GradingState())
    val state: StateFlow<GradingState> = _state.asStateFlow()

    val available: Boolean get() = gateway.isAvailable()

    /** Retourne false si la demande est ignorée (déjà en cours). */
    fun grade(context: AiExerciseContext, answer: String, onGraded: (GradeResult) -> Unit): Boolean {
        val current = _state.value
        if (current.busy) return false
        _state.value = GradingState(
            phase = if (current.failed) AiPhase.RETRYING else AiPhase.LOADING
        )
        scope.launch {
            try {
                val result = gateway.grade(context, answer)
                _state.value = GradingState(AiPhase.SUCCESS)
                onGraded(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeminiException) {
                _state.value = GradingState(AiPhase.ERROR, e.message)
            } catch (e: Exception) {
                _state.value = GradingState(AiPhase.ERROR, "La correction a échoué. Réessaie.")
            }
        }
        return true
    }

    fun reset() {
        _state.value = GradingState()
    }
}
