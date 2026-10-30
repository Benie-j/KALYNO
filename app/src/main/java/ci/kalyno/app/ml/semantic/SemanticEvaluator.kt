package ci.kalyno.app.ml.semantic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Point d'entrée unique pour le Dojo : combine le moteur ONNX et les seuils pilotés par l'admin.
 * Le ViewModel ne manipule ni ONNX, ni Supabase, ni seuils.
 */
class SemanticEvaluator(
    private val appContext: Context,
    private val settings: SemanticSettingsRepository
) {
    /** Faux si le moteur ONNX est coupé sur cet appareil : le Dojo corrige alors par mots-clés. */
    val isAvailable: Boolean get() = SemanticEvaluationEngine.isUsable(appContext)

    /**
     * Charge le moteur HORS du thread appelant (le chargement copie 35 Mo et crée la session ONNX) et ne lève
     * jamais : toute panne devient un résultat ENGINE_ERROR, jamais un plantage.
     */
    private suspend fun engineOrNull(): SemanticEvaluationEngine? = withContext(Dispatchers.Default) {
        try {
            SemanticEvaluationEngine.getInstance(appContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w("SemanticEvaluator", "Moteur indisponible : ${e.message}")
            null
        }
    }

    private fun unavailable() = EvaluationResult(
        false, 0f,
        "L'évaluation automatique est momentanément indisponible. Ta réponse n'a pas été notée.",
        EvaluationVerdict.ENGINE_ERROR
    )

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
        studentAnswer: String
    ): EvaluationResult =
        engineOrNull()?.evaluateWithLessonAsync(question, expectedShort, passages, studentAnswer) ?: unavailable()

    /** Précalcule les vecteurs des passages d'une leçon (appel en arrière-plan). */
    suspend fun preloadPassages(passages: List<String>) {
        engineOrNull()?.preloadAsync(passages)
    }

    /** Précalcule les vecteurs des réponses officielles des cartes de la session (appel en arrière-plan). */
    suspend fun preloadReferences(references: List<String>) {
        engineOrNull()?.preloadReferencesAsync(references)
    }

    /**
     * @param adminReference réponse de référence saisie par l'admin
     * @param question énoncé de la carte (ses mots ne comptent pas comme termes propres de la réponse)
     * @param scope identifiant de la matière (seuil spécifique) ou null pour le seuil global
     */
    suspend fun evaluate(
        adminReference: String,
        studentAnswer: String,
        question: String = "",
        scope: String? = null
    ): EvaluationResult =
        engineOrNull()?.evaluateAsync(adminReference, studentAnswer, settings.thresholdsFor(scope), question) ?: unavailable()
}
