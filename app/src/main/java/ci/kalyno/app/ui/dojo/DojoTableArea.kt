package ci.kalyno.app.ui.dojo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import ci.kalyno.app.data.dojo.CellResult
import ci.kalyno.app.data.dojo.TableAttempt
import ci.kalyno.app.data.dojo.TableCell
import ci.kalyno.app.data.dojo.TableExercise
import ci.kalyno.app.data.dojo.TableGrade
import ci.kalyno.app.ui.explorer.RichText
import ci.kalyno.app.ui.quiz.QuizColors

private val MIN_COLUMN = 88.dp
private val MAX_COLUMN = 200.dp
private val MIN_INPUT_COLUMN = 128.dp
private val ROW_MIN_HEIGHT = 52.dp

/**
 * Tableau à compléter, en Compose natif (aucune WebView).
 * - cellules fixes : texte en lecture seule, jamais focalisables ;
 * - cellules à compléter : un champ par cellule, la saisie est rangée sous la clé de la cellule ([onCellChange]) ;
 * - colonnes alignées (largeur commune à toute la colonne), défilement horizontal si le tableau dépasse l'écran ;
 * - après la validation ([grade] non nul) chaque cellule prend la couleur de son résultat et [expected] donne la bonne réponse
 *   des cellules fausses ou vides. [expected] reste vide avant la validation : les réponses n'entrent pas dans l'écran.
 */
@Composable
internal fun DojoTableArea(
    table: TableExercise,
    typed: Map<String, String>,
    grade: TableGrade?,
    expected: Map<String, String>,
    onCellChange: (String, String) -> Unit
) {
    val locked = grade != null
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    val base = remember(table) { columnWidths(table) }
    val lastKey = table.blankKeys.lastOrNull()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val total = base.fold(0.dp) { acc, w -> acc + w }
        // Tableau plus étroit que l'écran : colonnes étirées ; plus large : défilement horizontal
        val factor = if (total > 0.dp && total < maxWidth) maxWidth / total else 1f
        val widths = base.map { it * factor }

        Box(modifier = Modifier.horizontalScroll(scroll)) {
            Column {
                table.rows.forEach { row ->
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        for (col in 0 until table.columnCount) {
                            val cell = row.getOrNull(col) ?: TableCell.Fixed("")
                            val cellModifier = Modifier
                                .width(widths[col])
                                .fillMaxHeight()
                                .heightIn(min = ROW_MIN_HEIGHT)
                            when (cell) {
                                is TableCell.Fixed -> FixedCell(cell.text, cellModifier)
                                is TableCell.Blank -> BlankCell(
                                    value = typed[cell.key].orEmpty(),
                                    result = grade?.cells?.get(cell.key),
                                    expected = expected[cell.key],
                                    locked = locked,
                                    description = "${TableAttempt.positionLabel(cell.key)}, cellule à compléter",
                                    isLast = cell.key == lastKey,
                                    modifier = cellModifier,
                                    onChange = { onCellChange(cell.key, it) },
                                    onNext = { focus.moveFocus(FocusDirection.Next) },
                                    onDone = { focus.clearFocus() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Largeur de chaque colonne selon son contenu le plus long ; une colonne de saisie reste assez large pour le clavier. */
private fun columnWidths(table: TableExercise): List<Dp> = List(table.columnCount) { col ->
    var longest = 0
    var hasInput = false
    table.rows.forEach { row ->
        when (val cell = row.getOrNull(col)) {
            is TableCell.Fixed -> longest = maxOf(longest, cell.text.length.coerceAtMost(28))
            is TableCell.Blank -> hasInput = true
            null -> Unit
        }
    }
    val fitted = (longest * 8 + 28).dp.coerceIn(MIN_COLUMN, MAX_COLUMN)
    if (hasInput) maxOf(fitted, MIN_INPUT_COLUMN) else fitted
}

@Composable
private fun FixedCell(text: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFFF1F5F9))
            .border(0.5.dp, QuizColors.Border)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (text.isNotBlank()) {
            // Cellule de texte : formules rendues sans WebView (comme les tableaux des leçons)
            val safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            RichText(
                html = safe,
                color = QuizColors.Ink,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                allowKatex = false
            )
        }
    }
}

@Composable
private fun BlankCell(
    value: String,
    result: CellResult?,
    expected: String?,
    locked: Boolean,
    description: String,
    isLast: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit
) {
    val (background, border) = when (result) {
        CellResult.CORRECT -> QuizColors.CorrectBg to QuizColors.CorrectBorder
        CellResult.WRONG -> QuizColors.WrongBg to QuizColors.WrongBorder
        CellResult.EMPTY -> QuizColors.PartialBg to QuizColors.PartialBorder
        null -> QuizColors.Surface to QuizColors.Primary
    }
    Column(
        modifier = modifier
            .background(background)
            .border(if (result == null) 1.dp else 2.dp, border)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = !locked,
            readOnly = locked,
            singleLine = true,
            textStyle = TextStyle(color = QuizColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(QuizColors.Primary),
            keyboardOptions = KeyboardOptions(imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .semantics { contentDescription = description },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.heightIn(min = 36.dp)) {
                    if (value.isEmpty() && !locked) Text("…", color = QuizColors.Muted, fontSize = 16.sp)
                    inner()
                }
            }
        )
        // Correction : bonne réponse sous une cellule fausse ou vide (fournie seulement après la validation)
        if (!expected.isNullOrBlank() && (result == CellResult.WRONG || result == CellResult.EMPTY)) {
            Text(
                text = "Attendu : $expected",
                color = QuizColors.CorrectText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
