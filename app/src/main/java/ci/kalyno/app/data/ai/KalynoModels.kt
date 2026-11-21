package ci.kalyno.app.data.ai

/** Verdict de la correction sémantique (valeurs exactes renvoyées par la fonction Edge). */
enum class KalynoVerdict { CORRECT, PARTIAL, INCORRECT }

/** Correction validée : n'existe que si le JSON reçu a été entièrement vérifié par [KalynoResultParser]. */
data class GradeResult(
    val verdict: KalynoVerdict,
    val score: Int,
    val confidence: Int,
    val feedback: String,
    val strengths: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val missingElements: List<String> = emptyList(),
    val correctedAnswer: String = ""
) {
    /** Kalyno doute : on le dit à l'élève, sans changer le verdict. */
    val uncertain: Boolean get() = confidence < LOW_CONFIDENCE

    companion object {
        const val LOW_CONFIDENCE = 50
    }
}

/** Contexte pédagogique d'une question, commun au Dojo, au Challenge et à l'assistant. */
data class AiExerciseContext(
    val question: String,
    val reference: String = "",
    val accepted: List<String> = emptyList(),
    val choices: List<String> = emptyList(),
    val explanation: String? = null,
    val hint: String? = null,
    val subject: String? = null,
    val level: String? = null,
    val lesson: String? = null,
    /** Réponse saisie par l'élève (pour « pourquoi ma réponse est fausse ? »). */
    val studentAnswer: String? = null,
    /** Vrai une fois l'exercice validé : l'assistant peut alors expliquer la correction. */
    val locked: Boolean = false
)

data class ChatTurn(val fromUser: Boolean, val text: String)

enum class AiPhase { IDLE, LOADING, SUCCESS, ERROR, RETRYING }

/** Échec d'un appel IA. [retryable] : on peut réessayer sans rien perdre. */
class KalynoException(
    val code: String,
    override val message: String,
    val retryable: Boolean
) : Exception(message)

/** Texte de retour affiché dans le panneau de correction : commentaire, points justes, erreurs, manques. */
fun GradeResult.toDetailText(): String = buildString {
    append(feedback)
    if (strengths.isNotEmpty()) append("\n\nCe qui est juste : ").append(strengths.joinToString(" ; "))
    if (errors.isNotEmpty()) append("\n\nÀ corriger : ").append(errors.joinToString(" ; "))
    if (missingElements.isNotEmpty()) append("\n\nIl manquait : ").append(missingElements.joinToString(" ; "))
    if (uncertain) append("\n\nKalyno n'est pas sûr de cette correction : compare avec le corrigé.")
}
