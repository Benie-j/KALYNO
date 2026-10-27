package ci.kalyno.app.data.badges

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Date de déblocage d'un badge pour un élève (seule donnée à persister : le reste se recalcule). */
@Entity(tableName = "badge_unlocks", primaryKeys = ["studentId", "badgeId"])
data class BadgeUnlockEntity(
    val studentId: String,
    val badgeId: String,
    val unlockedAt: Long
)

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badge_unlocks WHERE studentId = :studentId")
    suspend fun unlocksFor(studentId: String): List<BadgeUnlockEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(entity: BadgeUnlockEntity)
}
