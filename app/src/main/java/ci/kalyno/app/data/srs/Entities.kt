package ci.kalyno.app.data.srs

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

/**
 * Une matière du programme scolaire (ex: Mathématiques, Physique).
 * Synchronisée depuis KALYNO-admin (Supabase).
 */
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filiere: String,        // "3E", "TLE_A", "TLE_C", "TLE_D"
    val coefficient: Float      // coefficient officiel à l'examen
)

/**
 * Une flashcard (micro-capsule de révision).
 * Synchronisée depuis KALYNO-admin (Supabase).
 */
@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val chapitre: String,
    val question: String,
    val reponse: String
)

/**
 * L'évaluation donnée par l'élève à la fin d'une carte.
 * Pilote directement le calcul SM-2.
 */
enum class ReviewRating {
    OUBLIE,       // "J'ai oublié"
    HESITE,       // "J'ai hésité"
    TROP_FACILE   // "Trop facile"
}

/**
 * L'état SRS courant d'une carte pour UN élève donné.
 * C'est la table centrale du moteur : une ligne par (élève, carte).
 */
@Entity(
    tableName = "card_progress",
    primaryKeys = ["studentId", "flashcardId"],
    foreignKeys = [
        ForeignKey(
            entity = FlashcardEntity::class,
            parentColumns = ["id"],
            childColumns = ["flashcardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("flashcardId"), Index("dueDate")]
)
data class CardProgressEntity(
    val studentId: String,
    val flashcardId: String,

    // -- Paramètres SM-2 --
    val easeFactor: Float = 2.5f,      // facteur de facilité, jamais < 1.3
    val intervalDays: Int = 0,         // intervalle courant, en jours
    val repetitions: Int = 0,          // nombre de révisions réussies d'affilée

    // -- Planification --
    val dueDate: Long,                 // timestamp epoch (ms) de la prochaine échéance
    val lastReviewedAt: Long? = null
)

/**
 * Historique immuable de chaque révision (pour analytics / audit).
 * Une ligne par passage de carte, jamais modifiée après écriture.
 */
@Entity(
    tableName = "review_log",
    indices = [Index("studentId"), Index("flashcardId"), Index("reviewedAt")]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val studentId: String,
    val flashcardId: String,
    val rating: ReviewRating,
    val intervalBefore: Int,
    val intervalAfter: Int,
    val reviewedAt: Long            // timestamp epoch (ms)
)
