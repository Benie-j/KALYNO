package ci.kalyno.app.data.dojo

import androidx.room.Embedded
import androidx.room.Relation
import ci.kalyno.app.data.catalog.CatalogLessonEntity
import ci.kalyno.app.data.srs.FlashcardEntity

/**
 * Relation Room « une question appartient à une leçon » : flashcards.lessonId -> catalog_lessons.id.
 *
 * Volontairement SANS @ForeignKey : `catalog_lessons` est vidé puis réinséré à chaque synchronisation
 * (voir CatalogDao.replaceLevelTree). Une clé étrangère en cascade supprimerait les cartes et toute la
 * progression SRS de l'élève. Une @Relation lit la jointure sans créer de contrainte SQL.
 *
 * [lesson] est null tant que le catalogue n'a pas été synchronisé : on retombe alors sur `chapitre`.
 */
data class QuestionWithLesson(
    @Embedded val question: FlashcardEntity,
    @Relation(parentColumn = "lessonId", entityColumn = "id")
    val lesson: CatalogLessonEntity?
) {
    val lessonTitle: String? get() = lesson?.title?.takeIf { it.isNotBlank() } ?: question.chapitre.takeIf { it.isNotBlank() }
    val lessonSummary: String? get() = lesson?.summary?.takeIf { it.isNotBlank() }
}
