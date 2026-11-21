package ci.kalyno.app.ui.ai

import ci.kalyno.app.data.ai.AiExerciseContext
import ci.kalyno.app.data.ai.AiPhase
import ci.kalyno.app.data.ai.ChatTurn

/** État d'une correction IA en cours (Dojo et Challenge). */
data class GradingState(
    val phase: AiPhase = AiPhase.IDLE,
    val error: String? = null
) {
    /** Une correction est en vol : validations multiples et modification de la réponse interdites. */
    val busy: Boolean get() = phase == AiPhase.LOADING || phase == AiPhase.RETRYING
    val failed: Boolean get() = phase == AiPhase.ERROR
}

data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String)

/** État de l'assistant (écran indépendant et feuille d'aide des exercices). */
data class KalynoUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val phase: AiPhase = AiPhase.IDLE,
    val error: String? = null,
    val context: AiExerciseContext? = null,
    val signedIn: Boolean = true
) {
    val busy: Boolean get() = phase == AiPhase.LOADING || phase == AiPhase.RETRYING
    val canSend: Boolean get() = !busy && input.isNotBlank() && signedIn
}
