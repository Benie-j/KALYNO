package ci.kalyno.app.data.catalog

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Catalogue pédagogique stocké dans Room (synchronisé depuis l'admin / Supabase).
 *
 *   catalog_levels  (Classe / Série : 3e, Tle A1, Tle A2, Tle C, Tle D…)
 *     └─ catalog_subjects  (Discipline)
 *          ├─ catalog_themes   (Thème = chapitre global)
 *          └─ catalog_lessons  (Leçon, rattachée à un thème ; ses blocs restent dans le cache de contenu)
 *
 * Aucune donnée éducative n'est écrite en dur : ces tables ne contiennent que ce que l'admin publie.
 */

@Entity(tableName = "catalog_levels")
data class CatalogLevelEntity(
    @PrimaryKey val id: String,       // identifiant admin, ex. "TLE_A1"
    val label: String,                // libellé court affiché, ex. "Tle A1"
    val cycle: String,                // groupe d'examen publié par l'admin ("BEPC", "BAC"…), vide si inconnu
    val iconKey: String?,             // clé d'icône vectorielle (voir KalynoIcons)
    val tagline: String?,             // courte description de la série (facultative)
    val position: Int
)

@Entity(
    tableName = "catalog_subjects",
    foreignKeys = [
        ForeignKey(
            entity = CatalogLevelEntity::class,
            parentColumns = ["id"],
            childColumns = ["levelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("levelId")]
)
data class CatalogSubjectEntity(
    @PrimaryKey val id: String,
    val levelId: String,
    val name: String,
    val iconKey: String?,
    val position: Int
)

@Entity(
    tableName = "catalog_themes",
    foreignKeys = [
        ForeignKey(
            entity = CatalogSubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class CatalogThemeEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val title: String,
    val position: Int
)

@Entity(
    tableName = "catalog_lessons",
    foreignKeys = [
        ForeignKey(
            entity = CatalogSubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CatalogThemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["themeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("themeId")]
)
data class CatalogLessonEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val themeId: String?,             // null = leçon pas encore rangée dans un thème
    val title: String,
    val position: Int,
    val tag: String?,
    /** Résumé court de la leçon (bloc « intro » de l'admin), injecté dans le passage E5 du Dojo. */
    val summary: String? = null
)
