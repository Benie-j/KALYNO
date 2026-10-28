package ci.kalyno.app.ui.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.MathText

// ===========================================================================
// KIT QUIZ KALYNO : carte de question, cartes de réponse, footer fixe de rétroaction.
// Partagé par le Dojo et le Défi. Icônes : ImageVector uniquement (aucun emoji).
// ===========================================================================

/** Palette exacte du quiz (fond global #F8FAFC, cartes #FFFFFF + bordure #E2E8F0, vert pétrole #0F766E). */
object QuizColors {
    val Background = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFE2E8F0)
    val Primary = Color(0xFF0F766E)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF64748B)

    val SelectedBg = Color(0xFFF0FDFA)
    val CorrectBg = Color(0xFFECFDF5)
    val CorrectBorder = Color(0xFF10B981)
    val CorrectText = Color(0xFF047857)
    val WrongBg = Color(0xFFFEF2F2)
    val WrongBorder = Color(0xFFEF4444)
    val WrongText = Color(0xFFB91C1C)
    val PartialBg = Color(0xFFFFFBEB)
    val PartialBorder = Color(0xFFF59E0B)
    val PartialText = Color(0xFFB45309)
}

// ---------------------------------------------------------------------------
// 1) KalynoQuestionCard : cadre de question compact et épuré
// ---------------------------------------------------------------------------

/**
 * Cadre de la question : fond blanc, bordure subtile, coins arrondis, rappel discret de la leçon
 * en vert pétrole. [content] reçoit la zone de réponse (cartes de suggestions ou champ de saisie).
 */
@Composable
fun KalynoQuestionCard(
    question: String,
    modifier: Modifier = Modifier,
    lessonLabel: String? = null,
    typeLabel: String? = null,
    hint: String? = null,
    hintVisible: Boolean = false,
    onRequestHint: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(QuizColors.Surface)
            .border(1.dp, QuizColors.Border, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (!lessonLabel.isNullOrBlank() || !typeLabel.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!lessonLabel.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint = QuizColors.Primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = lessonLabel,
                        color = QuizColors.Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!typeLabel.isNullOrBlank()) {
                    Text(
                        text = typeLabel,
                        color = QuizColors.Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(QuizColors.SelectedBg)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        MathText(
            text = question,
            color = QuizColors.Ink,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.ExtraBold
        )

        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            if (hintVisible) {
                MathText(
                    text = hint,
                    color = QuizColors.PartialText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(QuizColors.PartialBg)
                        .padding(10.dp)
                )
            } else if (onRequestHint != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(QuizColors.PartialBg)
                        .clickable(onClick = onRequestHint)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = QuizColors.PartialText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Voir un indice",
                        color = QuizColors.PartialText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        content()
    }
}

// ---------------------------------------------------------------------------
// 2) KalynoChoiceCard : carte de suggestion (A, B, C, D) et ses états
// ---------------------------------------------------------------------------

/** États d'une carte de suggestion : neutre, sélectionné, validé correct, validé incorrect. */
private enum class ChoiceState { Neutral, Selected, Correct, Wrong }

/**
 * Carte de suggestion compacte avec badge circulaire de la lettre.
 * Après validation, le badge devient une icône Check (bonne réponse) ou Close (mauvaise réponse choisie).
 * La bonne réponse n'est révélée qu'une fois [locked] vrai.
 */
@Composable
fun KalynoChoiceCard(
    letter: String,
    text: String,
    selected: Boolean,
    locked: Boolean,
    isCorrect: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = when {
        locked && isCorrect -> ChoiceState.Correct
        locked && selected -> ChoiceState.Wrong
        selected -> ChoiceState.Selected
        else -> ChoiceState.Neutral
    }
    val dimmed = locked && state == ChoiceState.Neutral

    val spec = tween<Color>(durationMillis = 180)
    val container by animateColorAsState(
        targetValue = when (state) {
            ChoiceState.Neutral -> QuizColors.Surface
            ChoiceState.Selected -> QuizColors.SelectedBg
            ChoiceState.Correct -> QuizColors.CorrectBg
            ChoiceState.Wrong -> QuizColors.WrongBg
        },
        animationSpec = spec,
        label = "choiceBg"
    )
    val border by animateColorAsState(
        targetValue = when (state) {
            ChoiceState.Neutral -> QuizColors.Border
            ChoiceState.Selected -> QuizColors.Primary
            ChoiceState.Correct -> QuizColors.CorrectBorder
            ChoiceState.Wrong -> QuizColors.WrongBorder
        },
        animationSpec = spec,
        label = "choiceBorder"
    )
    val badge by animateColorAsState(
        targetValue = when (state) {
            ChoiceState.Neutral -> QuizColors.Background
            ChoiceState.Selected -> QuizColors.Primary
            ChoiceState.Correct -> QuizColors.CorrectBorder
            ChoiceState.Wrong -> QuizColors.WrongBorder
        },
        animationSpec = spec,
        label = "choiceBadge"
    )

    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (dimmed) 0.55f else 1f }
            .clip(shape)
            .background(container)
            .border(BorderStroke(if (state == ChoiceState.Neutral) 1.dp else 1.5.dp, border), shape)
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(badge)
                .then(
                    if (state == ChoiceState.Neutral) Modifier.border(1.dp, QuizColors.Border, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                ChoiceState.Correct -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                ChoiceState.Wrong -> Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                else -> Text(
                    text = letter,
                    color = if (state == ChoiceState.Selected) Color.White else QuizColors.Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        MathText(
            text = text,
            color = QuizColors.Ink,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = if (state == ChoiceState.Neutral) FontWeight.Medium else FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// 3) Footer fixe : rétroaction + bouton d'action dynamique
// ---------------------------------------------------------------------------

enum class FeedbackTone { Correct, Partial, Wrong }

/**
 * Barre fixe en bas de l'écran. Avant validation : seulement le bouton « VALIDER MA RÉPONSE ».
 * Après validation : la rétroaction (explication comprise) se déploie AU-DESSUS du bouton, qui devient
 * « CONTINUER ». Comme le footer est hors de la zone défilante, le résultat est toujours visible sans scroll ;
 * si l'explication est très longue, elle défile dans le footer (hauteur plafonnée à 42 % de l'écran).
 *
 * À placer en dernier enfant d'une Column plein écran dont la zone de question a `weight(1f)`.
 */
@Composable
fun QuizStickyFooter(
    validated: Boolean,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    feedback: (@Composable () -> Unit)? = null
) {
    val maxFeedbackHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = QuizColors.Surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, QuizColors.Border),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            AnimatedVisibility(
                visible = validated && feedback != null,
                enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxFeedbackHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    feedback?.invoke()
                    Spacer(Modifier.height(12.dp))
                }
            }
            KalynoPrimaryButton(
                label = actionLabel,
                onClick = onAction,
                enabled = actionEnabled,
                height = 50.dp
            )
        }
    }
}

/**
 * Contenu standard de la rétroaction : verdict (icône Check / Close), détail, bonne réponse,
 * prochaine révision FSRS et explication en étapes.
 */
@Composable
fun QuizFeedbackPanel(
    tone: FeedbackTone,
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    meta: String? = null,
    correctAnswer: String? = null,
    alsoAccepted: List<String> = emptyList(),
    explanationSteps: List<String> = emptyList()
) {
    val (bg, borderColor, accent, icon) = when (tone) {
        FeedbackTone.Correct -> ToneStyle(QuizColors.CorrectBg, QuizColors.CorrectBorder, QuizColors.CorrectText, Icons.Rounded.Check)
        FeedbackTone.Partial -> ToneStyle(QuizColors.PartialBg, QuizColors.PartialBorder, QuizColors.PartialText, Icons.Rounded.Remove)
        FeedbackTone.Wrong -> ToneStyle(QuizColors.WrongBg, QuizColors.WrongBorder, QuizColors.WrongText, Icons.Rounded.Close)
    }
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(borderColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                color = accent,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text = detail, color = QuizColors.Ink, fontSize = 13.sp)
        }
        if (!meta.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text = meta, color = QuizColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        if (!correctAnswer.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "BONNE RÉPONSE",
                color = QuizColors.CorrectText,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(4.dp))
            MathText(
                text = correctAnswer,
                color = QuizColors.CorrectText,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (alsoAccepted.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Aussi accepté : " + alsoAccepted.joinToString(" · "),
                    color = QuizColors.Muted,
                    fontSize = 12.sp
                )
            }
        }

        if (explanationSteps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "EXPLICATION",
                color = QuizColors.Primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                explanationSteps.forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        if (explanationSteps.size > 1) {
                            Box(
                                modifier = Modifier.size(20.dp).clip(CircleShape).background(QuizColors.Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", color = QuizColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        MathText(
                            text = step,
                            color = QuizColors.Ink,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private data class ToneStyle(val bg: Color, val border: Color, val accent: Color, val icon: ImageVector)
