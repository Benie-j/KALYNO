package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.dashboard.SrsConfig
import ci.kalyno.app.data.srs.ReviewRating
import ci.kalyno.app.ml.semantic.EvaluationResult
import ci.kalyno.app.ml.semantic.EvaluationVerdict

/**
 * Pont entre le Dojo et le moteur sémantique. Pur Kotlin : testable sans Android ni ONNX.
 */
object DojoGrading {

    /**
     * Le moteur d'embedding est fait pour des phrases. Une référence courte (« 3 », « mitochondrie »)
     * reste corrigée par les mots-clés : exacte sur les nombres, tolérante aux fautes de frappe.
     */
    fun useSemantic(reference: String): Boolean {
        val ref = reference.trim()
        if (ref.isEmpty() || '|' in ref) return false
        return ref.split(Regex("\\s+")).size >= SrsConfig.DOJO_SEMANTIC_MIN_WORDS
    }

    /**
     * Le rattrapage par la leçon n'a de sens que pour une réponse rédigée : un nombre ou un mot isolé
     * reste jugé par les mots-clés (exacts sur les nombres), jamais par la similarité.
     */
    fun canRescueWithLesson(reference: String): Boolean {
        val ref = reference.trim()
        return ref.count { it.isLetter() } >= 3 && ref.replace(',', '.').toDoubleOrNull() == null
    }

    /**
     * Reformulation validée par le texte de la leçon alors que les mots-clés l'ont refusée.
     * Retourne null pour garder la correction par mots-clés (moteur en panne, hors sujet, contresens).
     * Une réponse rattrapée vaut au mieux « Hésité » : la carte revient plus tôt et le niveau Dojo ne monte pas.
     */
    fun fromLesson(result: EvaluationResult): Verdict? = when (result.verdict) {
        EvaluationVerdict.CORRECT -> Verdict(correct = true, rating = ReviewRating.HESITE)
        EvaluationVerdict.PARTIAL -> Verdict(correct = false, rating = ReviewRating.HESITE, partial = true)
        else -> null
    }

    /**
     * Convertit le verdict sémantique en note FSRS.
     * Retourne null si le moteur est en panne : l'appelant se replie alors sur les mots-clés,
     * l'élève n'est jamais bloqué ni noté injustement.
     */
    fun fromSemantic(result: EvaluationResult, ctx: AnswerContext): Verdict? = when (result.verdict) {
        EvaluationVerdict.ENGINE_ERROR -> null
        EvaluationVerdict.CORRECT -> {
            // Un rappel libre validé par similarité reste au mieux « Su » : « Trop facile » est réservé au QCM/mot-clé exact
            val rating = AnswerGrader.goodRating(ctx).let { if (it == ReviewRating.TROP_FACILE) ReviewRating.SU else it }
            Verdict(correct = true, rating = rating)
        }
        EvaluationVerdict.PARTIAL -> Verdict(correct = false, rating = ReviewRating.HESITE, partial = true)
        EvaluationVerdict.INCORRECT,
        EvaluationVerdict.CONTRADICTION,
        EvaluationVerdict.EMPTY_INPUT -> Verdict(correct = false, rating = ReviewRating.OUBLIE)
    }
}
