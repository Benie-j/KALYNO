package ci.kalyno.app.ui.explorer

/**
 * Une leçon proposée dans « Pour toi » parce que le moteur FSRS juge ses cartes à réviser.
 * [reviewChapter] = la même leçon réduite aux seules cartes dues : c'est elle qui lance la révision.
 */
data class ForYouPick(
    val chapter: ExplorerChapter,
    val dueCardIds: Set<String>,
    val relearnCount: Int
) {
    val dueCount: Int get() = dueCardIds.size
    val reviewChapter: ExplorerChapter = chapter.copy(items = chapter.items.filter { it.cardId in dueCardIds })
}
