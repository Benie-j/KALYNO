package ci.kalyno.app.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.ai.AiExerciseContext
import ci.kalyno.app.ui.components.MathText
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText

/** Onglet « Gemini » : conversation libre, sans contexte d'exercice. */
@Composable
fun GeminiAssistantScreen(modifier: Modifier = Modifier) {
    val vm: GeminiAssistantViewModel = viewModel(key = "gemini-tab")
    LaunchedEffect(Unit) { vm.setContext(null) }
    Column(modifier = modifier.fillMaxSize().background(KalynoBackground)) {
        Text(
            "Gemini",
            color = KalynoText,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        ChatPane(vm = vm, modifier = Modifier.weight(1f), suggestions = FREE_SUGGESTIONS)
    }
}

/** Contenu de la feuille d'aide d'un exercice : même conversation, avec le contexte de la question. */
@Composable
fun GeminiExerciseHelp(context: AiExerciseContext, vmKey: String, modifier: Modifier = Modifier) {
    val vm: GeminiAssistantViewModel = viewModel(key = "gemini-help-$vmKey")
    LaunchedEffect(context) { vm.setContext(context) }
    ChatPane(
        vm = vm,
        modifier = modifier,
        suggestions = if (context.locked) HELP_AFTER else HELP_BEFORE
    )
}

private val FREE_SUGGESTIONS = listOf("Explique-moi une notion", "Donne-moi un exemple", "Reformule autrement")
private val HELP_BEFORE = listOf("Explique-moi cette question.", "Explique cette notion autrement.")
private val HELP_AFTER = listOf("Pourquoi ma réponse est-elle incorrecte ?", "Explique cette notion autrement.")

@Composable
private fun ChatPane(vm: GeminiAssistantViewModel, modifier: Modifier, suggestions: List<String>) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.busy) {
        val last = state.messages.size + if (state.busy || state.error != null) 1 else 0
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    Column(modifier = modifier.fillMaxWidth().imePadding()) {
        if (!state.signedIn) {
            Notice("Connecte-toi pour discuter avec Gemini.", error = false, onRetry = null)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.messages.isEmpty() && state.signedIn) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Text("Pose une question ou choisis :", color = KalynoMuted, fontSize = 14.sp)
                        suggestions.forEach { s ->
                            Text(
                                s,
                                color = KalynoBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(KalynoBlueSoft)
                                    .clickable { vm.send(s) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
            items(state.messages, key = { it.id }) { Bubble(it.fromUser, it.text) }
            if (state.busy) item(key = "typing") { TypingIndicator() }
            if (state.error != null && !state.busy) {
                item(key = "error") { Notice(state.error!!, error = true, onRetry = vm::retry) }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::onInput,
                modifier = Modifier.weight(1f),
                enabled = state.signedIn,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                placeholder = { Text("Pose ta question…", color = KalynoMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = KalynoBlue,
                    unfocusedBorderColor = KalynoOutline
                )
            )
            Spacer(Modifier.size(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (state.canSend) KalynoBlue else KalynoOutline)
                    .clickable(enabled = state.canSend) { vm.send() }
            ) {
                KalynoSvgIcon(name = "send", size = 22.dp, tint = Color.White)
            }
        }
    }
}

@Composable
private fun Bubble(fromUser: Boolean, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (fromUser) KalynoBlue else Color.White)
                .border(BorderStroke(1.dp, if (fromUser) KalynoBlue else KalynoOutline), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            MathText(
                text = text,
                color = if (fromUser) Color.White else KalynoText,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = KalynoBlue)
        Spacer(Modifier.size(8.dp))
        Text("Gemini réfléchit…", color = KalynoMuted, fontSize = 13.sp)
    }
}

@Composable
private fun Notice(message: String, error: Boolean, onRetry: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (error) KalynoDangerSoft else KalynoBlueSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(message, color = if (error) KalynoDanger else KalynoBlue, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            Text(
                "Réessayer",
                color = KalynoDanger,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onRetry).padding(start = 12.dp)
            )
        }
    }
}
