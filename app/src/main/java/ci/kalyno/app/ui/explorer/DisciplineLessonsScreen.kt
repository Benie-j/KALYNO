package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.components.KalynoIconEmptyState
import ci.kalyno.app.ui.components.KalynoProgressBar
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.icons.iconAssetFor
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText

private val NODE_SIZE = 40.dp
private val NODE_TOP = 14.dp

/** Une leçon est terminée quand toutes ses cartes ont été découvertes. */
private fun ExplorerChapter.isDone(): Boolean = questionCount > 0 && discovered >= questionCount

/**
 * Une section de la page : un thème et ses leçons.
 * [theme] est null pour les leçons que l'admin n'a pas encore rangées dans un thème.
 */
private data class ThemeSection(val theme: ExplorerTheme?, val lessons: List<ExplorerChapter>)

/**
 * Page "Thèmes et leçons" d'une discipline : Discipline > Thème > Leçon.
 *
 * Chaque thème ouvre une section ; ses leçons forment un parcours (roadmap) : étapes terminées,
 * étape en cours, étapes à venir. Tant que l'admin n'a défini aucun thème, la page garde
 * l'ancien rendu (un seul parcours, sans en-têtes).
 */
@Composable
fun DisciplineLessonsScreen(
    discipline: Discipline,
    themes: List<ExplorerTheme>,
    lessons: List<ExplorerChapter>,
    onBack: () -> Unit,
    onOpenLesson: (ExplorerChapter) -> Unit
) {
    val revealed = remember(discipline.id) { HashSet<String>() }

    val sections = remember(themes, lessons) {
        val knownIds = themes.mapTo(HashSet()) { it.id }
        val grouped = themes.map { theme -> ThemeSection(theme, lessons.filter { it.themeId == theme.id }) }
            .filter { it.lessons.isNotEmpty() }
        val loose = lessons.filter { it.themeId == null || it.themeId !in knownIds }
        if (loose.isEmpty()) grouped else grouped + ThemeSection(null, loose)
    }
    val hasThemes = themes.isNotEmpty()

    // Étape en cours = première leçon pas encore terminée, tous thèmes confondus
    val currentId = remember(sections) {
        sections.asSequence().flatMap { it.lessons.asSequence() }.firstOrNull { !it.isDone() }?.id
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 28.dp)
    ) {
        ExplorerBackButton(onClick = onBack)
        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(KalynoBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = iconAssetFor(discipline.iconKey), size = 26.dp, tint = KalynoBlue)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = discipline.name,
                color = KalynoText,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = summaryLine(themeCount = sections.count { it.theme != null }, lessonCount = lessons.size),
            color = KalynoMuted,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(22.dp))

        if (lessons.isEmpty()) {
            KalynoIconEmptyState(
                icon = "book-open",
                title = "Ça arrive bientôt",
                text = "Tes profs préparent les leçons de cette discipline. Reviens vite, on est ensemble."
            )
        }

        var position = 0
        sections.forEachIndexed { sectionIndex, section ->
            if (hasThemes) {
                ThemeHeader(
                    overline = section.theme?.let { "THÈME ${themes.indexOfFirst { t -> t.id == it.id } + 1}" },
                    title = section.theme?.title ?: "Autres leçons",
                    lessons = section.lessons
                )
                Spacer(Modifier.height(14.dp))
            }

            section.lessons.forEachIndexed { index, lesson ->
                val globalIndex = position++
                RevealOnAppear(id = lesson.id, index = globalIndex, revealed = revealed, bottomSpace = 0.dp) {
                    RoadmapStep(
                        number = index + 1,
                        lesson = lesson,
                        isCurrent = lesson.id == currentId,
                        isFirst = index == 0,
                        isLast = index == section.lessons.lastIndex,
                        aboveDone = index > 0 && section.lessons[index - 1].isDone(),
                        belowDone = lesson.isDone(),
                        onClick = { onOpenLesson(lesson) }
                    )
                }
            }

            if (sectionIndex != sections.lastIndex) Spacer(Modifier.height(26.dp))
        }
    }
}

private fun summaryLine(themeCount: Int, lessonCount: Int): String {
    val lessons = when (lessonCount) {
        0 -> return "Aucune leçon publiée"
        1 -> "1 leçon"
        else -> "$lessonCount leçons"
    }
    return when (themeCount) {
        0 -> lessons
        1 -> "1 thème · $lessons"
        else -> "$themeCount thèmes · $lessons"
    }
}

/** En-tête d'un thème : numéro, titre, leçons terminées et barre de progression. */
@Composable
private fun ThemeHeader(overline: String?, title: String, lessons: List<ExplorerChapter>) {
    val done = lessons.count { it.isDone() }
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KalynoBlueSoft)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (overline != null) {
                    Text(
                        text = overline,
                        color = KalynoBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = title,
                    color = KalynoText,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(10.dp))
            KalynoSvgIcon(name = "layers", size = 22.dp, tint = KalynoBlue)
        }
        Spacer(Modifier.height(10.dp))
        KalynoProgressBar(
            progress = if (lessons.isEmpty()) 0f else done.toFloat() / lessons.size,
            color = KalynoBlue,
            track = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$done sur ${lessons.size} terminée${if (done > 1) "s" else ""}",
            color = KalynoMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Une étape du parcours : rail vertical (pastille + trait) à gauche, carte de la leçon à droite. */
@Composable
private fun RoadmapStep(
    number: Int,
    lesson: ExplorerChapter,
    isCurrent: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    aboveDone: Boolean,
    belowDone: Boolean,
    onClick: () -> Unit
) {
    val done = lesson.isDone()

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // ----- Rail : traits colorés selon l'avancement + pastille numérotée -----
        Box(
            modifier = Modifier
                .width(NODE_SIZE)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2f
                    val center = (NODE_TOP + NODE_SIZE / 2).toPx()
                    val stroke = 3.dp.toPx()
                    if (!isFirst) {
                        drawLine(
                            color = if (aboveDone) KalynoBlue else KalynoOutline,
                            start = Offset(x, 0f),
                            end = Offset(x, center),
                            strokeWidth = stroke
                        )
                    }
                    if (!isLast) {
                        drawLine(
                            color = if (belowDone) KalynoBlue else KalynoOutline,
                            start = Offset(x, center),
                            end = Offset(x, size.height),
                            strokeWidth = stroke
                        )
                    }
                }
        ) {
            val nodeModifier = Modifier
                .padding(top = NODE_TOP)
                .size(NODE_SIZE)
                .align(Alignment.TopCenter)
                .clip(CircleShape)
            when {
                done -> Box(
                    modifier = nodeModifier.background(KalynoBlue),
                    contentAlignment = Alignment.Center
                ) {
                    KalynoSvgIcon(name = "check", size = 20.dp, tint = Color.White)
                }

                isCurrent -> Box(
                    modifier = nodeModifier
                        .background(Color.White)
                        .border(3.dp, KalynoBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number.toString(),
                        color = KalynoBlue,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                else -> Box(
                    modifier = nodeModifier.background(KalynoBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number.toString(),
                        color = KalynoMuted,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // ----- Carte de la leçon -----
        Box(modifier = Modifier.weight(1f).padding(bottom = 14.dp)) {
            LessonStepCard(lesson = lesson, onClick = onClick)
        }
    }
}

@Composable
private fun LessonStepCard(lesson: ExplorerChapter, onClick: () -> Unit) {
    KalynoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        contentPadding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (!lesson.tag.isNullOrBlank()) {
                    SmartTagPill(lesson.tag)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = lesson.title,
                    color = KalynoText,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val meta = buildList {
                    if (lesson.minutes > 0) add("${lesson.minutes} min")
                    if (lesson.sessions.isNotEmpty()) add("${lesson.sessions.size} séances")
                    else if (lesson.capsules.isNotEmpty()) add("${lesson.capsules.size} blocs")
                    if (lesson.questionCount > 0) add("${lesson.questionCount} questions")
                }
                if (meta.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KalynoSvgIcon(name = "clock", size = 13.dp, tint = KalynoMuted)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = meta.joinToString(" · "),
                            color = KalynoMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            KalynoSvgIcon(name = "chevron-right", size = 20.dp, tint = KalynoMuted)
        }

        if (lesson.questionCount > 0) {
            Spacer(Modifier.height(8.dp))
            KalynoProgressBar(progress = lesson.progress, height = 6.dp)
        }
    }
}
