package ci.kalyno.app.data.lessons

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ci.kalyno.app.data.cloud.CloudSync
import ci.kalyno.app.data.cloud.CloudSyncScheduler
import ci.kalyno.app.data.dashboard.KalynoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Validation d'UNE séance par UN élève : une ligne par (élève, séance), écrite au clic sur « Terminer ».
 * Pas de clé étrangère : le catalogue est réécrit à chaque synchronisation (même raison que `lesson_progress`).
 * `studentId` est l'identifiant LOCAL ; l'UUID Supabase (`user_id`) n'est utilisé que par CloudSync.
 */
@Entity(
    tableName = "session_progress",
    primaryKeys = ["studentId", "sessionId"],
    indices = [Index("studentId")]
)
data class SessionProgressEntity(
    val studentId: String,
    val sessionId: String,
    val completedAt: Long          // epoch ms du clic sur « Terminer »
)

@Dao
abstract class SessionProgressDao {

    @Query("SELECT sessionId FROM session_progress WHERE studentId = :studentId")
    abstract fun observeCompletedSessionIds(studentId: String): Flow<List<String>>

    @Query("SELECT * FROM session_progress WHERE studentId = :studentId")
    abstract suspend fun completedRows(studentId: String): List<SessionProgressEntity>

    /** Idempotent : un second clic (ou un second appareil) ne change pas la date de première validation. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(row: SessionProgressEntity)
}

/** Point d'accès unique : « séance terminée = ses exercices (et eux seuls) sont débloqués ». */
class SessionProgressRepository private constructor(context: Context) {

    private val dao = KalynoDatabase.get(context).sessionProgressDao()
    private val cloud = CloudSync(context)

    fun observeCompleted(studentId: String): Flow<Set<String>> =
        dao.observeCompletedSessionIds(studentId).map { it.toSet() }.distinctUntilChanged()

    suspend fun markCompleted(studentId: String, sessionId: String) = withContext(Dispatchers.IO) {
        dao.insertIfAbsent(SessionProgressEntity(studentId, sessionId, System.currentTimeMillis()))
        // Sauvegarde cloud en arrière-plan : la validation est déjà dans Room, rien ne bloque l'élève
        CloudSyncScheduler.request(cloud, studentId)
    }

    companion object {
        @Volatile
        private var instance: SessionProgressRepository? = null

        fun get(context: Context): SessionProgressRepository =
            instance ?: synchronized(this) {
                instance ?: SessionProgressRepository(context.applicationContext).also { instance = it }
            }
    }
}
