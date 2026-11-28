package ci.kalyno.app.ui.explorer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.DriftingBlob
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.components.KalynoRing
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.components.entrance
import ci.kalyno.app.ui.components.rememberCountUp
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.components.rememberPulse
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.icons.iconAssetFor
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoLightBlue
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoShadow
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow
import kotlin.math.roundToInt

// ===========================================================================
// Explorer v3 : blocs d'interface de l'accueil Explorer.
// Palette inchangée (Deep Teal #0F766E, fond #F8FAFC, ambre en accent).
// Aucune donnée inventée : tout vient de l'état réel (progression, FSRS, série).
// ===========================================================================


/** Une leçon est terminée quand toutes ses cartes ont été découvertes. */
internal fun ExplorerChapter.isFinished(): Boolean = questionCount > 0 && discovered >= questionCount

private fun plural(count: Int, one: String, many: String): String =
    if (count == 1) "1 $one" else "$count $many"

// ---------------------------------------------------------------------------
// En-tête
// ---------------------------------------------------------------------------

@Composable
internal fun ExplorerTopBar(onOpenLeaderboard: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Explorer",
                color = KalynoText,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Chaque jour, un pas de plus vers ton examen",
                color = KalynoMuted,
                fontSize = 13.sp
            )
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, KalynoOutline, CircleShape)
                .clickable {
                    haptic()
                    onOpenLeaderboard()
                },
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = "trophy", size = 22.dp, tint = KalynoAmberDeep)
        }
    }
}

// ---------------------------------------------------------------------------
// Mission du jour : la raison de revenir chaque jour
// ---------------------------------------------------------------------------

/**
 * Grande carte dégradée Deep Teal.
 * - cartes dues (FSRS) > 0 : « N à réviser » + bouton de révision immédiate ;
 * - sinon : reprise de la leçon en cours ;
 * - sinon : « Tout est à jour ».
 * L'anneau montre la progression globale réelle ; la flamme respire.
 */
@Composable
internal fun DailyMissionHero(
    streak: Int,
    progress: Float,
    dueCount: Int,
    topPick: ForYouPick?,
    continueChapter: ExplorerChapter?,
    onReview: (ForYouPick) -> Unit,
    onContinue: (ExplorerChapter) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    val percent = rememberCountUp((progress.coerceIn(0f, 1f) * 100f).roundToInt())
    val flame = rememberPulse(min = 1f, max = 1.18f, durationMillis = 800)

    val headline: String
    val sub: String
    val action: String?
    val onAction: (() -> Unit)?
    when {
        dueCount > 0 && topPick != null -> {
            headline = plural(dueCount, "carte à réviser", "cartes à réviser")
            sub = "Quelques minutes maintenant, et ça reste gravé pour de bon."
            action = "RÉVISER MAINTENANT"
            onAction = { onReview(topPick) }
        }
        continueChapter != null -> {
            headline = "Reprends où tu t'es arrêté"
            sub = continueChapter.title
            action = "CONTINUER"
            onAction = { onContinue(continueChapter) }
        }
        else -> {
            headline = if (progress <= 0f) "Prêt à démarrer ?" else "Tout est à jour"
            sub = "Choisis une matière plus bas et lance un nouveau parcours."
            action = null
            onAction = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(6.dp, shape, ambientColor = KalynoShadow, spotColor = KalynoShadow)
            .clip(shape)
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
    ) {
        DriftingBlob(
            modifier = Modifier
                .size(170.dp)
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = (-50).dp),
            color = Color.White.copy(alpha = 0.08f)
        )
        DriftingBlob(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-34).dp, y = 34.dp),
            color = KalynoLightBlue.copy(alpha = 0.20f),
            durationMillis = 6400
        )

        Column(modifier = Modifier.padding(20.dp)) {
            // Série du jour
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = flame
                        scaleY = flame
                    }
                ) {
                    KalynoSvgIcon(name = "flame", size = 16.dp, tint = KalynoYellow)
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (streak > 0) plural(streak, "jour d'affilée", "jours d'affilée")
                    else "Lance ta série aujourd'hui",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MISSION DU JOUR",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = headline,
                        color = Color.White,
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sub,
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(14.dp))
                KalynoRing(
                    progress = progress,
                    diameter = 88.dp,
                    strokeWidth = 8.dp,
                    color = Color.White,
                    track = Color.White.copy(alpha = 0.22f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "maîtrisé",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (action != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                val haptic = rememberHaptic()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .clickable {
                            haptic()
                            onAction()
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = action,
                        color = KalynoBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    KalynoSvgIcon(name = "chevron-right", size = 16.dp, tint = KalynoBlue)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Titre de section
// ---------------------------------------------------------------------------

@Composable
internal fun ExplorerSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (subtitle != null) 34.dp else 22.dp)
                .clip(CircleShape)
                .background(KalynoBlue)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KalynoText,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (subtitle != null) {
                Text(text = subtitle, color = KalynoMuted, fontSize = 13.sp)
            }
        }
        if (trailing != null) {
            KalynoPill(text = trailing, fontSize = 11)
        }
    }
}

// ---------------------------------------------------------------------------
// Disciplines : liste verticale, une carte par matière
// ---------------------------------------------------------------------------

@Composable
internal fun DisciplineList(
    disciplines: List<Discipline>,
    progressById: Map<String, Float>,
    pressedId: String?,
    loading: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!loading && disciplines.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExplorerSectionTitle(
            title = "Disciplines",
            subtitle = "Touche une matière pour ouvrir son parcours",
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = if (loading) null else plural(disciplines.size, "matière", "matières")
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) {
                repeat(3) {
                    ShimmerBlock(
                        modifier = Modifier.fillMaxWidth().height(92.dp),
                        shape = RoundedCornerShape(22.dp)
                    )
                }
            } else {
                disciplines.forEachIndexed { index, discipline ->
                    DisciplineRow(
                        discipline = discipline,
                        progress = progressById[discipline.id] ?: 0f,
                        selected = discipline.id == pressedId,
                        onClick = { onClick(discipline.id) },
                        modifier = Modifier.entrance(index = index, stepMillis = 50)
                    )
                }
            }
        }
    }
}

@Composable
private fun DisciplineRow(
    discipline: Discipline,
    progress: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val container by animateColorAsState(if (selected) KalynoBlue else Color.White, tween(120), label = "rowBg")
    val border by animateColorAsState(if (selected) KalynoBlue else KalynoOutline, tween(120), label = "rowBorder")
    val title by animateColorAsState(if (selected) Color.White else KalynoText, tween(120), label = "rowTitle")
    val meta by animateColorAsState(
        if (selected) Color.White.copy(alpha = 0.85f) else KalynoMuted,
        tween(120),
        label = "rowMeta"
    )
    val iconBg by animateColorAsState(
        if (selected) Color.White.copy(alpha = 0.2f) else KalynoBlueSoft,
        tween(120),
        label = "rowIconBg"
    )
    val iconTint by animateColorAsState(if (selected) Color.White else KalynoBlue, tween(120), label = "rowIcon")
    val percent = rememberCountUp((progress.coerceIn(0f, 1f) * 100).roundToInt(), durationMillis = 700)

    KalynoCard(
        modifier = modifier.fillMaxWidth(),
        onClick = {
            haptic()
            onClick()
        },
        shape = RoundedCornerShape(22.dp),
        background = container,
        borderColor = border,
        contentPadding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = iconAssetFor(discipline.iconKey), size = 26.dp, tint = iconTint)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = discipline.name,
                    color = title,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${plural(discipline.lessonCount, "leçon", "leçons")} · $percent%",
                    color = meta,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.height(8.dp))
                KalynoProgressBar(
                    progress = progress,
                    height = 6.dp,
                    color = if (selected) Color.White else KalynoBlue,
                    track = if (selected) Color.White.copy(alpha = 0.25f) else KalynoBlueSoft
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "chevron-right", size = 16.dp, tint = iconTint)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Carte de leçon (résultats de recherche)
// ---------------------------------------------------------------------------

/** Carte de défi : anneau de progression, méta, et une action claire « S'entraîner ». */
@Composable
internal fun ChallengeCard(
    chapter: ExplorerChapter,
    onStart: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val canStart = chapter.questionCount > 0

    KalynoCard(
        modifier = modifier,
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KalynoRing(progress = chapter.progress, diameter = 52.dp, strokeWidth = 5.dp) {
                KalynoSvgIcon(name = iconAssetFor(chapter.subjectIconKey), size = 21.dp, tint = KalynoBlue)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = chapter.subjectName,
                        color = KalynoBlue,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    SmartTagPill(chapter.tag)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = chapter.title,
                    color = KalynoText,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                KalynoSvgIcon(name = "layers", size = 13.dp, tint = KalynoMuted)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = plural(chapter.questionCount, "question", "questions"),
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.width(10.dp))
                KalynoSvgIcon(name = "clock", size = 13.dp, tint = KalynoMuted)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${chapter.minutes} min",
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (canStart) KalynoBlue else KalynoOutline)
                    .clickable(enabled = canStart) {
                        haptic()
                        onStart()
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KalynoSvgIcon(name = "target", size = 14.dp, tint = if (canStart) Color.White else KalynoMuted)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "S'entraîner",
                    color = if (canStart) Color.White else KalynoMuted,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Résultats de recherche
// ---------------------------------------------------------------------------

@Composable
internal fun SearchResultsBlock(
    chapters: List<ExplorerChapter>,
    onStart: (ExplorerChapter) -> Unit,
    onOpen: (ExplorerChapter) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExplorerSectionTitle(
            title = "Résultats",
            trailing = plural(chapters.size, "leçon", "leçons")
        )
        chapters.forEachIndexed { index, chapter ->
            ChallengeCard(
                chapter = chapter,
                onStart = { onStart(chapter) },
                onOpen = { onOpen(chapter) },
                modifier = Modifier.fillMaxWidth().entrance(index = index, stepMillis = 50)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Squelette de chargement
// ---------------------------------------------------------------------------

@Composable
internal fun ExplorerSkeleton() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ShimmerBlock(Modifier.width(170.dp).height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                ShimmerBlock(
                    modifier = Modifier.weight(1f).height(170.dp),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
        ShimmerBlock(Modifier.width(140.dp).height(22.dp))
        ShimmerBlock(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(24.dp)
        )
    }
}
