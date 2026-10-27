package ci.kalyno.app.data.dashboard

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Citation épinglée par l'élève. Le texte est copié : elle reste lisible hors connexion, même retirée par l'admin. */
@Entity(tableName = "quote_favorites")
data class QuoteFavoriteEntity(
    @PrimaryKey val quoteKey: String,
    val content: String,
    val author: String?,
    val savedAt: Long
)

@Dao
interface QuoteFavoriteDao {
    @Query("SELECT * FROM quote_favorites ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<QuoteFavoriteEntity>>

    @Upsert
    suspend fun upsert(item: QuoteFavoriteEntity)

    @Query("DELETE FROM quote_favorites WHERE quoteKey = :quoteKey")
    suspend fun delete(quoteKey: String)
}

/** Favori tel qu'affiché par l'interface. */
data class FavoriteQuote(val key: String, val quote: DailyQuote)

/** Identifiant stable d'une citation (même texte = même clé, d'un lancement à l'autre). */
fun DailyQuote.favoriteKey(): String = text.trim().hashCode().toString(16)
