package ci.kalyno.app.data.lessons

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ci.kalyno.app.data.dashboard.KalynoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Validation explicite d'une leçon par UN élève : une ligne par (élève, leçon).
 *
 * Table à part (et non une colonne de `catalog_lessons`) pour deux raisons :
 *  - l'état est propre à chaque élève, le catalogue est commun ;
 *  - le catalogue est entièrement supprimé puis réinséré à chaque synchronisation (cascade),
 *    ce qui effacerait la progression. Aucune clé étrangère ici, pour la même raison.
 *
 * Pas de ligne = leçon non terminée. Une ligne n'est écrite que lorsque l'élève appuie sur « Terminer ».
 */
@Entity(
    tableName = "lesson_progress",
    primaryKeys = ["studentId", "lessonId"],
    indices = [Index("studentId")]
)
data class LessonProgressEntity(
    val studentId: String,
    val lessonId: String,          // = id du chapitre publié par l'admin = catalog_lessons.id
    val isCompleted: Boolean,
    val completedAt: Long?         // timestamp epoch (ms) du clic sur « Terminer »
)

@Dao
abstract class LessonProgressDao {

    /** Identifiants des leçons terminées, mis à jour en direct (l'écran de leçon change dès le clic). */
    @Query("SELECT lessonId FROM lesson_progress WHERE studentId = :studentId AND isCompleted = 1")
    abstract fun observeCompletedLessonIds(studentId: String): Flow<List<String>>

    @Query("SELECT lessonId FROM lesson_progress WHERE studentId = :studentId AND isCompleted = 1")
    abstract suspend fun completedLessonIds(studentId: String): List<String>

    /**
     * Cartes débloquées pour le Dojo : uniquement celles dont la leçon est terminée.
     * Une carte sans `lessonId` (jamais resynchronisée) n'est PAS débloquée : règle stricte.
     */
    @Query(
        """
        SELECT f.id FROM flashcards f
        INNER JOIN lesson_progress lp ON lp.lessonId = f.lessonId
        WHERE lp.studentId = :studentId AND lp.isCompleted = 1
        """
    )
    abstract suspend fun unlockedCardIds(studentId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(row: LessonProgressEntity)

    @Query(
        "UPDATE lesson_progress SET isCompleted = 1, completedAt = :now " +
            "WHERE studentId = :studentId AND lessonId = :lessonId AND isCompleted = 0"
    )
    abstract suspend fun flagCompleted(studentId: String, lessonId: String, now: Long)

    /** Idempotent : un second clic ne change ni l'état ni la date de première validation. */
    @Transaction
    open suspend fun markCompleted(studentId: String, lessonId: String, now: Long) {
        insertIfAbsent(LessonProgressEntity(studentId, lessonId, isCompleted = true, completedAt = now))
        flagCompleted(studentId, lessonId, now)
    }
}

/** Point d'accès unique à la règle « leçon terminée = exercices débloqués ». */
class LessonProgressRepository private constructor(context: Context) {

    private val dao = KalynoDatabase.get(context).lessonProgressDao()

    fun observeCompleted(studentId: String): Flow<Set<String>> =
        dao.observeCompletedLessonIds(studentId).map { it.toSet() }.distinctUntilChanged()

    suspend fun markCompleted(studentId: String, lessonId: String) = withContext(Dispatchers.IO) {
        dao.markCompleted(studentId, lessonId, System.currentTimeMillis())
    }

    /** Ids des cartes que le Dojo a le droit de proposer à cet élève. */
    suspend fun unlockedCardIds(studentId: String): Set<String> = withContext(Dispatchers.IO) {
        dao.unlockedCardIds(studentId).toHashSet()
    }

    companion object {
        @Volatile
        private var instance: LessonProgressRepository? = null

        fun get(context: Context): LessonProgressRepository =
            instance ?: synchronized(this) {
                instance ?: LessonProgressRepository(context.applicationContext).also { instance = it }
            }
    }
}
