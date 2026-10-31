package ci.kalyno.app.data.faq

import android.content.Context
import android.util.Log
import ci.kalyno.app.data.dashboard.KalynoDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

enum class FaqSyncResult {
    /** Le serveur avait des changements : Room a été mis à jour. */
    UPDATED,

    /** Déjà à jour : aucune écriture. */
    UNCHANGED,

    /** Pas de réseau, serveur indisponible ou réponse invalide : Room reste tel quel (FAQ hors ligne intacte). */
    FAILED
}

/**
 * Centre d'aide : offline-first.
 *
 * - L'interface ne lit QUE Room ([observeItems]) : affichage instantané, 100 % fonctionnel hors ligne.
 * - [refresh] interroge Supabase en arrière-plan et réécrit Room ; le `Flow` de Room remet l'écran à jour
 *   tout seul, sans que l'interface n'attende le réseau.
 * - Un échec réseau ne touche jamais au contenu déjà stocké.
 */
class FaqRepository private constructor(context: Context) {

    private val dao = KalynoDatabase.get(context.applicationContext).faqDao()

    /** La FAQ locale, dans l'ordre de l'admin ; se ré-émet après chaque synchronisation qui change quelque chose. */
    fun observeItems(): Flow<List<FaqItem>> = dao.observeAll().map { rows -> rows.map(FaqEntity::toItem) }

    suspend fun hasCachedContent(): Boolean = withContext(Dispatchers.IO) { dao.count() > 0 }

    /** Récupère l'état courant de la FAQ côté Supabase et aligne Room dessus. Ne lève pas d'erreur réseau. */
    suspend fun refresh(): FaqSyncResult = withContext(Dispatchers.IO) {
        syncLock.withLock {
            try {
                val remote = FaqRemoteSource.fetch()
                // Comparaison sans ordre : le tri du serveur et celui de SQLite peuvent différer sur les accents.
                if (remote.toSet() == dao.allOnce().toSet()) {
                    FaqSyncResult.UNCHANGED
                } else {
                    dao.replaceAll(remote)
                    FaqSyncResult.UPDATED
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Synchronisation FAQ différée : ${e.message}")
                FaqSyncResult.FAILED
            } catch (e: Exception) {
                Log.w(TAG, "Synchronisation FAQ impossible : ${e.message}")
                FaqSyncResult.FAILED
            }
        }
    }

    companion object {
        private const val TAG = "KalynoFaq"
        private val syncLock = Mutex()

        @Volatile
        private var instance: FaqRepository? = null

        fun get(context: Context): FaqRepository =
            instance ?: synchronized(this) {
                instance ?: FaqRepository(context.applicationContext).also { instance = it }
            }
    }
}
