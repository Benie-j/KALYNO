package ci.kalyno.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.reports.ReportCategory
import ci.kalyno.app.data.reports.ReportOutcome
import ci.kalyno.app.data.reports.ReportRules
import ci.kalyno.app.data.reports.ReportSourceType
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText
import kotlinx.coroutines.launch
import java.io.Serializable

// ===========================================================================
// CONTEXTE DU SIGNALEMENT
// ===========================================================================

/**
 * Ce que l'élève signale. Le contexte est rempli par l'écran appelant : l'élève n'a rien à deviner,
 * et l'équipe retrouve l'élément exact (source + identifiant + libellé lisible).
 *
 * [title] et [subtitle] sont affichés dans la feuille ; [contextLabel] est envoyé avec le rapport.
 * Serializable : la feuille survit à une rotation d'écran.
 */
data class ReportTarget(
    val sourceType: ReportSourceType,
    val sourceId: String?,
    val title: String,
    val subtitle: String?,
    val contextLabel: String
) : Serializable {

    companion object {
        fun lesson(lessonId: String, lessonTitle: String, subjectName: String) = ReportTarget(
            sourceType = ReportSourceType.LESSON,
            sourceId = lessonId,
            title = lessonTitle,
            subtitle = subjectName,
            contextLabel = "$subjectName › $lessonTitle"
        )

        /** Exercice du Dojo : [cardId] est l'identifiant de la carte (flashcard). */
        fun dojoExercise(cardId: String, subjectName: String, lessonTitle: String, question: String) = ReportTarget(
            sourceType = ReportSourceType.EXERCISE,
            sourceId = cardId,
            title = excerpt(question),
            subtitle = "$subjectName › $lessonTitle · Réf. ${cardId.take(REF_LENGTH)}",
            contextLabel = "$subjectName › $lessonTitle · Réf. ${cardId.take(REF_LENGTH)}"
        )

        /** Question du premier exercice (exercice de bienvenue) : [questionId] est l'identifiant de la question. */
        fun welcomeExercise(
            questionId: String,
            challengeTitle: String,
            position: Int,
            total: Int,
            question: String
        ): ReportTarget {
            val where = if (challengeTitle.isBlank()) "Exercice de bienvenue" else "Exercice de bienvenue · $challengeTitle"
            return ReportTarget(
                sourceType = ReportSourceType.EXERCISE,
                sourceId = questionId,
                title = excerpt(question),
                subtitle = "$where · Question $position/$total",
                contextLabel = "$where · Q$position/$total"
            )
        }

        fun settings(levelLabel: String, versionLabel: String) = ReportTarget(
            sourceType = ReportSourceType.SETTINGS,
            sourceId = null,
            title = "Paramètres",
            subtitle = "$levelLabel · $versionLabel",
            contextLabel = "Paramètres · $versionLabel · $levelLabel"
        )

        private const val REF_LENGTH = 8
        private const val EXCERPT_LENGTH = 110

        private fun excerpt(text: String): String {
            val flat = text.trim().replace(Regex("\\s+"), " ")
            return if (flat.length > EXCERPT_LENGTH) flat.take(EXCERPT_LENGTH - 1) + "…" else flat
        }
    }
}

// ===========================================================================
// ÉTAT PARTAGÉ PAR LES ÉCRANS (feuille + snackbar de confirmation)
// ===========================================================================

@Stable
class ReportLayerState internal constructor(
    val snackbarHostState: SnackbarHostState,
    private val targetState: MutableState<ReportTarget?>
) {
    val target: ReportTarget? get() = targetState.value

    /** Ouvre la feuille de signalement pour [target]. */
    fun open(target: ReportTarget) {
        targetState.value = target
    }

    internal fun close() {
        targetState.value = null
    }
}

@Composable
fun rememberReportLayerState(): ReportLayerState {
    val host = remember { SnackbarHostState() }
    val target = rememberSaveable { mutableStateOf<ReportTarget?>(null) }
    return remember(host, target) { ReportLayerState(host, target) }
}

/** Feuille + confirmation. À poser dans un `Box` : la snackbar s'ancre en bas de ce `Box`. */
@Composable
fun BoxScope.ReportLayer(state: ReportLayerState) {
    ReportSheetHost(state)
    ReportSnackbarHost(state, Modifier.align(Alignment.BottomCenter))
}

/** La feuille seule (quand la snackbar est portée par un `Scaffold`). */
@Composable
fun ReportSheetHost(state: ReportLayerState) {
    val target = state.target ?: return
    val scope = rememberCoroutineScope()
    ReportBottomSheet(
        target = target,
        onDismiss = state::close,
        onSubmitted = { outcome ->
            state.close()
            scope.launch {
                state.snackbarHostState.showSnackbar(
                    message = confirmationMessage(outcome),
                    duration = SnackbarDuration.Long
                )
            }
        }
    )
}

/** La snackbar seule (à passer au paramètre `snackbarHost` d'un `Scaffold`). */
@Composable
fun ReportSnackbarHost(state: ReportLayerState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = state.snackbarHostState,
        modifier = modifier.navigationBarsPadding()
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = KalynoText,
            contentColor = Color.White,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

private fun confirmationMessage(outcome: ReportOutcome): String = when (outcome) {
    ReportOutcome.SENT -> "Merci ! Ton signalement a bien été transmis à l'équipe."
    ReportOutcome.QUEUED -> "Pas de connexion : ton signalement est gardé et sera envoyé dès le retour du réseau."
}

// ===========================================================================
// ICÔNE D'ENTRÉE
// ===========================================================================

/** Petit drapeau à poser dans l'en-tête d'un écran. */
@Composable
fun ReportFlagButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Flag,
            contentDescription = "Signaler une erreur",
            tint = KalynoMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ===========================================================================
// LA FEUILLE
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportBottomSheet(
    target: ReportTarget,
    onDismiss: () -> Unit,
    onSubmitted: (ReportOutcome) -> Unit,
    viewModel: ReportViewModel = viewModel()
) {
    val form by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // Pas de fermeture par glissement pendant l'envoi
        confirmValueChange = { !viewModel.state.value.sending }
    )

    var categoryCode by rememberSaveable(target) { mutableStateOf<String?>(null) }
    var description by rememberSaveable(target) { mutableStateOf("") }

    val categories = remember(target.sourceType) { ReportCategory.forSource(target.sourceType) }
    val category = ReportCategory.fromCode(categoryCode)
    val canSend = ReportRules.isValid(category, description)

    LaunchedEffect(target) { viewModel.reset() }

    ModalBottomSheet(
        onDismissRequest = { if (!viewModel.state.value.sending) onDismiss() },
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // ----- En-tête -----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(KalynoAmberSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, tint = KalynoAmberDeep, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Signaler une erreur",
                    color = KalynoText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // ----- Contexte automatique -----
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KalynoBlueSoft)
                    .padding(14.dp)
            ) {
                Text(
                    text = "TU SIGNALES",
                    color = KalynoBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = target.title,
                    color = KalynoText,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3
                )
                target.subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(text = it, color = KalynoMuted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
                }
            }

            // ----- Motif -----
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Quel est le problème ?",
                color = KalynoText,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { item ->
                    FilterChip(
                        selected = item == category,
                        onClick = { if (!form.sending) categoryCode = item.name },
                        label = { Text(item.label, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White,
                            labelColor = KalynoText,
                            selectedContainerColor = KalynoBlueSoft,
                            selectedLabelColor = KalynoBlue
                        )
                    )
                }
            }

            // ----- Description -----
            Spacer(Modifier.height(18.dp))
            val required = category?.descriptionRequired == true
            Text(
                text = if (required) "Explique-nous (obligatoire)" else "Un détail à ajouter ? (facultatif)",
                color = KalynoText,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { if (!form.sending) description = it.take(ReportRules.MAX_DESCRIPTION) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !form.sending,
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Décris ce qui ne va pas…", color = KalynoMuted) },
                supportingText = {
                    val remaining = ReportRules.MIN_REQUIRED_DESCRIPTION - ReportRules.clean(description).length
                    Text(
                        text = if (required && remaining > 0) {
                            "Encore $remaining caractère${if (remaining > 1) "s" else ""} minimum"
                        } else {
                            "${description.length}/${ReportRules.MAX_DESCRIPTION}"
                        },
                        color = KalynoMuted
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedBorderColor = KalynoBlue,
                    unfocusedBorderColor = KalynoOutline,
                    cursorColor = KalynoBlue,
                    focusedTextColor = KalynoText,
                    unfocusedTextColor = KalynoText,
                    disabledTextColor = KalynoMuted
                )
            )

            form.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it, color = KalynoDanger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // ----- Envoi -----
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val chosen = category ?: return@Button
                    viewModel.submit(target, chosen, description) { outcome ->
                        // On referme la feuille en douceur, puis l'écran affiche la confirmation
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onSubmitted(outcome) }
                    }
                },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KalynoBlue,
                    contentColor = Color.White,
                    disabledContainerColor = KalynoOutline,
                    disabledContentColor = KalynoMuted
                )
            ) {
                if (form.sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("ENVOI EN COURS…", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                } else {
                    Text("ENVOYER LE SIGNALEMENT", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
