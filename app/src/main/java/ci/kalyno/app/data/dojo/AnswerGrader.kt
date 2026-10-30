package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.srs.LearningBehavior
import ci.kalyno.app.data.srs.ReviewRating
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Résultat objectif d'une réponse + note FSRS correspondante. */
data class Verdict(
    val correct: Boolean,
    val rating: ReviewRating,
    /** Réponse incomplète (QCM multiple : aucune erreur, mais il manque des bonnes réponses). */
    val partial: Boolean = false
)

/** Circonstances de la réponse, qui nuancent la note envoyée au moteur FSRS. */
data class AnswerContext(
    val elapsedMs: Long,
    val questionLength: Int,
    val hintUsed: Boolean = false,
    val calculatorUsed: Boolean = false
)

/**
 * Convertit une réponse en note FSRS, sans auto-évaluation :
 * - erreur -> Oublié ; réponse incomplète ou presque juste -> Hésité ;
 * - juste -> Su, ou Trop facile si la réponse est rapide et sans aide ;
 * - indice utilisé -> au mieux Hésité ; calculatrice utilisée -> au mieux Su.
 */
object AnswerGrader {

    private const val FAST_BASE_MS = 2_500L
    private const val FAST_PER_CHAR_MS = 45L
    private const val TOKEN_COVERAGE = 0.75

    private val MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^a-z0-9]+")

    /** Temps en dessous duquel une bonne réponse est jugée « trop facile » (selon la longueur à lire). */
    fun fastThresholdMs(questionLength: Int): Long =
        FAST_BASE_MS + questionLength.toLong() * FAST_PER_CHAR_MS

    internal fun goodRating(ctx: AnswerContext): ReviewRating = when {
        ctx.hintUsed -> ReviewRating.HESITE
        ctx.calculatorUsed -> ReviewRating.SU
        ctx.elapsedMs <= fastThresholdMs(ctx.questionLength) -> ReviewRating.TROP_FACILE
        // Réponse juste mais très lente : souvenir laborieux, donc plus fragile
        LearningBehavior.isSlow(ctx.elapsedMs, ctx.questionLength) -> ReviewRating.HESITE
        else -> ReviewRating.SU
    }

    fun gradeChoice(selected: Set<Int>, correct: Set<Int>, ctx: AnswerContext): Verdict {
        if (correct.isEmpty() || selected.isEmpty()) return Verdict(false, ReviewRating.OUBLIE)
        if (selected == correct) return Verdict(true, goodRating(ctx))
        val wrong = selected - correct
        if (wrong.isEmpty()) return Verdict(false, ReviewRating.HESITE, partial = true)
        return Verdict(false, ReviewRating.OUBLIE)
    }

    /** Mots-clés acceptés : ceux de l'admin, séparés par « | » ; un seul suffit pour valider. */
    fun acceptedAnswers(raw: String): List<String> =
        raw.split('|').map { it.trim() }.filter { it.isNotEmpty() }

    fun gradeText(typed: String, accepted: List<String>, ctx: AnswerContext): Verdict {
        val typedCompact = compact(typed)
        if (typedCompact.isEmpty() || accepted.isEmpty()) return Verdict(false, ReviewRating.OUBLIE)

        var best = Match.NONE
        for (answer in accepted) {
            val m = match(typed, typedCompact, answer)
            if (m.ordinal > best.ordinal) best = m
            if (best == Match.EXACT) break
        }
        return when (best) {
            Match.EXACT -> Verdict(true, goodRating(ctx))
            Match.CLOSE -> Verdict(true, ReviewRating.HESITE)
            Match.NONE -> Verdict(false, ReviewRating.OUBLIE)
        }
    }

    private enum class Match { NONE, CLOSE, EXACT }

    private fun match(typedRaw: String, typed: String, answerRaw: String): Match {
        val answer = compact(answerRaw)
        if (answer.isEmpty()) return Match.NONE
        if (typed == answer) return Match.EXACT

        // « x = 3 » accepte aussi « 3 »
        val answerSide = answer.substringAfterLast('=')
        val typedSide = typed.substringAfterLast('=')
        if (answerSide.isNotEmpty() && answerSide == typedSide) return Match.EXACT

        // Réponses chiffrées : égalité numérique, jamais de tolérance sur les chiffres
        val answerNumber = numberOf(answerSide)
        if (answerNumber != null || answerSide.any { it.isDigit() }) {
            val typedNumber = numberOf(typedSide)
            val same = answerNumber != null && typedNumber != null &&
                abs(answerNumber - typedNumber) <= 1e-9 * max(1.0, abs(answerNumber))
            if (same) return Match.EXACT
            // « la réponse est 3 » : un seul nombre dans la phrase, et c'est le bon
            return if (containsKeyword(typedRaw, answerRaw, answerNumber)) Match.CLOSE else Match.NONE
        }

        // Petite faute de frappe tolérée selon la longueur
        val allowed = when {
            answerSide.length >= 9 -> 2
            answerSide.length >= 5 -> 1
            else -> 0
        }
        if (allowed > 0 && editDistance(typedSide, answerSide) <= allowed) return Match.CLOSE

        // Réponse longue : les mots importants doivent y être (au moins 75 %)
        val answerTokens = tokens(answerRaw)
        if (answerTokens.size >= 4) {
            val typedTokens = tokens(typedRaw)
            val covered = answerTokens.count { a ->
                typedTokens.any { t -> t == a || (a.length >= 5 && editDistance(t, a) <= 1) }
            }
            if (covered.toDouble() / answerTokens.size >= TOKEN_COVERAGE) return Match.CLOSE
        }

        // Mot-clé noyé dans une phrase : « c'est la photosynthèse » valide « photosynthèse »
        if (containsKeyword(typedRaw, answerRaw, null)) return Match.CLOSE
        return Match.NONE
    }

    private val WORD = Regex("[a-z0-9]+(?:\\.[0-9]+)?")
    private val NUMBER = Regex("-?\\d+(?:\\.\\d+)?")
    private val NEGATIONS = setOf("pas", "non", "jamais", "aucun", "aucune", "ni")

    private fun normalized(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace('\u2019', '\'')
            .replace('\u2212', '-')
            .replace('\u2013', '-')
            .replace(Regex("(?<=\\d),(?=\\d)"), ".")

    private fun words(value: String): List<String> =
        WORD.findAll(normalized(value)).map { it.value }.toList()

    /**
     * Le mot-clé de l'admin apparaît comme mot entier dans la phrase de l'élève.
     * Refusé si la phrase contient une négation (« ce n'est pas ... ») ou si le mot-clé est trop court.
     */
    private fun containsKeyword(typedRaw: String, keywordRaw: String, keywordNumber: Double?): Boolean {
        val typedWords = words(typedRaw)
        if (typedWords.size < 2) return false
        if (typedWords.any { it in NEGATIONS }) return false

        if (keywordNumber != null) {
            val numbers = NUMBER.findAll(normalized(typedRaw)).mapNotNull { it.value.toDoubleOrNull() }.toList()
            return numbers.size == 1 && abs(numbers[0] - keywordNumber) <= 1e-9 * max(1.0, abs(keywordNumber))
        }

        val keywordWords = words(keywordRaw)
        if (keywordWords.isEmpty() || keywordWords.joinToString("").length < 3) return false
        val haystack = " " + typedWords.joinToString(" ") + " "
        if (haystack.contains(" " + keywordWords.joinToString(" ") + " ")) return true
        if (keywordWords.size == 1 && keywordWords[0].length >= 6) {
            return typedWords.any { editDistance(it, keywordWords[0]) <= 1 }
        }
        return false
    }

    /** Minuscules, sans accents ni espaces, virgule décimale -> point. */
    internal fun compact(value: String): String {
        val base = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace('’', '\'')
            .replace('−', '-')
            .replace('–', '-')
            .replace(',', '.')
        return base.filter { !it.isWhitespace() }.trimEnd('.', '!', '?', ';', ':')
    }

    private fun tokens(value: String): List<String> =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .split(NON_WORD)
            .filter { it.length >= 3 }
            .distinct()

    private fun numberOf(value: String): Double? =
        value.toDoubleOrNull()?.takeIf { it.isFinite() }

    internal fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
