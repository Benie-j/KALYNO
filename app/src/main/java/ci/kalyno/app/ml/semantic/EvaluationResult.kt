package ci.kalyno.app.ml.semantic

/** Cause précise du verdict, utile pour l'UI et les statistiques admin. */
enum class EvaluationVerdict {
    CORRECT,        // similarité >= seuil et aucun garde-fou déclenché
    PARTIAL,        // proche mais sous le seuil
    INCORRECT,      // trop éloignée
    CONTRADICTION,  // similaire en surface mais sens inversé (négation, antonyme, chiffres)
    EMPTY_INPUT,    // réponse vide
    ENGINE_ERROR    // erreur technique : la réponse n'est pas notée
}

/** Résultat structuré d'une évaluation sémantique. */
data class EvaluationResult(
    val isValid: Boolean,
    val similarityScore: Float,
    val feedback: String,
    val verdict: EvaluationVerdict
)
