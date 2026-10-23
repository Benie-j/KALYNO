package ci.kalyno.app.data.dashboard

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ci.kalyno.app.data.srs.FlashcardEntity
import ci.kalyno.app.data.srs.SubjectEntity

@Dao
interface DashboardDao {

    // @Upsert (et non REPLACE) : REPLACE supprimerait en cascade la progression SRS des cartes.
    @Upsert
    suspend fun upsertSubjects(items: List<SubjectEntity>)

    @Upsert
    suspend fun upsertFlashcards(items: List<FlashcardEntity>)

    @Query("SELECT id FROM subjects WHERE filiere = :levelId")
    suspend fun subjectIdsForLevel(levelId: String): List<String>

    @Query("SELECT * FROM subjects WHERE filiere = :levelId")
    suspend fun subjectsForLevel(levelId: String): List<SubjectEntity>

    @Query("SELECT flashcardId FROM card_progress WHERE studentId = :studentId")
    suspend fun progressedCardIds(studentId: String): List<String>

    @Query("SELECT * FROM flashcards WHERE id IN (:ids)")
    suspend fun flashcardsByIds(ids: List<String>): List<FlashcardEntity>
}
