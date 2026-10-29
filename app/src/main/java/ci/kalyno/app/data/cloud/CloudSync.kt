package ci.kalyno.app.data.cloud

import android.content.Context
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.auth.SupabaseAuthApi
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.srs.CardProgressEntity
import ci.kalyno.app.data.srs.ReviewLogEntity
import ci.kalyno.app.data.srs.ReviewRating
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
 * Synchronisation du compte : profil, progression SRS et historique des révisions.
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
        // L'historique sert aux quotas du jour et à la série : on le restaure puis on envoie le local
        pullLogs(studentId, userId, token)
        pushLogs(studentId, userId, token)
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
                        "&select=flashcard_id,ease_factor,interval_days,repetitions,due_date,last_reviewed_at," +
                        "stability,difficulty,lapses" +
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
                    dojoStage = ci.kalyno.app.data.dojo.DojoStagePolicy.fromRepetitions(r.getInt("repetitions")),
                    dueDate = r.getLong("due_date"),
                    lastReviewedAt = reviewedAt,
                    stability = r.optDouble("stability", 0.0),
                    difficulty = r.optDouble("difficulty", 0.0),
                    lapses = r.optInt("lapses", 0)
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
                        .put("stability", p.stability)
                        .put("difficulty", p.difficulty)
                        .put("lapses", p.lapses)
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

    /** Récupère l'historique des révisions faites ailleurs (nouveau téléphone). Les doublons sont ignorés. */
    private suspend fun pullLogs(studentId: String, userId: String, token: String) {
        var since = store.lastLogPullAt()

        while (true) {
            val rows = JSONArray(
                SupabaseRest.request(
                    "GET",
                    "student_review_log?user_id=eq.$userId&reviewed_at=gt.$since" +
                        "&select=flashcard_id,rating,interval_before,interval_after,reviewed_at" +
                        "&order=reviewed_at.asc&limit=$PAGE_SIZE",
                    token
                )
            )
            if (rows.length() == 0) break

            val items = (0 until rows.length()).mapNotNull { i ->
                val r = rows.getJSONObject(i)
                val rating = runCatching { ReviewRating.valueOf(r.getString("rating")) }.getOrNull()
                    ?: return@mapNotNull null
                ReviewLogEntity(
                    studentId = studentId,
                    flashcardId = r.getString("flashcard_id"),
                    rating = rating,
                    intervalBefore = r.getInt("interval_before"),
                    intervalAfter = r.getInt("interval_after"),
                    reviewedAt = r.getLong("reviewed_at")
                )
            }
            if (items.isNotEmpty()) dao.insertLogs(items)

            since = rows.getJSONObject(rows.length() - 1).getLong("reviewed_at")
            store.setLastLogPullAt(since)
            if (rows.length() < PAGE_SIZE) break
        }
    }

    /** Envoie l'historique local pas encore envoyé ; le serveur ignore les doublons. */
    private suspend fun pushLogs(studentId: String, userId: String, token: String) {
        var afterId = store.lastLogPushId()

        while (true) {
            val rows = dao.logsAfter(studentId, afterId, LOG_PUSH_BATCH)
            if (rows.isEmpty()) break

            val body = JSONArray()
            rows.forEach { l ->
                body.put(
                    JSONObject()
                        .put("user_id", userId)
                        .put("flashcard_id", l.flashcardId)
                        .put("rating", l.rating.name)
                        .put("grade", l.rating.grade)
                        .put("interval_before", l.intervalBefore)
                        .put("interval_after", l.intervalAfter)
                        .put("reviewed_at", l.reviewedAt)
                )
            }
            SupabaseRest.request(
                "POST", "student_review_log", token, body.toString(), "resolution=ignore-duplicates,return=minimal"
            )

            afterId = rows.last().logId
            store.setLastLogPushId(afterId)
            if (rows.size < LOG_PUSH_BATCH) break
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
        const val LOG_PUSH_BATCH = 500
        const val EXPIRY_MARGIN_MS = 60_000L
        val tokenLock = Mutex()
    }
}
