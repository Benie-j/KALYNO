package ci.kalyno.app.data.cloud

import android.content.Context
import android.util.Log
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.StreakLogic
import ci.kalyno.app.data.StreakState
import ci.kalyno.app.data.StreakStore
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.auth.SupabaseAuthApi
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.progress.DojoAttemptEntity
import ci.kalyno.app.data.srs.CardProgressEntity
import ci.kalyno.app.data.srs.ReviewLogEntity
import ci.kalyno.app.data.srs.ReviewRating
import ci.kalyno.app.data.lessons.LessonProgressEntity
import ci.kalyno.app.data.lessons.SessionProgressEntity
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
 * Synchronisation du compte : profil, progression SRS, historique des révisions et série de jours.
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
    private val sessionDao = KalynoDatabase.get(appContext).sessionProgressDao()
    private val attemptDao = KalynoDatabase.get(appContext).attemptDao()

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

        // Une seule fois après ce correctif : on renvoie toute la progression locale
        val flags = appContext.getSharedPreferences("kalyno_cloud_flags", Context.MODE_PRIVATE)
        if (!flags.getBoolean(FULL_RESEND_FLAG, false)) {
            store.setLastPushAt(0L)
            store.setLastLogPushId(0L)
        }

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
        step("séances reçues") { pullSessions(studentId, userId, token) }
        step("cartes reçues") { pull(studentId, userId, token) }
        step("cartes envoyées") { push(studentId, userId, token) }
        step("leçons envoyées") { pushLessons(studentId, userId, token) }
        step("séances envoyées") { pushSessions(studentId, userId, token) }
        step("historique reçu") { pullLogs(studentId, userId, token) }
        step("historique envoyé") { pushLogs(studentId, userId, token) }
        step("tentatives Dojo reçues") { pullDojoAttempts(studentId, userId, token) }
        step("tentatives Dojo envoyées") { pushDojoAttempts(studentId, userId, token) }

        // La série a sa propre étape : une erreur ici n'empêche pas les autres, et ne bloque pas le drapeau d'envoi complet
        var streakError: Exception? = null
        try {
            syncStreak(userId, token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Étape « série » en échec : ${e.message}")
            streakError = e
        }

        val error = firstError
        if (error != null) throw error
        flags.edit().putBoolean(FULL_RESEND_FLAG, true).apply()
        if (streakError != null) throw streakError
    }

    // ------------------------------------------------------------------ Série de jours

    /** Série du compte stockée dans student_streaks, ou null s'il n'y a pas encore de ligne. */
    private fun fetchStreakRow(userId: String, token: String): StreakState? {
        val rows = JSONArray(
            SupabaseRest.request(
                "GET",
                "student_streaks?user_id=eq.$userId&select=streak,last_completion_day&limit=1",
                token
            )
        )
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        if (row.isNull("streak") || row.isNull("last_completion_day")) return null
        return StreakState(row.getInt("streak"), row.getLong("last_completion_day"))
    }

    private fun postStreak(userId: String, state: StreakState, token: String) {
        val body = JSONObject()
            .put("user_id", userId)
            .put("streak", state.streak)
            .put("last_completion_day", state.lastDay)
            .put("updated_at", java.time.Instant.now().toString())
            .toString()
        SupabaseRest.request("POST", "student_streaks?on_conflict=user_id", token, body, UPSERT)
    }

    /**
     * Fusionne la série locale et celle du cloud (règle : dernier jour de session), restaure ou envoie
     * selon le cas. La clé distante est toujours le user_id du compte connecté.
     * Table absente côté Supabase : étape ignorée, la série locale reste en attente d'envoi.
     */
    private suspend fun syncStreak(userId: String, token: String) {
        val streaks = StreakStore(appContext)
        val local = streaks.stateFor(userId)
        val remote = try {
            fetchStreakRow(userId, token)
        } catch (e: IOException) {
            if (isMissingSchema(e)) {
                Log.w(TAG, "Table student_streaks absente côté Supabase : série non synchronisée")
                return
            }
            throw e
        }

        val merge = StreakLogic.merge(local, remote)
        if (merge.restoreLocal) streaks.restore(userId, merge.state)
        if (merge.pushRemote) {
            try {
                postStreak(userId, merge.state, token)
            } catch (e: IOException) {
                if (isMissingSchema(e)) {
                    Log.w(TAG, "Table student_streaks absente côté Supabase : série non envoyée")
                    return
                }
                throw e
            }
        }
        streaks.markSynced(userId, merge.state)
    }

    // ------------------------------------------------------------------ Cartes apprises

    private fun cardSelect(extended: Boolean) =
        "flashcard_id,ease_factor,interval_days,repetitions,due_date,last_reviewed_at,stability,difficulty,lapses" +
            if (extended) ",success_count,fail_count,relearn_left,avg_latency_ms,dojo_stage" else ""

    /** Lit une page de cartes ; si le serveur ne connaît pas encore les nouvelles colonnes, repli sur l'ancien format. */
    private fun fetchCards(userId: String, offset: Int, token: String): JSONArray {
        fun get(extended: Boolean) = JSONArray(
            SupabaseRest.request(
                "GET",
                "student_card_progress?user_id=eq.$userId" +
                    "&select=${cardSelect(extended)}&order=last_reviewed_at.asc,flashcard_id.asc" +
                    "&limit=$PAGE_SIZE&offset=$offset",
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
        var offset = 0
        while (true) {
            val rows = fetchCards(userId, offset, token)
            if (rows.length() == 0) break
            val remote = (0 until rows.length()).map { rows.getJSONObject(it) }
            val ids = remote.map { it.getString("flashcard_id") }
            val existing = ids.chunked(500).flatMap { dao.existingFlashcardIds(it) }.toSet()
            val local = ids.chunked(500).flatMap { dao.progressByIds(studentId, it) }.associateBy { it.flashcardId }
            val toSave = remote.mapNotNull { r ->
                val id = r.getString("flashcard_id")
                val reviewedAt = r.getLong("last_reviewed_at")
                if (id !in existing) return@mapNotNull null
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
            offset += rows.length()
            if (rows.length() < PAGE_SIZE) break
        }
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

    private fun sendCards(rows: List<CardProgressEntity>, userId: String, token: String, extended: Boolean) {
        val body = JSONArray()
        rows.forEach { body.put(cardJson(it, userId, extended)) }
        SupabaseRest.request("POST", "student_card_progress", token, body.toString(), UPSERT)
    }

    /** Erreur « données refusées » (400, 409, 422) : propre à une carte, contrairement à un réseau coupé ou à un refus d'accès. */
    private fun isRejectedData(e: IOException): Boolean {
        val m = e.message.orEmpty()
        return m.startsWith("Supabase HTTP 400") || m.startsWith("Supabase HTTP 409") || m.startsWith("Supabase HTTP 422")
    }

    /**
     * Envoie un lot de cartes. Si le serveur refuse le lot (ex. une carte supprimée du catalogue), on renvoie les
     * cartes une par une : seule la carte fautive est ignorée (et notée dans les logs), les autres sont sauvegardées.
     * Avant, une seule carte refusée bloquait toute la sauvegarde, à chaque synchronisation.
     */
    private fun postCards(rows: List<CardProgressEntity>, userId: String, token: String) {
        try {
            sendCards(rows, userId, token, extendedCardColumns)
        } catch (e: IOException) {
            when {
                extendedCardColumns && isMissingSchema(e) -> {
                    Log.w(TAG, "Colonnes FSRS étendues absentes côté Supabase : envoi au format réduit")
                    extendedCardColumns = false
                    postCards(rows, userId, token)
                }
                rows.size > 1 && isRejectedData(e) -> {
                    Log.w(TAG, "Lot refusé (${e.message?.take(160)}) : envoi carte par carte")
                    rows.forEach { row ->
                        try {
                            sendCards(listOf(row), userId, token, extendedCardColumns)
                        } catch (single: IOException) {
                            if (isRejectedData(single)) {
                                Log.w(TAG, "Carte ${row.flashcardId} ignorée : ${single.message?.take(160)}")
                            } else throw single
                        }
                    }
                }
                else -> throw e
            }
        }
    }

    private suspend fun push(studentId: String, userId: String, token: String) {
        dao.allReviewedProgress(studentId).chunked(PUSH_BATCH).forEach { batch ->
            postCards(batch, userId, token)
        }
    }

    // ------------------------------------------------------------------ Historique des révisions

    private fun logSelect(extended: Boolean) =
        "flashcard_id,rating,interval_before,interval_after,reviewed_at" +
            if (extended) ",latency_ms,retrievability_before" else ""

    private fun fetchLogs(userId: String, offset: Int, token: String): JSONArray {
        fun get(extended: Boolean) = JSONArray(
            SupabaseRest.request(
                "GET",
                "student_review_log?user_id=eq.$userId" +
                    "&select=${logSelect(extended)}&order=reviewed_at.asc,flashcard_id.asc" +
                    "&limit=$PAGE_SIZE&offset=$offset",
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
        var offset = 0
        while (true) {
            val rows = fetchLogs(userId, offset, token)
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
            offset += rows.length()
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

    // ------------------------------------------------------------------ Tentatives du Dojo

    /** Clé déterministe : le rejeu d'une même tentative ne crée jamais une seconde ligne cloud. */
    private fun dojoAttemptClientId(userId: String, a: DojoAttemptEntity): String =
        "$userId:${a.flashcardId}:${a.attemptedAt}:${a.attemptNumber}"

    private fun dojoAttemptJson(a: DojoAttemptEntity, userId: String): JSONObject =
        JSONObject()
            .put("client_attempt_id", dojoAttemptClientId(userId, a))
            .put("user_id", userId)
            .put("flashcard_id", a.flashcardId)
            .put("attempted_at", a.attemptedAt)
            .put("is_success", a.isSuccess)
            .put("attempt_number", a.attemptNumber)
            .put("rating", a.rating.name)
            .put("kind", a.kind)
            .put("question", a.question)
            .put("student_answer", a.studentAnswer ?: JSONObject.NULL)
            .put("reference_answer", a.referenceAnswer)
            .put("lesson_title", a.lessonTitle)
            .put("subject_name", a.subjectName)
            .put("is_legacy", a.isLegacy)

    private suspend fun pushDojoAttempts(studentId: String, userId: String, token: String) {
        attemptDao.allForStudent(studentId).chunked(PUSH_BATCH).forEach { batch ->
            val body = JSONArray()
            batch.forEach { body.put(dojoAttemptJson(it, userId)) }
            SupabaseRest.request(
                "POST",
                "student_dojo_attempts?on_conflict=client_attempt_id",
                token,
                body.toString(),
                IGNORE_DUPLICATES
            )
        }
    }

    /** Restaure les tentatives enregistrées sur les autres appareils ; Room ignore les doublons. */
    private suspend fun pullDojoAttempts(studentId: String, userId: String, token: String) {
        var offset = 0
        while (true) {
            val rows = JSONArray(
                SupabaseRest.request(
                    "GET",
                    "student_dojo_attempts?user_id=eq.$userId" +
                        "&select=flashcard_id,attempted_at,is_success,attempt_number,rating,kind,question," +
                        "student_answer,reference_answer,lesson_title,subject_name,is_legacy" +
                        "&order=attempted_at.asc,client_attempt_id.asc&limit=$PAGE_SIZE&offset=$offset",
                    token
                )
            )
            if (rows.length() == 0) break
            val items = (0 until rows.length()).mapNotNull { i ->
                val r = rows.getJSONObject(i)
                val rating = runCatching { ReviewRating.valueOf(r.getString("rating")) }.getOrNull()
                    ?: return@mapNotNull null
                DojoAttemptEntity(
                    studentId = studentId,
                    flashcardId = r.getString("flashcard_id"),
                    attemptedAt = r.getLong("attempted_at"),
                    isSuccess = r.getBoolean("is_success"),
                    attemptNumber = r.optInt("attempt_number", 0),
                    rating = rating,
                    kind = r.getString("kind"),
                    question = r.getString("question"),
                    studentAnswer = if (r.isNull("student_answer")) null else r.getString("student_answer"),
                    referenceAnswer = r.getString("reference_answer"),
                    lessonTitle = r.optString("lesson_title", ""),
                    subjectName = r.optString("subject_name", ""),
                    isLegacy = r.optBoolean("is_legacy", false)
                )
            }
            if (items.isNotEmpty()) attemptDao.insertAll(items)
            offset += rows.length()
            if (rows.length() < PAGE_SIZE) break
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

    /**
     * Séances terminées (table `lesson_session_progress`). `user_id` est l'UUID du compte Supabase (store.userId()),
     * jamais l'identifiant local de l'élève. Fusion : on ajoute seulement les séances absentes en local.
     */
    private suspend fun pullSessions(studentId: String, userId: String, token: String) {
        val rows = try {
            JSONArray(
                SupabaseRest.request(
                    "GET",
                    "lesson_session_progress?user_id=eq.$userId&select=session_id,completed_at&limit=$LESSON_LIMIT",
                    token
                )
            )
        } catch (e: IOException) {
            if (isMissingSchema(e)) {
                Log.w(TAG, "Table lesson_session_progress absente côté Supabase : séances non restaurées")
                return
            }
            throw e
        }
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            sessionDao.insertIfAbsent(
                SessionProgressEntity(
                    studentId = studentId,
                    sessionId = r.getString("session_id"),
                    completedAt = parseCompletedAt(r.opt("completed_at"))
                )
            )
        }
    }

    /** `completed_at` peut être un horodatage ISO (timestamptz) ou un nombre (epoch ms) selon le schéma. */
    private fun parseCompletedAt(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is String -> runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrElse { value.toLongOrNull() ?: System.currentTimeMillis() }
        else -> System.currentTimeMillis()
    }

    /** Envoie les séances terminées (idempotent : « ignore-duplicates » sur la clé primaire, aucun doublon). */
    private suspend fun pushSessions(studentId: String, userId: String, token: String) {
        val done = sessionDao.completedRows(studentId)
        if (done.isEmpty()) return
        val signature = done.map { it.sessionId }.sorted().hashCode()
        if (signature == pushedSessionsSignature) return

        try {
            done.chunked(200).forEach { chunk -> postSessionRows(chunk, userId, token) }
        } catch (e: IOException) {
            if (isMissingSchema(e)) {
                Log.w(TAG, "Table lesson_session_progress absente côté Supabase : séances non envoyées")
                return
            }
            throw e
        }
        pushedSessionsSignature = signature
    }

    private fun postSessionRows(rows: List<SessionProgressEntity>, userId: String, token: String) {
        fun send(asIso: Boolean) {
            val body = JSONArray()
            rows.forEach { r ->
                body.put(
                    JSONObject()
                        .put("user_id", userId)
                        .put("session_id", r.sessionId)
                        .put("completed_at", if (asIso) java.time.Instant.ofEpochMilli(r.completedAt).toString() else r.completedAt)
                )
            }
            SupabaseRest.request("POST", "lesson_session_progress", token, body.toString(), IGNORE_DUPLICATES)
        }
        try {
            send(asIso = true)
        } catch (e: IOException) {
            // Colonne numérique plutôt que timestamptz : on retente avec des millisecondes
            if (e.message?.startsWith("Supabase HTTP 400") == true) send(asIso = false) else throw e
        }
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
            Log.w(TAG, "Échec du renouvellement de session Supabase", e)
            throw IOException(
                "Impossible de renouveler la session cloud. Synchronisation à réessayer.",
                e
            )
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

        @Volatile
        var pushedSessionsSignature = 0

        /** Passent à faux si Supabase ne connaît pas encore les colonnes FSRS étendues (repli sur l'ancien format). */
        @Volatile
        var extendedCardColumns = true

        @Volatile
        var extendedLogColumns = true

        const val TAG = "KalynoCloud"
        const val FULL_RESEND_FLAG = "full_resend_v2_done"
        const val UPSERT = "resolution=merge-duplicates,return=minimal"
        const val IGNORE_DUPLICATES = "resolution=ignore-duplicates,return=minimal"
        val MISSING_SCHEMA_MARKERS = listOf("PGRST204", "PGRST205", "42703", "42P01", "HTTP 404")
    }
}
