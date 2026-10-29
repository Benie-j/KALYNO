package ci.kalyno.app.ui.dojo

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.dojo.ExerciseKind
import ci.kalyno.app.ui.quiz.KalynoChoiceCard
import ci.kalyno.app.ui.quiz.QuizColors

/**
 * Zone de réponse du Dojo : elle s'adapte à l'état de la carte.
 * - [DojoExercise.requiresFreeInput] faux : cartes de choix (QCM) ;
 * - vrai : les choix disparaissent, un champ texte prend leur place (la validation passe par le bouton fixe du bas).
 * Composable sans état propre : tout vient de [DojoUiState], rien n'est recalculé à la recomposition.
 */
@Composable
internal fun ColumnScope.DojoAnswerArea(
    state: DojoUiState,
    exercise: DojoExercise,
    onToggleChoice: (Int) -> Unit,
    onTyped: (String) -> Unit,
    onSubmit: () -> Unit,
    onHaptic: () -> Unit
) {
    val locked = state.result != null

    Crossfade(
        targetState = exercise.requiresFreeInput,
        animationSpec = tween(220),
        label = "dojoAnswerMode"
    ) { freeInput ->
        if (freeInput) {
            FreeInputField(
                value = state.typed,
                locked = locked || state.evaluating,
                correct = state.result?.correct,
                sentence = exercise.useSemantic,
                onValueChange = onTyped,
                onSubmit = onSubmit
            )
        } else {
            ChoiceList(
                state = state,
                exercise = exercise,
                locked = locked,
                onToggleChoice = onToggleChoice,
                onHaptic = onHaptic
            )
        }
    }
}

@Composable
private fun FreeInputField(
    value: String,
    locked: Boolean,
    correct: Boolean?,
    sentence: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !locked,
        // Une phrase se rédige sur plusieurs lignes ; un mot-clé ou un nombre tient sur une seule
        singleLine = !sentence,
        minLines = if (sentence) 3 else 1,
        maxLines = if (sentence) 6 else 1,
        shape = RoundedCornerShape(14.dp),
        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
        placeholder = {
            Text(
                if (sentence) "Explique avec tes mots..." else "Écris ta réponse ici...",
                color = QuizColors.Muted
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = if (sentence) ImeAction.Default else ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = QuizColors.Surface,
            unfocusedContainerColor = QuizColors.Surface,
            disabledContainerColor = when (correct) {
                true -> QuizColors.CorrectBg
                false -> QuizColors.WrongBg
                null -> QuizColors.Surface // correction sémantique en cours
            },
            focusedBorderColor = QuizColors.Primary,
            unfocusedBorderColor = QuizColors.Border,
            disabledBorderColor = when (correct) {
                true -> QuizColors.CorrectBorder
                false -> QuizColors.WrongBorder
                null -> QuizColors.Border
            },
            cursorColor = QuizColors.Primary,
            focusedTextColor = QuizColors.Ink,
            unfocusedTextColor = QuizColors.Ink,
            disabledTextColor = QuizColors.Ink
        )
    )
}

@Composable
private fun ChoiceList(
    state: DojoUiState,
    exercise: DojoExercise,
    locked: Boolean,
    onToggleChoice: (Int) -> Unit,
    onHaptic: () -> Unit
) {
    val multiple = exercise.kind == ExerciseKind.MULTIPLE_CHOICE
    Column {
        if (multiple && !locked) {
            Text(
                text = "Coche toutes les bonnes réponses.",
                color = QuizColors.Muted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exercise.item.choices.forEachIndexed { i, choice ->
                KalynoChoiceCard(
                    letter = ('A'.code + i).toChar().toString(),
                    text = choice,
                    selected = i in state.selected,
                    locked = locked,
                    // La bonne réponse n'est révélée qu'après la validation
                    isCorrect = locked && i in exercise.correctIndices,
                    onClick = {
                        onHaptic()
                        onToggleChoice(i)
                    }
                )
            }
        }
    }
}
