package ci.kalyno.app.ui.explorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.LearningCapsule
import ci.kalyno.app.ui.components.rememberHaptic
import ci.kalyno.app.ui.theme.KalynoAmberDeep
import ci.kalyno.app.ui.theme.KalynoAmberSoft
import ci.kalyno.app.ui.theme.KalynoBlue
import ci.kalyno.app.ui.theme.KalynoBlueSoft
import ci.kalyno.app.ui.theme.KalynoMuted
import ci.kalyno.app.ui.theme.KalynoOutline
import ci.kalyno.app.ui.theme.KalynoText
import ci.kalyno.app.ui.theme.KalynoYellow
import kotlinx.coroutines.delay
import java.text.Normalizer

// ===========================================================================
// MODÈLE : un bloc de leçon = une "capsule" publiée par l'admin
// ===========================================================================

enum class LessonBlockType {
    /** Introduction (paragraphe d'accroche, sous le titre de la leçon). */
    INTRO,

    /** Texte explicatif aéré. */
    TEXT,

    /** Formule ou Règle d'Or : fond teinté doux, bordure accentuée. */
    RULE,

    /** Astuce : accent orange chaleureux. */
    TIP,

    /** Piège à éviter : accent orange chaleureux, variante alerte. */
    PITFALL
}

data class LessonBlock(
    val id: String,
    val type: LessonBlockType,
    val title: String?,
    /** Paragraphes déjà découpés (séparés par une ligne vide dans le back-end). */
    val paragraphs: List<String>
)

/**
 * Transforme une capsule admin en bloc d'UI.
 *
 * 1) Si l'admin a renseigné `kind`, c'est lui qui décide.
 * 2) Sinon (back-end pas encore à jour), on devine d'après le début du titre :
 *    "Formule…", "Règle…", "Astuce…", "Piège…", "Attention…", "Intro…".
 */
fun LearningCapsule.toLessonBlock(): LessonBlock {
    val fromKind = when (kind.normalizedKey()) {
        "intro", "introduction", "title", "titre" -> LessonBlockType.INTRO
        "rule", "regle", "regle_d_or", "regledor", "formula", "formule" -> LessonBlockType.RULE
        "tip", "astuce", "conseil" -> LessonBlockType.TIP
        "pitfall", "piege", "warning", "attention" -> LessonBlockType.PITFALL
        else -> null
    }

    val titleKey = title.normalizedKey()
    val type = fromKind ?: when {
        titleKey.startsWith("formule") || titleKey.startsWith("regle") -> LessonBlockType.RULE
        titleKey.startsWith("astuce") || titleKey.startsWith("conseil") -> LessonBlockType.TIP
        titleKey.startsWith("piege") || titleKey.startsWith("attention") ||
            titleKey.startsWith("erreur") -> LessonBlockType.PITFALL
        titleKey.startsWith("intro") -> LessonBlockType.INTRO
        else -> LessonBlockType.TEXT
    }

    // Titre purement générique ("Astuce") : le label du bloc suffit, on évite le doublon.
    val displayTitle = title.trim().takeIf { it.isNotEmpty() && titleKey !in GENERIC_TITLES }

    return LessonBlock(
        id = id,
        type = type,
        title = displayTitle,
        paragraphs = if (body.contains('<')) {
            listOf(body.trim()).filter { it.isNotEmpty() }
        } else {
            body.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        }
    )
}

private val GENERIC_TITLES = setOf(
    "astuce", "conseil", "piege", "piege_a_eviter", "regle", "regle_d_or", "formule",
    "introduction", "intro", "attention"
)

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

/** minuscules, sans accents, espaces/apostrophes -> "_" (ex. "Règle d'Or" -> "regle_d_or"). */
private fun String.normalizedKey(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

// ===========================================================================
// ANIMATION D'APPARITION (fondu + glissement vers le haut)
// ===========================================================================

/**
 * Fait apparaître [content] avec `fadeIn` + `slideInVertically`, en cascade selon [index].
 *
 * [revealed] mémorise les blocs déjà montrés : on ne rejoue pas l'animation si l'écran est
 * recomposé (rotation, retour du clavier…). L'espacement [bottomSpace] est DANS le bloc animé
 * pour que les blocs encore invisibles ne créent pas de trous.
 */
@Composable
fun RevealOnAppear(
    id: String,
    index: Int,
    revealed: MutableSet<String>,
    modifier: Modifier = Modifier,
    bottomSpace: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    val visibleState = remember(id) { MutableTransitionState(id in revealed) }

    LaunchedEffect(id) {
        if (!visibleState.currentState) {
            delay(index.coerceAtMost(MAX_STAGGERED_BLOCKS) * STAGGER_MS)
            visibleState.targetState = true
            revealed.add(id)
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 420)) +
            slideInVertically(animationSpec = tween(durationMillis = 420)) { fullHeight -> fullHeight / 5 }
    ) {
        Box(modifier = Modifier.padding(bottom = bottomSpace)) { content() }
    }
}

private const val STAGGER_MS = 90L
private const val MAX_STAGGERED_BLOCKS = 8

// ===========================================================================
// BOUTON RETOUR (partagé par les écrans Discipline et Leçon)
// ===========================================================================

@Composable
fun ExplorerBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, KalynoOutline, CircleShape)
            .clickable {
                haptic()
                onClick()
            }
            .semantics { contentDescription = "Retour" },
        contentAlignment = Alignment.Center
    ) {
        Text(text = "←", color = KalynoText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ===========================================================================
// RENDU DES BLOCS
// ===========================================================================

/** Aiguillage : choisit le composant selon le type de bloc configuré par l'admin. */
@Composable
fun LessonBlockView(block: LessonBlock, modifier: Modifier = Modifier) {
    when (block.type) {
        LessonBlockType.INTRO -> IntroBlock(block, modifier)
        LessonBlockType.TEXT -> TextBlock(block, modifier)
        LessonBlockType.RULE -> AccentBlock(
            block = block,
            label = "⭐ FORMULE · RÈGLE D'OR",
            labelColor = KalynoBlue,
            accent = KalynoBlue,
            container = KalynoBlueSoft,
            emphasizeBody = true,
            modifier = modifier
        )
        LessonBlockType.TIP -> AccentBlock(
            block = block,
            label = "💡 ASTUCE",
            labelColor = KalynoAmberDeep,
            accent = KalynoYellow,
            container = KalynoAmberSoft,
            modifier = modifier
        )
        LessonBlockType.PITFALL -> AccentBlock(
            block = block,
            label = "⚠️ PIÈGE À ÉVITER",
            labelColor = KalynoAmberDeep,
            accent = KalynoYellow,
            container = KalynoAmberSoft,
            modifier = modifier
        )
    }
}

/** Introduction : paragraphe d'accroche sans cadre, juste sous le titre. */
@Composable
private fun IntroBlock(block: LessonBlock, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        block.title?.let {
            Text(text = it, color = KalynoText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        block.paragraphs.forEach {
            Text(
                text = it,
                color = KalynoMuted,
                fontSize = 17.sp,
                lineHeight = 27.sp,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

/** Texte explicatif aéré : carte blanche, grande interligne, paragraphes bien séparés. */
@Composable
private fun TextBlock(block: LessonBlock, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, shape)
            .border(1.dp, KalynoOutline, shape)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        block.title?.let {
            Text(
                text = it,
                color = KalynoText,
                fontSize = 19.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        block.paragraphs.forEach {
            RichText(it, KalynoText, 16.sp, 26.sp)
        }
    }
}

/**
 * Bloc d'accent (Règle d'Or / Astuce / Piège) : fond teinté doux, bordure colorée
 * et barre verticale d'accent à gauche.
 */
@Composable
private fun AccentBlock(
    block: LessonBlock,
    label: String,
    labelColor: Color,
    accent: Color,
    container: Color,
    modifier: Modifier = Modifier,
    emphasizeBody: Boolean = false
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(container)
            .border(1.5.dp, accent, shape)
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            block.title?.let {
                Text(
                    text = it,
                    color = KalynoText,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            block.paragraphs.forEach {
                RichText(
                    it,
                    KalynoText,
                    if (emphasizeBody) 18.sp else 15.sp,
                    if (emphasizeBody) 28.sp else 23.sp,
                    if (emphasizeBody) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
