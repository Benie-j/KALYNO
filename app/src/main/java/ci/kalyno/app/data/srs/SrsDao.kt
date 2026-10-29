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

    /** Niveau Dojo de plusieurs cartes d'un coup (cartes jamais révisées : absentes du résultat = niveau 0). */
    @Query("SELECT flashcardId, dojoStage FROM card_progress WHERE studentId = :studentId AND flashcardId IN (:ids)")
    suspend fun stagesFor(studentId: String, ids: List<String>): List<CardStageRow>

    @Query("SELECT * FROM card_progress WHERE studentId = :studentId AND flashcardId = :flashcardId")
    suspend fun getProgress(studentId: String, flashcardId: String): CardProgressEntity?

    /** Cartes deja revisees au moins une fois, pour calculer maitrise et retention. */
    @Query(
        "SELECT repetitions, stability, lastReviewedAt FROM card_progress " +
            "WHERE studentId = :studentId AND repetitions > 0"
    )
    suspend fun masteryRows(studentId: String): List<MasteryRow>

    /** Historique brut (carte, note, date), trié par carte puis par date : sert à isoler le TOUT premier passage de chaque carte. */
    @Query(
        "SELECT flashcardId, rating, reviewedAt FROM review_log " +
            "WHERE studentId = :studentId ORDER BY flashcardId ASC, reviewedAt ASC"
    )
    suspend fun reviewHistory(studentId: String): List<ReviewLogRow>

    /** Chaque carte publiée avec sa matière : sert à mesurer la progression par discipline ("Major du Canton"). */
    @Query("SELECT id, subjectId FROM flashcards")
    suspend fun allFlashcardSubjects(): List<FlashcardSubjectRow>

    /**
     * Détail des cartes jugées maîtrisées par le moteur (repetitions >= seuil), avec leur
     * contexte pédagogique (matière, chapitre). Sert l'écran "Ma progression" : le moteur
     * décide seul de ce qui est maîtrisé, l'UI ne fait qu'afficher.
     */
    @Query(
        """
        SELECT 
            f.id AS flashcardId,
            f.subjectId AS subjectId,
            s.name AS subjectName,
            f.chapitre AS chapitre,
            f.question AS question,
            p.repetitions AS repetitions,
            p.stability AS stability,
            p.lastReviewedAt AS lastReviewedAt
        FROM card_progress p
        INNER JOIN flashcards f ON f.id = p.flashcardId
        INNER JOIN subjects s ON s.id = f.subjectId
        WHERE p.studentId = :studentId AND p.repetitions >= :minRepetitions
        ORDER BY p.lastReviewedAt DESC
        """
    )
    suspend fun masteredDetails(studentId: String, minRepetitions: Int): List<MasteredCardRow>
}

data class CardStageRow(val flashcardId: String, val dojoStage: Int)
data class ReviewLogRow(val flashcardId: String, val rating: ReviewRating, val reviewedAt: Long)
data class FlashcardSubjectRow(val id: String, val subjectId: String)

/** Une carte jugée maîtrisée par le moteur, avec son contexte pédagogique complet. */
data class MasteredCardRow(
    val flashcardId: String,
    val subjectId: String,
    val subjectName: String,
    val chapitre: String,
    val question: String,
    val repetitions: Int,
    val stability: Double,
    val lastReviewedAt: Long?
)

/** Ligne minimale pour le calcul de la grille KPI (notions maitrisees, retention). */
data class MasteryRow(
    val repetitions: Int,
    val stability: Double,
    val lastReviewedAt: Long?
)
