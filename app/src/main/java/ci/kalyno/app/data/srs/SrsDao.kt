package ci.kalyno.app.data.srs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Résultat brut d'une jointure carte + progression + coefficient matière.
 * Utilisé pour calculer le score d'urgence côté Kotlin (pas en SQL),
 * car la formule mélange plusieurs règles métier qui évoluent souvent.
 */
data class CardWithContext(
    val flashcardId: String,
    val subjectId: String,
    val coefficient: Float,
    val dueDate: Long,
    val easeFactor: Float,
    val intervalDays: Int,
    val repetitions: Int,
    val stability: Double = 0.0,
    val lastReviewedAt: Long? = null
)

@Dao
interface SrsDao {

    @Upsert
    suspend fun upsertSubjects(subjects: List<SubjectEntity>)

    @Upsert
    suspend fun upsertFlashcards(cards: List<FlashcardEntity>)

    /**
     * Crée une ligne de progression par défaut pour une nouvelle carte
     * synchronisée, si elle n'existe pas déjà pour cet élève.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initProgress(progress: CardProgressEntity)

    @Update
    suspend fun updateProgress(progress: CardProgressEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun logReview(log: ReviewLogEntity)

    /**
     * Toutes les cartes d'un élève avec leur contexte (coefficient, échéance),
     * toutes matières confondues. Le tri final par score se fait en Kotlin.
     */
    @Query(
        """
        SELECT 
            f.id AS flashcardId,
            f.subjectId AS subjectId,
            s.coefficient AS coefficient,
            p.dueDate AS dueDate,
            p.easeFactor AS easeFactor,
            p.intervalDays AS intervalDays,
            p.repetitions AS repetitions,
            p.stability AS stability,
            p.lastReviewedAt AS lastReviewedAt
        FROM card_progress p
        INNER JOIN flashcards f ON f.id = p.flashcardId
        INNER JOIN subjects s ON s.id = f.subjectId
        WHERE p.studentId = :studentId
        """
    )
    fun observeAllProgress(studentId: String): Flow<List<CardWithContext>>

    /**
     * Raccourci SQL : uniquement les cartes déjà en retard (dueDate dépassée).
     * Utile pour l'alerte "Zone de Sauvetage" sans recharger toute la base.
     */
    @Query(
        """
        SELECT 
            f.id AS flashcardId,
            f.subjectId AS subjectId,
            s.coefficient AS coefficient,
            p.dueDate AS dueDate,
            p.easeFactor AS easeFactor,
            p.intervalDays AS intervalDays,
            p.repetitions AS repetitions,
            p.stability AS stability,
            p.lastReviewedAt AS lastReviewedAt
        FROM card_progress p
        INNER JOIN flashcards f ON f.id = p.flashcardId
        INNER JOIN subjects s ON s.id = f.subjectId
        WHERE p.studentId = :studentId AND p.dueDate <= :nowMillis
        """
    )
    suspend fun getDueCards(studentId: String, nowMillis: Long): List<CardWithContext>

    @Query("SELECT * FROM card_progress WHERE studentId = :studentId AND flashcardId = :flashcardId")
    suspend fun getProgress(studentId: String, flashcardId: String): CardProgressEntity?
}
