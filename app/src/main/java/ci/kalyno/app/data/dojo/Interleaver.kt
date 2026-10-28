package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.dashboard.ReviewItem
import java.util.Random

/**
 * Entrelacement : on alterne les sujets au lieu d'enchaîner une même matière.
 * Plusieurs matières -> on alterne les matières ; une seule -> on alterne les chapitres.
 * Jamais deux questions du même groupe d'affilée tant qu'un autre est disponible.
 * L'ordre de priorité FSRS est conservé dans chaque groupe.
 */
object Interleaver {

    fun mix(items: List<ReviewItem>, seed: Long = 0L): List<ReviewItem> {
        if (items.size < 3) return items

        val bySubject = items.map { it.subjectName }.distinct().size > 1
        val queues = LinkedHashMap<String, ArrayDeque<ReviewItem>>()
        for (item in items) {
            val key = if (bySubject) item.subjectName else item.chapterTitle
            queues.getOrPut(key) { ArrayDeque() }.addLast(item)
        }
        if (queues.size < 2) return items

        val random = Random(seed)
        val result = ArrayList<ReviewItem>(items.size)
        var last: String? = null

        while (queues.isNotEmpty()) {
            val others = queues.filterKeys { it != last }
            val pool = if (others.isEmpty()) queues else others
            val biggest = pool.values.maxOf { it.size }
            val ties = pool.filterValues { it.size == biggest }.keys.toList()
            val pick = ties[random.nextInt(ties.size)]

            val queue = queues.getValue(pick)
            result += queue.removeFirst()
            if (queue.isEmpty()) queues.remove(pick)
            last = pick
        }
        return result
    }
}
