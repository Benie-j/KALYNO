package ci.kalyno.app.data.reports

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** D'où vient le signalement (colonne `source_type` côté Supabase). */
enum class ReportSourceType { LESSON, EXERCISE, SETTINGS }

/**
 * Motifs rapides proposés à l'élève, par type de source.
 *
 * Le nom de l'enum est la valeur stockée (Room et Supabase) : ne jamais le renommer une fois publié.
 * [descriptionRequired] : vrai quand le motif seul ne suffit pas à l'équipe pour agir.
 */
enum class ReportCategory(
    val label: String,
    val source: ReportSourceType,
    val descriptionRequired: Boolean
) {
    // Leçon
    TYPO("Erreur de frappe / orthographe", ReportSourceType.LESSON, false),
    UNCLEAR_EXPLANATION("Explication peu claire", ReportSourceType.LESSON, false),
    OTHER("Autre", ReportSourceType.LESSON, true),

    // Exercice (Dojo et exercice de bienvenue)
    WRONG_OFFICIAL_ANSWER("Réponse officielle fausse", ReportSourceType.EXERCISE, false),
    SEMANTIC_GRADING("Problème de correction sémantique (E5)", ReportSourceType.EXERCISE, true),
    AMBIGUOUS_STATEMENT("Énoncé ambigu", ReportSourceType.EXERCISE, false),

    // Paramètres
    APP_BUG("Bug de l'application", ReportSourceType.SETTINGS, true),
    SYNC_PROBLEM("Problème de synchronisation", ReportSourceType.SETTINGS, true);

    companion object {
        fun forSource(type: ReportSourceType): List<ReportCategory> = values().filter { it.source == type }

        fun fromCode(code: String?): ReportCategory? = values().firstOrNull { it.name == code }
    }
}

/** Règles de saisie du formulaire (testables sans Android). */
object ReportRules {
    const val MAX_DESCRIPTION = 1000
    const val MIN_REQUIRED_DESCRIPTION = 10

    fun clean(raw: String): String = raw.trim().take(MAX_DESCRIPTION)

    /** Vrai si le motif est choisi et que la description respecte la règle du motif. */
    fun isValid(category: ReportCategory?, description: String): Boolean {
        if (category == null) return false
        val text = clean(description)
        return if (category.descriptionRequired) text.length >= MIN_REQUIRED_DESCRIPTION else true
    }
}

/**
 * Un signalement de l'élève. Une ligne n'est JAMAIS supprimée : `syncedAt` passe de NULL à l'heure
 * d'envoi quand Supabase l'a bien reçu, et la ligne reste comme historique local.
 *
 * L'identifiant est un UUID généré sur le téléphone : renvoyer plusieurs fois le même rapport
 * (réseau coupé en plein envoi, nouvel essai) ne crée jamais de doublon côté serveur.
 */
@Entity(
    tableName = "reports",
    indices = [Index(value = ["syncedAt"])]
)
data class ReportEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val sourceType: String,
    val sourceId: String?,
    val category: String,
    val description: String,
    /** Contexte lisible (« Mathématiques › Fractions ») : l'ID seul ne parle pas à l'équipe. */
    val contextLabel: String?,
    val status: String,
    val createdAt: Long,          // timestamp epoch (ms)
    val syncedAt: Long? = null,   // null = encore en attente d'envoi
    /** Réponse écrite par l'équipe (visible de l'élève), copiée depuis Supabase à l'ouverture de « Mes signalements ». */
    val teamReply: String? = null,
    val teamRepliedAt: Long? = null,
    /** null = l'élève n'a pas encore vu la réponse (affiche « Nouveau »). */
    val replySeenAt: Long? = null
)

/** Volontairement sans suppression : aucune requête `DELETE`, aucun `@Delete`. */
@Dao
abstract class ReportDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(report: ReportEntity): Long

    /** Rapports pas encore reçus par Supabase, du plus ancien au plus récent. */
    @Query("SELECT * FROM reports WHERE syncedAt IS NULL ORDER BY createdAt ASC LIMIT :limit")
    abstract suspend fun pending(limit: Int): List<ReportEntity>

    @Query("SELECT COUNT(*) FROM reports WHERE syncedAt IS NULL")
    abstract suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM reports WHERE syncedAt IS NULL")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE id = :id")
    abstract suspend fun byId(id: String): ReportEntity?

    @Query("SELECT COUNT(*) FROM reports WHERE teamReply IS NOT NULL AND teamReply != '' AND replySeenAt IS NULL")
    abstract fun observeUnreadReplies(): Flow<Int>

    @Query("UPDATE reports SET replySeenAt = :at WHERE teamReply IS NOT NULL AND teamReply != '' AND replySeenAt IS NULL")
    abstract suspend fun markRepliesSeen(at: Long)

    /** Copie l'état décidé côté équipe (statut + réponse). Ne supprime et n'écrase rien d'autre. */
    @Query("UPDATE reports SET status = :status, teamReply = :reply, teamRepliedAt = :repliedAt, replySeenAt = :seenAt WHERE id = :id")
    abstract suspend fun applyServerState(id: String, status: String, reply: String?, repliedAt: Long?, seenAt: Long?)

    @Query("SELECT COUNT(*) > 0 FROM reports WHERE id = :id AND syncedAt IS NOT NULL")
    abstract suspend fun isSynced(id: String): Boolean

    @Query("UPDATE reports SET syncedAt = :at WHERE id IN (:ids) AND syncedAt IS NULL")
    abstract suspend fun markSynced(ids: List<String>, at: Long)
}
