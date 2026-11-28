package ci.kalyno.app.ui.progress

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
