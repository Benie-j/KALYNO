package ci.kalyno.app.ui.explorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.LearningCapsule
import ci.kalyno.app.ui.components.KalynoCard
import ci.kalyno.app.ui.icons.KalynoSvgIcon
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoText

/**
 * Structure pédagogique : une Leçon (chapitre) se compose de Séances chronologiques
 * (Séance 1, Séance 2…), chacune regroupant plusieurs blocs.
 *
 * Les blocs sans séance (leçons créées avant cette structure) restent affichés en tête, sans en-tête :
 * rien ne disparaît tant que l'admin n'a pas rangé ses blocs.
 */
data class SessionGroup(
    /** Numéro affiché (1, 2…) ; null pour les blocs hors séance. */
    val number: Int?,
    val title: String?,
    val blocks: List<LessonBlock>
)

/** Ligne du LazyColumn de la leçon : un en-tête de séance ou un bloc de contenu. */
sealed interface LessonRow {
    val key: String

    data class Header(val group: SessionGroup, override val key: String) : LessonRow
    data class Block(val block: LessonBlock) : LessonRow {
        override val key: String get() = block.id
    }
}

/** Regroupe les blocs de la leçon par séance, dans l'ordre chronologique défini par l'admin. */
fun groupLessonBlocks(chapter: ExplorerChapter): List<SessionGroup> {
    val knownIds = chapter.sessions.map { it.id }.toSet()
    val loose = chapter.capsules.filter { it.sessionId == null || it.sessionId !in knownIds }
    val bySession: Map<String?, List<LearningCapsule>> = chapter.capsules.groupBy { it.sessionId }

    return buildList {
        if (loose.isNotEmpty()) add(SessionGroup(null, null, loose.map { it.toLessonBlock() }))
        chapter.sessions.forEachIndexed { index, session ->
            val capsules = bySession[session.id].orEmpty()
            // Une séance encore vide n'est pas montrée à l'élève
            if (capsules.isNotEmpty()) {
                add(
                    SessionGroup(
                        number = index + 1,
                        title = session.title.takeIf { it.isNotBlank() },
                        blocks = capsules.map { it.toLessonBlock() }
                    )
                )
            }
        }
    }
}

fun buildLessonRows(chapter: ExplorerChapter): List<LessonRow> =
    groupLessonBlocks(chapter).flatMap { group ->
        buildList {
            if (group.number != null) add(LessonRow.Header(group, key = "session-${group.number}"))
            group.blocks.forEach { add(LessonRow.Block(it)) }
        }
    }

/** En-tête d'une séance : pastille numérotée Vert Pétrole + intitulé. */
@Composable
fun SessionHeader(group: SessionGroup, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(KalynoBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = group.number?.toString().orEmpty(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SÉANCE ${group.number}",
                color = KalynoBlue,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.ExtraBold
            )
            group.title?.let {
                Text(
                    text = it,
                    color = KalynoText,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Notes et ressources complémentaires facultatives de la leçon : carte repliable, absente si l'admin n'a rien saisi. */
@Composable
fun LessonComplementCard(complement: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(complement) { mutableStateOf(false) }
    val block = remember(complement) {
        LearningCapsule(id = "complement", title = "", body = complement, minutes = 0, cards = emptyList())
            .toLessonBlock()
    }

    KalynoCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KalynoBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                KalynoSvgIcon(name = "layers", size = 18.dp, tint = KalynoBlue)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notes et ressources",
                    color = KalynoText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(text = "Complément facultatif", color = KalynoMuted, fontSize = 12.sp)
            }
            Box(modifier = Modifier.rotate(if (expanded) 90f else 0f)) {
                KalynoSvgIcon(name = "chevron-right", size = 18.dp, tint = KalynoMuted)
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                LessonBlockView(block)
            }
        }
    }
}
