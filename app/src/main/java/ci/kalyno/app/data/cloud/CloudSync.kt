package ci.kalyno.app.data.cloud

import android.content.Context
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.auth.SupabaseAuthApi
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.srs.CardProgressEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Profil de l'élève sauvegardé dans le cloud. */
data class RemoteProfile(val nickname: String, val levelId: String)

/**
 * Synchronisation du compte : profil et progression SRS.
 *
 * - Local d'abord : Room reste la source de travail, le cloud sert de sauvegarde et de reprise.
 * - Une carte en conflit : la révision la plus récente gagne.
 * - Sans compte connecté ou sans réseau, tout est ignoré sans erreur.
 */
class CloudSync(context: Context) {

    private val appContext = context.applicationContext
    private val store = AuthStore(appContext)
    private val authApi = SupabaseAuthApi()
    private val prefs = AppPrefs(appContext)
    private val dao = KalynoDatabase.get(appContext).dashboardDao()

    /** Profil du compte, ou null s'il n'existe pas encore. */
    suspend fun fetchProfile(): RemoteProfile? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext null
        val userId = store.userId() ?: return@withContext null
        val rows = JSONArray(
            SupabaseRest.request("GET", "student_profiles?user_id=eq.$userId&select=nickname,level_id", token)
        )
        if (rows.length() == 0) null
        else rows.getJSONObject(0).let { RemoteProfile(it.getString("nickname"), it.getString("level_id")) }
    }

    /** Envoie pseudo et classe, seulement s'ils ont changé depuis le dernier envoi. */
    suspend fun pushProfile() = withContext(Dispatchers.IO) {
        val nickname = prefs.nickname ?: return@withContext
        val level = prefs.level ?: return@withContext
        val signature = "$nickname|$level"
        if (store.profileSignature() == signature) return@withContext

        val token = accessToken() ?: return@withContext
        val userId = store.userId() ?: return@withContext
        val body = JSONObject()
            .put("user_id", userId)
            .put("nickname", nickname)
            .put("level_id", level)
            .toString()
        SupabaseRest.request("POST", "student_profiles", token, body, "resolution=merge-duplicates,return=minimal")
        store.setProfileSignature(signature)
    }

    /** Récupère d'abord le cloud (autre téléphone), puis envoie les révisions locales plus récentes. */
    suspend fun syncProgress(studentId: String) = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext
        val userId = store.userId() ?: return@withContext
        pull(studentId, userId, token)
        push(studentId, userId, token)
    }

    private suspend fun pull(studentId: String, userId: String, token: String) {
        var since = store.lastPullAt()
        var maxSeen = since
        var allApplied = true

        while (true) {
            val rows = JSONArray(
                SupabaseRest.request(
                    "GET",
                    "student_card_progress?user_id=eq.$userId&last_reviewed_at=gt.$since" +
                        "&select=flashcard_id,ease_factor,interval_days,repetitions,due_date,last_reviewed_at" +
                        "&order=last_reviewed_at.asc&limit=$PAGE_SIZE",
                    token
                )
            )
            if (rows.length() == 0) break

            val remote = (0 until rows.length()).map { rows.getJSONObject(it) }
            val ids = remote.map { it.getString("flashcard_id") }
            val existing = ids.chunked(500).flatMap { dao.existingFlashcardIds(it) }.toSet()
            val local = ids.chunked(500).flatMap { dao.progressByIds(studentId, it) }.associateBy { it.flashcardId }

            val toSave = remote.mapNotNull { r ->
                val id = r.getString("flashcard_id")
                val reviewedAt = r.getLong("last_reviewed_at")
                if (id !in existing) {
                    allApplied = false // contenu pas (encore) présent sur ce téléphone
                    return@mapNotNull null
                }
                val mine = local[id]
                if (mine != null && (mine.lastReviewedAt ?: 0L) >= reviewedAt) null
                else CardProgressEntity(
                    studentId = studentId,
                    flashcardId = id,
                    easeFactor = r.getDouble("ease_factor").toFloat(),
                    intervalDays = r.getInt("interval_days"),
                    repetitions = r.getInt("repetitions"),
                    dueDate = r.getLong("due_date"),
                    lastReviewedAt = reviewedAt
                )
            }
            if (toSave.isNotEmpty()) dao.upsertProgress(toSave)

            since = remote.maxOf { it.getLong("last_reviewed_at") }
            maxSeen = maxOf(maxSeen, since)
            if (rows.length() < PAGE_SIZE) break
        }

        // On n'avance le repère que si tout a pu être appliqué : sinon on retentera au prochain passage
        if (allApplied) store.setLastPullAt(maxSeen)
    }

    private suspend fun push(studentId: String, userId: String, token: String) {
        var since = store.lastPushAt()

        while (true) {
            val rows = dao.progressReviewedSince(studentId, since, PUSH_BATCH)
            if (rows.isEmpty()) break

            val body = JSONArray()
            rows.forEach { p ->
                body.put(
                    JSONObject()
                        .put("user_id", userId)
                        .put("flashcard_id", p.flashcardId)
                        .put("ease_factor", p.easeFactor.toDouble())
                        .put("interval_days", p.intervalDays)
                        .put("repetitions", p.repetitions)
                        .put("due_date", p.dueDate)
                        .put("last_reviewed_at", p.lastReviewedAt ?: 0L)
                )
            }
            SupabaseRest.request(
                "POST", "student_card_progress", token, body.toString(), "resolution=merge-duplicates,return=minimal"
            )

            since = rows.last().lastReviewedAt ?: since
            store.setLastPushAt(since)
            if (rows.size < PUSH_BATCH) break
        }
    }

    /** Jeton d'accès valide : renouvelé automatiquement s'il expire dans moins d'une minute. */
    private suspend fun accessToken(): String? = tokenLock.withLock {
        val session = store.session() ?: return@withLock null
        if (session.expiresAtMillis - EXPIRY_MARGIN_MS > System.currentTimeMillis()) {
            return@withLock session.accessToken
        }
        try {
            val renewed = authApi.refresh(session.refreshToken)
            store.saveSession(renewed)
            renewed.accessToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val PUSH_BATCH = 200
        const val EXPIRY_MARGIN_MS = 60_000L
        val tokenLock = Mutex()
    }
}
