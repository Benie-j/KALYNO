package ci.kalyno.app.data.cloud

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pousse la progression vers Supabase en arrière-plan, sans jamais bloquer l'élève.
 *
 * Les demandes rapprochées (une réponse du Dojo après l'autre) sont regroupées : une seule synchro
 * part [DEBOUNCE_MS] après la première. Hors ligne ou sans compte, rien n'est perdu : la progression
 * reste dans Room et part à la prochaine synchro (ouverture du dashboard, ou prochaine réponse).
 */
object CloudSyncScheduler {

    private const val TAG = "KalynoCloud"
    private const val DEBOUNCE_MS = 3_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = AtomicBoolean(false)

    fun request(cloud: CloudSync, studentId: String) {
        if (!pending.compareAndSet(false, true)) return
        scope.launch {
            delay(DEBOUNCE_MS)
            pending.set(false)
            try {
                cloud.syncProgress(studentId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Synchro reportée : ${e.message}")
            }
        }
    }
}
