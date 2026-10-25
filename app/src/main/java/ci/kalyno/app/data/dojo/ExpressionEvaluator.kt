package ci.kalyno.app.data.dojo

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class CalcException(message: String) : Exception(message)

enum class AngleMode { DEG, RAD }

/**
 * Évaluateur d'expressions de la calculatrice scientifique (pur Kotlin, testable sur la JVM).
 * Gère + − × ÷ ^ ! % ( ), √, π, e, Ans, sin cos tan asin acos atan ln log exp abs,
 * la multiplication implicite (2π, 3(4+1)) et les parenthèses laissées ouvertes.
 */
object ExpressionEvaluator {

    fun evaluate(expression: String, angle: AngleMode = AngleMode.DEG, ans: Double = 0.0): Double {
        val value = Parser(expression, angle, ans).parse()
        if (value.isNaN() || value.isInfinite()) throw CalcException("Résultat indéfini")
        return value
    }

    fun evaluateOrNull(expression: String, angle: AngleMode = AngleMode.DEG, ans: Double = 0.0): Double? =
        try {
            evaluate(expression, angle, ans)
        } catch (e: CalcException) {
            null
        }

    /** 12 chiffres significatifs, virgule décimale, notation scientifique pour les extrêmes. */
    fun format(value: Double): String {
        if (value == 0.0) return "0"
        val rounded = BigDecimal(value).round(MathContext(12)).stripTrailingZeros()
        val magnitude = abs(value)
        val text = if (magnitude >= 1e12 || magnitude < 1e-6) {
            rounded.toString().replace("E+", "E")
        } else {
            rounded.toPlainString()
        }
        return text.replace('.', ',')
    }

    private class Parser(source: String, private val angle: AngleMode, private val ans: Double) {

        private val text = source.filter { !it.isWhitespace() }
        private var pos = 0

        fun parse(): Double {
            if (text.isEmpty()) throw CalcException("Expression vide")
            val value = expression()
            if (pos < text.length) throw CalcException("Syntaxe incorrecte")
            return value
        }

        private fun peek(): Char? = if (pos < text.length) text[pos] else null

        private fun expression(): Double {
            var value = term()
            var running = true
            while (running) {
                val c = peek()
                if (c == '+') {
                    pos++
                    value += term()
                } else if (c == '-' || c == '−') {
                    pos++
                    value -= term()
                } else {
                    running = false
                }
            }
            return value
        }

        private fun term(): Double {
            var value = unary()
            var running = true
            while (running) {
                val c = peek()
                if (c == '*' || c == '×') {
                    pos++
                    value *= unary()
                } else if (c == '/' || c == '÷') {
                    pos++
                    val divisor = unary()
                    if (divisor == 0.0) throw CalcException("Division par zéro")
                    value /= divisor
                } else if (startsOperand(c)) {
                    value *= unary()
                } else {
                    running = false
                }
            }
            return value
        }

        private fun startsOperand(c: Char?): Boolean =
            c != null && (c.isDigit() || c == '.' || c == ',' || c == '(' || c == '√' || c == 'π' || c.isLetter())

        private fun unary(): Double {
            val c = peek()
            if (c == '-' || c == '−') {
                pos++
                return -unary()
            }
            if (c == '+') {
                pos++
                return unary()
            }
            return power()
        }

        private fun power(): Double {
            val base = postfix()
            if (peek() == '^') {
                pos++
                return base.pow(unary())
            }
            return base
        }

        private fun postfix(): Double {
            var value = primary()
            var running = true
            while (running) {
                val c = peek()
                if (c == '!') {
                    pos++
                    value = factorial(value)
                } else if (c == '%') {
                    pos++
                    value /= 100.0
                } else {
                    running = false
                }
            }
            return value
        }

        private fun primary(): Double {
            val c = peek() ?: throw CalcException("Expression incomplète")
            if (c.isDigit() || c == '.' || c == ',') return number()
            if (c == '(') {
                pos++
                val inner = expression()
                if (peek() == ')') pos++
                return inner
            }
            if (c == 'π') {
                pos++
                return PI
            }
            if (c == '√') {
                pos++
                val operand = primary()
                if (operand < 0) throw CalcException("Racine d'un négatif")
                return sqrt(operand)
            }
            if (c.isLetter()) return identifier()
            throw CalcException("Syntaxe incorrecte")
        }

        private fun number(): Double {
            val start = pos
            var separators = 0
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.' || text[pos] == ',')) {
                if (text[pos] == '.' || text[pos] == ',') separators++
                pos++
            }
            if (separators > 1) throw CalcException("Nombre incorrect")
            return text.substring(start, pos).replace(',', '.').toDoubleOrNull()
                ?: throw CalcException("Nombre incorrect")
        }

        private fun identifier(): Double {
            val start = pos
            while (pos < text.length && text[pos].isLetter()) pos++
            val name = text.substring(start, pos).lowercase()
            if (name == "e") return Math.E
            if (name == "pi") return PI
            if (name == "ans") return ans

            val arg = primary()
            return when (name) {
                "sin" -> snap(sin(toRadians(arg)))
                "cos" -> snap(cos(toRadians(arg)))
                "tan" -> {
                    val c = cos(toRadians(arg))
                    if (abs(c) < 1e-12) throw CalcException("Tangente indéfinie")
                    snap(sin(toRadians(arg)) / c)
                }
                "asin" -> fromRadians(asin(arg))
                "acos" -> fromRadians(acos(arg))
                "atan" -> fromRadians(atan(arg))
                "ln" -> {
                    if (arg <= 0.0) throw CalcException("Logarithme indéfini")
                    ln(arg)
                }
                "log" -> {
                    if (arg <= 0.0) throw CalcException("Logarithme indéfini")
                    log10(arg)
                }
                "exp" -> exp(arg)
                "sqrt" -> {
                    if (arg < 0.0) throw CalcException("Racine d'un négatif")
                    sqrt(arg)
                }
                "abs" -> abs(arg)
                else -> throw CalcException("Fonction inconnue : $name")
            }
        }

        private fun toRadians(x: Double): Double = if (angle == AngleMode.DEG) Math.toRadians(x) else x

        private fun fromRadians(x: Double): Double = if (angle == AngleMode.DEG) Math.toDegrees(x) else x

        /** sin(180°) vaut 0, pas 1,2e-16. */
        private fun snap(x: Double): Double = if (abs(x) < 1e-14) 0.0 else x

        private fun factorial(x: Double): Double {
            if (x < 0.0 || x != floor(x) || x > 170.0) throw CalcException("Factorielle impossible")
            var result = 1.0
            for (i in 2..x.toInt()) result *= i
            return result
        }
    }
}
