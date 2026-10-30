package ci.kalyno.app.ml.semantic

import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/**
 * Garde-fous logiques appliqués EN PLUS de la similarité cosinus.
 *
 * Les embeddings placent "X est positif" et "X n'est pas positif" très près l'un de l'autre :
 * seule une vérification explicite évite ces faux positifs. Les garde-fous sont volontairement
 * conservateurs : ils peuvent rejeter une reformulation correcte mais ne valident jamais un contresens.
 */
internal object LogicGuard {

    enum class Violation { NEGATION_MISMATCH, ANTONYM_CONFLICT, NUMBER_MISMATCH }

    private val NE = setOf("ne", "n")
    // Mots qui, associés à "ne", forment une négation
    private val NE_PARTNERS = setOf(
        "pas", "plus", "jamais", "rien", "aucun", "aucune", "nul", "nulle",
        "personne", "guere", "point", "nullement", "aucunement", "ni"
    )
    // Mots négatifs qui n'ont pas besoin de "ne" (réponses courtes : "pas de ...", "sans ...")
    private val STANDALONE = setOf("pas", "non", "sans", "aucun", "aucune", "jamais", "rien", "nullement")

    /**
     * Couples d'antonymes (radicaux sans accents). Étendre selon les programmes (BEPC, Tle A/C/D).
     * Un radical de 6 caractères ou plus est comparé par préfixe, sinon par égalité exacte.
     */
    private val ANTONYMS: List<Pair<String, String>> = listOf(
        "positif" to "negatif", "croissan" to "decroissan", "augment" to "diminu",
        "hausse" to "baisse", "acide" to "basique", "vrai" to "faux", "pair" to "impair",
        "exothermique" to "endothermique", "conducteur" to "isolant", "oxydation" to "reduction",
        "minimum" to "maximum", "fini" to "infini", "convexe" to "concave",
        "monte" to "descend", "gagne" to "perd", "interieur" to "exterieur",
        "solide" to "liquide", "legal" to "illegal", "democratie" to "dictature",
        "somme" to "difference", "addition" to "soustraction", "multiplication" to "division",
        "avant" to "apres", "nord" to "sud"
    )

    fun inspect(reference: String, answer: String): Violation? {
        val ref = tokens(reference)
        val ans = tokens(answer)
        if (negationCount(ref) % 2 != negationCount(ans) % 2) return Violation.NEGATION_MISMATCH
        if (hasAntonymConflict(ref, ans)) return Violation.ANTONYM_CONFLICT
        if (hasNumberMismatch(reference, answer)) return Violation.NUMBER_MISMATCH
        return null
    }

    // ---- Négations -------------------------------------------------------------------------

    /** Compte les négations ; la parité du résultat gère la double négation. "ne ... que" n'est pas une négation. */
    private fun negationCount(t: List<String>): Int {
        val consumed = BooleanArray(t.size)
        var count = 0
        for (i in t.indices) {
            if (t[i] !in NE) continue
            for (j in i + 1 until min(t.size, i + 5)) {
                if (!consumed[j] && t[j] in NE_PARTNERS) { consumed[j] = true; count++; break }
            }
        }
        for (i in t.indices) if (!consumed[i] && t[i] in STANDALONE) count++
        return count
    }

    // ---- Antonymes -------------------------------------------------------------------------

    private fun has(t: List<String>, stem: String): Boolean =
        if (stem.length >= 6) t.any { it.startsWith(stem) }
        else t.any { it == stem || it == stem + "s" || it == stem + "e" || it == stem + "es" }

    private fun hasAntonymConflict(ref: List<String>, ans: List<String>): Boolean =
        ANTONYMS.any { (a, b) ->
            (has(ref, a) && !has(ref, b) && has(ans, b) && !has(ans, a)) ||
            (has(ref, b) && !has(ref, a) && has(ans, a) && !has(ans, b))
        }

    // ---- Nombres ---------------------------------------------------------------------------

    private val NUMBER = Regex("\\d+(?:[.,]\\d+)?")

    /** Si l'élève donne des chiffres, tous ceux de la référence doivent y figurer. */
    private fun hasNumberMismatch(reference: String, answer: String): Boolean {
        val r = numbers(reference)
        val a = numbers(answer)
        return r.isNotEmpty() && a.isNotEmpty() && !a.containsAll(r)
    }

    private fun numbers(s: String): Set<String> = NUMBER.findAll(s).map { normNumber(it.value) }.toSet()

    /** "2,50" et "2.5" sont identiques ; "100" reste "100". */
    private fun normNumber(raw: String): String {
        val x = raw.replace(',', '.')
        return if ('.' in x) x.trimEnd('0').trimEnd('.') else x
    }

    // ---- Normalisation ---------------------------------------------------------------------

    private val TOKEN = Regex("[a-z0-9]+")

    private fun tokens(s: String): List<String> {
        val flat = Normalizer.normalize(s.lowercase(Locale.ROOT).replace('\u2019', '\''), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return TOKEN.findAll(flat).map { it.value }.toList()
    }
}
