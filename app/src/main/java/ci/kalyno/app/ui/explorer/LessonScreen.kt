package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.KalynoIconEmptyState
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.icons.iconAssetFor
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoCorrectSoft
import ci.kalyno.app.ui.theme.KalynoCorrectText
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText
import kotlinx.coroutines.launch
import ci.kalyno.app.ui.report.ReportFlagButton
import ci.kalyno.app.ui.report.ReportLayer
import ci.kalyno.app.ui.report.ReportTarget
import ci.kalyno.app.ui.report.rememberReportLayerState

/** Une cible de la barre de navigation rapide : un libellé et l'index de l'item visé dans le LazyColumn. */
private data class JumpTarget(val index: Int, val label: String)

/**
 * Écran de leçon dynamique.
 *
 * Le contenu n'est pas figé : chaque capsule configurée par l'admin devient un bloc empilé
 * (introduction, texte, règle d'or, astuce, piège). Les blocs apparaissent en cascade avec un
 * fondu + glissement vers le haut ([RevealOnAppear]). Une barre de raccourcis horizontale
 * ([LessonJumpBar]) permet de sauter directement à un bloc titré sans défiler manuellement.
 */
@Composable
fun LessonScreen(
    chapter: ExplorerChapter,
    /** Vrai une fois que l'élève a appuyé sur « Terminer » (état lu dans Room). */
    isCompleted: Boolean,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    onStartQuestions: () -> Unit
) {
    val listState = rememberLazyListState()
    val report = rememberReportLayerState()
    val scope = rememberCoroutineScope()
    // Séances chronologiques (Séance 1, 2…) puis leurs blocs ; les anciennes leçons restent une simple pile de blocs.
    val rows = remember(chapter) { buildLessonRows(chapter) }
    val hasComplement = !chapter.complement.isNullOrBlank()
    val revealed = remember(chapter.id) { HashSet<String>() }

    // Index 0 = hero ; chaque ligne occupe index (i + 1) ; cette correspondance doit rester
    // synchronisée avec l'ordre des `item { }` du LazyColumn plus bas.
    // Avec des séances : une puce par séance. Sans séance : une puce par bloc titré (comportement historique).
    val jumpTargets = remember(rows) {
        val hasSessions = rows.any { it is LessonRow.Header }
        buildList {
            add(JumpTarget(0, "Début"))
            rows.forEachIndexed { i, row ->
                when {
                    row is LessonRow.Header -> add(JumpTarget(i + 1, "Séance ${row.group.number}"))
                    row is LessonRow.Block && !hasSessions -> {
                        val label = row.block.title?.trim()
                        if (!label.isNullOrEmpty()) add(JumpTarget(i + 1, label))
                    }
                }
            }
        }
    }

    // Progression de lecture (fine barre sous l'en-tête), dérivée du LazyListState.
    val readProgress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) {
                0f
            } else {
                val index = listState.firstVisibleItemIndex
                val visible = info.visibleItemsInfo.firstOrNull { it.index == index }
                val within = visible?.let { (-it.offset).toFloat() / it.size.coerceAtLeast(1) }
                    ?.coerceIn(0f, 1f) ?: 0f
                ((index + within) / (total - 1)).coerceIn(0f, 1f)
            }
        }
    }

    // Puce active : la dernière cible dont l'index est déjà atteint ou dépassé par le scroll.
    val activeTargetIndex by remember(jumpTargets) {
        derivedStateOf {
            val current = listState.firstVisibleItemIndex
            jumpTargets.indexOfLast { it.index <= current }.coerceAtLeast(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // ----- En-tête fixe -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExplorerBackButton(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            KalynoSvgIcon(name = iconAssetFor(chapter.subjectIconKey), size = 18.dp, tint = KalynoBlue)
            Spacer(Modifier.width(8.dp))
            Text(
                text = chapter.subjectName,
                color = KalynoBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            ReportFlagButton(
                onClick = {
                    report.open(
                        ReportTarget.lesson(
                            lessonId = chapter.id,
                            lessonTitle = chapter.title,
                            subjectName = chapter.subjectName
                        )
                    )
                }
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(KalynoBlueSoft)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(readProgress)
                    .background(KalynoBlue)
            )
        }

        if (jumpTargets.size > 1) {
            LessonJumpBar(
                targets = jumpTargets,
                activeIndex = activeTargetIndex,
                onSelect = { targetIndex ->
                    scope.launch { listState.animateScrollToItem(targetIndex) }
                }
            )
        }

        // ----- Blocs empilés -----
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp
                )
            ) {
                item(key = "hero") {
                    RevealOnAppear(id = "hero", index = 0, revealed = revealed, bottomSpace = 20.dp) {
                        LessonHero(chapter)
                    }
                }

                itemsIndexed(rows, key = { _, row -> row.key }) { i, row ->
                    when (row) {
                        is LessonRow.Header -> RevealOnAppear(id = row.key, index = i + 1, revealed = revealed, bottomSpace = 10.dp) {
                            SessionHeader(row.group)
                        }
                        is LessonRow.Block -> RevealOnAppear(id = row.key, index = i + 1, revealed = revealed) {
                            LessonBlockView(row.block)
                        }
                    }
                }

                if (rows.isEmpty()) {
                    item(key = "empty") {
                        RevealOnAppear(id = "empty", index = 1, revealed = revealed) {
                            KalynoIconEmptyState(
                                icon = "pen-line",
                                title = "Leçon en préparation",
                                text = "Le contenu de cette leçon apparaîtra ici dès sa publication."
                            )
                        }
                    }
                }

                if (hasComplement) {
                    item(key = "complement") {
                        RevealOnAppear(id = "complement", index = rows.size + 1, revealed = revealed) {
                            LessonComplementCard(complement = chapter.complement.orEmpty())
                        }
                    }
                }

                // Validation explicite en fin de lecture : tant que l'élève n'appuie pas, la leçon reste non terminée
                if (rows.isNotEmpty() || hasComplement) {
                    item(key = "finish") {
                        RevealOnAppear(id = "finish", index = rows.size + 2, revealed = revealed, bottomSpace = 12.dp) {
                            LessonFinishBar(isCompleted = isCompleted, onComplete = onComplete)
                        }
                    }
                }

                // Les questions ne se débloquent qu'une fois la leçon terminée
                if (chapter.questionCount > 0 && isCompleted) {
                    item(key = "cta") {
                        RevealOnAppear(id = "cta", index = rows.size + 3, revealed = revealed, bottomSpace = 0.dp) {
                            KalynoPrimaryButton(
                                label = "DÉMARRER LES ${chapter.questionCount} QUESTIONS",
                                onClick = onStartQuestions
                            )
                        }
                    }
                }
            }
            ReportLayer(report)
        }
    }
}

/**
 * Fin de leçon : bouton « Terminer » tant que la leçon n'est pas validée, puis confirmation.
 * La validation est volontaire (aucune détection de scroll) et irréversible côté élève.
 */
@Composable
internal fun LessonFinishBar(isCompleted: Boolean, onComplete: () -> Unit) {
    if (isCompleted) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(KalynoCorrectSoft)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KalynoSvgIcon(name = "check-circle", size = 20.dp, tint = KalynoCorrectText)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Leçon terminée · ses exercices sont débloqués dans le Dojo",
                color = KalynoCorrectText,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KalynoPrimaryButton(label = "TERMINER · J'AI LU CETTE LEÇON", onClick = onComplete)
            Text(
                text = "Appuie quand tu as fini de lire pour débloquer les exercices dans le Dojo.",
                color = KalynoMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * Barre de navigation rapide horizontale : une puce par section titrée de la leçon.
 * Un clic fait défiler le [LazyColumn] jusqu'au bloc visé et donne un micro-retour haptique.
 */
@Composable
private fun LessonJumpBar(
    targets: List<JumpTarget>,
    activeIndex: Int,
    onSelect: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(KalynoBackground)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(targets, key = { _, target -> target.index }) { i, target ->
            val active = i == activeIndex
            val shape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(if (active) KalynoBlue else Color.White)
                    .run { if (!active) border(1.dp, KalynoOutline, shape) else this }
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(target.index)
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = target.label,
                    color = if (active) Color.White else KalynoText,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/** Bloc Titre / Introduction : bannière dégradée Deep Teal, très inspirante. */
@Composable
private fun LessonHero(chapter: ExplorerChapter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = iconAssetFor(chapter.subjectIconKey), size = 24.dp, tint = Color.White)
        }
        Text(
            text = chapter.title,
            color = Color.White,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold
        )
        val meta = buildList {
            if (chapter.minutes > 0) add("clock" to "${chapter.minutes} min")
            if (chapter.sessions.isNotEmpty()) add("layers" to "${chapter.sessions.size} séances")
            else if (chapter.capsules.isNotEmpty()) add("book-open" to "${chapter.capsules.size} blocs")
            if (chapter.questionCount > 0) add("target" to "${chapter.questionCount} questions")
        }
        if (meta.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                meta.forEach { (icon, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KalynoSvgIcon(name = icon, size = 14.dp, tint = Color.White.copy(alpha = 0.9f))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
