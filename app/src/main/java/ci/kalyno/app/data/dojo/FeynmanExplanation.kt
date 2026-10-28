package ci.kalyno.app.data.dojo

import java.text.Normalizer

/** Explication selon Feynman : idée en mots simples, analogie du quotidien, essentiel en une phrase. */
data class FeynmanExplanation(
    val steps: List<String>,
    val analogy: String?,
    val takeaway: String?
) {
    val structured: Boolean get() = analogy != null || takeaway != null
}

/**
 * Lit le champ `explanation` de l'admin, sans changer la base. Repères reconnus en début de ligne :
 *   En clair : ...  |  Analogie : ... (ou Image / Imagine)  |  Retiens : ... (ou À retenir)
 * Sans repère, l'explication s'affiche comme avant.
 */
object FeynmanParser {

    private enum class Tag { CLEAR, ANALOGY, TAKEAWAY }

    private val LINE = Regex("^([\\p{L}' ]{2,20})\\s*:\\s*(.+)$")
    private val MARKS = Regex("\\p{Mn}+")
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")

    private val TAGS = mapOf(
        "en clair" to Tag.CLEAR, "en simple" to Tag.CLEAR,
        "analogie" to Tag.ANALOGY, "image" to Tag.ANALOGY, "imagine" to Tag.ANALOGY,
        "retiens" to Tag.TAKEAWAY, "retenir" to Tag.TAKEAWAY, "a retenir" to Tag.TAKEAWAY
    )

    fun parse(raw: String?): FeynmanExplanation {
        if (raw.isNullOrBlank()) return FeynmanExplanation(emptyList(), null, null)

        var analogy: String? = null
        var takeaway: String? = null
        val steps = ArrayList<String>()

        for (line in raw.lines()) {
            val text = line.trim().trimStart('-', '•', '*').trim()
            if (text.isEmpty()) continue
            val match = LINE.matchEntire(text)
            val tag = match?.let { TAGS[normalize(it.groupValues[1])] }
            val body = match?.groupValues?.get(2)?.trim().orEmpty()
            when (tag) {
                Tag.ANALOGY -> analogy = body
                Tag.TAKEAWAY -> takeaway = body
                Tag.CLEAR -> steps += body
                null -> steps += text
            }
        }

        if (steps.size == 1) {
            val sentences = steps[0].split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }
            if (sentences.size > 1) {
                steps.clear()
                steps += sentences
            }
        }
        return FeynmanExplanation(steps, analogy, takeaway)
    }

    private fun normalize(label: String): String =
        Normalizer.normalize(label.trim().lowercase(), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace('\u2019', '\'')
}
