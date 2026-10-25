package ci.kalyno.app.ui.dojo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ci.kalyno.app.data.dojo.AngleMode
import ci.kalyno.app.data.dojo.ExpressionEvaluator
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText

/** État de la calculatrice (porté par le ViewModel du Dojo). */
class CalculatorModel {
    var expression by mutableStateOf("")
        private set
    var angle by mutableStateOf(AngleMode.DEG)
        private set
    var failed by mutableStateOf(false)
        private set
    private var ans = 0.0
    private var justEvaluated = false

    /** Résultat affiché en direct (null tant que l'expression est vide ou incomplète). */
    val liveResult: String?
        get() = if (expression.isBlank()) {
            null
        } else {
            ExpressionEvaluator.evaluateOrNull(expression, angle, ans)?.let { ExpressionEvaluator.format(it) }
        }

    fun insert(token: String) {
        val first = token.first()
        val startsNew = first.isDigit() || first == ',' || first == '(' || first.isLetter() || first == 'π' || first == '√'
        if (justEvaluated && startsNew) expression = ""
        justEvaluated = false
        failed = false
        expression += token
    }

    fun backspace() {
        failed = false
        justEvaluated = false
        val token = FUNCTION_TOKENS.firstOrNull { expression.endsWith(it) }
        expression = if (token != null) expression.dropLast(token.length) else expression.dropLast(1)
    }

    fun clear() {
        expression = ""
        failed = false
        justEvaluated = false
    }

    fun calculate() {
        if (expression.isBlank()) return
        val value = ExpressionEvaluator.evaluateOrNull(expression, angle, ans)
        if (value == null) {
            failed = true
        } else {
            ans = value
            expression = ExpressionEvaluator.format(value)
            justEvaluated = true
            failed = false
        }
    }

    fun toggleAngle() {
        angle = if (angle == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG
    }

    private companion object {
        val FUNCTION_TOKENS = listOf("asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "ln(", "log(", "exp(", "Ans")
    }
}

private enum class KeyStyle { NUMBER, OPERATOR, FUNCTION, DANGER, EQUALS }

private enum class KeyAction { INSERT, BACKSPACE, CLEAR, EQUALS }

private class CalcKey(
    val label: String,
    val style: KeyStyle,
    val action: KeyAction = KeyAction.INSERT,
    val token: String = label
)

private fun num(label: String) = CalcKey(label, KeyStyle.NUMBER)
private fun op(label: String) = CalcKey(label, KeyStyle.OPERATOR)
private fun fn(label: String, token: String = label) = CalcKey(label, KeyStyle.FUNCTION, token = token)

private val KEY_ROWS: List<List<CalcKey>> = listOf(
    listOf(fn("sin", "sin("), fn("cos", "cos("), fn("tan", "tan("), fn("ln", "ln("), fn("log", "log(")),
    listOf(fn("π"), fn("e"), fn("√", "√("), fn("x²", "^2"), fn("xʸ", "^")),
    listOf(fn("("), fn(")"), fn("n!", "!"), fn("1/x", "^(-1)"), CalcKey("⌫", KeyStyle.DANGER, KeyAction.BACKSPACE)),
    listOf(num("7"), num("8"), num("9"), op("÷"), CalcKey("AC", KeyStyle.DANGER, KeyAction.CLEAR)),
    listOf(num("4"), num("5"), num("6"), op("×"), fn("Ans")),
    listOf(num("1"), num("2"), num("3"), op("−"), fn("EXP", "×10^(")),
    listOf(num("0"), num(","), fn("%"), op("+"), CalcKey("=", KeyStyle.EQUALS, KeyAction.EQUALS))
)

/**
 * Calculatrice scientifique en fenêtre : DEG/RAD, fonctions usuelles, résultat en direct.
 * [canUseResult] affiche « Utiliser dans ma réponse » (saisie libre en cours uniquement).
 */
@Composable
fun ScientificCalculatorDialog(
    model: CalculatorModel,
    canUseResult: Boolean,
    onUseResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = rememberHaptic()
    val live = model.liveResult
    val shownResult = when {
        model.failed -> "Erreur"
        live != null -> live
        else -> "0"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🧮 Calculatrice",
                    color = KalynoText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (model.angle == AngleMode.DEG) "DEG" else "RAD",
                    color = KalynoBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(KalynoBlueSoft)
                        .clickable {
                            haptic()
                            model.toggleAngle()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = "✕",
                    color = KalynoMuted,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(KalynoBackground)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = model.expression.ifEmpty { " " },
                    color = KalynoMuted,
                    fontSize = 18.sp,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState(), reverseScrolling = true)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = shownResult,
                    color = if (model.failed) KalynoDanger else KalynoText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState(), reverseScrolling = true)
                )
            }

            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KEY_ROWS.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { key ->
                            CalcKeyButton(
                                key = key,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptic()
                                    when (key.action) {
                                        KeyAction.INSERT -> model.insert(key.token)
                                        KeyAction.BACKSPACE -> model.backspace()
                                        KeyAction.CLEAR -> model.clear()
                                        KeyAction.EQUALS -> model.calculate()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (canUseResult && live != null) {
                Spacer(Modifier.height(12.dp))
                KalynoPrimaryButton(
                    label = "UTILISER DANS MA RÉPONSE",
                    onClick = { onUseResult(live) }
                )
            }
        }
    }
}

@Composable
private fun CalcKeyButton(key: CalcKey, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val (container, content) = when (key.style) {
        KeyStyle.NUMBER -> Color.White to KalynoText
        KeyStyle.OPERATOR -> KalynoBlueSoft to KalynoBlue
        KeyStyle.FUNCTION -> KalynoBackground to KalynoText
        KeyStyle.DANGER -> KalynoDangerSoft to KalynoDanger
        KeyStyle.EQUALS -> KalynoBlue to Color.White
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(container)
            .border(1.dp, if (key.style == KeyStyle.NUMBER) KalynoOutline else container, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key.label,
            color = content,
            fontSize = if (key.label.length > 2) 14.sp else 18.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}
