package ci.kalyno.app.ui.progress

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.kalyno.app.data.dashboard.MasteredCard
import ci.kalyno.app.data.dashboard.MasteryTier
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

/** En-tête de la liste : nombre de cartes maîtrisées (distinct du nombre de notions). */
@Composable
internal fun MasteredCardsHeader(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Cartes maîtrisées",
            color = KalynoText,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f)
        )
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
internal fun MasteredCardsEmptyText() {
    Text(
        text = "Aucune carte maîtrisée pour l'instant : une carte apparaît ici après 3 répétitions réussies.",
        color = KalynoMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

/** Une carte maîtrisée : question, matière, chapitre, répétitions réussies, niveau et rétention. */
@Composable
internal fun MasteredCardItem(card: MasteredCard, modifier: Modifier = Modifier) {
    val style = card.tier.cardStyle()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(BorderStroke(1.dp, KalynoOutline), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(style.soft),
            contentAlignment = Alignment.Center
        ) {
            KalynoSvgIcon(name = style.icon, size = 18.dp, tint = style.tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            MathText(
                text = card.question,
                color = KalynoText,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            val where = listOf(card.subjectName, card.chapterTitle).filter { it.isNotBlank() }.joinToString(" · ")
            if (where.isNotEmpty()) {
                Text(
                    text = where,
                    color = KalynoMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = if (card.repetitions == 1) "1 répétition réussie" else "${card.repetitions} répétitions réussies",
                color = style.tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(style.soft)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(text = style.label, color = style.tint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            card.score?.let { score ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${(score.coerceIn(0f, 1f) * 100).toInt()} % rétention",
                    color = KalynoMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
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
