package ci.kalyno.app.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.Feedback
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.dashboard.ReviewItem
import ci.kalyno.app.data.dashboard.ReviewSession
import ci.kalyno.app.data.srs.ReviewRating
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.StreakBadge
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrect
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerBorder
import ci.kalyno.app.ui.theme.KalynoDangerSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoRetryText
import ci.kalyno.app.ui.theme.KalynoText

// ===========================================================================
// SESSION DOJO : carte 3D recto/verso + feedback SRS
// (signature inchangée : MainScreen et DashboardViewModel n'ont pas à changer)
// ===========================================================================

@Composable
fun ReviewSessionScreen(
    session: ReviewSession,
    onRate: (cardId: String, rating: ReviewRating) -> Unit,
    onClose: (completed: Boolean) -> Unit
) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val dayStreak = remember { StreakStore(context.applicationContext).current() }

    var index by rememberSaveable(session.id) { mutableIntStateOf(0) }
    var forgotten by rememberSaveable(session.id) { mutableIntStateOf(0) }
    var hesitated by rememberSaveable(session.id) { mutableIntStateOf(0) }
    var easy by rememberSaveable(session.id) { mutableIntStateOf(0) }
    val total = session.items.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (index < total) {
            SessionTopBar(
                index = index,
                total = total,
                dayStreak = dayStreak,
                onClose = {
                    haptic()
                    onClose(false)
                }
            )
        }

        AnimatedContent(
            targetState = index,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                (slideInHorizontally(tween(320)) { it / 2 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 2 } + fadeOut(tween(200)))
            },
            label = "dojoCard"
        ) { i ->
            if (i >= total) {
                Finished(
                    total = total,
                    forgotten = forgotten,
                    hesitated = hesitated,
                    easy = easy,
                    onDone = { onClose(true) }
                )
            } else {
                val item = session.items[i]
                CardStep(
                    item = item,
                    title = session.title,
                    onRate = { rating ->
                        when (rating) {
                            ReviewRating.OUBLIE -> forgotten += 1
                            ReviewRating.HESITE -> hesitated += 1
                            else -> easy += 1
                        }
                        onRate(item.cardId, rating)
                        index = i + 1
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Barre du haut : fermer, progression ultra-fine, compteur, flamme
// ---------------------------------------------------------------------------

@Composable
private fun SessionTopBar(index: Int, total: Int, dayStreak: Int, onClose: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else index.toFloat() / total,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "sessionProgress"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Barre linéaire ultra-fine
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(KalynoBlueSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(KalynoBlue)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = KalynoMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${index + 1} / $total",
                color = KalynoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.weight(1f))
            StreakBadge(count = dayStreak)
        }
    }
}

// ---------------------------------------------------------------------------
// Une carte : recto (question) -> validation -> verso (correction) -> SRS
// ---------------------------------------------------------------------------

@Composable
private fun CardStep(
    item: ReviewItem,
    title: String?,
    onRate: (ReviewRating) -> Unit
) {
    val haptic = rememberHaptic()

    val isQcm = item.choices.isNotEmpty()
    val isCalc = !isQcm && looksLikeCalculation(item.answer)

    var flipped by rememberSaveable(item.cardId) { mutableStateOf(false) }
    var selected by rememberSaveable(item.cardId) { mutableIntStateOf(-1) }
    var typed by rememberSaveable(item.cardId) { mutableStateOf("") }

    // Résultat de l'auto-évaluation (null = carte de rappel libre, l'élève juge lui-même)
    val result: Boolean? = when {
        !flipped -> null
        isQcm -> if (selected < 0) null else selected == item.correctIndex
        isCalc -> if (typed.isBlank()) null else normalize(typed) == normalize(item.answer)
        else -> null
    }

    val canValidate = when {
        isQcm -> selected >= 0
        isCalc -> typed.isNotBlank()
        else -> true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp, bottom = 12.dp)
        ) {
            FlipCard(
                flipped = flipped,
                front = {
                    FrontFace(
                        item = item,
                        title = title,
                        isQcm = isQcm,
                        isCalc = isCalc,
                        selected = selected,
                        typed = typed,
                        enabled = !flipped,
                        onSelect = {
                            haptic()
                            selected = it
                        },
                        onTyped = { typed = it }
                    )
                },
                back = {
                    BackFace(
                        item = item,
                        isQcm = isQcm,
                        selected = selected,
                        typed = typed,
                        result = result
                    )
                }
            )
        }

        if (!flipped) {
            KalynoPrimaryButton(
                label = if (isQcm || isCalc) "VALIDER MA RÉPONSE" else "RÉVÉLER LA RÉPONSE",
                onClick = { flipped = true },
                enabled = canValidate
            )
        } else {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(tween(450, delayMillis = 250)) { it / 2 } +
                    fadeIn(tween(450, delayMillis = 250))
            ) {
                SrsPanel(onRate = onRate)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Carte 3D : rotation autour de l'axe Y, deux faces (la face arrière est
// re-pivotée de 180° pour ne pas apparaître en miroir).
// ---------------------------------------------------------------------------

@Composable
private fun FlipCard(
    flipped: Boolean,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "flip"
    )
    val density = LocalDensity.current.density

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
    ) {
        if (rotation <= 90f) {
            front()
        } else {
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                back()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// RECTO
// ---------------------------------------------------------------------------

@Composable
private fun FrontFace(
    item: ReviewItem,
    title: String?,
    isQcm: Boolean,
    isCalc: Boolean,
    selected: Int,
    typed: String,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onTyped: (String) -> Unit
) {
    KalynoCard(modifier = Modifier.fillMaxWidth(), contentPadding = 22.dp) {
        KalynoPill(text = "🧠 ACTIVE RECALL")
        Spacer(Modifier.height(8.dp))

        val context = listOfNotNull(
            title,
            item.subjectName.takeIf { it.isNotBlank() },
            item.chapterTitle.takeIf { it.isNotBlank() }
        ).distinct().joinToString(" · ")
        if (context.isNotBlank()) {
            Text(text = context, color = KalynoMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = item.front,
            color = KalynoText,
            fontSize = 22.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // Indice optionnel (bloc teinté orange)
        if (!item.hint.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KalynoAmberSoft)
                    .padding(14.dp)
            ) {
                Text("💡 INDICE", color = KalynoAmberDeep, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(item.hint.orEmpty(), color = KalynoRetryText, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }

        Spacer(Modifier.height(18.dp))

        when {
            isQcm -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item.choices.forEachIndexed { i, choice ->
                    ChoiceButton(
                        letter = ('A'.code + i).toChar().toString(),
                        text = choice,
                        selected = selected == i,
                        enabled = enabled,
                        onClick = { onSelect(i) }
                    )
                }
            }

            isCalc -> {
                OutlinedTextField(
                    value = typed,
                    onValueChange = onTyped,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    placeholder = { Text("Écris ton résultat ici...", color = KalynoMuted) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = KalynoBlue,
                        unfocusedBorderColor = KalynoOutline,
                        cursorColor = KalynoBlue,
                        focusedTextColor = KalynoText,
                        unfocusedTextColor = KalynoText
                    )
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "✏️ Prends ton brouillon : calcule d'abord, écris ensuite.",
                    color = KalynoMuted,
                    fontSize = 13.sp
                )
            }

            else -> Text(
                text = "Réfléchis, cherche dans ta mémoire… puis révèle la réponse.",
                color = KalynoMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ChoiceButton(
    letter: String,
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val container by animateColorAsState(
        targetValue = if (selected) KalynoBlueSoft else Color.White,
        label = "choiceBg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) KalynoBlue else KalynoOutline,
        label = "choiceBorder"
    )
    val badge by animateColorAsState(
        targetValue = if (selected) KalynoBlue else KalynoBackground,
        label = "choiceBadge"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(badge),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                color = if (selected) Color.White else KalynoMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = KalynoText,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// VERSO
// ---------------------------------------------------------------------------

@Composable
private fun BackFace(
    item: ReviewItem,
    isQcm: Boolean,
    selected: Int,
    typed: String,
    result: Boolean?
) {
    KalynoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 22.dp,
        background = Color.White,
        borderColor = if (result == false) KalynoDangerBorder else KalynoOutline
    ) {
        KalynoPill(text = "✅ CORRECTION", container = KalynoCorrectSoft, content = KalynoCorrectText)

        // Verdict (QCM et calculs uniquement)
        if (result != null) {
            Spacer(Modifier.height(14.dp))
            val message = remember(item.cardId, result) {
                if (result) Feedback.success() else Feedback.retry()
            }
            val shownAnswer = when {
                isQcm -> item.choices.getOrNull(selected).orEmpty()
                else -> typed
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (result) KalynoCorrectSoft else KalynoDangerSoft)
                    .padding(14.dp)
            ) {
                Text(
                    text = if (result) "🎉 $message" else "💪 $message",
                    color = if (result) KalynoCorrectText else KalynoDanger,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!result && shownAnswer.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Ta réponse : $shownAnswer", color = KalynoText, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "BONNE RÉPONSE",
            color = KalynoCorrectText,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.answer,
            color = KalynoCorrect,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(KalynoCorrectSoft)
                .padding(16.dp)
        )

        val steps = explanationSteps(item.explanation)
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "EXPLICATION PAS À PAS",
                color = KalynoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                steps.forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        if (steps.size > 1) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(KalynoBlueSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    color = KalynoBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = step,
                            color = KalynoText,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Panneau de feedback métacognitif (SRS) : Oublié / Hésité / Facile
// ---------------------------------------------------------------------------

@Composable
private fun SrsPanel(onRate: (ReviewRating) -> Unit) {
    val haptic = rememberHaptic()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Comment c'était ?",
            color = KalynoMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SrsButton(
                emoji = "🔴",
                label = "Oublié",
                container = KalynoDangerSoft,
                content = KalynoDanger,
                border = KalynoDangerBorder,
                modifier = Modifier.weight(1f)
            ) {
                haptic()
                onRate(ReviewRating.OUBLIE)
            }
            SrsButton(
                emoji = "🟡",
                label = "Hésité",
                container = KalynoAmberSoft,
                content = KalynoAmberDeep,
                border = Color(0xFFFDE68A),
                modifier = Modifier.weight(1f)
            ) {
                haptic()
                onRate(ReviewRating.HESITE)
            }
            SrsButton(
                emoji = "🟢",
                label = "Facile",
                container = KalynoBlue,
                content = Color.White,
                border = KalynoBlue,
                glow = true,
                modifier = Modifier.weight(1f)
            ) {
                haptic()
                onRate(ReviewRating.SU)
            }
        }
    }
}

@Composable
private fun SrsButton(
    emoji: String,
    label: String,
    container: Color,
    content: Color,
    border: Color,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .then(
                if (glow) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = KalynoBlue,
                        spotColor = KalynoBlue
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ---------------------------------------------------------------------------
// Fin de session
// ---------------------------------------------------------------------------

@Composable
private fun Finished(
    total: Int,
    forgotten: Int,
    hesitated: Int,
    easy: Int,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎉", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Session terminée !",
            color = KalynoText,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (total == 1) "1 carte révisée" else "$total cartes révisées",
            color = KalynoMuted,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryChip("🔴", forgotten, KalynoDangerSoft, KalynoDanger)
            SummaryChip("🟡", hesitated, KalynoAmberSoft, KalynoAmberDeep)
            SummaryChip("🟢", easy, KalynoCorrectSoft, KalynoCorrectText)
        }
        Spacer(Modifier.height(32.dp))
        KalynoPrimaryButton(label = "TERMINER", onClick = onDone)
    }
}

@Composable
private fun SummaryChip(emoji: String, count: Int, container: Color, content: Color) {
    Text(
        text = "$emoji $count",
        color = content,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

// ---------------------------------------------------------------------------
// Utilitaires purs
// ---------------------------------------------------------------------------

/** Réponse courte et chiffrée (ex. "x = 3", "12,5") : on propose un champ de saisie. */
private fun looksLikeCalculation(answer: String): Boolean {
    val a = answer.trim()
    return a.length <= 24 && a.any { it.isDigit() } && a.count { it.isLetter() } <= 4
}

/** Comparaison tolérante : espaces, virgule/point décimal, signe moins typographique. */
private fun normalize(value: String): String =
    value.lowercase()
        .replace(Regex("\\s+"), "")
        .replace(',', '.')
        .replace('−', '-')
        .trimEnd('.')

/** Découpe l'explication en étapes (une par ligne, sinon une par phrase). */
private fun explanationSteps(explanation: String?): List<String> {
    if (explanation.isNullOrBlank()) return emptyList()
    val lines = explanation.lines().map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotEmpty() }
    if (lines.size > 1) return lines
    val sentences = explanation.trim().split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    return if (sentences.size > 1) sentences else listOf(explanation.trim())
}
