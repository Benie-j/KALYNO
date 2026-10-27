package ci.kalyno.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GoogleBlue = Color(0xFF4285F4)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)

/**
 * Logo Google officiel à 4 couleurs, dessiné au Canvas (aucun asset externe requis).
 * Composé de 4 arcs (un par couleur) + la barre horizontale caractéristique du "G".
 */
@Composable
fun GoogleLogo(size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = this.size.minDimension * 0.22f
        val radius = (this.size.minDimension - strokeWidth) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val arcSize = Size(radius * 2f, radius * 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val stroke = Stroke(width = strokeWidth)

        // Les 4 quarts du anneau, dans l'ordre officiel (rouge en haut, puis jaune, vert, bleu)
        drawArc(color = GoogleRed, startAngle = -90f, sweepAngle = -89f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(color = GoogleYellow, startAngle = 180f, sweepAngle = -89f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(color = GoogleGreen, startAngle = 90f, sweepAngle = -89f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(color = GoogleBlue, startAngle = 0f, sweepAngle = -91f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)

        // Barre horizontale du "G" (bras droit), en bleu, caractéristique du logo
        val barHeight = strokeWidth
        val barWidth = radius * 1.05f
        drawRect(
            color = GoogleBlue,
            topLeft = Offset(center.x - barHeight / 2f, center.y - barHeight / 2f),
            size = Size(barWidth, barHeight)
        )
    }
}
