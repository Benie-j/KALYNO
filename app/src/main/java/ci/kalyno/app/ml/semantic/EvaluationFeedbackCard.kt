package ci.kalyno.app.ml.semantic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Charte KALYNO (mode clair)
private val Petrol = Color(0xFF0F766E)
private val Amber = Color(0xFFF59E0B)
private val Title = Color(0xFF0F172A)
private val Secondary = Color(0xFF64748B)
private val Border = Color(0xFFE2E8F0)

/** Carte de retour pédagogique : sobre, sans émoji, couleur d'accent selon le verdict. */
@Composable
fun EvaluationFeedbackCard(result: EvaluationResult, modifier: Modifier = Modifier) {
    val accent = if (result.isValid) Petrol else Amber
    val label = when (result.verdict) {
        EvaluationVerdict.CORRECT -> "Réponse validée"
        EvaluationVerdict.PARTIAL -> "Presque"
        EvaluationVerdict.CONTRADICTION -> "Sens à vérifier"
        EvaluationVerdict.ENGINE_ERROR -> "Évaluation indisponible"
        else -> "À revoir"
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(result.feedback, color = Title, fontSize = 15.sp, lineHeight = 22.sp)
            if (result.verdict != EvaluationVerdict.ENGINE_ERROR && result.verdict != EvaluationVerdict.EMPTY_INPUT) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Proximité sémantique : ${(result.similarityScore * 100).toInt().coerceAtLeast(0)} %",
                    color = Secondary, fontSize = 12.sp
                )
            }
        }
    }
}
