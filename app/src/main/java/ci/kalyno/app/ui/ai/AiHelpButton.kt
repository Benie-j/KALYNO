package ci.kalyno.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.ai.AiExerciseContext
import ci.kalyno.app.data.ai.KalynoRepository
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft

/**
 * « Demander à Kalyno » depuis un exercice (Dojo ou Challenge). L'aide ne valide rien et ne modifie pas la réponse :
 * elle ouvre seulement une conversation avec le contexte de la question. Aucun appel tant que l'élève n'écrit pas.
 * Affichée uniquement avec un compte connecté.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHelpButton(context: AiExerciseContext, vmKey: String, modifier: Modifier = Modifier) {
    val appContext = LocalContext.current
    if (!remember { KalynoRepository.get(appContext).isAvailable() }) return
    var open by rememberSaveable(vmKey) { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KalynoBlueSoft)
            .clickable { open = true }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        KalynoSvgIcon(name = "sparkles", size = 18.dp, tint = KalynoBlue)
        Text(
            "Demander à Kalyno",
            color = KalynoBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            KalynoExerciseHelp(
                context = context,
                vmKey = vmKey,
                modifier = Modifier.fillMaxHeight(0.85f)
            )
        }
    }
}
