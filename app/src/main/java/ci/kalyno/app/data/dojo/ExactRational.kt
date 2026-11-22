package ci.kalyno.app.data.dojo

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Nombre rationnel exact (BigInteger), toujours réduit, dénominateur strictement positif.
 * Deux rationnels de même valeur sont donc `==` : aucune comparaison approximative en Double.
 */
class ExactRational private constructor(val numerator: BigInteger, val denominator: BigInteger) {

    companion object {
        /** Null si [denominator] est nul (valeur indéfinie). */
        fun of(numerator: BigInteger, denominator: BigInteger): ExactRational? {
            if (denominator.signum() == 0) return null
            if (numerator.signum() == 0) return ExactRational(BigInteger.ZERO, BigInteger.ONE)
            val g = numerator.gcd(denominator)
            var n = numerator.divide(g)
            var d = denominator.divide(g)
            if (d.signum() < 0) {
                n = n.negate()
                d = d.negate()
            }
            return ExactRational(n, d)
        }

        /** Décimal exact : « 0,5 » → 1/2. [text] doit être des chiffres avec une virgule ou un point facultatif. */
        fun fromDecimal(text: String): ExactRational? {
            val value = try {
                BigDecimal(text.replace(',', '.'))
            } catch (e: NumberFormatException) {
                return null
            }
            return if (value.scale() > 0) {
                of(value.unscaledValue(), BigInteger.TEN.pow(value.scale()))
            } else {
                of(value.toBigIntegerExact(), BigInteger.ONE)
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ExactRational && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String = "$numerator/$denominator"

    operator fun unaryMinus(): ExactRational = of(numerator.negate(), denominator)!!

    operator fun div(other: ExactRational): ExactRational? =
        of(numerator.multiply(other.denominator), denominator.multiply(other.numerator))
}

/**
 * Lecture stricte des réponses numériques simples, pour comparer des valeurs et non des chaînes.
 *
 * Formes reconnues (la chaîne entière, rien d'autre) : entier, décimal (« 0,5 » ou « 0.5 »), « a/b »,
 * `\frac{a}{b}`, `\dfrac{a}{b}`, `\tfrac{a}{b}`, avec un signe devant, au numérateur ou au dénominateur,
 * éventuellement entourées de `$…$` ou `\(…\)`. Tout le reste (texte, unités, nombre mixte « 1 2/3 »,
 * racines, puissances, dénominateur nul) donne null : l'appelant garde alors la comparaison textuelle.
 */
object MathAnswer {

    private const val NUM = "\\d+(?:[.,]\\d+)?"
    private const val SIGN = "([+-])?"

    private val PLAIN = Regex("^$SIGN ?($NUM)$")
    private val SLASH = Regex("^$SIGN ?($NUM) ?/ ?$SIGN ?($NUM)$")
    private val LATEX_FRAC = Regex("^$SIGN ?\\\\[dt]?frac ?\\{ ?$SIGN ?($NUM) ?\\} ?\\{ ?$SIGN ?($NUM) ?\\}$")
    private val SPACES = Regex("[\\s\\u00A0\\u202F\\u2009]+")

    private fun clean(raw: String): String {
        var s = raw
            .replace('\u2212', '-').replace('\u2013', '-').replace('\u2014', '-')
            .replace(SPACES, " ")
            .trim()
        if (s.startsWith("$$") && s.endsWith("$$") && s.length >= 4) s = s.substring(2, s.length - 2)
        else if (s.startsWith("$") && s.endsWith("$") && s.length >= 2) s = s.substring(1, s.length - 1)
        else if (s.startsWith("\\(") && s.endsWith("\\)") && s.length >= 4) s = s.substring(2, s.length - 2)
        return s.trim()
    }

    private fun negative(vararg signs: String): Boolean = signs.count { it == "-" } % 2 == 1

    /** Valeur exacte de [raw], ou null si ce n'est pas une des formes reconnues (ou si le dénominateur est nul). */
    fun parse(raw: String): ExactRational? {
        val s = clean(raw)
        if (s.isEmpty()) return null

        PLAIN.matchEntire(s)?.let { m ->
            val v = ExactRational.fromDecimal(m.groupValues[2]) ?: return null
            return if (negative(m.groupValues[1])) -v else v
        }
        SLASH.matchEntire(s)?.let { m ->
            val n = ExactRational.fromDecimal(m.groupValues[2]) ?: return null
            val d = ExactRational.fromDecimal(m.groupValues[4]) ?: return null
            val q = n.div(d) ?: return null
            return if (negative(m.groupValues[1], m.groupValues[3])) -q else q
        }
        LATEX_FRAC.matchEntire(s)?.let { m ->
            val n = ExactRational.fromDecimal(m.groupValues[3]) ?: return null
            val d = ExactRational.fromDecimal(m.groupValues[5]) ?: return null
            val q = n.div(d) ?: return null
            return if (negative(m.groupValues[1], m.groupValues[2], m.groupValues[4])) -q else q
        }
        return null
    }

    /** Vrai seulement si les deux textes sont des valeurs reconnues ET exactement égales. */
    fun equivalent(expected: String, typed: String): Boolean {
        val a = parse(expected) ?: return false
        val b = parse(typed) ?: return false
        return a == b
    }
}
