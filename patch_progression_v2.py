#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Patch KALYNO : « Ma progression » v2.

- Fiches apprises : vraies cartes à retourner (recto question, verso réponse) dans un carrousel.
- Cartes maîtrisées : nouveau design (liseré de niveau, pastilles de répétitions, anneau de rétention).
- Historique du Dojo : « Ta semaine au Dojo » (histogramme 7 jours), fil du temps, points de résultat par jour.
- Aucune couleur ajoutée ; aucune modification de MainScreen.

Fichiers modifiés (sauvegarde .bak de chacun) :
  ui/progress/ProgressHistoryScreen.kt      (remplacé)
  ui/progress/MasteredCardsUi.kt            (remplacé)
  ui/progress/ProgressHistoryViewModel.kt   (remplacé)
  data/progress/DojoAttempts.kt             (2 requêtes + 1 classe ajoutées)

Usage, depuis la racine du dépôt KALYNO :
    python3 patch_progression_v2.py
"""
import os
import shutil
import sys

BASE = os.path.join("app", "src", "main", "java", "ci", "kalyno", "app")
SCREEN = os.path.join(BASE, "ui", "progress", "ProgressHistoryScreen.kt")
MASTERED = os.path.join(BASE, "ui", "progress", "MasteredCardsUi.kt")
VIEWMODEL = os.path.join(BASE, "ui", "progress", "ProgressHistoryViewModel.kt")
DAO = os.path.join(BASE, "data", "progress", "DojoAttempts.kt")

SCREEN_CONTENT = r'''package ci.kalyno.app.ui.progress

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ci.kalyno.app.data.dashboard.MasteredCard
import ci.kalyno.app.data.dashboard.MasteryTier
import ci.kalyno.app.ui.components.DriftingBlob
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoPill
import ci.kalyno.app.ui.components.KalynoRing
import ci.kalyno.app.ui.components.MathText
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.components.entrance
import ci.kalyno.app.ui.components.rememberCountUp
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.components.rememberPulse
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrect
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoDanger
import ci.kalyno.app.ui.theme.KalynoDangerSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoRetryText
import ci.kalyno.app.ui.theme.KalynoShadow
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow

private val SideMargin = 16.dp
private const val MASTERED_PREVIEW = 3

/**
 * « Ma progression » : héros de rétention, bilan de réussite, niveaux de maîtrise, cartes maîtrisées,
 * puis chronologie de toutes les tentatives du Dojo.
 * Tout vient de Room (hors-ligne) ; [retentionScore] est la rétention FSRS déjà calculée par le tableau de bord,
 * pour que le chiffre soit identique à celui de la carte.
 */
@Composable
fun ProgressHistoryScreen(
    retentionScore: Float?,
    onBack: () -> Unit,
    masteredCount: Int? = null,
    masteredCards: List<MasteredCard> = emptyList(),
    viewModel: ProgressHistoryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showAllMastered by rememberSaveable { mutableStateOf(false) }

    // Chargement progressif : on demande la page suivante quand la fin de la liste approche
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.canLoadMore, state.rows.size) {
        if (nearEnd && state.canLoadMore) viewModel.loadMore()
    }

    val summary = state.summary
    val side = Modifier.padding(horizontal = SideMargin)
    val timeline = remember(state.rows, state.canLoadMore) { buildTimeline(state.rows, state.canLoadMore) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ----- 1. Héros : anneau de rétention + trois chiffres clés -----
        item(key = "hero") {
            ProgressHero(
                retentionScore = retentionScore,
                learnedCards = summary?.learnedCards,
                masteredCount = masteredCount,
                masteredCardsCount = masteredCards.size,
                onBack = onBack
            )
        }

        // ----- 2. Bilan de réussite -----
        if (summary != null && summary.total > 0) {
            item(key = "success") { SuccessCard(summary, side) }
        }

        // ----- 3. Niveaux de maîtrise -----
        if (masteredCards.isNotEmpty()) {
            item(key = "tiers") { TierCard(masteredCards, side) }
        }

        // ----- 4. Fiches apprises : de vraies cartes à retourner -----
        if (state.learned.isNotEmpty()) {
            item(key = "learned-header") {
                val total = summary?.learnedCards ?: state.learned.size
                SectionTitle(
                    iconName = "layers",
                    title = "Fiches apprises",
                    subtitle = if (total > state.learned.size) {
                        "Tes ${state.learned.size} plus récentes · touche une carte pour la retourner"
                    } else {
                        "Touche une carte pour la retourner et revoir la réponse"
                    },
                    count = total,
                    modifier = side
                )
            }
            item(key = "learned-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SideMargin, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.learned, key = { it.id }) { card -> FlipCard(card) }
                }
            }
        }

        // ----- 5. Cartes maîtrisées (aperçu, puis tout voir) -----
        item(key = "mastered-header") {
            Box(side) { MasteredCardsHeader(masteredCards.size) }
        }
        if (masteredCards.isEmpty()) {
            item(key = "mastered-empty") { Box(side) { MasteredCardsEmptyText() } }
        } else {
            val shown = if (showAllMastered) masteredCards else masteredCards.take(MASTERED_PREVIEW)
            items(shown, key = { "mastered-${it.cardId}" }) { card -> MasteredCardItem(card, side) }
            if (masteredCards.size > MASTERED_PREVIEW) {
                item(key = "mastered-toggle") {
                    ToggleLink(
                        label = if (showAllMastered) "Réduire la liste" else "Voir les ${masteredCards.size} cartes",
                        expanded = showAllMastered,
                        onClick = { showAllMastered = !showAllMastered },
                        modifier = side
                    )
                }
            }
        }

        // ----- 6. Historique du Dojo -----
        if (state.neverPlayed) {
            item(key = "empty") { EmptyState(side) }
        } else {
            item(key = "week") { WeekCard(state.week, side) }
            item(key = "history-title") {
                SectionTitle(
                    iconName = "history",
                    title = "Historique du Dojo",
                    subtitle = "Chaque réponse, de la plus récente à la plus ancienne",
                    count = null,
                    modifier = side.padding(top = 6.dp)
                )
            }
            item(key = "filters") {
                FilterBar(state, viewModel::selectStatus, viewModel::selectSubject)
            }
            if (!state.loading && state.rows.isEmpty()) {
                item(key = "no-result") {
                    Text(
                        text = "Aucune tentative pour ce filtre.",
                        color = KalynoMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
            }
            items(timeline, key = { it.row.key }, contentType = { it.row::class }) { entry ->
                when (val row = entry.row) {
                    is HistoryRow.Day -> DayHeader(row.label, entry.dots)
                    is HistoryRow.Attempt -> TimelineAttempt(row.item, entry.lineAbove, entry.lineBelow)
                }
            }
            if (state.canLoadMore) {
                item(key = "more") {
                    ShimmerBlock(side.fillMaxWidth().height(64.dp), RoundedCornerShape(24.dp))
                }
            }
        }
    }
}

// ===========================================================================
// HÉROS
// ===========================================================================

private fun retentionVerdict(percent: Int?): String = when {
    percent == null -> "Pas encore calculée"
    percent >= 85 -> "Mémoire en béton"
    percent >= 70 -> "Très solide"
    percent >= 50 -> "Ça se construit"
    else -> "À consolider"
}

@Composable
private fun ProgressHero(
    retentionScore: Float?,
    learnedCards: Int?,
    masteredCount: Int?,
    masteredCardsCount: Int,
    onBack: () -> Unit
) {
    val haptic = rememberHaptic()
    val retention = retentionScore?.coerceIn(0f, 1f)
    val percent = retention?.let { (it * 100).toInt() }
    val shownPercent = rememberCountUp(percent ?: 0, durationMillis = 1100)
    val shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
    ) {
        DriftingBlob(
            modifier = Modifier
                .size(210.dp)
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-60).dp),
            color = Color.White.copy(alpha = 0.08f)
        )
        DriftingBlob(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-50).dp, y = 40.dp),
            color = Color.White.copy(alpha = 0.06f),
            durationMillis = 6400
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Barre du haut : retour + titre
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable {
                            haptic()
                            onBack()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    KalynoSvgIcon(
                        name = "arrow-left",
                        size = 22.dp,
                        tint = Color.White,
                        contentDescription = "Retour"
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Ma progression",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(18.dp))

            // Anneau de rétention : se remplit et le pourcentage monte en même temps
            KalynoRing(
                progress = retention ?: 0f,
                modifier = Modifier.entrance(1),
                diameter = 176.dp,
                strokeWidth = 14.dp,
                color = Color.White,
                track = Color.White.copy(alpha = 0.2f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (percent == null) "—" else shownPercent.toString(),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (percent != null) {
                            Text(
                                text = "%",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                            )
                        }
                    }
                    Text(
                        text = "de rétention",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .entrance(2)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                KalynoSvgIcon(name = "sparkles", size = 14.dp, tint = KalynoYellow)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = retentionVerdict(percent),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ce que tu retiens encore, estimé par ton moteur de révision.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassStat(
                    iconName = "layers",
                    value = learnedCards,
                    label = "Fiches apprises",
                    modifier = Modifier.weight(1f).entrance(3)
                )
                GlassStat(
                    iconName = "graduation-cap",
                    value = masteredCount,
                    label = if (masteredCount == 1) "Notion maîtrisée" else "Notions maîtrisées",
                    modifier = Modifier.weight(1f).entrance(4)
                )
                GlassStat(
                    iconName = "trophy",
                    value = masteredCardsCount,
                    label = if (masteredCardsCount == 1) "Carte maîtrisée" else "Cartes maîtrisées",
                    modifier = Modifier.weight(1f).entrance(5),
                    tint = KalynoYellow
                )
            }
        }
    }
}

/** Tuile « verre dépoli » : icône, valeur qui monte de 0 à la valeur réelle, libellé sur deux lignes. */
@Composable
private fun GlassStat(
    iconName: String,
    value: Int?,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    val shown = rememberCountUp(value ?: 0)
    val tileShape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(tileShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), tileShape)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        KalynoSvgIcon(name = iconName, size = 22.dp, tint = tint)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (value == null) "–" else shown.toString(),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ===========================================================================
// BILAN DE RÉUSSITE
// ===========================================================================

@Composable
private fun SuccessCard(summary: ProgressSummaryUi, modifier: Modifier = Modifier) {
    val rate = (summary.successRate ?: 0f).coerceIn(0f, 1f)
    val percent = (rate * 100).toInt()
    val shownPercent = rememberCountUp(percent)
    val shownSuccesses = rememberCountUp(summary.successes)
    val shownFailures = rememberCountUp(summary.failures)

    // La barre se remplit à l'ouverture, puis suit la valeur si une tentative s'ajoute
    val fill = remember { Animatable(0f) }
    LaunchedEffect(rate) {
        fill.animateTo(rate, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }

    val message = when {
        percent >= 80 -> "Excellent rythme, continue comme ça !"
        percent >= 60 -> "Belle progression, tu es sur la bonne voie."
        percent >= 40 -> "Chaque erreur est une occasion de mieux retenir."
        else -> "Pas de panique : les échecs d'aujourd'hui sont les réussites de demain."
    }

    KalynoCard(modifier = modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Réussite au Dojo",
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = shownPercent.toString(),
                        color = KalynoText,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "%",
                        color = KalynoMuted,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 2.dp, bottom = 7.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutcomePill(
                    count = shownSuccesses,
                    label = "Succès",
                    container = KalynoCorrectSoft,
                    content = KalynoCorrectText
                )
                OutcomePill(
                    count = shownFailures,
                    label = "Échecs",
                    container = KalynoDangerSoft,
                    content = KalynoDanger
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .background(if (summary.failures > 0) KalynoDangerSoft else KalynoCorrectSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill.value)
                    .clip(CircleShape)
                    .background(KalynoCorrect)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "${summary.total} " + if (summary.total == 1) "tentative au total" else "tentatives au total",
            color = KalynoMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(text = message, color = KalynoMuted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun OutcomePill(count: Int, label: String, container: Color, content: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = count.toString(),
            color = content,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(text = label, color = content, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ===========================================================================
// NIVEAUX DE MAÎTRISE
// ===========================================================================

private data class TierStyle(
    val icon: String,
    val label: String,
    val caption: String,
    val tint: Color,
    val soft: Color
)

private fun MasteryTier.style(): TierStyle = when (this) {
    MasteryTier.EXPERTE -> TierStyle("trophy", "Experte", "Ancrée durablement", KalynoAmberDeep, KalynoAmberSoft)
    MasteryTier.AVANCEE -> TierStyle("target", "Avancée", "En pleine consolidation", KalynoBlue, KalynoBlueSoft)
    MasteryTier.SOLIDE -> TierStyle("check-circle", "Solide", "Tout juste validée", KalynoCorrectText, KalynoCorrectSoft)
}

@Composable
private fun TierCard(cards: List<MasteredCard>, modifier: Modifier = Modifier) {
    val counts = remember(cards) { cards.groupingBy { it.tier }.eachCount() }
    val total = cards.size.coerceAtLeast(1)

    KalynoCard(modifier = modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Text(
            text = "Ton niveau de maîtrise",
            color = KalynoText,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Comment tes cartes maîtrisées se répartissent.",
            color = KalynoMuted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        listOf(MasteryTier.EXPERTE, MasteryTier.AVANCEE, MasteryTier.SOLIDE).forEachIndexed { index, tier ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            val count = counts[tier] ?: 0
            TierRow(style = tier.style(), count = count, share = count.toFloat() / total)
        }
    }
}

@Composable
private fun TierRow(style: TierStyle, count: Int, share: Float) {
    val animatedShare by animateFloatAsState(
        targetValue = share.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "tierShare"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(style.soft),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = style.icon, size = 20.dp, tint = style.tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = style.label,
                    color = KalynoText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = count.toString(),
                    color = style.tint,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(text = style.caption, color = KalynoMuted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(style.soft)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedShare)
                        .clip(CircleShape)
                        .background(style.tint)
                )
            }
        }
    }
}

@Composable
private fun ToggleLink(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "toggleChevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KalynoBlueSoft)
            .clickable {
                haptic()
                onClick()
            }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = KalynoBlue, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.rotate(rotation)) {
            KalynoSvgIcon(name = "chevron-down", size = 14.dp, tint = KalynoBlue)
        }
    }
}

// ===========================================================================
// FILTRES
// ===========================================================================

@Composable
private fun FilterBar(
    state: ProgressHistoryUiState,
    onStatus: (HistoryStatus) -> Unit,
    onSubject: (String?) -> Unit
) {
    val haptic = rememberHaptic()
    val summary = state.summary
    val segmentShape = RoundedCornerShape(16.dp)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Contrôle segmenté : Tout / Succès / Échecs, avec le nombre de chaque
        Row(
            modifier = Modifier
                .padding(horizontal = SideMargin)
                .fillMaxWidth()
                .clip(segmentShape)
                .background(Color.White)
                .border(1.dp, KalynoOutline, segmentShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HistoryStatus.entries.forEach { status ->
                val selected = state.filter.status == status
                val container by animateColorAsState(
                    targetValue = if (selected) KalynoBlue else Color.Transparent,
                    animationSpec = tween(160),
                    label = "segmentContainer"
                )
                val content by animateColorAsState(
                    targetValue = if (selected) Color.White else KalynoMuted,
                    animationSpec = tween(160),
                    label = "segmentContent"
                )
                val label = when (status) {
                    HistoryStatus.ALL -> "Tout" + (summary?.let { " · ${it.total}" } ?: "")
                    HistoryStatus.SUCCESS -> "Succès" + (summary?.let { " · ${it.successes}" } ?: "")
                    HistoryStatus.FAILURE -> "Échecs" + (summary?.let { " · ${it.failures}" } ?: "")
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(container)
                        .clickable {
                            haptic()
                            onStatus(status)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = content,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }

        if (state.subjects.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = SideMargin),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.subjects) { subject ->
                    HistoryChip(subject, selected = state.filter.subject == subject) { onSubject(subject) }
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val container by animateColorAsState(
        targetValue = if (selected) KalynoBlue else Color.White,
        animationSpec = tween(160),
        label = "chipContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) Color.White else KalynoText,
        animationSpec = tween(160),
        label = "chipContent"
    )
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(container)
            .border(1.dp, if (selected) KalynoBlue else KalynoOutline, shape)
            .clickable {
                haptic()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = content,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

// ===========================================================================
// CHRONOLOGIE
// ===========================================================================

/** Une ligne de la chronologie, avec ce qu'il faut savoir pour dessiner le fil qui relie les tentatives. */
private data class TimelineEntry(
    val row: HistoryRow,
    val lineAbove: Boolean,
    val lineBelow: Boolean,
    /** Résultat de chaque tentative du jour (vrai = succès) ; vide pour une tentative. */
    val dots: List<Boolean>
)

private fun buildTimeline(rows: List<HistoryRow>, moreBelow: Boolean): List<TimelineEntry> {
    val out = ArrayList<TimelineEntry>(rows.size)
    rows.forEachIndexed { index, row ->
        when (row) {
            is HistoryRow.Day -> {
                val dots = ArrayList<Boolean>()
                var j = index + 1
                while (j < rows.size) {
                    val next = rows[j]
                    if (next is HistoryRow.Attempt) {
                        dots += next.item.success
                        j++
                    } else {
                        break
                    }
                }
                out += TimelineEntry(row, lineAbove = false, lineBelow = false, dots = dots)
            }
            is HistoryRow.Attempt -> {
                val previousIsAttempt = rows.getOrNull(index - 1) is HistoryRow.Attempt
                val next = rows.getOrNull(index + 1)
                val nextIsAttempt = next is HistoryRow.Attempt
                out += TimelineEntry(
                    row = row,
                    lineAbove = previousIsAttempt,
                    // Le fil continue vers le bas tant qu'il reste des tentatives (même à charger)
                    lineBelow = nextIsAttempt || (next == null && moreBelow),
                    dots = emptyList()
                )
            }
        }
    }
    return out
}

private val TimelineNode = 28.dp
private val NodeCenterY = 22.dp
private const val MAX_DAY_DOTS = 10

@Composable
private fun DayHeader(label: String, dots: List<Boolean>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SideMargin, end = SideMargin, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(TimelineNode), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(KalynoBlue)
                    .border(3.dp, KalynoBlueSoft, CircleShape)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = KalynoText,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (dots.isNotEmpty()) {
                Text(
                    text = if (dots.size == 1) "1 réponse" else "${dots.size} réponses",
                    color = KalynoMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        // Un point par réponse : vert = succès, rouge doux = échec
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            dots.take(MAX_DAY_DOTS).forEach { success ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (success) KalynoCorrect else KalynoDanger.copy(alpha = 0.55f))
                )
            }
            if (dots.size > MAX_DAY_DOTS) {
                Text(
                    text = "+${dots.size - MAX_DAY_DOTS}",
                    color = KalynoMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/** Une tentative sur le fil du temps : pastille de résultat sur la ligne, carte dépliable à droite. */
@Composable
private fun TimelineAttempt(item: AttemptUi, lineAbove: Boolean, lineBelow: Boolean) {
    val accent = if (item.success) KalynoCorrect else KalynoDanger

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Le fil déborde de 6 dp en haut et en bas pour traverser l'espace entre deux cartes
                val x = (SideMargin + TimelineNode / 2).toPx()
                val nodeY = NodeCenterY.toPx()
                val top = if (lineAbove) -6.dp.toPx() else nodeY
                val bottom = if (lineBelow) size.height + 6.dp.toPx() else nodeY
                if (bottom > top) {
                    drawLine(
                        color = KalynoOutline,
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
    ) {
        Row(Modifier.padding(horizontal = SideMargin)) {
            Box(
                modifier = Modifier
                    .padding(top = NodeCenterY - TimelineNode / 2)
                    .size(TimelineNode)
                    .clip(CircleShape)
                    .background(accent)
                    .border(3.dp, KalynoBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(
                    name = if (item.success) "check" else "x",
                    size = 14.dp,
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(10.dp))
            AttemptCard(item, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AttemptCard(item: AttemptUi, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    val accentSoft = if (item.success) KalynoCorrectSoft else KalynoDangerSoft
    val accentText = if (item.success) KalynoCorrectText else KalynoDanger

    KalynoCard(
        modifier = modifier.animateContentSize(),
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        contentPadding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = item.lessonTitle,
                color = KalynoText,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.time,
                color = KalynoMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusBadge(item.success)
            if (item.subjectName.isNotBlank()) {
                Text(
                    text = item.subjectName,
                    color = KalynoBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(50))
                        .background(KalynoBlueSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            if (item.isLegacy) {
                Text(
                    text = "Ancien",
                    color = KalynoMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(KalynoOutline.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        item.attemptLine?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                color = accentText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentSoft)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Question", color = KalynoMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (expanded) {
            MathText(text = item.question, color = KalynoText, fontSize = 14.sp, lineHeight = 20.sp)
        } else {
            // Aperçu en texte simple : les formules ($…$) ne sont rendues qu'à l'ouverture de la carte
            Text(
                text = item.question.replace("$", ""),
                color = KalynoText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            AnswerBlock(
                label = "Ta réponse",
                text = item.studentAnswer
                    ?: "Non conservée : cette tentative date d'avant la mise à jour de l'historique.",
                background = accentSoft,
                textColor = accentText
            )
            Spacer(Modifier.height(8.dp))
            AnswerBlock(
                label = "Réponse attendue",
                text = item.referenceAnswer,
                background = KalynoBlueSoft,
                textColor = KalynoText
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "Masquer le détail" else "Voir le détail",
                color = KalynoBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.width(4.dp))
            Box(Modifier.rotate(rotation)) {
                KalynoSvgIcon(name = "chevron-down", size = 14.dp, tint = KalynoBlue)
            }
        }
    }
}

@Composable
private fun StatusBadge(success: Boolean) {
    Text(
        text = if (success) "Succès" else "Échec",
        color = if (success) KalynoCorrectText else KalynoDanger,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (success) KalynoCorrectSoft else KalynoDangerSoft)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun AnswerBlock(label: String, text: String, background: Color, textColor: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(12.dp)
    ) {
        Text(label, color = KalynoMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        MathText(text = text, color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

// ===========================================================================
// ÉTAT VIDE
// ===========================================================================

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val pulse = rememberPulse(1f, 1.1f, 1100)
    KalynoCard(modifier = modifier.fillMaxWidth(), contentPadding = 24.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .clip(CircleShape)
                    .background(KalynoBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "rocket", size = 34.dp, tint = KalynoBlue)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Ton aventure commence ici",
                color = KalynoText,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Dès ta première séance au Dojo, chaque réponse s'ajoute ici : la leçon, la question, ta réponse et la réponse attendue.",
                color = KalynoMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ===========================================================================
// TITRES DE SECTION
// ===========================================================================

@Composable
private fun SectionTitle(
    iconName: String,
    title: String,
    subtitle: String,
    count: Int?,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KalynoBlueSoft),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = iconName, size = 20.dp, tint = KalynoBlue)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, color = KalynoText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = subtitle, color = KalynoMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString(),
                color = KalynoBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(KalynoBlueSoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

// ===========================================================================
// FICHES APPRISES : cartes à retourner
// ===========================================================================

/**
 * Une fiche apprise sous forme de vraie carte : recto = question, verso = réponse officielle.
 * Un toucher la retourne (rotation 3D) ; l'état de retournement est conservé à la rotation de l'écran.
 */
@Composable
private fun FlipCard(card: LearnedCardUi) {
    val haptic = rememberHaptic()
    var flipped by rememberSaveable(card.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "flip"
    )
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .size(width = 268.dp, height = 208.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .shadow(4.dp, shape, ambientColor = KalynoShadow, spotColor = KalynoShadow)
            .clickable {
                haptic()
                flipped = !flipped
            }
    ) {
        if (rotation <= 90f) {
            // Recto
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color.White)
                    .border(1.dp, KalynoOutline, shape)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (card.subjectName.isNotBlank()) {
                        KalynoPill(
                            text = card.subjectName,
                            fontSize = 11,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    KalynoSvgIcon(name = "check-circle", size = 20.dp, tint = KalynoCorrect)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = card.question.replace("$", ""),
                    color = KalynoText,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KalynoSvgIcon(name = "shuffle", size = 14.dp, tint = KalynoMuted)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Touche pour voir la réponse",
                        color = KalynoMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Verso (retourné pour ne pas apparaître en miroir)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KalynoPill(
                            text = "RÉPONSE",
                            container = KalynoAmberSoft,
                            content = KalynoAmberDeep,
                            fontSize = 11
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Appris · ${card.learnedOn}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        MathText(
                            text = card.answer,
                            color = Color.White,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val footer = listOfNotNull(card.lessonTitle.takeIf { it.isNotBlank() }, card.effort)
                        .joinToString(" · ")
                    if (footer.isNotEmpty()) {
                        Text(
                            text = footer,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ===========================================================================
// SEMAINE AU DOJO
// ===========================================================================

@Composable
private fun WeekCard(week: List<DayBarUi>, modifier: Modifier = Modifier) {
    if (week.isEmpty()) return
    val totalWeek = week.sumOf { it.total }
    val activeDays = week.count { it.total > 0 }
    val maxTotal = (week.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)

    val subtitle = if (totalWeek == 0) {
        "Aucune réponse cette semaine : une seule séance suffit pour te relancer."
    } else {
        val answers = if (totalWeek == 1) "1 réponse" else "$totalWeek réponses"
        val days = if (activeDays == 1) "1 jour actif" else "$activeDays jours actifs"
        "$answers · $days sur 7"
    }

    KalynoCard(modifier = modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Text(
            text = "Ta semaine au Dojo",
            color = KalynoText,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(2.dp))
        Text(text = subtitle, color = KalynoMuted, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            week.forEach { day -> WeekBar(day, maxTotal, Modifier.weight(1f)) }
        }
    }
}

private val WeekBarMaxHeight = 72.dp

@Composable
private fun WeekBar(day: DayBarUi, maxTotal: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = day.total.toFloat() / maxTotal,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "weekBar"
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (day.total > 0) {
            Text(
                text = day.total.toString(),
                color = if (day.isToday) KalynoBlue else KalynoMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(3.dp))
            // Pile : échecs (rouge doux) au-dessus, succès (vert) en dessous
            Column(
                modifier = Modifier
                    .width(20.dp)
                    .height((WeekBarMaxHeight * fraction).coerceAtLeast(8.dp))
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (day.failures > 0) {
                    Box(
                        Modifier
                            .weight(day.failures.toFloat())
                            .fillMaxWidth()
                            .background(KalynoDanger.copy(alpha = 0.55f))
                    )
                }
                if (day.successes > 0) {
                    Box(
                        Modifier
                            .weight(day.successes.toFloat())
                            .fillMaxWidth()
                            .background(KalynoCorrect)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(KalynoOutline)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = day.label,
            color = if (day.isToday) KalynoBlue else KalynoMuted,
            fontSize = 12.sp,
            fontWeight = if (day.isToday) FontWeight.ExtraBold else FontWeight.Medium
        )
    }
}
'''

MASTERED_CONTENT = r'''package ci.kalyno.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.dashboard.MasteredCard
import ci.kalyno.app.data.dashboard.MasteryTier
import ci.kalyno.app.ui.components.KalynoRing
import ci.kalyno.app.ui.components.MathText
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText

private data class CardTierStyle(val icon: String, val label: String, val tint: Color, val soft: Color)

private fun MasteryTier.cardStyle(): CardTierStyle = when (this) {
    MasteryTier.SOLIDE -> CardTierStyle("check-circle", "Solide", KalynoCorrectText, KalynoCorrectSoft)
    MasteryTier.AVANCEE -> CardTierStyle("target", "Avancée", KalynoBlue, KalynoBlueSoft)
    MasteryTier.EXPERTE -> CardTierStyle("trophy", "Experte", KalynoAmberDeep, KalynoAmberSoft)
}

/** Nombre de pastilles de répétition affichées (au-delà, un « + » indique qu'il y en a davantage). */
private const val REPETITION_PIPS = 5

/** En-tête de la liste : nombre de cartes maîtrisées (distinct du nombre de notions). */
@Composable
internal fun MasteredCardsHeader(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KalynoAmberSoft),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = "trophy", size = 20.dp, tint = KalynoAmberDeep)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Cartes maîtrisées",
                color = KalynoText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Validées par 3 répétitions réussies",
                color = KalynoMuted,
                fontSize = 12.sp
            )
        }
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(KalynoCorrectSoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = count.toString(),
                    color = KalynoCorrectText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
internal fun MasteredCardsEmptyText() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KalynoBlueSoft)
            .padding(14.dp)
    ) {
        KalynoSvgIcon(name = "target", size = 22.dp, tint = KalynoBlue)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Aucune carte maîtrisée pour l'instant : une carte apparaît ici après 3 répétitions réussies.",
            color = KalynoText,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

/**
 * Une carte maîtrisée : liseré et badge de niveau, question, matière · chapitre,
 * pastilles de répétitions réussies et anneau de rétention.
 */
@Composable
internal fun MasteredCardItem(card: MasteredCard, modifier: Modifier = Modifier) {
    val style = card.tier.cardStyle()
    val shape = RoundedCornerShape(20.dp)
    val railWidth = 5.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, KalynoOutline, shape)
            .drawBehind {
                drawRect(
                    color = style.tint,
                    topLeft = Offset.Zero,
                    size = Size(railWidth.toPx(), size.height)
                )
            }
            .padding(start = railWidth + 14.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Badge de niveau
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(style.soft)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                KalynoSvgIcon(name = style.icon, size = 13.dp, tint = style.tint)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = style.label,
                    color = style.tint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(8.dp))
            MathText(
                text = card.question,
                color = KalynoText,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold
            )
            val where = listOf(card.subjectName, card.chapterTitle).filter { it.isNotBlank() }.joinToString(" · ")
            if (where.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = where,
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            // Pastilles : une par répétition réussie
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(REPETITION_PIPS) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (index < card.repetitions) style.tint else style.soft)
                        )
                    }
                }
                if (card.repetitions > REPETITION_PIPS) {
                    Spacer(Modifier.width(3.dp))
                    Text(text = "+", color = style.tint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (card.repetitions == 1) "1 répétition réussie" else "${card.repetitions} répétitions réussies",
                    color = style.tint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        card.score?.let { score ->
            Spacer(Modifier.width(12.dp))
            val clamped = score.coerceIn(0f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                KalynoRing(
                    progress = clamped,
                    diameter = 54.dp,
                    strokeWidth = 5.dp,
                    color = style.tint,
                    track = style.soft
                ) {
                    Text(
                        text = "${(clamped * 100).toInt()}",
                        color = style.tint,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(text = "rétention", color = KalynoMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Section complète (en-tête + liste bornée) pour les écrans qui ne sont pas déjà une liste paresseuse. */
@Composable
fun MasteredCardsSection(cards: List<MasteredCard>) {
    MasteredCardsHeader(cards.size)
    Spacer(Modifier.height(10.dp))
    if (cards.isEmpty()) {
        MasteredCardsEmptyText()
    } else {
        LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(cards, key = { it.cardId }) { card -> MasteredCardItem(card) }
        }
    }
}
'''

VIEWMODEL_CONTENT = r'''package ci.kalyno.app.ui.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.dashboard.StudentId
import ci.kalyno.app.data.progress.DailyCountRow
import ci.kalyno.app.data.progress.DojoAttemptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

enum class HistoryStatus(val code: Int) { ALL(0), SUCCESS(1), FAILURE(2) }

data class HistoryFilter(val status: HistoryStatus = HistoryStatus.ALL, val subject: String? = null)

data class ProgressSummaryUi(
    val total: Int,
    val successes: Int,
    val failures: Int,
    val learnedCards: Int,
    /** Part de succès parmi toutes les tentatives (0..1) ; null tant qu'il n'y a aucune tentative. */
    val successRate: Float?
)

/** Une fiche apprise (réussie au moins une fois) : recto = question, verso = réponse officielle. */
data class LearnedCardUi(
    val id: Long,
    val question: String,
    val answer: String,
    val lessonTitle: String,
    val subjectName: String,
    /** « Aujourd'hui », « Hier » ou la date : jour de la dernière réussite. */
    val learnedOn: String,
    /** « Du premier coup » ou « Après 2 échecs » ; null si le rang est inconnu (historique ancien). */
    val effort: String?
)

/** Une journée de la semaine glissante (de J-6 à aujourd'hui). */
data class DayBarUi(
    val label: String,
    val total: Int,
    val successes: Int,
    val isToday: Boolean
) {
    val failures: Int get() = total - successes
}

data class ProgressHistoryUiState(
    val loading: Boolean = true,
    val summary: ProgressSummaryUi? = null,
    val subjects: List<String> = emptyList(),
    val filter: HistoryFilter = HistoryFilter(),
    val rows: List<HistoryRow> = emptyList(),
    /** Il reste des tentatives plus anciennes à charger. */
    val canLoadMore: Boolean = false,
    /** Fiches apprises, de la plus récemment réussie à la plus ancienne (bornées). */
    val learned: List<LearnedCardUi> = emptyList(),
    /** Activité des 7 derniers jours, du plus ancien à aujourd'hui. */
    val week: List<DayBarUi> = emptyList()
) {
    /** Aucune tentative du tout (hors filtre) : on affiche l'état vide plutôt qu'une liste vide. */
    val neverPlayed: Boolean get() = !loading && (summary?.total ?: 0) == 0
}

/**
 * Historique d'apprentissage de l'élève : lit uniquement Room (hors-ligne), en flux. La page se met à jour
 * toute seule quand une tentative est enregistrée par le Dojo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = KalynoDatabase.get(app).attemptDao()
    private val studentId = StudentId.get(app)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val filter = MutableStateFlow(HistoryFilter())
    private val limit = MutableStateFlow(PAGE_SIZE)

    private val timeline = combine(filter, limit) { f, l -> f to l }
        .flatMapLatest { (f, l) -> dao.observeHistory(studentId, f.status.code, f.subject, l) }

    private val base: Flow<ProgressHistoryUiState> = combine(
        dao.observeSummary(studentId),
        dao.observeSubjects(studentId),
        filter,
        limit,
        timeline
    ) { summary, subjects, f, l, attempts ->
        ProgressHistoryUiState(
            loading = false,
            summary = ProgressSummaryUi(
                total = summary.total,
                successes = summary.successes,
                failures = summary.total - summary.successes,
                learnedCards = summary.learnedCards,
                successRate = if (summary.total > 0) summary.successes.toFloat() / summary.total else null
            ),
            subjects = subjects,
            filter = f,
            rows = HistoryFormat.group(attempts, LocalDate.now(), ZoneId.systemDefault()),
            canLoadMore = attempts.size >= l
        )
    }

    private val learnedFlow: Flow<List<LearnedCardUi>> =
        dao.observeLearned(studentId, LEARNED_LIMIT).map { list ->
            val today = LocalDate.now(zone)
            list.map { toLearnedUi(it, today, zone) }
        }

    private val weekFlow: Flow<List<DayBarUi>> = run {
        val since = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val offsetMs = zone.rules.getOffset(Instant.now()).totalSeconds * 1000L
        dao.observeDailyCounts(studentId, since, offsetMs).map { rows -> buildWeek(rows, LocalDate.now(zone)) }
    }

    val state: StateFlow<ProgressHistoryUiState> = combine(base, learnedFlow, weekFlow) { b, learned, week ->
        b.copy(learned = learned, week = week)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressHistoryUiState())

    fun selectStatus(status: HistoryStatus) = changeFilter { it.copy(status = status) }

    /** Toucher la discipline déjà choisie la désélectionne. */
    fun selectSubject(subject: String?) = changeFilter { it.copy(subject = if (it.subject == subject) null else subject) }

    fun loadMore() {
        if (state.value.canLoadMore) limit.value = limit.value + PAGE_SIZE
    }

    private fun changeFilter(change: (HistoryFilter) -> HistoryFilter) {
        filter.value = change(filter.value)
        limit.value = PAGE_SIZE
    }

    private companion object {
        const val PAGE_SIZE = 40
        const val LEARNED_LIMIT = 30
    }
}

private fun toLearnedUi(a: DojoAttemptEntity, today: LocalDate, zone: ZoneId): LearnedCardUi {
    val day = Instant.ofEpochMilli(a.attemptedAt).atZone(zone).toLocalDate()
    val effort = when {
        a.attemptNumber <= 0 -> null
        a.attemptNumber == 1 -> "Du premier coup"
        else -> {
            val failures = a.attemptNumber - 1
            "Après $failures ${if (failures == 1) "échec" else "échecs"}"
        }
    }
    return LearnedCardUi(
        id = a.id,
        question = a.question,
        answer = a.referenceAnswer,
        lessonTitle = a.lessonTitle,
        subjectName = a.subjectName,
        learnedOn = HistoryFormat.dayLabel(day, today),
        effort = effort
    )
}

/** Sept jours de J-6 à aujourd'hui ; un jour sans tentative vaut zéro (jamais « inconnu »). */
private fun buildWeek(rows: List<DailyCountRow>, today: LocalDate): List<DayBarUi> {
    val byDay = rows.associateBy { it.day }
    return (6 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val row = byDay[date.toEpochDay()]
        DayBarUi(
            label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH)
                .take(1)
                .uppercase(Locale.FRENCH),
            total = row?.total ?: 0,
            successes = row?.successes ?: 0,
            isToday = back == 0
        )
    }
}
'''

DAO_OLD_CLASS = r'''    val learnedCards: Int
)
'''

DAO_NEW_CLASS = r'''    val learnedCards: Int
)

/** Activité d'une journée locale (jour = nombre de jours depuis 1970, dans le fuseau de l'élève). */
data class DailyCountRow(
    val day: Long,
    val total: Int,
    val successes: Int
)
'''

DAO_OLD_END = r'''    abstract fun observeSubjects(studentId: String): Flow<List<String>>
}'''

DAO_NEW_END = r'''    abstract fun observeSubjects(studentId: String): Flow<List<String>>

    /**
     * Fiches apprises : pour chaque fiche réussie au moins une fois, sa dernière réussite
     * (la plus récente d'abord). [limit] borne la liste affichée.
     */
    @Query(
        """
        SELECT * FROM dojo_attempts
        WHERE id IN (
            SELECT MAX(id) FROM dojo_attempts
            WHERE studentId = :studentId AND isSuccess = 1
            GROUP BY flashcardId
        )
        ORDER BY attemptedAt DESC, id DESC
        LIMIT :limit
        """
    )
    abstract fun observeLearned(studentId: String, limit: Int): Flow<List<DojoAttemptEntity>>

    /**
     * Tentatives et succès par jour depuis [sinceMs]. [offsetMs] = décalage du fuseau de l'élève,
     * pour que « jour » soit le jour local et non le jour UTC.
     */
    @Query(
        """
        SELECT ((attemptedAt + :offsetMs) / 86400000) AS day,
               COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN isSuccess = 1 THEN 1 ELSE 0 END), 0) AS successes
        FROM dojo_attempts
        WHERE studentId = :studentId AND attemptedAt >= :sinceMs
        GROUP BY day
        ORDER BY day ASC
        """
    )
    abstract fun observeDailyCounts(studentId: String, sinceMs: Long, offsetMs: Long): Flow<List<DailyCountRow>>
}'''


def write(path: str, content: str) -> None:
    shutil.copyfile(path, path + ".bak")
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(content)
    print("OK : " + path)


def patch_dao(path: str) -> bool:
    with open(path, "r", encoding="utf-8", newline="") as handle:
        text = handle.read()
    if "observeLearned" in text:
        print("DAO déjà à jour : " + path)
        return True
    if text.count(DAO_OLD_CLASS) != 1 or text.count(DAO_OLD_END) != 1:
        print("ERREUR : DojoAttempts.kt ne ressemble pas à la version attendue (rien n'a été modifié).")
        return False
    text = text.replace(DAO_OLD_CLASS, DAO_NEW_CLASS).replace(DAO_OLD_END, DAO_NEW_END)
    shutil.copyfile(path, path + ".bak")
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("OK : " + path)
    return True


def main() -> int:
    root = sys.argv[1] if len(sys.argv) > 1 else os.getcwd()
    paths = [os.path.join(root, p) for p in (SCREEN, MASTERED, VIEWMODEL, DAO)]
    missing = [p for p in paths if not os.path.isfile(p)]
    if missing:
        for p in missing:
            print("ERREUR : fichier introuvable : " + p)
        print("Lance le script depuis la racine du dépôt KALYNO (ou passe-la en argument).")
        return 1
    # Le DAO d'abord : s'il ne correspond pas, on ne touche à rien.
    if not patch_dao(paths[3]):
        return 1
    write(paths[0], SCREEN_CONTENT)
    write(paths[1], MASTERED_CONTENT)
    write(paths[2], VIEWMODEL_CONTENT)
    print("Terminé. Lance le build.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
