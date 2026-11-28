package ci.kalyno.app.ui.exercises

import ci.kalyno.app.data.LearningChapter
import ci.kalyno.app.data.LearningSubject
import org.json.JSONObject

// ===========================================================================
// Rangement des exercices IA : discipline -> leçon -> séance (si disponible).
//
// Aucune donnée inventée : chaque exercice généré porte déjà `subject_id`, `chapter_id` (la leçon)
// et `source_refs` (les blocs de cours utilisés). Chaque bloc de cours connaît sa séance
// (`session_id`). Les noms viennent du contenu publié déjà chargé par l'app.
// ===========================================================================

internal data class ExerciseSessionGroup(
    val key: String,
    /** « Séance 2 · Titre » ; null quand l'exercice n'est rattaché à aucune séance. */
    val label: String?,
    val items: List<JSONObject>
)

internal data class ExerciseLessonGroup(
    val key: String,
    /** Titre de la leçon ; null quand la leçon n'est pas identifiable. */
    val title: String?,
    val done: Int,
    val total: Int,
    val sessions: List<ExerciseSessionGroup>
)

internal data class ExerciseSubjectGroup(
    val key: String,
    val name: String,
    val iconKey: String?,
    val done: Int,
    val total: Int,
    val lessons: List<ExerciseLessonGroup>
)

private const val UNKNOWN_SUBJECT_KEY = "_autres"
private const val UNKNOWN_LESSON_KEY = "_lecon"
private const val NO_SESSION_KEY = "_sans_seance"

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

private class Placement(
    val subjectKey: String,
    val subjectName: String,
    val subjectIcon: String?,
    val subjectOrder: Int,
    val lessonKey: String,
    val lessonTitle: String?,
    val lessonOrder: Int,
    val sessionKey: String,
    val sessionLabel: String?,
    val sessionOrder: Int
)

/**
 * Traduit les identifiants portés par les exercices en noms lisibles (discipline, leçon, séance).
 * Un exercice dont la leçon n'est pas connue est rangé sous « Autres exercices » : rien ne disparaît.
 */
internal class ExerciseCatalog(private val subjects: List<LearningSubject>) {

    private class ChapterRef(val subject: LearningSubject, val subjectOrder: Int, val chapter: LearningChapter, val chapterOrder: Int)

    private val chapterById: Map<String, ChapterRef> = buildMap {
        subjects.forEachIndexed { si, subject ->
            subject.chapters.forEachIndexed { ci, chapter ->
                put(chapter.id, ChapterRef(subject, si, chapter, ci))
            }
        }
    }

    private val subjectById: Map<String, Pair<Int, LearningSubject>> =
        subjects.withIndex().associate { it.value.id to (it.index to it.value) }

    /** Vrai si l'on sait nommer la leçon de cet exercice. */
    fun knows(exercise: JSONObject): Boolean {
        val chapterId = exercise.stringOrNull("chapter_id") ?: return false
        return chapterById.containsKey(chapterId)
    }

    private fun place(exercise: JSONObject): Placement {
        val chapterId = exercise.stringOrNull("chapter_id")
        val ref = chapterId?.let { chapterById[it] }

        if (ref == null) {
            // Leçon inconnue : on tente au moins la discipline
            val subjectId = exercise.stringOrNull("subject_id")
            val known = subjectId?.let { subjectById[it] }
            return Placement(
                subjectKey = subjectId?.takeIf { known != null } ?: UNKNOWN_SUBJECT_KEY,
                subjectName = known?.second?.name?.takeIf { it.isNotBlank() } ?: "Autres exercices",
                subjectIcon = known?.second?.iconKey,
                subjectOrder = known?.first ?: Int.MAX_VALUE,
                lessonKey = UNKNOWN_LESSON_KEY,
                lessonTitle = null,
                lessonOrder = Int.MAX_VALUE,
                sessionKey = NO_SESSION_KEY,
                sessionLabel = null,
                sessionOrder = -1
            )
        }

        // Séances visibles par l'élève (une séance masquée par l'admin n'est jamais nommée)
        val available = ref.chapter.sessions.filter { it.isAvailable }
        val refs = exercise.optJSONArray("source_refs")
        val sourceIds = mutableSetOf<String>()
        if (refs != null) {
            for (i in 0 until refs.length()) {
                val id = refs.optString(i)
                if (id.isNotBlank()) sourceIds.add(id)
            }
        }
        // Séance la plus précoce parmi les blocs de cours réellement utilisés par l'exercice
        val sessionIndex = ref.chapter.capsules
            .asSequence()
            .filter { it.id in sourceIds }
            .mapNotNull { capsule -> capsule.sessionId }
            .map { sessionId -> available.indexOfFirst { it.id == sessionId } }
            .filter { it >= 0 }
            .minOrNull()

        val session = if (sessionIndex != null) available[sessionIndex] else null
        val label = if (session != null && sessionIndex != null) {
            val n = sessionIndex + 1
            val title = session.title.trim()
            when {
                title.isBlank() -> "Séance $n"
                title.startsWith("Séance", ignoreCase = true) -> title
                else -> "Séance $n · $title"
            }
        } else {
            null
        }

        return Placement(
            subjectKey = ref.subject.id,
            subjectName = ref.subject.name.ifBlank { "Autres exercices" },
            subjectIcon = ref.subject.iconKey,
            subjectOrder = ref.subjectOrder,
            lessonKey = ref.chapter.id,
            lessonTitle = ref.chapter.title.takeIf { it.isNotBlank() },
            lessonOrder = ref.chapterOrder,
            sessionKey = session?.id ?: NO_SESSION_KEY,
            sessionLabel = label,
            sessionOrder = sessionIndex ?: -1
        )
    }

    /**
     * Range les exercices visibles par discipline, puis par leçon, puis par séance.
     * Les compteurs (terminés / total) portent sur TOUS les exercices de la discipline ou de la leçon,
     * pour rester stables quand un filtre est actif.
     */
    fun group(
        all: List<JSONObject>,
        visible: List<JSONObject>,
        isCompleted: (JSONObject) -> Boolean
    ): List<ExerciseSubjectGroup> {
        val allPlaced = all.map { it to place(it) }
        val subjectTotals = allPlaced.groupBy { it.second.subjectKey }
        val lessonTotals = allPlaced.groupBy { it.second.subjectKey + "|" + it.second.lessonKey }

        val visiblePlaced = visible.map { it to place(it) }

        return visiblePlaced
            .groupBy { it.second.subjectKey }
            .values
            .sortedBy { it.first().second.subjectOrder }
            .map { inSubject ->
                val first = inSubject.first().second
                val subjectAll = subjectTotals[first.subjectKey].orEmpty()

                val lessons = inSubject
                    .groupBy { it.second.lessonKey }
                    .values
                    .sortedBy { it.first().second.lessonOrder }
                    .map { inLesson ->
                        val lp = inLesson.first().second
                        val lessonAll = lessonTotals[first.subjectKey + "|" + lp.lessonKey].orEmpty()
                        ExerciseLessonGroup(
                            key = lp.lessonKey,
                            title = lp.lessonTitle,
                            done = lessonAll.count { isCompleted(it.first) },
                            total = lessonAll.size,
                            sessions = inLesson
                                .groupBy { it.second.sessionKey }
                                .values
                                .sortedBy { it.first().second.sessionOrder }
                                .map { inSession ->
                                    ExerciseSessionGroup(
                                        key = inSession.first().second.sessionKey,
                                        label = inSession.first().second.sessionLabel,
                                        items = inSession.map { it.first }
                                    )
                                }
                        )
                    }

                ExerciseSubjectGroup(
                    key = first.subjectKey,
                    name = first.subjectName,
                    iconKey = first.subjectIcon,
                    done = subjectAll.count { isCompleted(it.first) },
                    total = subjectAll.size,
                    lessons = lessons
                )
            }
    }

    companion object {
        val EMPTY = ExerciseCatalog(emptyList())
    }
}
