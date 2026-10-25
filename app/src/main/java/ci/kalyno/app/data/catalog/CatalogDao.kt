package ci.kalyno.app.data.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CatalogDao {

    // ----- Classes / séries -----

    @Query("SELECT * FROM catalog_levels ORDER BY position ASC, id ASC")
    abstract fun observeLevels(): Flow<List<CatalogLevelEntity>>

    @Query("SELECT * FROM catalog_levels ORDER BY position ASC, id ASC")
    abstract suspend fun levelsOnce(): List<CatalogLevelEntity>

    /** @Upsert (et non REPLACE) : un REPLACE supprimerait la ligne et, par cascade, tout son arbre. */
    @Upsert
    abstract suspend fun upsertLevels(items: List<CatalogLevelEntity>)

    @Query("DELETE FROM catalog_levels WHERE id NOT IN (:keepIds)")
    abstract suspend fun deleteLevelsNotIn(keepIds: List<String>)

    /** Appelé uniquement après une réponse distante réussie sans classe active. */
    @Query("DELETE FROM catalog_levels")
    abstract suspend fun deleteAllLevels()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertLevelIfAbsent(level: CatalogLevelEntity)

    // ----- Arborescence d'une classe -----

    @Query("SELECT * FROM catalog_subjects WHERE levelId = :levelId ORDER BY position ASC, name ASC")
    abstract fun observeSubjects(levelId: String): Flow<List<CatalogSubjectEntity>>

    @Query(
        "SELECT t.* FROM catalog_themes t " +
            "INNER JOIN catalog_subjects s ON s.id = t.subjectId " +
            "WHERE s.levelId = :levelId ORDER BY t.position ASC, t.title ASC"
    )
    abstract fun observeThemes(levelId: String): Flow<List<CatalogThemeEntity>>

    @Query(
        "SELECT l.* FROM catalog_lessons l " +
            "INNER JOIN catalog_subjects s ON s.id = l.subjectId " +
            "WHERE s.levelId = :levelId ORDER BY l.position ASC"
    )
    abstract fun observeLessons(levelId: String): Flow<List<CatalogLessonEntity>>

    @Query("DELETE FROM catalog_subjects WHERE levelId = :levelId")
    abstract suspend fun deleteSubjectsOf(levelId: String)

    // Les enfants sont insérés après leurs parents, sur une table vidée : pas de conflit réel.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSubjects(items: List<CatalogSubjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertThemes(items: List<CatalogThemeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLessons(items: List<CatalogLessonEntity>)

    /** Remplace tout l'arbre d'une classe en une seule transaction (un seul rafraîchissement d'écran). */
    @Transaction
    open suspend fun replaceLevelTree(
        fallbackLevel: CatalogLevelEntity,
        subjects: List<CatalogSubjectEntity>,
        themes: List<CatalogThemeEntity>,
        lessons: List<CatalogLessonEntity>
    ) {
        insertLevelIfAbsent(fallbackLevel) // la clé étrangère exige que la classe existe
        deleteSubjectsOf(fallbackLevel.id) // cascade : thèmes + leçons de cette classe
        insertSubjects(subjects)
        insertThemes(themes)
        insertLessons(lessons)
    }
}
