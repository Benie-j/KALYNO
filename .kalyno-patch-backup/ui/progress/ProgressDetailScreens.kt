package ci.kalyno.app.ui.progress

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ci.kalyno.app.data.dashboard.MasteredCard
import ci.kalyno.app.ui.components.DriftingBlob
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoIconEmptyState
import ci.kalyno.app.ui.components.ShimmerBlock
import ci.kalyno.app.ui.components.rememberCountUp
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBackground
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoTealDark
import ci.kalyno.app.ui.theme.KalynoText

// ===========================================================================
// Écrans détaillés de « Ma progression » : chacun classe ses éléments par matière, puis par leçon.
//   - Historique du Dojo : toutes les réponses (filtrables Succès / Échecs)
//   - Cartes apprises    : cartes maîtrisées (3 répétitions réussies)
//   - Fiches apprises    : fiches réussies au moins une fois, à retourner
// Matières et leçons se déplient au toucher ; tout vient de Room (hors-ligne).
// ===========================================================================

private val DetailMargin = 16.dp
private val LessonIndent = 28.dp

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

private fun subjectKey(prefix: String, subject: String) = "$prefix-s-$subject"
private fun lessonKey(prefix: String, subject: String, lesson: String) = "$prefix-l-$subject|$lesson"

// ---------------------------------------------------------------------------
// 1. Historique du Dojo
// ---------------------------------------------------------------------------

@Composable
internal fun HistoryDetailScreen(viewModel: ProgressHistoryViewModel, onBack: () -> Unit) {
    val attempts by viewModel.allAttempts.collectAsStateWithLifecycle()
    var status by rememberSaveable { mutableStateOf(HistoryStatus.ALL) }

    val all = attempts
    if (all == null) {
        DetailLoading(icon = "history", title = "Historique du Dojo", onBack = onBack)
        return
    }

    val successes = remember(all) { all.count { it.success } }
    val filtered = remember(all, status) {
        when (status) {
            HistoryStatus.ALL -> all
            HistoryStatus.SUCCESS -> all.filter { it.success }
            HistoryStatus.FAILURE -> all.filter { !it.success }
        }
    }
    val groups = remember(filtered) { groupBySubjectAndLesson(filtered, { it.subjectName }, { it.lessonTitle }) }

    GroupedDetail(
        prefix = "history",
        groups = groups,
        header = {
            DetailHeader(
                icon = "history",
                title = "Historique du Dojo",
                count = all.size,
                countLabel = if (all.size == 1) "réponse enregistrée" else "réponses enregistrées",
                onBack = onBack
            )
        },
        topContent = {
            if (all.isNotEmpty()) {
                item(key = "history-filter") {
                    StatusSegment(
                        selected = status,
                        total = all.size,
                        successes = successes,
                        failures = all.size - successes,
                        onSelect = { status = it },
                        modifier = Modifier.padding(horizontal = DetailMargin)
                    )
                }
            }
        },
        emptyContent = {
            if (all.isEmpty()) {
                KalynoIconEmptyState(
                    icon = "history",
                    title = "Aucune réponse pour l'instant",
                    text = "Dès ta première séance au Dojo, chaque réponse s'ajoute ici, classée par matière et par leçon."
                )
            } else {
                Text(
                    text = "Aucune tentative pour ce filtre.",
                    color = KalynoMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            }
        },
        subjectSubtitle = { subject ->
            val ok = subject.lessons.sumOf { lesson -> lesson.items.count { it.success } }
            "${plural(subject.lessons.size, "leçon", "leçons")} · ${plural(ok, "succès", "succès")} · " +
                plural(subject.count - ok, "échec", "échecs")
        },
        lessonSubtitle = { lesson ->
            val ok = lesson.items.count { it.success }
            "${plural(ok, "succès", "succès")} · ${plural(lesson.items.size - ok, "échec", "échecs")}"
        },
        lessonContent = { lessonKey, lesson ->
            items(lesson.items, key = { "$lessonKey-${it.id}" }) { attempt ->
                AttemptCard(
                    item = attempt,
                    modifier = Modifier.padding(start = LessonIndent, end = DetailMargin),
                    showContext = false
                )
            }
        }
    )
}

// ---------------------------------------------------------------------------
// 2. Cartes apprises (cartes maîtrisées)
// ---------------------------------------------------------------------------

@Composable
internal fun MasteredDetailScreen(cards: List<MasteredCard>, onBack: () -> Unit) {
    val groups = remember(cards) { groupBySubjectAndLesson(cards, { it.subjectName }, { it.chapterTitle }) }

    GroupedDetail(
        prefix = "cards",
        groups = groups,
        header = {
            DetailHeader(
                icon = "trophy",
                title = "Cartes apprises",
                count = cards.size,
                countLabel = if (cards.size == 1) "carte validée par 3 répétitions" else "cartes validées par 3 répétitions",
                onBack = onBack
            )
        },
        emptyContent = {
            Box(Modifier.padding(horizontal = DetailMargin)) { MasteredCardsEmptyText() }
        },
        subjectSubtitle = { subject -> plural(subject.lessons.size, "leçon", "leçons") },
        lessonSubtitle = { lesson -> plural(lesson.items.size, "carte", "cartes") },
        lessonContent = { lessonKey, lesson ->
            items(lesson.items, key = { "$lessonKey-${it.cardId}" }) { card ->
                MasteredCardItem(card, Modifier.padding(start = LessonIndent, end = DetailMargin))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// 3. Fiches apprises
// ---------------------------------------------------------------------------

@Composable
internal fun SheetsDetailScreen(viewModel: ProgressHistoryViewModel, onBack: () -> Unit) {
    val learned by viewModel.allLearned.collectAsStateWithLifecycle()

    val all = learned
    if (all == null) {
        DetailLoading(icon = "layers", title = "Fiches apprises", onBack = onBack)
        return
    }

    val groups = remember(all) { groupBySubjectAndLesson(all, { it.subjectName }, { it.lessonTitle }) }

    GroupedDetail(
        prefix = "sheets",
        groups = groups,
        header = {
            DetailHeader(
                icon = "layers",
                title = "Fiches apprises",
                count = all.size,
                countLabel = if (all.size == 1) "fiche réussie au moins une fois" else "fiches réussies au moins une fois",
                onBack = onBack
            )
        },
        emptyContent = {
            KalynoIconEmptyState(
                icon = "layers",
                title = "Aucune fiche apprise",
                text = "Une fiche apparaît ici dès que tu la réussis au Dojo. Touche-la ensuite pour la retourner et revoir la réponse."
            )
        },
        subjectSubtitle = { subject ->
            "${plural(subject.lessons.size, "leçon", "leçons")} · touche une fiche pour la retourner"
        },
        lessonSubtitle = { lesson -> plural(lesson.items.size, "fiche", "fiches") },
        lessonContent = { lessonKey, lesson ->
            item(key = "$lessonKey-row") {
                LazyRow(
                    contentPadding = PaddingValues(start = LessonIndent, end = DetailMargin, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(lesson.items, key = { it.id }) { card -> FlipCard(card) }
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Liste commune : matière (dépliable) -> leçon (dépliable) -> contenu propre à l'écran
// ---------------------------------------------------------------------------

@Composable
private fun <T> GroupedDetail(
    prefix: String,
    groups: List<SubjectGroup<T>>,
    header: @Composable () -> Unit,
    subjectSubtitle: (SubjectGroup<T>) -> String,
    lessonSubtitle: (LessonGroup<T>) -> String?,
    emptyContent: @Composable () -> Unit,
    lessonContent: LazyListScope.(String, LessonGroup<T>) -> Unit,
    topContent: LazyListScope.() -> Unit = {}
) {
    // Clés des matières et leçons dépliées (liste : conservée à la rotation de l'écran)
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val allKeys = remember(groups) {
        groups.flatMap { subject ->
            listOf(subjectKey(prefix, subject.title)) +
                subject.lessons.map { lessonKey(prefix, subject.title, it.title) }
        }
    }
    val allOpen = allKeys.isNotEmpty() && allKeys.all { it in expanded }
    val toggle: (String) -> Unit = { key ->
        expanded = if (key in expanded) expanded - key else expanded + key
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "$prefix-header") { header() }
        topContent()

        if (groups.isEmpty()) {
            item(key = "$prefix-empty") { emptyContent() }
        } else {
            item(key = "$prefix-expand") {
                ExpandAllLink(
                    allOpen = allOpen,
                    onClick = { expanded = if (allOpen) emptyList() else allKeys },
                    modifier = Modifier.padding(horizontal = DetailMargin)
                )
            }
            groups.forEach { subject ->
                val sKey = subjectKey(prefix, subject.title)
                val subjectOpen = sKey in expanded
                item(key = sKey) {
                    SubjectHeader(
                        title = subject.title,
                        subtitle = subjectSubtitle(subject),
                        count = subject.count,
                        expanded = subjectOpen,
                        onClick = { toggle(sKey) },
                        modifier = Modifier.padding(horizontal = DetailMargin)
                    )
                }
                if (subjectOpen) {
                    subject.lessons.forEach { lesson ->
                        val lKey = lessonKey(prefix, subject.title, lesson.title)
                        val lessonOpen = lKey in expanded
                        item(key = lKey) {
                            LessonHeader(
                                title = lesson.title,
                                subtitle = lessonSubtitle(lesson),
                                count = lesson.items.size,
                                expanded = lessonOpen,
                                onClick = { toggle(lKey) },
                                modifier = Modifier.padding(start = LessonIndent, end = DetailMargin)
                            )
                        }
                        if (lessonOpen) lessonContent(this, lKey, lesson)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Éléments d'interface
// ---------------------------------------------------------------------------

@Composable
private fun DetailHeader(
    icon: String,
    title: String,
    count: Int,
    countLabel: String,
    onBack: () -> Unit
) {
    val haptic = rememberHaptic()
    val shown = rememberCountUp(count, durationMillis = 900)
    val shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(KalynoBlue, KalynoTealDark)))
    ) {
        DriftingBlob(
            modifier = Modifier
                .size(190.dp)
                .align(Alignment.TopEnd)
                .offset(x = 64.dp, y = (-54).dp),
            color = Color.White.copy(alpha = 0.08f)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    KalynoSvgIcon(name = icon, size = 30.dp, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = shown.toString(),
                        color = Color.White,
                        fontSize = 40.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = countLabel,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailLoading(icon: String, title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KalynoBackground)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DetailHeader(icon = icon, title = title, count = 0, countLabel = "chargement…", onBack = onBack)
        ShimmerBlock(Modifier.padding(horizontal = DetailMargin).fillMaxWidth().height(72.dp), RoundedCornerShape(20.dp))
        ShimmerBlock(Modifier.padding(horizontal = DetailMargin).fillMaxWidth().height(72.dp), RoundedCornerShape(20.dp))
        ShimmerBlock(Modifier.padding(horizontal = DetailMargin).fillMaxWidth().height(72.dp), RoundedCornerShape(20.dp))
    }
}

@Composable
private fun SubjectHeader(
    title: String,
    subtitle: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "subjectChevron")
    KalynoCard(
        modifier = modifier.fillMaxWidth(),
        onClick = {
            haptic()
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        contentPadding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KalynoBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "graduation-cap", size = 24.dp, tint = KalynoBlue)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = KalynoText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            Spacer(Modifier.width(8.dp))
            Box(Modifier.rotate(rotation)) {
                KalynoSvgIcon(name = "chevron-down", size = 18.dp, tint = KalynoMuted)
            }
        }
    }
}

@Composable
private fun LessonHeader(
    title: String,
    subtitle: String?,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "lessonChevron")
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, KalynoOutline, shape)
            .clickable {
                haptic()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KalynoAmberSoft),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = "book-open", size = 17.dp, tint = KalynoAmberDeep)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = KalynoText,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(text = subtitle, color = KalynoMuted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            color = KalynoMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.rotate(rotation)) {
            KalynoSvgIcon(name = "chevron-down", size = 16.dp, tint = KalynoMuted)
        }
    }
}

@Composable
private fun ExpandAllLink(allOpen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    val rotation by animateFloatAsState(if (allOpen) 180f else 0f, label = "expandAllChevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KalynoBlueSoft)
            .clickable {
                haptic()
                onClick()
            }
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (allOpen) "Tout replier" else "Tout déplier",
            color = KalynoBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.rotate(rotation)) {
            KalynoSvgIcon(name = "chevron-down", size = 14.dp, tint = KalynoBlue)
        }
    }
}

/** Contrôle segmenté Tout / Succès / Échecs, avec le nombre de chaque. */
@Composable
private fun StatusSegment(
    selected: HistoryStatus,
    total: Int,
    successes: Int,
    failures: Int,
    onSelect: (HistoryStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, KalynoOutline, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HistoryStatus.entries.forEach { status ->
            val isSelected = selected == status
            val container by animateColorAsState(
                targetValue = if (isSelected) KalynoBlue else Color.Transparent,
                animationSpec = tween(160),
                label = "segmentContainer"
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) Color.White else KalynoMuted,
                animationSpec = tween(160),
                label = "segmentContent"
            )
            val label = when (status) {
                HistoryStatus.ALL -> "Tout · $total"
                HistoryStatus.SUCCESS -> "Succès · $successes"
                HistoryStatus.FAILURE -> "Échecs · $failures"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(container)
                    .clickable {
                        haptic()
                        onSelect(status)
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
}
