package ci.kalyno.app.ml.semantic

/**
 * Préfixes imposés par multilingual-e5-small (fiche du modèle, intfloat/multilingual-e5-small) :
 * chaque texte DOIT commencer par « query: » ou « passage: », sans quoi la qualité des embeddings baisse.
 *
 *  - [query]   : saisie de l'élève (et question posée lors de la recherche d'un passage de leçon) ;
 *  - [passage] : texte de référence (leçon + réponse officielle).
 *
 * Pur Kotlin : testable sans Android ni ONNX.
 */
object E5Prompt {
    const val QUERY = "query: "
    const val PASSAGE = "passage: "

    /**
     * Plafonds de longueur. Le tokenizer coupe le texte à 1200 caractères : la réponse officielle étant
     * placée EN FIN de passage, c'est elle qui serait amputée si le titre, le résumé ou la référence
     * étaient trop longs. Budget : 9 (préfixe) + 8 + 120 (titre) + 3 + 240 (résumé) + 2 + 21 + 700 < 1200.
     */
    const val MAX_TITLE_CHARS = 120
    const val MAX_SUMMARY_CHARS = 240
    const val MAX_REFERENCE_CHARS = 700

    /** Vrai si le texte porte l'un des deux préfixes imposés par la fiche E5. */
    fun hasPrefix(text: String): Boolean = text.startsWith(QUERY) || text.startsWith(PASSAGE)

    fun query(text: String): String = QUERY + text.trim()

    fun passage(text: String): String = PASSAGE + text.trim()

    /**
     * Passage de référence enrichi par la leçon :
     * `passage: Leçon : <titre> - <résumé>. Réponse officielle : <réponse admin>`
     * Titre ou résumé absents (catalogue pas encore synchronisé) : ils sont simplement omis.
     */
    fun lessonPassage(lessonTitle: String?, lessonSummary: String?, adminReference: String): String {
        val title = lessonTitle?.trim().orEmpty().take(MAX_TITLE_CHARS)
        val summary = lessonSummary?.trim().orEmpty().take(MAX_SUMMARY_CHARS).trimEnd('.', ' ')
        val context = when {
            title.isNotEmpty() && summary.isNotEmpty() -> "Leçon : $title - $summary. "
            title.isNotEmpty() -> "Leçon : $title. "
            else -> ""
        }
        return passage(context + "Réponse officielle : " + adminReference.trim().take(MAX_REFERENCE_CHARS))
    }
}
