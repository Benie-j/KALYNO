package ci.kalyno.app.data.faq

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FaqDao {

    /** Toute la FAQ dans l'ordre voulu par l'admin. Se ré-émet toute seule après chaque synchronisation. */
    @Query("SELECT * FROM faq_items ORDER BY orderIndex ASC, question ASC, id ASC")
    abstract fun observeAll(): Flow<List<FaqEntity>>

    @Query("SELECT * FROM faq_items ORDER BY orderIndex ASC, question ASC, id ASC")
    abstract suspend fun allOnce(): List<FaqEntity>

    @Query("SELECT COUNT(*) FROM faq_items")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<FaqEntity>)

    @Query("DELETE FROM faq_items")
    abstract suspend fun deleteAll()

    /**
     * Remplace le miroir local par l'état exact du serveur, en UNE transaction : une question supprimée ou
     * modifiée par l'admin disparaît / change ici aussi, et l'écran ne voit jamais de liste à moitié vide
     * (Room n'émet qu'une fois la transaction validée). Liste vide = l'admin a vidé la FAQ.
     */
    @Transaction
    open suspend fun replaceAll(items: List<FaqEntity>) {
        deleteAll()
        if (items.isNotEmpty()) insertAll(items)
    }
}
