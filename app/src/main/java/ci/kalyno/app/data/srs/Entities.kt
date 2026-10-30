package ci.kalyno.app.data.srs

import androidx.room.ColumnInfo
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
    indices = [Index("subjectId"), Index("lessonId")]
)
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val chapitre: String,
    val question: String,
    val reponse: String,
    // -- Exercice piloté par l'admin (null = carte créée avant le Dojo : saisie libre) --
    val kind: String? = null,            // SINGLE_CHOICE, MULTIPLE_CHOICE ou FREE_TEXT
    val choicesJson: String? = null,     // choix proposés, au format JSON
    val correctIndices: String? = null,  // indices des bonnes réponses, séparés par des virgules
    val explanation: String? = null,     // explication détaillée (révélée après la réponse)
    // -- Phrase de référence absolue de l'admin, utilisée par le moteur ONNX en saisie libre (null = on retombe sur `reponse`) --
    val adminReference: String? = null,
    // -- Leçon (chapitre publié) dont la carte est issue : le Dojo ne propose la carte que si cette leçon est terminée --
    val lessonId: String? = null
)

/**
 * L'évaluation donnée par l'élève à la fin d'une carte (4 notes).
 * [grade] est la note FSRS correspondante : 1 = Again, 2 = Hard, 3 = Good, 4 = Easy.
 * Room enregistre le nom de l'énumération : l'ordre peut évoluer sans migration.
 */
enum class ReviewRating(val grade: Int) {
    OUBLIE(1),       // "J'ai oublié"
    HESITE(2),       // "J'ai hésité"
    SU(3),           // "J'ai su"
    TROP_FACILE(4)   // "Trop facile"
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

    // -- Anciens champs SM-2 (conservés : le cloud et les anciennes versions les lisent) --
    val easeFactor: Float = 2.5f,      // dérivé de la difficulté FSRS, entre 1,3 et 3,0
    val intervalDays: Int = 0,         // intervalle courant, en jours
    val repetitions: Int = 0,          // nombre de révisions réussies d'affilée

    // -- Planification --
    val dueDate: Long,                 // timestamp epoch (ms) de la prochaine échéance
    val lastReviewedAt: Long? = null,

    // -- Mémoire FSRS (0 = carte créée avant FSRS : reprise automatique depuis l'intervalle et l'ease) --
    @ColumnInfo(defaultValue = "0") val stability: Double = 0.0,   // jours pour tomber à 90 % de rétention
    @ColumnInfo(defaultValue = "0") val difficulty: Double = 0.0,  // 1 (facile) à 10 (difficile)
    @ColumnInfo(defaultValue = "0") val lapses: Int = 0,           // nombre d'oublis après apprentissage

    // -- Dojo : niveau de répétition espacée de CET élève sur cette carte (0 = QCM ; >= seuil = saisie libre) --
    @ColumnInfo(defaultValue = "0") val dojoStage: Int = 0,

    // -- Comportement de l'élève sur CETTE carte --
    @ColumnInfo(defaultValue = "0") val successCount: Int = 0,    // révisions réussies (Hésité, Su, Trop facile)
    @ColumnInfo(defaultValue = "0") val failCount: Int = 0,       // révisions ratées (Oublié)
    @ColumnInfo(defaultValue = "0") val relearnLeft: Int = 0,     // succès encore exigés avant de sortir du réapprentissage
    @ColumnInfo(defaultValue = "0") val avgLatencyMs: Long = 0    // temps de réponse moyen lissé (0 = inconnu)
) {
    /** Règle dérivée (aucune colonne) : au-delà du seuil, la carte passe du QCM à la saisie libre. */
    val requiresFreeInput: Boolean
        get() = ci.kalyno.app.data.dojo.DojoStagePolicy.requiresFreeInput(dojoStage)
}

/**
 * Historique immuable de chaque révision (pour analytics / audit).
 * Une ligne par passage de carte, jamais modifiée après écriture.
 */
@Entity(
    tableName = "review_log",
    indices = [
        Index("studentId"),
        Index("flashcardId"),
        Index("reviewedAt"),
        // Une même révision ne peut exister qu'une fois : la synchro cloud peut rejouer sans doublon
        Index(value = ["studentId", "flashcardId", "reviewedAt"], unique = true)
    ]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val studentId: String,
    val flashcardId: String,
    val rating: ReviewRating,
    val intervalBefore: Int,
    val intervalAfter: Int,
    val reviewedAt: Long,                          // timestamp epoch (ms)
    @ColumnInfo(defaultValue = "0") val latencyMs: Long = 0,                // temps de réponse (0 = inconnu)
    @ColumnInfo(defaultValue = "0") val retrievabilityBefore: Double = 0.0  // R prédite juste avant la révision
)
