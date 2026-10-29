package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.LearningChapter

/**
 * Découpe le texte d'une leçon en passages courts (1 à 2 phrases) que le moteur sémantique
 * peut comparer à la réponse de l'élève. Lecture seule : les blocs de leçon ne sont jamais modifiés.
 * Pur Kotlin : testable sans Android.
 */
object LessonPassages {

    /** Longueur maximale d'un passage : au-delà, le vecteur dilue le sens (le modèle lit 128 tokens). */
    const val MAX_PASSAGE_CHARS = 280

    /** Un fragment plus court n'est qu'un titre ou une étiquette : inutilisable comme référence. */
    const val MIN_PASSAGE_CHARS = 25

    /** Borne le temps de calcul des vecteurs sur un téléphone d'entrée de gamme. */
    const val MAX_PER_LESSON = 48

    fun fromChapter(chapter: LearningChapter): List<String> =
        chapter.capsules
            .flatMap { split(it.body) }
            .distinct()
            .take(MAX_PER_LESSON)

    fun split(raw: String): List<String> {
        val text = clean(raw)
        if (text.isBlank()) return emptyList()
        val out = ArrayList<String>()
        for (paragraph in text.split(Regex("\\n\\s*\\n|\\n"))) {
            val p = paragraph.trim()
            if (p.length < MIN_PASSAGE_CHARS) continue
            if (p.length <= MAX_PASSAGE_CHARS) {
                out += p
                continue
            }
            // Paragraphe long : on regroupe les phrases jusqu'à la longueur maximale
            val current = StringBuilder()
            for (sentence in p.split(Regex("(?<=[.!?;:])\\s+"))) {
                val s = sentence.trim()
                if (s.isEmpty()) continue
                if (current.isNotEmpty() && current.length + 1 + s.length > MAX_PASSAGE_CHARS) {
                    flush(current, out)
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(s.take(MAX_PASSAGE_CHARS))
            }
            flush(current, out)
        }
        return out
    }

    private fun flush(current: StringBuilder, out: MutableList<String>) {
        val value = current.toString().trim()
        if (value.length >= MIN_PASSAGE_CHARS) out += value
        current.setLength(0)
    }

    private val BREAKS = Regex("(?i)<br\\s*/?>|</p>|</li>|</div>|</h[1-6]>")
    private val TAGS = Regex("<[^>]+>")
    private val DISPLAY_MATH = Regex("\\$\\$(.+?)\\$\\$", RegexOption.DOT_MATCHES_ALL)
    private val INLINE_MATH = Regex("\\$(.+?)\\$")
    private val TEX_COMMAND = Regex("\\\\[a-zA-Z]+")
    private val TEX_SYMBOLS = Regex("[{}^_]")
    private val SPACES = Regex("[ \\t\\u00A0]+")

    /** HTML simple et LaTeX -> texte brut lisible par le modèle (les chiffres sont conservés). */
    internal fun clean(raw: String): String {
        var t = raw.replace(BREAKS, "\n")
        t = t.replace(TAGS, "")
        t = t.replace(DISPLAY_MATH) { math(it.groupValues[1]) }
        t = t.replace(INLINE_MATH) { math(it.groupValues[1]) }
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        t = t.replace("**", "").replace("__", "")
        return t.lines().joinToString("\n") { it.replace(SPACES, " ").trim() }.trim()
    }

    private fun math(inner: String): String =
        " " + inner.replace(TEX_COMMAND, " ").replace(TEX_SYMBOLS, "").trim() + " "
}
