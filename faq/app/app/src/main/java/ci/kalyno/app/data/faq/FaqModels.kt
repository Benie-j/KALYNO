package ci.kalyno.app.data.faq

import androidx.compose.runtime.Immutable
import java.text.Normalizer

/** Question/réponse telle que l'interface la lit (aucune dépendance à Room). */
@Immutable
data class FaqItem(
    val id: String,
    val category: String,
    val question: String,
    val answer: String
)

/** Une catégorie et ses questions, dans l'ordre de l'admin. */
@Immutable
data class FaqSection(val category: String, val items: List<FaqItem>)

internal fun FaqEntity.toItem() = FaqItem(id = id, category = category, question = question, answer = answer)

/**
 * Recherche et regroupement, en Kotlin pur (testable sans Android).
 *
 * - Insensible à la casse ET aux accents : « eleve » trouve « élève ».
 * - Plusieurs mots = tous doivent apparaître (dans la question, la réponse ou la catégorie).
 * - [fold] conserve la longueur du texte : les positions trouvées dans le texte normalisé valent
 *   aussi pour le texte d'origine, ce qui permet de surligner les mots cherchés.
 */
object FaqSearch {

    /** Minuscule sans accent, caractère par caractère (donc même longueur que [text]). */
    fun fold(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text) {
            val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
            val base = decomposed.firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() } ?: c
            out.append(base.lowercaseChar())
        }
        return out.toString()
    }

    /** Mots de la recherche, normalisés. Vide si la recherche est vide. */
    fun terms(query: String): List<String> =
        fold(query).split(WHITESPACE).filter { it.isNotEmpty() }.distinct()

    fun matches(item: FaqItem, terms: List<String>): Boolean {
        if (terms.isEmpty()) return true
        val haystack = fold(item.question) + '\n' + fold(item.answer) + '\n' + fold(item.category)
        return terms.all { haystack.contains(it) }
    }

    fun filter(items: List<FaqItem>, query: String): List<FaqItem> {
        val terms = terms(query)
        return if (terms.isEmpty()) items else items.filter { matches(it, terms) }
    }

    /**
     * Regroupe par catégorie. Les catégories suivent l'ordre de leur première question dans [items]
     * (déjà trié par `order_index`) : l'admin pilote l'ordre des groupes avec les numéros d'ordre.
     */
    fun group(items: List<FaqItem>): List<FaqSection> =
        items.groupBy { it.category.trim() }.map { (category, list) -> FaqSection(category, list) }

    /** Plages (bornes incluses) des mots cherchés dans [text], fusionnées et triées : sert au surlignage. */
    fun highlightRanges(text: String, terms: List<String>): List<IntRange> {
        if (terms.isEmpty() || text.isEmpty()) return emptyList()
        val folded = fold(text)
        val raw = ArrayList<IntRange>()
        for (term in terms) {
            var from = 0
            while (true) {
                val at = folded.indexOf(term, from)
                if (at < 0) break
                raw += at until at + term.length
                from = at + term.length
            }
        }
        if (raw.isEmpty()) return emptyList()
        raw.sortBy { it.first }
        val merged = ArrayList<IntRange>()
        var current = raw.first()
        for (r in raw.drop(1)) {
            if (r.first <= current.last + 1) {
                current = current.first..maxOf(current.last, r.last)
            } else {
                merged += current
                current = r
            }
        }
        merged += current
        return merged
    }

    private val WHITESPACE = Regex("\\s+")
}
