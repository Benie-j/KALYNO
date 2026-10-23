package ci.kalyno.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.theme.*

private const val MIN_LEN = 2
private const val MAX_LEN = 16

@Composable
fun NicknameScreen(onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val clean = text.trim()
    val valid = clean.length >= MIN_LEN

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✨", fontSize = 36.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Quel est ton pseudo pour l'aventure ?",
            color = KalynoText,
            fontSize = 24.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MAX_LEN) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Ton pseudo") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(clean) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KalynoBlue,
                unfocusedBorderColor = KalynoOutline,
                cursorColor = KalynoBlue,
                focusedTextColor = KalynoText,
                unfocusedTextColor = KalynoText,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onConfirm(clean) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KalynoBlue,
                contentColor = Color.White,
                disabledContainerColor = KalynoOutline,
                disabledContentColor = KalynoMuted
            )
        ) {
            Text("C'est parti !", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
