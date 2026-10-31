package ci.kalyno.app.data.reports

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Renvoie les signalements restés dans Room faute de réseau.
 * Si des essais échouent, les rapports restent en attente (rien n'est supprimé) et repartent
 * au prochain lancement de l'appli ou au prochain signalement.
 */
class ReportSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val result = ReportRepository.get(applicationContext).syncPending()
        when {
            result.remaining == 0 -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Renvoi des signalements reporté (essai ${runAttemptCount + 1}) : ${e.message}")
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private companion object {
        const val TAG = "KalynoReports"
        const val MAX_ATTEMPTS = 5
    }
}

object ReportSyncScheduler {

    private const val WORK_NAME = "kalyno-report-sync"
    private const val BACKOFF_SECONDS = 30L

    /** Réseau requis : hors ligne, la tâche attend la connexion, même après un redémarrage du téléphone. */
    fun request(context: Context) {
        val work = OneTimeWorkRequestBuilder<ReportSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, work)
    }

    /** Au lancement de l'appli : reprend les rapports restés en attente lors d'une session précédente. */
    suspend fun requestIfPending(context: Context) {
        if (ReportRepository.get(context).hasPending()) request(context)
    }
}
