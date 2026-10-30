package ci.kalyno.app.data.progress

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import ci.kalyno.app.data.srs.ReviewRating
import kotlinx.coroutines.flow.Flow

/**
 * Une tentative de l'élève dans le Dojo : une ligne par réponse validée, jamais modifiée.
 *
 * Question, réponse attendue, leçon et discipline sont COPIÉES au moment de la réponse : l'admin peut
 * modifier ou retirer une carte, et le catalogue est réinséré à chaque synchronisation, sans que
 * l'historique de l'élève ne change. Pas de clé étrangère pour la même raison.
 */
@Entity(
    tableName = "dojo_attempts",
    indices = [
        Index(value = ["studentId", "attemptedAt"]),
        Index(value = ["studentId", "flashcardId"]),
        // Une même tentative ne peut exister qu'une fois (migration et rejeu sans doublon)
        Index(value = ["studentId", "flashcardId", "attemptedAt"], unique = true)
    ]
)
data class DojoAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: String,
    val flashcardId: String,
    val attemptedAt: Long,                 // timestamp epoch (ms)
    val isSuccess: Boolean,
    /**
     * Rang de la tentative sur cette carte depuis la dernière réussite : 1 = premier essai, 2 = deuxième…
     * Une réussite au rang 3 veut dire « réussi après deux échecs ». 0 = inconnu (historique antérieur à la mise à jour).
     */
    val attemptNumber: Int,
    val rating: ReviewRating,
    val kind: String,                      // SINGLE_CHOICE, MULTIPLE_CHOICE ou FREE_TEXT
    val question: String,
    /** Réponse saisie (ou choix cochés) ; null pour l'historique antérieur, qui ne conservait pas le détail. */
    val studentAnswer: String?,
    val referenceAnswer: String,
    val lessonTitle: String,
    val subjectName: String,
    /** Vrai pour une ligne reconstruite depuis l'ancien journal des révisions (sans réponse saisie). */
    val isLegacy: Boolean = false
)

/** Totaux de l'élève, calculés par SQL (aucune liste chargée en mémoire). */
data class AttemptSummaryRow(
    val total: Int,
    val successes: Int,
    /** Nombre de fiches différentes réussies au moins une fois. */
    val learnedCards: Int
)

@Dao
abstract class AttemptDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(attempt: DojoAttemptEntity): Long

    @Query(
        "SELECT * FROM dojo_attempts WHERE studentId = :studentId AND flashcardId = :flashcardId " +
            "ORDER BY attemptedAt DESC, id DESC LIMIT 1"
    )
    abstract suspend fun lastFor(studentId: String, flashcardId: String): DojoAttemptEntity?

    /**
     * Enregistre une tentative en calculant son rang dans la même transaction :
     * après une réussite on repart à 1, sinon le rang suit celui de l'échec précédent.
     */
    @Transaction
    open suspend fun record(attempt: DojoAttemptEntity): Long {
        val last = lastFor(attempt.studentId, attempt.flashcardId)
        val rank = if (last == null || last.isSuccess) 1 else last.attemptNumber.coerceAtLeast(1) + 1
        return insert(attempt.copy(attemptNumber = rank))
    }

    /**
     * Historique du plus récent au plus ancien.
     * [status] : 0 = tout, 1 = succès, 2 = échecs. [subject] null = toutes les disciplines.
     */
    @Query(
        """
        SELECT * FROM dojo_attempts
        WHERE studentId = :studentId
          AND (:status = 0 OR (:status = 1 AND isSuccess = 1) OR (:status = 2 AND isSuccess = 0))
          AND (:subject IS NULL OR subjectName = :subject)
        ORDER BY attemptedAt DESC, id DESC
        LIMIT :limit
        """
    )
    abstract fun observeHistory(studentId: String, status: Int, subject: String?, limit: Int): Flow<List<DojoAttemptEntity>>

    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN isSuccess = 1 THEN 1 ELSE 0 END), 0) AS successes,
               COUNT(DISTINCT CASE WHEN isSuccess = 1 THEN flashcardId END) AS learnedCards
        FROM dojo_attempts WHERE studentId = :studentId
        """
    )
    abstract fun observeSummary(studentId: String): Flow<AttemptSummaryRow>

    @Query(
        "SELECT DISTINCT subjectName FROM dojo_attempts " +
            "WHERE studentId = :studentId AND subjectName <> '' ORDER BY subjectName COLLATE NOCASE"
    )
    abstract fun observeSubjects(studentId: String): Flow<List<String>>
}
