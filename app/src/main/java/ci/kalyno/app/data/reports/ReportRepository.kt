package ci.kalyno.app.data.reports

import android.content.Context
import android.util.Log
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.cloud.CloudSync
import ci.kalyno.app.data.cloud.SupabaseRest
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.dashboard.StudentId
import ci.kalyno.app.data.dashboard.SupabaseHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.UUID

/** Ce que l'élève a rempli dans le formulaire. */
data class ReportDraft(
    val sourceType: ReportSourceType,
    val sourceId: String?,
    val category: ReportCategory,
    val description: String,
    val contextLabel: String?
)

enum class ReportOutcome {
    /** Supabase a bien reçu le signalement. */
    SENT,

    /** Pas de réseau (ou serveur indisponible) : gardé dans Room, envoyé dès que possible. */
    QUEUED
}

data class ReportSyncResult(val sent: Int, val remaining: Int)

/**
 * Signalements : offline-first.
 *
 * 1. Le rapport est d'abord écrit dans Room : même si l'appli est fermée en plein envoi, rien n'est perdu.
 * 2. Envoi direct à Supabase. Succès -> la ligne est marquée envoyée. Échec -> elle reste en attente et
 *    [ReportSyncScheduler] la renvoie dès que le réseau revient.
 * 3. Rien n'est jamais supprimé.
 *
 * Sans compte, l'envoi se fait avec la clé publique seule (rôle anonyme, insertion uniquement).
 */
class ReportRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = KalynoDatabase.get(appContext).reportDao()
    private val cloud = CloudSync(appContext)
    private val store = AuthStore(appContext)

    suspend fun submit(draft: ReportDraft): ReportOutcome = withContext(Dispatchers.IO) {
        val report = ReportEntity(
            id = UUID.randomUUID().toString(),
            // Compte connecté si possible, sinon l'identifiant local anonyme de l'élève
            userId = store.userId() ?: StudentId.get(appContext),
            sourceType = draft.sourceType.name,
            sourceId = draft.sourceId?.takeIf { it.isNotBlank() },
            category = draft.category.name,
            description = ReportRules.clean(draft.description),
            contextLabel = draft.contextLabel?.take(MAX_CONTEXT),
            status = STATUS_PENDING,
            createdAt = System.currentTimeMillis()
        )
        dao.insert(report)

        var delivered = false
        try {
            syncPending()
            delivered = dao.isSynced(report.id)
        } catch (e: CancellationException) {
            ReportSyncScheduler.request(appContext)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Envoi différé : ${e.message}")
        }

        if (delivered) {
            ReportOutcome.SENT
        } else {
            ReportSyncScheduler.request(appContext)
            ReportOutcome.QUEUED
        }
    }

    suspend fun hasPending(): Boolean = withContext(Dispatchers.IO) { dao.pendingCount() > 0 }

    /**
     * Envoie les rapports en attente. Un réseau coupé arrête simplement l'essai ; un rapport refusé par le
     * serveur (erreur 4xx) n'empêche pas les autres de partir, et reste en attente (jamais supprimé).
     */
    suspend fun syncPending(): ReportSyncResult = withContext(Dispatchers.IO) {
        syncLock.withLock {
            var sent = 0
            for (chunk in dao.pending(MAX_PER_RUN).chunked(BATCH)) {
                try {
                    push(chunk)
                    sent += chunk.size
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    if (!isPermanentRejection(e)) break // hors ligne ou serveur indisponible : on réessaiera
                    sent += pushOneByOne(chunk)
                }
            }
            ReportSyncResult(sent = sent, remaining = dao.pendingCount())
        }
    }

    private suspend fun pushOneByOne(chunk: List<ReportEntity>): Int {
        var sent = 0
        for (report in chunk) {
            try {
                push(listOf(report))
                sent++
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Signalement ${report.id} refusé, gardé en local : ${e.message}")
            }
        }
        return sent
    }

    /** Un POST par mode d'accès (compte / anonyme), puis marquage « envoyé » du lot réussi. */
    private suspend fun push(batch: List<ReportEntity>) {
        val token = cloud.validAccessToken()
        val signedUserId = store.userId()
        val (own, anonymous) = batch.partition { token != null && it.userId == signedUserId }

        if (own.isNotEmpty() && token != null) {
            SupabaseRest.request("POST", "reports", token, body(own), PREFER)
            dao.markSynced(own.map { it.id }, System.currentTimeMillis())
        }
        if (anonymous.isNotEmpty()) {
            SupabaseHttp.post("reports", body(anonymous), PREFER)
            dao.markSynced(anonymous.map { it.id }, System.currentTimeMillis())
        }
    }

    private fun body(reports: List<ReportEntity>): String {
        val array = JSONArray()
        reports.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("user_id", r.userId)
                    .put("source_type", r.sourceType)
                    .put("source_id", r.sourceId ?: JSONObject.NULL)
                    .put("category", r.category)
                    .put("description", r.description)
                    .put("context_label", r.contextLabel ?: JSONObject.NULL)
                    .put("status", r.status)
                    .put("created_at", Instant.ofEpochMilli(r.createdAt).toString())
            )
        }
        return array.toString()
    }

    /** Erreur 4xx qui ne dépend pas du réseau (401 jeton expiré, 408 et 429 sont réessayables). */
    private fun isPermanentRejection(e: IOException): Boolean {
        val code = HTTP_CODE.find(e.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: return false
        return code in 400..499 && code != 401 && code != 408 && code != 429
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        private const val TAG = "KalynoReports"
        private const val PREFER = "resolution=ignore-duplicates,return=minimal"
        private const val BATCH = 50
        private const val MAX_PER_RUN = 200
        private const val MAX_CONTEXT = 300
        private val HTTP_CODE = Regex("Supabase HTTP (\\d{3})")
        private val syncLock = Mutex()

        @Volatile
        private var instance: ReportRepository? = null

        fun get(context: Context): ReportRepository =
            instance ?: synchronized(this) {
                instance ?: ReportRepository(context).also { instance = it }
            }
    }
}
