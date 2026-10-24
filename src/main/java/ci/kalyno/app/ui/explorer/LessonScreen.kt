package ci.kalyno.app.ui.explorer

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.ui.components.KalynoEmptyState
import ci.kalyno.app.ui.components.KalynoPrimaryButton
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoTealDark

/**
 * Écran de leçon dynamique.
 *
 * Le contenu n'est pas figé : chaque capsule configurée par l'admin devient un bloc empilé
 * (introduction, texte, règle d'or, astuce, piège). Les blocs apparaissent en cascade avec un
 * fondu + glissement vers le haut ([RevealOnAppear]).
 */
@Composable
fun LessonScreen(
    chapter: ExplorerChapter,
    onBack: () -> Unit,
    onStartQuestions: () -> Unit
) {
    val scroll = rememberScrollState()
    val blocks = remember(chapter.capsules) { chapter.capsules.map { it.toLessonBlock() } }
    val revealed = remember(chapter.id) { HashSet<String>() }

    // Progression de lecture (fine barre sous l'en-tête)
    val readProgress by remember {
        derivedStateOf { if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f }
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
            Text(
                text = "${chapter.subjectEmoji} ${chapter.subjectName}",
                color = KalynoBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
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

        // ----- Blocs empilés -----
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 32.dp)
        ) {
            RevealOnAppear(id = "hero", index = 0, revealed = revealed, bottomSpace = 20.dp) {
                LessonHero(chapter)
            }

            blocks.forEachIndexed { i, block ->
                RevealOnAppear(id = block.id, index = i + 1, revealed = revealed) {
                    LessonBlockView(block)
                }
            }

            if (blocks.isEmpty()) {
                RevealOnAppear(id = "empty", index = 1, revealed = revealed) {
                    KalynoEmptyState(
                        emoji = "✍️",
                        title = "Leçon en préparation",
                        text = "Le contenu de cette leçon apparaîtra ici dès sa publication."
                    )
                }
            }

            if (chapter.questionCount > 0) {
                RevealOnAppear(id = "cta", index = blocks.size + 1, revealed = revealed, bottomSpace = 0.dp) {
                    KalynoPrimaryButton(
                        label = "DÉMARRER LES ${chapter.questionCount} QUESTIONS",
                        onClick = onStartQuestions
                    )
                }
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
            .padding(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = chapter.subjectEmoji, fontSize = 40.sp)
        Text(
            text = chapter.title,
            color = Color.White,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold
        )
        val meta = buildList {
            if (chapter.minutes > 0) add("⏱️ ${chapter.minutes} min")
            if (chapter.capsules.isNotEmpty()) add("📖 ${chapter.capsules.size} blocs")
            if (chapter.questionCount > 0) add("❓ ${chapter.questionCount} questions")
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
