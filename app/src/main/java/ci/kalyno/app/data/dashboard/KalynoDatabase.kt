package ci.kalyno.app.data.dashboard

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false
)
abstract class KalynoDatabase : RoomDatabase() {
    abstract fun srsDao(): SrsDao
    abstract fun dashboardDao(): DashboardDao

    companion object {
        @Volatile
        private var instance: KalynoDatabase? = null

        fun get(context: Context): KalynoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalynoDatabase::class.java,
                    "kalyno.db"
                ).build().also { instance = it }
            }
    }
}
