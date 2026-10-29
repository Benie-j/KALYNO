package ci.kalyno.app.ml.semantic

import android.content.Context

/**
 * Point d'entrée unique pour le Dojo : combine le moteur ONNX et les seuils pilotés par l'admin.
 * Le ViewModel ne manipule ni ONNX, ni Supabase, ni seuils.
 */
class SemanticEvaluator(
    private val appContext: Context,
    private val settings: SemanticSettingsRepository
) {
    /** Met à jour les seuils en arrière-plan (à appeler au lancement de l'appli ou à l'ouverture du Dojo). */
    suspend fun refreshSettings(): Boolean = settings.refresh()

    /**
     * Correction à partir du texte de la leçon quand l'admin n'a pas écrit de référence détaillée.
     * @param expectedShort bonne réponse courte de la carte (mot-clé ou option correcte)
     */
    suspend fun evaluateWithLesson(
        question: String,
        expectedShort: String,
        passages: List<String>,
        studentAnswer: String,
        scope: String? = null
    ): EvaluationResult =
        SemanticEvaluationEngine.getInstance(appContext).evaluateWithLessonAsync(
            question, expectedShort, passages, studentAnswer,
            settings.thresholdsFor(scope).relaxedForLesson()
        )

    /** Précalcule les vecteurs des passages d'une leçon (appel en arrière-plan). */
    suspend fun preloadPassages(passages: List<String>) =
        SemanticEvaluationEngine.getInstance(appContext).preloadAsync(passages)

    /**
     * @param adminReference réponse de référence saisie par l'admin
     * @param scope identifiant de la matière (seuil spécifique) ou null pour le seuil global
     */
    suspend fun evaluate(adminReference: String, studentAnswer: String, scope: String? = null): EvaluationResult =
        SemanticEvaluationEngine.getInstance(appContext)
            .evaluateAsync(adminReference, studentAnswer, settings.thresholdsFor(scope))
}
