package ci.kalyno.app.data.dashboard

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ci.kalyno.app.data.catalog.CatalogDao
import ci.kalyno.app.data.catalog.CatalogLessonEntity
import ci.kalyno.app.data.catalog.CatalogLevelEntity
import ci.kalyno.app.data.catalog.CatalogSubjectEntity
import ci.kalyno.app.data.catalog.CatalogThemeEntity
import ci.kalyno.app.data.srs.CardProgressEntity
import ci.kalyno.app.data.srs.FlashcardEntity
import ci.kalyno.app.data.srs.ReviewLogEntity
import ci.kalyno.app.data.srs.SrsDao
import ci.kalyno.app.data.srs.SubjectEntity

@Database(
    entities = [
        SubjectEntity::class,
        FlashcardEntity::class,
        CardProgressEntity::class,
        ReviewLogEntity::class,
        CatalogLevelEntity::class,
        CatalogSubjectEntity::class,
        CatalogThemeEntity::class,
        CatalogLessonEntity::class,
        QuoteFavoriteEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class KalynoDatabase : RoomDatabase() {
    abstract fun srsDao(): SrsDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun catalogDao(): CatalogDao
    abstract fun quoteFavoriteDao(): QuoteFavoriteDao

    companion object {
        @Volatile
        private var instance: KalynoDatabase? = null

        /**
         * Passage à FSRS : trois colonnes de mémoire par carte (les cartes existantes gardent leur
         * progression, elle est reprise au premier calcul) et un index unique sur l'historique.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE card_progress ADD COLUMN stability REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE card_progress ADD COLUMN difficulty REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE card_progress ADD COLUMN lapses INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "DELETE FROM review_log WHERE logId NOT IN " +
                        "(SELECT MIN(logId) FROM review_log GROUP BY studentId, flashcardId, reviewedAt)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_review_log_studentId_flashcardId_reviewedAt` " +
                        "ON `review_log` (`studentId`, `flashcardId`, `reviewedAt`)"
                )
            }
        }

        /** Dojo : type d'exercice, choix, bonnes réponses et explication stockés dans Room. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flashcards ADD COLUMN kind TEXT")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN choicesJson TEXT")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN correctIndices TEXT")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN explanation TEXT")
            }
        }

        /**
         * Catalogue pédagogique : classes/séries (Tle A1, Tle A2…), disciplines, thèmes, leçons,
         * reliés par clés étrangères. Aucune donnée existante (SRS, historique) n'est touchée.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catalog_levels` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`cycle` TEXT NOT NULL, `iconKey` TEXT, `tagline` TEXT, `position` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catalog_subjects` (`id` TEXT NOT NULL, `levelId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `iconKey` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`levelId`) REFERENCES `catalog_levels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_subjects_levelId` ON `catalog_subjects` (`levelId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catalog_themes` (`id` TEXT NOT NULL, `subjectId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`subjectId`) REFERENCES `catalog_subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_themes_subjectId` ON `catalog_themes` (`subjectId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catalog_lessons` (`id` TEXT NOT NULL, `subjectId` TEXT NOT NULL, " +
                        "`themeId` TEXT, `title` TEXT NOT NULL, `position` INTEGER NOT NULL, `tag` TEXT, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`subjectId`) REFERENCES `catalog_subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`themeId`) REFERENCES `catalog_themes`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_lessons_subjectId` ON `catalog_lessons` (`subjectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_lessons_themeId` ON `catalog_lessons` (`themeId`)")
            }
        }

        /** Citations épinglées par l'élève (cœur de la carte "Citation du jour"). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quote_favorites` (`quoteKey` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, `author` TEXT, `savedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`quoteKey`))"
                )
            }
        }

        fun get(context: Context): KalynoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalynoDatabase::class.java,
                    "kalyno.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
