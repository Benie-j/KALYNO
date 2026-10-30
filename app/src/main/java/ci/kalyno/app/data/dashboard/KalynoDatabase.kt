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
import ci.kalyno.app.data.badges.BadgeDao
import ci.kalyno.app.data.badges.BadgeUnlockEntity
import ci.kalyno.app.data.catalog.CatalogThemeEntity
import ci.kalyno.app.data.lessons.LessonProgressDao
import ci.kalyno.app.data.lessons.LessonProgressEntity
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
        QuoteFavoriteEntity::class,
        BadgeUnlockEntity::class,
        LessonProgressEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class KalynoDatabase : RoomDatabase() {
    abstract fun srsDao(): SrsDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun catalogDao(): CatalogDao
    abstract fun quoteFavoriteDao(): QuoteFavoriteDao
    abstract fun badgeDao(): BadgeDao
    abstract fun lessonProgressDao(): LessonProgressDao

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

        /** Déblocages de badges (une ligne par élève + badge, jamais écrasée une fois posée). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `badge_unlocks` (`studentId` TEXT NOT NULL, " +
                        "`badgeId` TEXT NOT NULL, `unlockedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`studentId`, `badgeId`))"
                )
            }
        }

        /**
         * Dojo : niveau de répétition par élève et par carte (QCM -> saisie libre) et phrase de
         * référence de l'admin. Les cartes déjà bien révisées reprennent un niveau cohérent
         * avec leur historique (nombre de révisions réussies d'affilée).
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flashcards ADD COLUMN adminReference TEXT")
                db.execSQL("ALTER TABLE card_progress ADD COLUMN dojoStage INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE card_progress SET dojoStage = MIN(repetitions, " +
                        "${ci.kalyno.app.data.dashboard.SrsConfig.DOJO_MAX_STAGE})"
                )
            }
        }

        /**
         * Déblocage des leçons : chaque carte retient la leçon dont elle est issue (`lessonId`) et chaque
         * élève valide ses leçons dans `lesson_progress`. Les cartes déjà stockées retrouvent leur leçon
         * par (matière, titre du chapitre) quand le catalogue est présent ; sinon la prochaine
         * synchronisation les renseigne. Aucune leçon n'est marquée terminée automatiquement.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flashcards ADD COLUMN lessonId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_lessonId` ON `flashcards` (`lessonId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lesson_progress` (`studentId` TEXT NOT NULL, `lessonId` TEXT NOT NULL, " +
                        "`isCompleted` INTEGER NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`studentId`, `lessonId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_progress_studentId` ON `lesson_progress` (`studentId`)")
                db.execSQL(
                    "UPDATE flashcards SET lessonId = (SELECT l.id FROM catalog_lessons l " +
                        "WHERE l.subjectId = flashcards.subjectId AND l.title = flashcards.chapitre LIMIT 1) " +
                        "WHERE lessonId IS NULL"
                )
            }
        }

        /**
         * Moteur E5 : chaque leçon du catalogue retient un résumé court. La relation carte -> leçon
         * (flashcards.lessonId -> catalog_lessons.id) est une relation Room SANS clé étrangère SQL :
         * le catalogue est vidé puis réinséré à chaque synchronisation, une clé étrangère en cascade
         * supprimerait les cartes et la progression de l'élève.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catalog_lessons ADD COLUMN summary TEXT")
            }
        }

        /** FSRS comportemental : compteurs, réapprentissage, latence moyenne et latence par révision. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE card_progress ADD COLUMN successCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE card_progress ADD COLUMN failCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE card_progress ADD COLUMN relearnLeft INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE card_progress ADD COLUMN avgLatencyMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE review_log ADD COLUMN latencyMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE review_log ADD COLUMN retrievabilityBefore REAL NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): KalynoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalynoDatabase::class.java,
                    "kalyno.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .build().also { instance = it }
            }
    }
}
