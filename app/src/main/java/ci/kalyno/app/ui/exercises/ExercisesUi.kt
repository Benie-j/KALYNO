package ci.kalyno.app.ui.exercises

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.ConfettiBurst
import ci.kalyno.app.ui.components.DriftingBlob
import ci.kalyno.app.ui.components.KalynoAurora
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.KalynoRing
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.components.bouncyClick
import ci.kalyno.app.ui.components.entrance
import ci.kalyno.app.ui.components.rememberCountUp
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.components.sheen
import ci.kalyno.app.ui.explorer.RichText
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrect
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoPurple
import ci.kalyno.app.ui.theme.KalynoRetry
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

// ===========================================================================
// Exercices IA : « le Labo ».
// Un orbe qui émet des ondes, un parcours qui se remplit, des défis présentés comme des
// tickets numérotés, une correction qui célèbre. Couleurs inchangées (palette KALYNO).
// ===========================================================================

// ---------------------------------------------------------------------------
// Aides
// ---------------------------------------------------------------------------

private data class KindInfo(val icon: String, val label: String, val mission: String, val badge: String)

private fun kindInfo(kind: String): KindInfo = when (kind) {
    "qcm", "multiple_choice" -> KindInfo("target", "CHOIX MULTIPLES", "Fais ton choix", "QCM")
    "table" -> KindInfo("layers", "TABLEAU À COMPLÉTER", "Complète le tableau", "TABLEAU")
    else -> KindInfo("pen-line", "RÉPONSE ÉCRITE", "Construis ta réponse", "RÉDACTION")
}

private fun difficultyLevel(raw: String): Int = when (raw.lowercase()) {
    "easy", "facile", "beginner" -> 1
    "medium", "intermediate", "moyen" -> 2
    "hard", "difficult", "difficile", "advanced" -> 3
    else -> 0
}

private fun difficultyLabel(level: Int): String = when (level) {
    1 -> "Facile"
    2 -> "Intermédiaire"
    3 -> "Avancé"
    else -> "Entraînement"
}

private fun blankKeysOf(rows: JSONArray): Set<String> {
    val keys = linkedSetOf<String>()
    for (r in 0 until rows.length()) {
        val row = rows.optJSONArray(r) ?: JSONArray()
        for (c in 0 until row.length()) {
            val cell = row.optJSONObject(c) ?: JSONObject()
            if (cell.optString("type") == "blank") {
                val key = cell.optString("key")
                if (key.isNotBlank()) keys.add(key)
            }
        }
    }
    return keys
}

@Composable
private fun kalynoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = KalynoBlue,
    unfocusedBorderColor = KalynoOutline,
    cursorColor = KalynoBlue,
    focusedTextColor = KalynoText,
    unfocusedTextColor = KalynoText,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White
)

// ---------------------------------------------------------------------------
// Petits éléments animés
// ---------------------------------------------------------------------------

/** Pastilles de difficulté (1 à 3 points). Rien n'est affiché si le niveau est inconnu. */
@Composable
private fun DifficultyDots(level: Int, onDark: Boolean = false) {
    if (level <= 0) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..3) {
            val filled = i <= level
            val color = when {
                filled && onDark -> Color.White
                filled -> KalynoYellow
                onDark -> Color.White.copy(alpha = 0.3f)
                else -> KalynoOutline
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * L'orbe du Labo : un noyau Vert Pétrole portant l'icône des exercices IA, qui flotte doucement
 * et émet des ondes de sonar. Sert de signature visuelle à l'onglet.
 */
@Composable
internal fun AiOrb(modifier: Modifier = Modifier, diameter: Dp = 56.dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600, easing = LinearEasing)),
        label = "orbWave"
    )
    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBob"
    )

    Box(
        modifier = modifier
            .size(diameter)
            .graphicsLayer { translationY = bob * 2.dp.toPx() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val core = this.size.minDimension * 0.39f
            val reach = this.size.minDimension * 0.5f
            for (k in 0..1) {
                val p = (wave + k * 0.5f) % 1f
                drawCircle(
                    color = KalynoBlue.copy(alpha = (1f - p) * 0.35f),
                    radius = core + (reach - core) * p,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        Box(
            modifier = Modifier
                .size(diameter * 0.78f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark))),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = "exercise-spark", size = diameter * 0.42f, tint = Color.White)
        }
    }
}

/** Trois points qui rebondissent : « l'IA réfléchit ». */
@Composable
private fun ThinkingDots(color: Color = KalynoBlue) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..2) {
            val lift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 160)
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        translationY = -lift * 5.dp.toPx()
                        alpha = 0.45f + 0.55f * lift
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun rememberSpin(): Float {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "spinAngle"
    )
    return angle
}

// ===========================================================================
// LISTE DES DÉFIS
// ===========================================================================

@Composable
internal fun ExerciseListContent(
    exercises: List<JSONObject>,
    loading: Boolean,
    error: String?,
    filter: String,
    onFilterChange: (String) -> Unit,
    isCompleted: (JSONObject) -> Boolean,
    onRefresh: () -> Unit,
    onOpen: (JSONObject) -> Unit,
    catalog: ExerciseCatalog = ExerciseCatalog.EMPTY
) {
    val haptic = rememberHaptic()
    val completedCount = exercises.count { isCompleted(it) }
    val remaining = exercises.size - completedCount
    val progress = if (exercises.isEmpty()) 0f else completedCount.toFloat() / exercises.size
    val next = exercises.firstOrNull { !isCompleted(it) }
    val visible = exercises.filter {
        when (filter) {
            "todo" -> !isCompleted(it)
            "done" -> isCompleted(it)
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ----- En-tête : orbe + titre + actualiser -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .entrance(0),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AiOrb()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Exercices IA",
                    color = KalynoText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Taillés pour toi, corrigés par l'IA",
                    color = KalynoMuted,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            val angle = if (loading) rememberSpin() else 0f
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .bouncyClick(shape = CircleShape, enabled = !loading) {
                        haptic()
                        onRefresh()
                    }
                    .background(Color.White)
                    .border(1.dp, KalynoOutline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.graphicsLayer { rotationZ = angle }) {
                    KalynoSvgIcon(
                        name = "refresh-cw",
                        size = 20.dp,
                        tint = KalynoBlue,
                        contentDescription = "Actualiser"
                    )
                }
            }
        }

        // ----- Carte parcours -----
        val headline = when {
            exercises.isEmpty() -> "Ton prochain défi se prépare"
            remaining == 1 -> "1 défi t'attend"
            remaining > 1 -> "$remaining défis t'attendent"
            else -> "Labo vidé : bravo !"
        }
        val subline = when {
            exercises.isEmpty() -> "L'IA te prépare des exercices sur mesure."
            remaining > 0 -> "$completedCount sur ${exercises.size} relevés. Chaque réponse est analysée."
            else -> "Tu as relevé les ${exercises.size} défis disponibles."
        }

        KalynoAurora(modifier = Modifier.fillMaxWidth().entrance(1)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        KalynoPill(
                            text = "TON LABO",
                            container = KalynoAmberSoft,
                            content = KalynoAmberDeep,
                            fontSize = 11
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = headline,
                            color = Color.White,
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = subline,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    val percent = rememberCountUp((progress * 100).roundToInt())
                    KalynoRing(
                        progress = progress,
                        diameter = 84.dp,
                        strokeWidth = 8.dp,
                        color = Color.White,
                        track = Color.White.copy(alpha = 0.22f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$percent%",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "accompli",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (next != null) {
                    Text(
                        text = "LANCER LE PROCHAIN DÉFI",
                        color = KalynoBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .bouncyClick(shape = CircleShape) {
                                haptic()
                                onOpen(next)
                            }
                            .background(Color.White)
                            .sheen(color = KalynoBlue.copy(alpha = 0.16f))
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // ----- Filtres -----
        SegmentedFilter(
            selected = filter,
            counts = listOf(exercises.size, remaining, completedCount),
            onSelect = onFilterChange,
            modifier = Modifier.entrance(2)
        )

        if (error != null) {
            Text(
                text = error,
                color = KalynoDanger,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KalynoDangerSoft)
                    .padding(14.dp)
            )
        }

        // ----- Contenu -----
        when {
            loading && exercises.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    ShimmerBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp),
                        shape = RoundedCornerShape(22.dp)
                    )
                }
            }

            visible.isEmpty() -> EmptyLab(
                noExercises = exercises.isEmpty(),
                filter = filter,
                loading = loading,
                onRefresh = onRefresh
            )

            else -> GroupedExerciseList(
                groups = catalog.group(exercises, visible, isCompleted),
                filter = filter,
                numberOf = { exercises.indexOf(it) + 1 },
                isCompleted = isCompleted,
                onOpen = onOpen
            )
        }
    }
}

/** Sélecteur à trois segments : la pastille Vert Pétrole glisse d'un choix à l'autre. */
@Composable
private fun SegmentedFilter(
    selected: String,
    counts: List<Int>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val options = listOf("all" to "Tout", "todo" to "À faire", "done" to "Terminés")
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val shape = RoundedCornerShape(16.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, KalynoOutline, shape)
    ) {
        val segment = maxWidth / options.size
        val thumbX by animateDpAsState(
            targetValue = segment * index,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "segmentThumb"
        )
        Box(
            modifier = Modifier
                .offset(x = thumbX)
                .width(segment)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KalynoBlue)
        )
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                val active = i == index
                val textColor by animateColorAsState(
                    targetValue = if (active) Color.White else KalynoMuted,
                    animationSpec = tween(180),
                    label = "segmentText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            haptic()
                            onSelect(option.first)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${option.second} · ${counts.getOrElse(i) { 0 }}",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Un défi présenté comme un ticket : numéro, type, titre, aperçu de l'énoncé, difficulté. */
@Composable
internal fun ExerciseCard(
    number: Int,
    exercise: JSONObject,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kind = kindInfo(exercise.optString("kind"))
    val level = difficultyLevel(exercise.optString("difficulty"))
    val accent = if (done) KalynoCorrect else KalynoBlue
    val title = exercise.optString("title").ifBlank { "Défi $number" }
    val statement = exercise.optString("statement")
    val longStatement = statement.length > 110

    KalynoCard(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        contentPadding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                if (done) listOf(KalynoCorrect, KalynoCorrectText)
                                else listOf(KalynoBlue, KalynoTealDark)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    KalynoSvgIcon(
                        name = if (done) "check" else kind.icon,
                        size = 24.dp,
                        tint = Color.White
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "N°" + number.toString().padStart(2, '0'),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = kind.label,
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    KalynoPill(
                        text = if (done) "Terminé" else "À faire",
                        container = if (done) KalynoCorrectSoft else KalynoAmberSoft,
                        content = if (done) KalynoCorrectText else KalynoAmberDeep,
                        fontSize = 11
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = title,
                    color = KalynoText,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 58.dp)
                        .clipToBounds()
                ) {
                    RichText(
                        html = statement,
                        color = KalynoMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    if (longStatement) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(22.dp)
                                .background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.White))
                                )
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DifficultyDots(level)
                        if (level > 0) {
                            Text(
                                text = difficultyLabel(level),
                                color = KalynoMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (done) "Revoir" else "Relever le défi",
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.width(4.dp))
                        KalynoSvgIcon(name = "chevron-right", size = 14.dp, tint = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLab(
    noExercises: Boolean,
    filter: String,
    loading: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AiOrb(diameter = 110.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (noExercises) "Ton labo se prépare" else "Tout est à jour !",
            color = KalynoText,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                noExercises -> "Tes exercices apparaîtront ici dès qu'ils seront disponibles."
                filter == "todo" -> "Tu as terminé tous les exercices disponibles. Bravo !"
                else -> "Aucun exercice dans cette catégorie pour le moment."
            },
            color = KalynoMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (noExercises) {
            Spacer(Modifier.height(16.dp))
            KalynoPrimaryButton(
                label = "ACTUALISER",
                onClick = onRefresh,
                enabled = !loading,
                height = 48.dp
            )
        }
    }
}

// ===========================================================================
// RÉSOLUTION D'UN DÉFI + BILAN
// ===========================================================================

@Composable
internal fun ExerciseSolveContent(
    exercise: JSONObject,
    position: Int,
    total: Int,
    result: JSONObject?,
    loading: Boolean,
    error: String?,
    selectedChoices: Set<Int>,
    onToggleChoice: (Int) -> Unit,
    tableAnswers: Map<String, String>,
    onTableAnswer: (String, String) -> Unit,
    freeAnswer: String,
    onFreeAnswer: (String) -> Unit,
    reasoningAnswer: String,
    onReasoningAnswer: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onNext: (() -> Unit)?
) {
    // Repart de zéro (défilement compris) à chaque nouveau défi
    key(exercise.optString("id")) {
        val kindRaw = exercise.optString("kind")
        val kind = kindInfo(kindRaw)
        val level = difficultyLevel(exercise.optString("difficulty"))
        val payload = exercise.optJSONObject("payload") ?: JSONObject()
        val isQcm = kindRaw == "qcm" || kindRaw == "multiple_choice"
        val scroll = rememberScrollState()
        val haptic = rememberHaptic()

        // La correction arrive en haut de page
        LaunchedEffect(result) { scroll.animateScrollTo(0) }

        val verdict = result?.optString("verdict")?.lowercase().orEmpty()
        val celebrate = result != null && verdict in setOf("correct", "success", "passed")

        // Petite vibration de victoire quand la réponse est juste
        LaunchedEffect(celebrate) { if (celebrate) haptic() }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(
                        start = 18.dp,
                        end = 18.dp,
                        top = 14.dp,
                        bottom = if (result == null) 150.dp else 28.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ----- Barre du haut : retour, progression, difficulté -----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .bouncyClick(shape = CircleShape) {
                                haptic()
                                onBack()
                            }
                            .background(Color.White)
                            .border(1.dp, KalynoOutline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        KalynoSvgIcon(
                            name = "arrow-left",
                            size = 22.dp,
                            tint = KalynoText,
                            contentDescription = "Retour à mes exercices"
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Défi $position",
                                color = KalynoText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = " sur $total",
                                color = KalynoMuted,
                                fontSize = 13.sp
                            )
                        }
                        KalynoProgressBar(
                            progress = position.toFloat() / total.toFloat(),
                            height = 6.dp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(KalynoAmberSoft)
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        DifficultyDots(level)
                        Text(
                            text = difficultyLabel(level).uppercase(),
                            color = KalynoAmberDeep,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp
                        )
                    }
                }

                // ----- Titre du défi -----
                KalynoAurora(modifier = Modifier.fillMaxWidth().entrance(0)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                KalynoSvgIcon(name = kind.icon, size = 18.dp, tint = Color.White)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "DÉFI · ${kind.label}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                        }
                        RichText(
                            html = exercise.optString("title"),
                            color = Color.White,
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // ----- Énoncé -----
                KalynoCard(
                    modifier = Modifier.entrance(1),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = 16.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KalynoPill(text = "ÉNONCÉ", fontSize = 11)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Lis attentivement", color = KalynoMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    RichText(
                        html = exercise.optString("statement"),
                        color = KalynoText,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }

                if (result == null) {
                    // ----- Zone de réponse -----
                    KalynoCard(
                        modifier = Modifier.entrance(2),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = 16.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TA MISSION",
                                    color = KalynoBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = kind.mission,
                                    color = KalynoText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            KalynoPill(text = kind.badge, fontSize = 11)
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(KalynoOutline)
                        )
                        Spacer(Modifier.height(14.dp))

                        when {
                            isQcm -> QcmAnswer(
                                choices = payload.optJSONArray("choices") ?: JSONArray(),
                                selected = selectedChoices,
                                onToggle = onToggleChoice
                            )

                            kindRaw == "table" -> TableAnswer(
                                payload = payload,
                                answers = tableAnswers,
                                onAnswer = onTableAnswer
                            )

                            else -> FreeTextAnswer(
                                reasoning = reasoningAnswer,
                                onReasoning = onReasoningAnswer,
                                answer = freeAnswer,
                                onAnswer = onFreeAnswer
                            )
                        }
                    }
                } else {
                    ExerciseResultBlocks(
                        correction = result,
                        verdict = verdict,
                        onBack = onBack,
                        onNext = onNext
                    )
                }
            }

            if (celebrate) ConfettiBurst()

            if (result == null) {
                SolveFooter(
                    loading = loading,
                    error = error,
                    onSubmit = onSubmit,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Réponses
// ---------------------------------------------------------------------------

@Composable
private fun QcmAnswer(
    choices: JSONArray,
    selected: Set<Int>,
    onToggle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sélectionne la ou les propositions correctes.",
                color = KalynoMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${selected.size} choisie(s)",
                color = KalynoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        for (i in 0 until choices.length()) {
            ChoiceOption(
                letter = "ABCDEFGH"[i % 8].toString(),
                html = choices.optString(i),
                checked = i in selected,
                onToggle = { onToggle(i) }
            )
        }

        if (choices.length() == 0) {
            Text(
                text = "Aucune proposition disponible pour cet exercice.",
                color = KalynoDanger,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ChoiceOption(
    letter: String,
    html: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val haptic = rememberHaptic()
    val shape = RoundedCornerShape(16.dp)
    val container by animateColorAsState(
        targetValue = if (checked) KalynoBlueSoft else Color.White,
        animationSpec = tween(160),
        label = "choiceContainer"
    )
    val border by animateColorAsState(
        targetValue = if (checked) KalynoBlue else KalynoOutline,
        animationSpec = tween(160),
        label = "choiceBorder"
    )
    val badge by animateColorAsState(
        targetValue = if (checked) KalynoBlue else KalynoBlueSoft,
        animationSpec = tween(160),
        label = "choiceBadge"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(shape = shape) {
                haptic()
                onToggle()
            }
            .background(container)
            .border(if (checked) 1.5.dp else 1.dp, border, shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(badge),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                KalynoSvgIcon(name = "check", size = 18.dp, tint = Color.White)
            } else {
                Text(
                    text = letter,
                    color = KalynoBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            RichText(
                html = html,
                color = KalynoText,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun TableAnswer(
    payload: JSONObject,
    answers: Map<String, String>,
    onAnswer: (String, String) -> Unit
) {
    val front = payload.optJSONObject("table_front") ?: JSONObject()
    val rows = front.optJSONArray("rows") ?: JSONArray()
    val blankKeys = blankKeysOf(rows)
    val filled = blankKeys.count { !answers[it].isNullOrBlank() }
    val tableProgress = if (blankKeys.isEmpty()) 0f else filled.toFloat() / blankKeys.size.toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Renseigne les cellules manquantes pour compléter le tableau.",
            color = KalynoMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(KalynoBlueSoft)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PROGRESSION DU TABLEAU",
                    color = KalynoMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "$filled / ${blankKeys.size}",
                    color = KalynoBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            KalynoProgressBar(progress = tableProgress, height = 7.dp, track = Color.White)
        }

        for (r in 0 until rows.length()) {
            val row = rows.optJSONArray(r) ?: JSONArray()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (c in 0 until row.length()) {
                    val cell = row.optJSONObject(c) ?: JSONObject()
                    if (cell.optString("type") == "blank") {
                        val key = cell.optString("key")
                        OutlinedTextField(
                            value = answers[key] ?: "",
                            onValueChange = { onAnswer(key, it) },
                            label = { Text("Réponse") },
                            placeholder = { Text("À compléter") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = kalynoFieldColors()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, KalynoOutline, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            RichText(
                                html = cell.optString("value"),
                                color = KalynoText,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }

        if (rows.length() == 0) {
            Text(
                text = "Le tableau ne contient aucune ligne.",
                color = KalynoDanger,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun FreeTextAnswer(
    reasoning: String,
    onReasoning: (String) -> Unit,
    answer: String,
    onAnswer: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepField(
            number = "1",
            title = "TON RAISONNEMENT",
            hint = "Explique les étapes de ta réflexion. Cette partie aide à comprendre ta méthode.",
            placeholder = "Comment as-tu procédé ?",
            value = reasoning,
            minLines = 3,
            onValueChange = onReasoning
        )
        StepField(
            number = "2",
            title = "TA RÉPONSE FINALE",
            hint = "Présente clairement le résultat auquel tu es arrivé.",
            placeholder = "Écris ta réponse ici…",
            value = answer,
            minLines = 5,
            onValueChange = onAnswer
        )
    }
}

@Composable
private fun StepField(
    number: String,
    title: String,
    hint: String,
    placeholder: String,
    value: String,
    minLines: Int,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(KalynoBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = KalynoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            )
        }
        Text(text = hint, color = KalynoMuted, fontSize = 13.sp, lineHeight = 18.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            placeholder = { Text(placeholder) },
            shape = RoundedCornerShape(16.dp),
            colors = kalynoFieldColors()
        )
    }
}

// ---------------------------------------------------------------------------
// Pied de page fixe : le bouton reste sous le pouce
// ---------------------------------------------------------------------------

@Composable
private fun SolveFooter(
    loading: Boolean,
    error: String?,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(KalynoOutline)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (error != null) {
                Text(
                    text = error,
                    color = KalynoDanger,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KalynoDangerSoft)
                        .padding(12.dp)
                )
            }
            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThinkingDots()
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "L'IA analyse ta réponse…",
                        color = KalynoMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            KalynoPrimaryButton(
                label = if (loading) "ANALYSE EN COURS…" else "VÉRIFIER MA RÉPONSE",
                onClick = onSubmit,
                enabled = !loading,
                height = 54.dp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bilan
// ---------------------------------------------------------------------------

@Composable
private fun ColumnScope.ExerciseResultBlocks(
    correction: JSONObject,
    verdict: String,
    onBack: () -> Unit,
    onNext: (() -> Unit)?
) {
    val haptic = rememberHaptic()
    val score = correction.optInt("score").coerceIn(0, 100)
    val details = correction.optJSONObject("correction")
    val analysis = details?.optJSONObject("reasoning_analysis")

    val correct = verdict in setOf("correct", "success", "passed")
    val partial = verdict in setOf("partial", "partially_correct")
    val tones = when {
        correct -> KalynoCorrect to KalynoCorrectText
        partial -> KalynoRetry to KalynoAmberDeep
        else -> KalynoPurple to KalynoPurple.copy(alpha = 0.8f)
    }
    val title = when {
        correct -> "Excellent travail !"
        partial -> "Tu progresses, continue !"
        else -> "On apprend de chaque essai."
    }
    val subtitle = when {
        correct -> "Ta réponse est validée."
        partial -> "Tu y es presque : lis l'analyse ci-dessous."
        else -> "Regarde la correction pour progresser."
    }

    // ----- Verdict -----
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .entrance(0)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(tones.first, tones.second)))
    ) {
        DriftingBlob(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .size(150.dp),
            color = Color.White.copy(alpha = 0.10f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                KalynoPill(
                    text = "BILAN DE TON DÉFI",
                    container = Color.White.copy(alpha = 0.2f),
                    content = Color.White,
                    fontSize = 11
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            val shown = rememberCountUp(score, durationMillis = 1100)
            KalynoRing(
                progress = score / 100f,
                diameter = 92.dp,
                strokeWidth = 9.dp,
                color = Color.White,
                track = Color.White.copy(alpha = 0.25f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$shown",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "/100",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // ----- Retour de l'IA -----
    val feedback = correction.optString("feedback")
    if (feedback.isNotBlank()) {
        KalynoCard(
            modifier = Modifier.entrance(1),
            shape = RoundedCornerShape(22.dp),
            contentPadding = 16.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(KalynoBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    KalynoSvgIcon(name = "sparkles", size = 18.dp, tint = KalynoBlue)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "LE RETOUR DE L'IA",
                    color = KalynoBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            RichText(
                html = feedback,
                color = KalynoText,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        }
    }

    // ----- Analyse du raisonnement -----
    if (analysis != null) {
        val summary = analysis.optString("summary")
        val steps = analysis.optJSONArray("steps")
        val strengths = analysis.optJSONArray("strengths")
        val errors = analysis.optJSONArray("errors")
        val advice = analysis.optString("advice")

        if (summary.isNotBlank()) {
            InfoBox(
                title = "LECTURE DE TON RAISONNEMENT",
                icon = "brain",
                container = KalynoBlueSoft,
                accent = KalynoBlue,
                modifier = Modifier.entrance(2)
            ) {
                Text(text = summary, color = KalynoText, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }

        if (steps != null && steps.length() > 0) {
            InfoBox(
                title = "ÉTAPES DE TON RAISONNEMENT",
                icon = "layers",
                container = Color.White,
                accent = KalynoBlue,
                bordered = true,
                modifier = Modifier.entrance(3)
            ) {
                for (i in 0 until steps.length()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(KalynoBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${i + 1}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = steps.optString(i),
                            color = KalynoText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        if (strengths != null && strengths.length() > 0) {
            InfoBox(
                title = "POINTS FORTS",
                icon = "check-circle",
                container = KalynoCorrectSoft,
                accent = KalynoCorrectText,
                modifier = Modifier.entrance(4)
            ) {
                for (i in 0 until strengths.length()) {
                    BulletLine(
                        icon = "check",
                        tint = KalynoCorrect,
                        text = strengths.optString(i),
                        textColor = KalynoCorrectText
                    )
                }
            }
        }

        if (errors != null && errors.length() > 0) {
            InfoBox(
                title = "POINTS À CORRIGER",
                icon = "target",
                container = KalynoDangerSoft,
                accent = KalynoDanger,
                modifier = Modifier.entrance(5)
            ) {
                for (i in 0 until errors.length()) {
                    BulletLine(
                        icon = "x",
                        tint = KalynoDanger,
                        text = errors.optString(i),
                        textColor = KalynoDanger
                    )
                }
            }
        }

        if (advice.isNotBlank()) {
            InfoBox(
                title = "PROCHAINE ÉTAPE",
                icon = "rocket",
                container = KalynoAmberSoft,
                accent = KalynoAmberDeep,
                modifier = Modifier.entrance(6)
            ) {
                Text(text = advice, color = KalynoText, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }

    // ----- À retenir -----
    val explanation = details?.optString("explanation").orEmpty()
    if (explanation.isNotBlank()) {
        InfoBox(
            title = "À RETENIR",
            icon = "lightbulb",
            container = KalynoBlueSoft,
            accent = KalynoBlue,
            modifier = Modifier.entrance(7)
        ) {
            RichText(
                html = explanation,
                color = KalynoText,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }

    // ----- Suite -----
    Column(
        modifier = Modifier.entrance(8),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onNext != null) {
            KalynoPrimaryButton(label = "DÉFI SUIVANT", onClick = onNext)
            Text(
                text = "Retour à mes exercices",
                color = KalynoBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClick(shape = RoundedCornerShape(16.dp)) {
                        haptic()
                        onBack()
                    }
                    .padding(vertical = 14.dp)
            )
        } else {
            KalynoPrimaryButton(label = "RETOUR À MES EXERCICES", onClick = onBack)
        }
    }
}

@Composable
private fun InfoBox(
    title: String,
    icon: String,
    container: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    bordered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(if (bordered) Modifier.border(1.dp, KalynoOutline, shape) else Modifier)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KalynoSvgIcon(name = icon, size = 16.dp, tint = accent)
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            )
        }
        content()
    }
}

@Composable
private fun BulletLine(icon: String, tint: Color, text: String, textColor: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = 3.dp)) {
            KalynoSvgIcon(name = icon, size = 14.dp, tint = tint)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
