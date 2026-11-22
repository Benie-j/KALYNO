package ci.kalyno.app.data.cloud

import android.content.Context
import android.util.Log
import ci.kalyno.app.data.StreakStore

/**
 * Point d'entrée commun quand une session ou un défi est terminé : enregistre la série sur le
 * téléphone tout de suite, puis demande la synchronisation (WorkManager attend le réseau si besoin).
 */
object StreakRecorder {
    fun complete(context: Context, studentId: String): Int {
        val app = context.applicationContext
        val streak = StreakStore(app).recordCompletion()
        try {
            CloudSyncScheduler.request(CloudSync(app), studentId)
        } catch (e: Exception) {
            // La série est déjà enregistrée en local (envoi en attente) : elle partira à la prochaine synchro
            Log.w("KalynoCloud", "Synchro de la série non programmée : ${e.message}")
        }
        return streak
    }
}
