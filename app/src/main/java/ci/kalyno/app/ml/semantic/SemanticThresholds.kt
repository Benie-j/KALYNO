package ci.kalyno.app.ml.semantic

/**
 * Seuils de décision. [valid] : réponse validée ; [partial] : "presque".
 * Les valeurs venues du réseau sont bornées : un mauvais réglage admin ne peut pas rendre le moteur laxiste.
 */
data class SemanticThresholds(val valid: Float, val partial: Float) {
    companion object {
        val DEFAULT = SemanticThresholds(valid = 0.75f, partial = 0.60f)
        const val MIN_VALID = 0.60f
        const val MAX_VALID = 0.95f

        /** Normalise des valeurs brutes ; retourne [DEFAULT] si elles sont absentes ou incohérentes. */
        fun sanitize(valid: Float?, partial: Float?): SemanticThresholds {
            if (valid == null || valid.isNaN()) return DEFAULT
            val v = valid.coerceIn(MIN_VALID, MAX_VALID)
            val p = (partial?.takeIf { !it.isNaN() } ?: (v - 0.15f)).coerceIn(0.30f, v - 0.05f)
            return SemanticThresholds(v, p)
        }
    }
}
