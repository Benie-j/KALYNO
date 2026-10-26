package ci.kalyno.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import ci.kalyno.app.ui.explorer.RichText

/**
 * Texte saisi dans l'admin : les formules LaTeX entre $ … $ (ou $$ … $$) sont rendues,
 * le reste s'affiche comme un texte normal. Sans « $ », c'est un simple Text.
 */
@Composable
fun MathText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Box(modifier) {
        if (!text.contains('$')) {
            Text(text = text, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight)
        } else {
            // RichText lit du HTML simple : on échappe &, < et > pour que « a < b » ne soit pas pris pour une balise.
            val safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            RichText(html = safe, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight)
        }
    }
}
