package ci.kalyno.app.data.cloud

import android.content.Context
import android.util.Log
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.auth.SupabaseAuthApi
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.srs.CardProgressEntity
import ci.kalyno.app.data.srs.ReviewLogEntity
import ci.kalyno.app.data.srs.ReviewRating
import ci.kalyno.app.data.lessons.LessonProgressEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
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

    internal val appContext = context.applicationContext
    private val store = AuthStore(appContext)
    private val authApi = SupabaseAuthApi()
    private val prefs = AppPrefs(appContext)
    private val dao = KalynoDatabase.get(appContext).dashboardDao()
    private val lessonDao = KalynoDatabase.get(appContext).lessonProgressDao()

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

    /**
     * Récupère d'abord le cloud (autre téléphone), puis envoie les révisions locales plus récentes.
     *
     * Chaque étape est isolée : si l'une échoue (table absente côté Supabase, réseau coupé), les autres
     * continuent, puis l'erreur est relancée pour que la tâche d'arrière-plan réessaie plus tard.
     * Avant, une seule étape en échec (ex. table des leçons manquante) bloquait l'envoi des cartes.
     */
    suspend fun syncProgress(studentId: String) = syncLock.withLock { syncProgressLocked(studentId) }

    private suspend fun syncProgressLocked(studentId: String) = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext
        val userId = store.userId() ?: return@withContext

        var firstError: Exception? = null
        suspend fun step(name: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Étape « $name » en échec : ${e.message}")
                if (firstError == null) firstError = e
            }
        }

        step("leçons reçues") { pullLessons(studentId, userId, token) }
        step("cartes reçues") { pull(studentId, userId, token) }
        step("cartes envoyées") { push(studentId, userId, token) }
        step("leçons envoyées") { pushLessons(studentId, userId, token) }
        step("historique reçu") { pullLogs(studentId, userId, token) }
        step("historique envoyé") { pushLogs(studentId, userId, token) }

        val error = firstError
        if (error != null) throw error
    }

    // ------------------------------------------------------------------ Cartes apprises

    private fun cardSelect(extended: Boolean) =
        "flashcard_id,ease_factor,interval_days,repetitions,due_date,last_reviewed_at,stability,difficulty,lapses" +
            if (extended) ",success_count,fail_count,relearn_left,avg_latency_ms,dojo_stage" else ""

    /** Lit une page de cartes ; si le serveur ne connaît pas encore les nouvelles colonnes, repli sur l'ancien format. */
    private fun fetchCards(userId: String, since: Long, token: String): JSONArray {
        fun get(extended: Boolean) = JSONArray(
            SupabaseRest.request(
                "GET",
                "student_card_progress?user_id=eq.$userId&last_reviewed_at=gt.$since" +
                    "&select=${cardSelect(extended)}&order=last_reviewed_at.asc&limit=$PAGE_SIZE",
                token
            )
        )
        return try {
            get(extendedCardColumns)
        } catch (e: IOException) {
            if (extendedCardColumns && isMissingSchema(e)) {
                Log.w(TAG, "Colonnes FSRS étendues absentes côté Supabase : format réduit (lancer le SQL de mise à jour)")
                extendedCardColumns = false
                get(false)
            } else throw e
        }
    }

    private suspend fun pull(studentId: String, userId: String, token: String) {
        var since = store.lastPullAt()
        var maxSeen = since
        var allApplied = true

        while (true) {
            val rows = fetchCards(userId, since, token)
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
                    dojoStage = r.optInt("dojo_stage", -1).takeIf { it >= 0 }
                        ?: ci.kalyno.app.data.dojo.DojoStagePolicy.fromRepetitions(r.getInt("repetitions")),
                    dueDate = r.getLong("due_date"),
                    lastReviewedAt = reviewedAt,
                    stability = r.optDouble("stability", 0.0),
                    difficulty = r.optDouble("difficulty", 0.0),
                    lapses = r.optInt("lapses", 0),
                    successCount = r.optInt("success_count", 0),
                    failCount = r.optInt("fail_count", 0),
                    relearnLeft = r.optInt("relearn_left", 0),
                    avgLatencyMs = r.optLong("avg_latency_ms", 0L)
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

    private fun cardJson(p: CardProgressEntity, userId: String, extended: Boolean): JSONObject {
        val o = JSONObject()
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
        if (extended) {
            o.put("success_count", p.successCount)
                .put("fail_count", p.failCount)
                .put("relearn_left", p.relearnLeft)
                .put("avg_latency_ms", p.avgLatencyMs)
                .put("dojo_stage", p.dojoStage)
        }
        return o
    }

    private fun postCards(rows: List<CardProgressEntity>, userId: String, token: String) {
        fun send(extended: Boolean) {
            val body = JSONArray()
            rows.forEach { body.put(cardJson(it, userId, extended)) }
            SupabaseRest.request("POST", "student_card_progress", token, body.toString(), UPSERT)
        }
        try {
            send(extendedCardColumns)
        } catch (e: IOException) {
            if (extendedCardColumns && isMissingSchema(e)) {
                Log.w(TAG, "Colonnes FSRS étendues absentes côté Supabase : envoi au format réduit")
                extendedCardColumns = false
                send(false)
            } else throw e
        }
    }

    private suspend fun push(studentId: String, userId: String, token: String) {
        var since = store.lastPushAt()

        while (true) {
            val rows = dao.progressReviewedSince(studentId, since, PUSH_BATCH)
            if (rows.isEmpty()) break

            postCards(rows, userId, token)

            // Le repère n'avance qu'après un envoi réussi : un échec est rejoué au prochain passage
            since = rows.last().lastReviewedAt ?: since
            store.setLastPushAt(since)
            if (rows.size < PUSH_BATCH) break
        }
    }

    // ------------------------------------------------------------------ Historique des révisions

    private fun logSelect(extended: Boolean) =
        "flashcard_id,rating,interval_before,interval_after,reviewed_at" +
            if (extended) ",latency_ms,retrievability_before" else ""

    private fun fetchLogs(userId: String, since: Long, token: String): JSONArray {
        fun get(extended: Boolean) = JSONArray(
            SupabaseRest.request(
                "GET",
                "student_review_log?user_id=eq.$userId&reviewed_at=gt.$since" +
                    "&select=${logSelect(extended)}&order=reviewed_at.asc&limit=$PAGE_SIZE",
                token
            )
        )
        return try {
            get(extendedLogColumns)
        } catch (e: IOException) {
            if (extendedLogColumns && isMissingSchema(e)) {
                extendedLogColumns = false
                get(false)
            } else throw e
        }
    }

    /** Récupère l'historique des révisions faites ailleurs (nouveau téléphone). Les doublons sont ignorés. */
    private suspend fun pullLogs(studentId: String, userId: String, token: String) {
        var since = store.lastLogPullAt()

        while (true) {
            val rows = fetchLogs(userId, since, token)
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
                    reviewedAt = r.getLong("reviewed_at"),
                    latencyMs = r.optLong("latency_ms", 0L),
                    retrievabilityBefore = r.optDouble("retrievability_before", 0.0)
                )
            }
            if (items.isNotEmpty()) dao.insertLogs(items)

            since = rows.getJSONObject(rows.length() - 1).getLong("reviewed_at")
            store.setLastLogPullAt(since)
            if (rows.length() < PAGE_SIZE) break
        }
    }

    private fun postLogs(rows: List<ReviewLogEntity>, userId: String, token: String) {
        fun send(extended: Boolean) {
            val body = JSONArray()
            rows.forEach { l ->
                val o = JSONObject()
                    .put("user_id", userId)
                    .put("flashcard_id", l.flashcardId)
                    .put("rating", l.rating.name)
                    .put("grade", l.rating.grade)
                    .put("interval_before", l.intervalBefore)
                    .put("interval_after", l.intervalAfter)
                    .put("reviewed_at", l.reviewedAt)
                if (extended) o.put("latency_ms", l.latencyMs).put("retrievability_before", l.retrievabilityBefore)
                body.put(o)
            }
            SupabaseRest.request("POST", "student_review_log", token, body.toString(), IGNORE_DUPLICATES)
        }
        try {
            send(extendedLogColumns)
        } catch (e: IOException) {
            if (extendedLogColumns && isMissingSchema(e)) {
                extendedLogColumns = false
                send(false)
            } else throw e
        }
    }

    /** Envoie l'historique local pas encore envoyé ; le serveur ignore les doublons. */
    private suspend fun pushLogs(studentId: String, userId: String, token: String) {
        var afterId = store.lastLogPushId()

        while (true) {
            val rows = dao.logsAfter(studentId, afterId, LOG_PUSH_BATCH)
            if (rows.isEmpty()) break

            postLogs(rows, userId, token)

            afterId = rows.last().logId
            store.setLastLogPushId(afterId)
            if (rows.size < LOG_PUSH_BATCH) break
        }
    }

    // ------------------------------------------------------------------ Leçons terminées

    /**
     * Restaure les leçons terminées sur ce téléphone. La validation ne se défait jamais :
     * on ajoute seulement celles qui manquent (l'union des appareils gagne).
     * Table absente côté Supabase : étape ignorée, les cartes ne sont pas bloquées.
     */
    private suspend fun pullLessons(studentId: String, userId: String, token: String) {
        val rows = try {
            JSONArray(
                SupabaseRest.request(
                    "GET",
                    "student_lesson_progress?user_id=eq.$userId&is_completed=eq.true" +
                        "&select=lesson_id,completed_at&limit=$LESSON_LIMIT",
                    token
                )
            )
        } catch (e: IOException) {
            if (isMissingSchema(e)) {
                Log.w(TAG, "Table student_lesson_progress absente côté Supabase : leçons non restaurées")
                return
            }
            throw e
        }
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            lessonDao.insertIfAbsent(
                LessonProgressEntity(
                    studentId = studentId,
                    lessonId = r.getString("lesson_id"),
                    isCompleted = true,
                    completedAt = r.optLong("completed_at", System.currentTimeMillis())
                )
            )
        }
    }

    /** Envoie les leçons terminées ; ignoré tant que la liste n'a pas changé depuis le dernier envoi. */
    private suspend fun pushLessons(studentId: String, userId: String, token: String) {
        val done = lessonDao.completedRows(studentId)
        if (done.isEmpty()) return
        val signature = done.map { it.lessonId }.sorted().hashCode()
        if (signature == pushedLessonsSignature) return

        try {
            done.chunked(200).forEach { chunk ->
                val body = JSONArray()
                chunk.forEach { l ->
                    body.put(
                        JSONObject()
                            .put("user_id", userId)
                            .put("lesson_id", l.lessonId)
                            .put("is_completed", true)
                            .put("completed_at", l.completedAt ?: System.currentTimeMillis())
                    )
                }
                SupabaseRest.request("POST", "student_lesson_progress", token, body.toString(), UPSERT)
            }
        } catch (e: IOException) {
            if (isMissingSchema(e)) {
                Log.w(TAG, "Table student_lesson_progress absente côté Supabase : leçons non envoyées")
                return
            }
            throw e
        }
        pushedLessonsSignature = signature
    }

    /** Vrai quand Supabase répond « table ou colonne inconnue » (SQL de mise à jour pas encore lancé). */
    private fun isMissingSchema(e: Exception): Boolean {
        val m = e.message.orEmpty()
        return MISSING_SCHEMA_MARKERS.any { m.contains(it) }
    }

    /** Jeton d'accès valide pour les autres dépôts (ex. signalements) ; null sans compte connecté. */
    internal suspend fun validAccessToken(): String? = accessToken()

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
        const val LESSON_LIMIT = 1000
        val tokenLock = Mutex()
        val syncLock = Mutex()

        @Volatile
        var pushedLessonsSignature = 0

        /** Passent à faux si Supabase ne connaît pas encore les colonnes FSRS étendues (repli sur l'ancien format). */
        @Volatile
        var extendedCardColumns = true

        @Volatile
        var extendedLogColumns = true

        const val TAG = "KalynoCloud"
        const val UPSERT = "resolution=merge-duplicates,return=minimal"
        const val IGNORE_DUPLICATES = "resolution=ignore-duplicates,return=minimal"
        val MISSING_SCHEMA_MARKERS = listOf("PGRST204", "PGRST205", "42703", "42P01", "HTTP 404")
    }
}
