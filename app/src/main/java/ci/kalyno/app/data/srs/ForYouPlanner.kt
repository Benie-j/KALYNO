package ci.kalyno.app.data.srs

/** Une leçon à réviser, avec les cartes dues classées de la plus urgente à la moins urgente. */
data class ChapterPriority(
    val chapterId: String,
    val dueCardIds: List<String>,
    val relearnCount: Int,
    val score: Float
)

/**
 * Section « Pour toi » : classe les leçons selon l'urgence FSRS de leurs cartes dues. Kotlin pur.
 *
 * Score d'une carte = urgence FSRS (1 - rétention actuelle, pondérée par le coefficient de la matière)
 * x [LearningBehavior.priorityBoost] (oublis, réapprentissage, matière délaissée). La latence agit en amont :
 * une bonne réponse très lente est notée « Hésité », la stabilité baisse, la rétention aussi.
 * Score d'une leçon = somme des [TOP_CARDS_PER_CHAPTER] meilleures cartes : une leçon pleine de cartes
 * urgentes passe devant, sans qu'un gros volume écrase tout.
 */
object ForYouPlanner {

    const val TOP_CARDS_PER_CHAPTER = 5

    fun rank(
        due: List<CardWithContext>,
        chapterOfCard: Map<String, String>,
        now: Long,
        subjectLastStudied: Map<String, Long>,
        limit: Int = 10
    ): List<ChapterPriority> =
        due.mapNotNull { card -> chapterOfCard[card.flashcardId]?.let { it to card } }
            .groupBy({ it.first }, { it.second })
            .map { (chapterId, cards) ->
                val scored = cards
                    .map { it to SrsEngine.urgencyScore(it, now) * LearningBehavior.priorityBoost(it, now, subjectLastStudied) }
                    .sortedByDescending { it.second }
                ChapterPriority(
                    chapterId = chapterId,
                    dueCardIds = scored.map { it.first.flashcardId },
                    relearnCount = cards.count { it.relearnLeft > 0 },
                    score = scored.take(TOP_CARDS_PER_CHAPTER).sumOf { it.second.toDouble() }.toFloat()
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
}
