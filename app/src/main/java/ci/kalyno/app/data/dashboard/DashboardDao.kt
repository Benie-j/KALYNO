package ci.kalyno.app.data.dashboard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import ci.kalyno.app.data.srs.CardProgressEntity
import ci.kalyno.app.data.srs.FlashcardEntity
import ci.kalyno.app.data.srs.ReviewLogEntity
import ci.kalyno.app.data.srs.SubjectEntity

/** Dernière introduction d'une nouvelle carte pour une matière. */
data class SubjectActivity(
    val subjectId: String,
    val lastIntroducedAt: Long
)

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

    /** Nouvelles cartes découvertes depuis [sinceMillis] (première révision = intervalle avant 0). */
    @Query(
        "SELECT COUNT(*) FROM review_log " +
            "WHERE studentId = :studentId AND intervalBefore = 0 AND reviewedAt >= :sinceMillis"
    )
    suspend fun countIntroducedSince(studentId: String, sinceMillis: Long): Int

    /** Dernière fois qu'une nouvelle carte a été découverte, par matière. */
    @Query(
        "SELECT f.subjectId AS subjectId, MAX(l.reviewedAt) AS lastIntroducedAt " +
            "FROM review_log l INNER JOIN flashcards f ON f.id = l.flashcardId " +
            "WHERE l.studentId = :studentId AND l.intervalBefore = 0 " +
            "GROUP BY f.subjectId"
    )
    suspend fun lastIntroductionBySubject(studentId: String): List<SubjectActivity>

    // ----- Synchronisation cloud -----

    @Upsert
    suspend fun upsertProgress(items: List<CardProgressEntity>)

    /** Révisions plus récentes que [since], de la plus ancienne à la plus récente. */
    @Query(
        "SELECT * FROM card_progress WHERE studentId = :studentId AND lastReviewedAt > :since " +
            "ORDER BY lastReviewedAt ASC LIMIT :limit"
    )
    suspend fun progressReviewedSince(studentId: String, since: Long, limit: Int): List<CardProgressEntity>

    @Query("SELECT * FROM card_progress WHERE studentId = :studentId AND flashcardId IN (:ids)")
    suspend fun progressByIds(studentId: String, ids: List<String>): List<CardProgressEntity>

    @Query("SELECT id FROM flashcards WHERE id IN (:ids)")
    suspend fun existingFlashcardIds(ids: List<String>): List<String>

    // ----- Historique des révisions -----

    /** Révisions enregistrées après [afterId] (identifiant local croissant), de la plus ancienne à la plus récente. */
    @Query(
        "SELECT * FROM review_log WHERE studentId = :studentId AND logId > :afterId " +
            "ORDER BY logId ASC LIMIT :limit"
    )
    suspend fun logsAfter(studentId: String, afterId: Long, limit: Int): List<ReviewLogEntity>

    /** Doublons ignorés grâce à l'index unique (élève, carte, instant). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLogs(items: List<ReviewLogEntity>)
}
