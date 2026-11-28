package ci.kalyno.app.ui.progress

/** Libellés de repli : une carte sans matière ou sans leçon n'est jamais perdue, elle est rangée à part. */
internal const val NO_SUBJECT = "Autres matières"
internal const val NO_LESSON = "Leçon sans titre"

/** Les éléments d'une même leçon. */
internal data class LessonGroup<T>(val title: String, val items: List<T>)

/** Les leçons d'une même matière. */
internal data class SubjectGroup<T>(val title: String, val lessons: List<LessonGroup<T>>) {
    val count: Int get() = lessons.sumOf { it.items.size }
}

/**
 * Classe des éléments par matière puis par leçon. L'ordre d'arrivée est conservé : si la liste est triée
 * du plus récent au plus ancien, la matière (et la leçon) travaillée en dernier arrive en premier.
 * Fonction pure, testée sans Android.
 */
internal fun <T> groupBySubjectAndLesson(
    items: List<T>,
    subjectOf: (T) -> String?,
    lessonOf: (T) -> String?
): List<SubjectGroup<T>> {
    val bySubject = LinkedHashMap<String, LinkedHashMap<String, MutableList<T>>>()
    for (item in items) {
        val subject = subjectOf(item)?.trim().orEmpty().ifEmpty { NO_SUBJECT }
        val lesson = lessonOf(item)?.trim().orEmpty().ifEmpty { NO_LESSON }
        bySubject.getOrPut(subject) { LinkedHashMap() }.getOrPut(lesson) { ArrayList() }.add(item)
    }
    return bySubject.map { (subject, lessons) ->
        SubjectGroup(subject, lessons.map { (lesson, list) -> LessonGroup(lesson, list) })
    }
}
