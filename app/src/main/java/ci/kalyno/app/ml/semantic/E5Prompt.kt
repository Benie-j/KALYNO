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

    fun query(text: String): String = QUERY + text.trim()

    fun passage(text: String): String = PASSAGE + text.trim()

    /**
     * Passage de référence enrichi par la leçon :
     * `passage: Leçon : <titre> - <résumé>. Réponse officielle : <réponse admin>`
     * Titre ou résumé absents (catalogue pas encore synchronisé) : ils sont simplement omis.
     */
    fun lessonPassage(lessonTitle: String?, lessonSummary: String?, adminReference: String): String {
        val title = lessonTitle?.trim().orEmpty()
        val summary = lessonSummary?.trim()?.trimEnd('.', ' ').orEmpty()
        val context = when {
            title.isNotEmpty() && summary.isNotEmpty() -> "Leçon : $title - $summary. "
            title.isNotEmpty() -> "Leçon : $title. "
            else -> ""
        }
        return passage(context + "Réponse officielle : " + adminReference.trim())
    }
}
