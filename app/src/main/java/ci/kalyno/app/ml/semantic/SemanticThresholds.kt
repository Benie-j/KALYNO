package ci.kalyno.app.ml.semantic

/**
 * Seuils de décision. [valid] : réponse validée ; [partial] : « presque ».
 *
 * Fiche de multilingual-e5-small : entraîné avec une température de 0,01, ses similarités cosinus se
 * concentrent entre 0,7 et 1,0 et « seul l'ordre relatif compte ». Mesures sur le modèle embarqué (réponses
 * en français) : reformulations justes 0,91-0,99 ; réponses fausses mais sur le sujet 0,82-0,98 ; hors sujet <= 0,75.
 * Le score seul ne peut donc pas certifier un fait : [valid] est placé tout en haut de la plage, et la
 * validation exige en plus les garde-fous de [LogicGuard] et la couverture des termes clés ([ContentGuard]).
 * Valeurs PROVISOIRES : à recalibrer sur de vraies réponses d'élèves (tools/calibrate_e5.py).
 */
data class SemanticThresholds(val valid: Float, val partial: Float) {
    companion object {
        val DEFAULT = SemanticThresholds(valid = 0.95f, partial = 0.88f)

        // Planchers : un réglage admin ne peut pas rendre le moteur plus laxiste que les mesures le permettent.
        const val MIN_VALID = 0.92f
        const val MAX_VALID = 0.99f
        const val MIN_PARTIAL = 0.84f
        const val MIN_GAP = 0.04f

        /** Normalise des valeurs brutes ; retourne [DEFAULT] si elles sont absentes ou incohérentes. */
        fun sanitize(valid: Float?, partial: Float?): SemanticThresholds {
            if (valid == null || valid.isNaN()) return DEFAULT
            val v = valid.coerceIn(MIN_VALID, MAX_VALID)
            val p = (partial?.takeIf { !it.isNaN() } ?: (v - 0.07f)).coerceIn(MIN_PARTIAL, v - MIN_GAP)
            return SemanticThresholds(v, p)
        }
    }
}
