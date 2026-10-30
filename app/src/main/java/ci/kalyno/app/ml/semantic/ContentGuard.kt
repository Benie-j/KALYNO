package ci.kalyno.app.ml.semantic

import java.text.Normalizer
import java.util.Locale

/**
 * Couverture des termes clés : E5 juge « le sens général », pas un fait précis (« mitochondries » et
 * « chloroplastes » sont presque aussi proches l'un de l'autre que deux reformulations). Une réponse ne peut
 * être validée que si elle reprend l'essentiel des termes PROPRES à la réponse officielle (ceux qui ne figurent
 * pas déjà dans la question), à l'accent, au pluriel et à la terminaison près.
 */
internal object ContentGuard {

    /** Part minimale des termes propres de la référence retrouvés dans la réponse pour pouvoir la valider. */
    const val MIN_COVERAGE = 0.5f

    private val TOKEN = Regex("[a-z0-9]+")
    private val MARKS = Regex("\\p{M}+")
    private val STOP = setOf(
        "le", "la", "les", "un", "une", "des", "du", "de", "d", "l", "au", "aux", "et", "ou", "mais", "donc", "or", "ni",
        "car", "que", "qu", "qui", "quoi", "dont", "ce", "cet", "cette", "ces", "se", "s", "sa", "son", "ses", "leur",
        "leurs", "mon", "ma", "mes", "ton", "ta", "tes", "notre", "nos", "votre", "vos", "il", "elle", "ils", "elles",
        "on", "nous", "vous", "je", "tu", "y", "en", "ne", "n", "pas", "plus", "est", "sont", "etre", "avoir", "fait",
        "font", "vaut", "egal", "egale", "dans", "sur", "sous", "par", "pour", "avec", "sans", "entre", "vers", "chez",
        "comme", "si", "tout", "tous", "toute", "toutes", "tres", "aussi", "alors", "ainsi", "cela", "ca", "c", "cest",
        "ici", "celui", "celle", "ceux", "celles", "quand", "lorsque", "pendant", "depuis", "jusqu", "apres", "avant",
        "afin", "ensuite", "puis", "grace", "dou", "environ", "peut", "permet", "permettent", "appelle", "ont"
    )

    /**
     * @param known texte déjà connu de l'élève (la question) : ses mots ne comptent pas comme « termes propres »
     * @return part (0..1) des termes propres de [reference] retrouvés dans [answer] ; 1 si la référence n'en a aucun
     */
    fun coverage(reference: String, answer: String, known: String = ""): Float {
        val ref = terms(reference)
        if (ref.isEmpty()) return 1f
        val ans = terms(answer)
        val ctx = terms(known)
        val own = ref.filter { t -> ctx.none { same(t, it) } }.ifEmpty { ref }
        return own.count { t -> ans.any { same(t, it) } }.toFloat() / own.size
    }

    internal fun terms(s: String): List<String> {
        val flat = Normalizer.normalize(s.lowercase(Locale.ROOT).replace('\u2019', '\''), Normalizer.Form.NFD)
            .replace(MARKS, "")
        return TOKEN.findAll(flat).map { it.value }
            .filter { it !in STOP && (it.all(Char::isDigit) || it.length >= 4) }
            .toList()
    }

    internal fun same(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.all(Char::isDigit) || b.all(Char::isDigit)) return false // les nombres se comparent exactement
        var p = 0
        while (p < a.length && p < b.length && a[p] == b[p]) p++
        if (p >= 5 && p >= minOf(a.length, b.length) - 2) return true   // même radical : pluriel, terminaison
        return a.length >= 5 && b.length >= 5 && (a.contains(b) || b.contains(a)) // oxygène / dioxygène
    }
}
