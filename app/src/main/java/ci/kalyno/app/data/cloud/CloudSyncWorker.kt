package ci.kalyno.app.data.cloud

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/**
 * Tâche WorkManager qui synchronise la progression avec Supabase.
 *
 * Sans compte connecté, [CloudSync.syncProgress] ne fait rien : la tâche réussit sans bruit.
 * En cas d'erreur réseau ou serveur, WorkManager réessaie avec un délai croissant (5 essais max) ;
 * la progression reste dans Room, donc rien n'est jamais perdu.
 */
class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val studentId = inputData.getString(KEY_STUDENT_ID) ?: return Result.failure()
        return try {
            CloudSync(applicationContext).syncProgress(studentId)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Synchro reportée (essai ${runAttemptCount + 1}) : ${e.message}")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_STUDENT_ID = "studentId"
        private const val TAG = "KalynoCloud"
        private const val MAX_ATTEMPTS = 5
    }
}
