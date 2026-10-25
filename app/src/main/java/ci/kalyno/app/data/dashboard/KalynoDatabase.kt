package ci.kalyno.app.data.dashboard

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        ReviewLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class KalynoDatabase : RoomDatabase() {
    abstract fun srsDao(): SrsDao
    abstract fun dashboardDao(): DashboardDao

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

        fun get(context: Context): KalynoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalynoDatabase::class.java,
                    "kalyno.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
