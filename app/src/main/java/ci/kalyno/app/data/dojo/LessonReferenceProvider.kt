package ci.kalyno.app.data.dojo

import android.content.Context
import ci.kalyno.app.ui.explorer.LearningContentCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fournit, pour chaque carte, les passages de SA leçon, lus dans le cache hors-ligne de l'Explorer.
 * Aucune requête réseau : sans leçon en cache, la carte est simplement corrigée comme avant.
 */
class LessonReferenceProvider(context: Context, private val levelId: String?) {

    private val cache = LearningContentCache(context.applicationContext)

    // cardId -> passages de la leçon (la même liste est partagée par toutes les cartes d'une leçon)
    @Volatile
    private var index: Map<String, List<String>>? = null

    suspend fun passagesFor(cardIds: Collection<String>): Map<String, List<String>> =
        withContext(Dispatchers.IO) {
            val level = levelId ?: return@withContext emptyMap()
            val known = index ?: build(level).also { index = it }
            buildMap {
                for (id in cardIds) known[id]?.let { put(id, it) }
            }
        }

    private fun build(level: String): Map<String, List<String>> {
        val subjects = cache.read(level) ?: return emptyMap()
        val out = HashMap<String, List<String>>()
        for (subject in subjects) {
            for (chapter in subject.chapters) {
                val passages = LessonPassages.fromChapter(chapter)
                if (passages.isEmpty()) continue
                for (card in chapter.cards + chapter.capsules.flatMap { it.cards }) out[card.id] = passages
            }
        }
        return out
    }
}
