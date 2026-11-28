package ci.kalyno.app.data.dashboard

import ci.kalyno.app.data.srs.MasteredCardRow
import ci.kalyno.app.data.srs.SrsEngine

/**
 * Règles de maîtrise de l'écran « Ma progression », sans dépendance Android.
 * Une carte est maîtrisée dès [MASTERED_REPETITIONS] répétitions réussies (CardProgressEntity.repetitions,
 * remis à 0 par le moteur après un « Oublié »). Une notion = un chapitre contenant au moins une carte maîtrisée.
 */
object MasteryCalculator {
    /** Nombre de répétitions réussies d'affilée pour qu'une carte soit maîtrisée. */
    const val MASTERED_REPETITIONS = 3
    const val ADVANCED_REPETITIONS = 5
    const val EXPERT_REPETITIONS = 8

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Palier d'une carte, ou null tant que le seuil de maîtrise n'est pas atteint. */
    fun tierOf(repetitions: Int): MasteryTier? = when {
        repetitions >= EXPERT_REPETITIONS -> MasteryTier.EXPERTE
        repetitions >= ADVANCED_REPETITIONS -> MasteryTier.AVANCEE
        repetitions >= MASTERED_REPETITIONS -> MasteryTier.SOLIDE
        else -> null
    }

    /** Probabilité de rétention (courbe de l'oubli FSRS) d'une carte, null si jamais revue ou sans stabilité. */
    fun retention(row: MasteredCardRow, now: Long): Float? {
        val last = row.lastReviewedAt ?: return null
        val stability = row.stability.takeIf { it > 0.0 } ?: return null
        return SrsEngine.retrievability((now - last) / DAY_MS.toDouble(), stability).toFloat()
    }

    /** Cartes maîtrisées une à une, des plus solides aux plus récentes (l'ordre SQL par date est conservé à égalité). */
    fun cards(rows: List<MasteredCardRow>, now: Long): List<MasteredCard> =
        rows.mapNotNull { row ->
            val tier = tierOf(row.repetitions) ?: return@mapNotNull null
            MasteredCard(
                cardId = row.flashcardId,
                question = row.question,
                subjectName = row.subjectName,
                chapterTitle = row.chapitre,
                repetitions = row.repetitions,
                tier = tier,
                score = retention(row, now)
            )
        }
            .distinctBy { it.cardId }
            .sortedWith(compareByDescending<MasteredCard> { it.tier }.thenByDescending { it.repetitions })

    /** Notions maîtrisées : cartes maîtrisées regroupées par matière et chapitre. */
    fun notions(rows: List<MasteredCardRow>, now: Long): List<MasteredNotion> =
        rows.filter { tierOf(it.repetitions) != null }
            .groupBy { "${it.subjectId}::${it.chapitre}" }
            .mapNotNull { (key, group) ->
                val chapitre = group.first().chapitre.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val tier = tierOf(group.maxOf { it.repetitions }) ?: return@mapNotNull null
                val score = group.mapNotNull { retention(it, now) }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toFloat()
                MasteredNotion(
                    key = key,
                    label = chapitre,
                    domain = group.first().subjectName,
                    masteredCards = group.size,
                    tier = tier,
                    score = score
                )
            }
            .sortedWith(compareByDescending<MasteredNotion> { it.tier }.thenByDescending { it.masteredCards })
}
