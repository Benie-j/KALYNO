package ci.kalyno.app.ml.semantic

/**
 * Seuils de décision. [valid] : réponse validée ; [partial] : "presque".
 * Les valeurs venues du réseau sont bornées : un mauvais réglage admin ne peut pas rendre le moteur laxiste.
 *
 * multilingual-e5-small a été entraîné avec une température de 0,01 : ses similarités cosinus se
 * concentrent entre 0,7 et 1,0 (fiche du modèle). Seul l'ordre relatif compte, mais nos seuils absolus
 * doivent donc être nettement plus hauts que ceux de l'ancien MiniLM. Valeurs PROVISOIRES : à calibrer
 * sur de vraies réponses d'élèves (le score de chaque correction est écrit dans Logcat, tag SemanticEngine).
 */
data class SemanticThresholds(val valid: Float, val partial: Float) {
    /**
     * Seuils pour la correction par la leçon : une réponse courte ressemble moins à un passage
     * de cours qu'à une phrase de référence écrite exprès. Valeur provisoire, à calibrer sur de vraies cartes.
     */
    fun relaxedForLesson(by: Float = LESSON_RELAXATION): SemanticThresholds {
        val v = (valid - by).coerceAtLeast(LESSON_MIN_VALID)
        val p = (partial - by).coerceIn(LESSON_MIN_PARTIAL, v - 0.05f)
        return SemanticThresholds(v, p)
    }

    companion object {
        const val LESSON_RELAXATION = 0.08f
        private const val LESSON_MIN_VALID = 0.78f
        private const val LESSON_MIN_PARTIAL = 0.72f

        val DEFAULT = SemanticThresholds(valid = 0.86f, partial = 0.80f)
        // Plancher E5 : un ancien réglage admin calé sur MiniLM (ex. 0,75) validerait presque tout.
        const val MIN_VALID = 0.80f
        const val MAX_VALID = 0.95f
        private const val MIN_PARTIAL = 0.70f

        /** Normalise des valeurs brutes ; retourne [DEFAULT] si elles sont absentes ou incohérentes. */
        fun sanitize(valid: Float?, partial: Float?): SemanticThresholds {
            if (valid == null || valid.isNaN()) return DEFAULT
            val v = valid.coerceIn(MIN_VALID, MAX_VALID)
            val p = (partial?.takeIf { !it.isNaN() } ?: (v - 0.06f)).coerceIn(MIN_PARTIAL, v - 0.05f)
            return SemanticThresholds(v, p)
        }
    }
}
