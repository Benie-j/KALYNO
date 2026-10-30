package ci.kalyno.app.data.cloud

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Programme la sauvegarde de la progression vers Supabase via WorkManager.
 *
 * - Réseau requis : hors ligne, la tâche attend la connexion (même après redémarrage du téléphone).
 * - Anti-rebond : chaque demande remplace la précédente et part [DELAY_SECONDS] s plus tard, donc une
 *   série de réponses du Dojo donne une seule synchro. Une synchro interrompue reprend sans perte
 *   (les repères avancent lot par lot).
 */
object CloudSyncScheduler {

    private const val WORK_NAME = "kalyno-cloud-sync"
    private const val DELAY_SECONDS = 3L
    private const val BACKOFF_SECONDS = 30L

    fun request(cloud: CloudSync, studentId: String) {
        val work = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(CloudSyncWorker.KEY_STUDENT_ID to studentId))
            .build()
        WorkManager.getInstance(cloud.appContext).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
    }
}
