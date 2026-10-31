package ci.kalyno.app.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.SupabaseConfig
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.dashboard.StudentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Prévient l'élève dès qu'une nouveauté est publiée : nouvelle leçon, nouvelles cartes, nouvelle annonce « À la une ».
 *
 * Une tâche WorkManager (toutes les 15 min, avec réseau) compare l'état publié sur Supabase à celui vu lors du
 * dernier passage. Les requêtes sont légères (identifiants, titres et simples comptages), quelques Ko par passage.
 * Le premier passage mémorise l'état sans rien notifier : seules les vraies nouveautés sont annoncées.
 *
 * 15 min est le minimum d'Android pour une tâche périodique. Pour une alerte instantanée, il faudrait une
 * notification push (Firebase) déclenchée côté serveur.
 */
object ContentWatcher {

    private const val WORK_NAME = "kalyno-content-watch"
    private const val PREFS = "kalyno_content_watch"
    internal const val CHANNEL = "kalyno_new_content"
    private const val TAG = "KalynoContent"

    private const val ID_LESSON = 4103
    private const val ID_CARDS = 4104
    private const val ID_BANNER = 4105

    private const val CHECK_EVERY_MINUTES = 15L
    private const val CHUNK = 40
    private const val PAGE = 1000

    private const val K_LEVEL = "level"
    private const val K_LESSONS = "lessons"
    private const val K_BANNERS = "banners"
    private const val K_TOTAL = "total_cards"
    private const val K_PER_CHAPTER = "cards_per_chapter"

    /** À appeler au lancement de l'appli : sans effet si la tâche est déjà programmée. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        createChannel(app)
        val request = PeriodicWorkRequestBuilder<ContentWatchWorker>(CHECK_EVERY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    internal fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Nouveautés", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Nouvelles leçons, nouvelles cartes et annonces « À la une »."
            }
        )
    }

    private data class Item(val id: String, val title: String)

    private class State(
        val lessonIds: Set<String>,
        val bannerIds: Set<String>,
        val totalCards: Int,
        val perChapter: Map<String, Int>
    )

    /** Un passage de contrôle. Lance une exception en cas d'erreur réseau : rien n'est alors mémorisé. */
    internal suspend fun check(context: Context, levelId: String) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // ----- État actuellement publié (léger) -----
        val subjectIds = items(getBody("subjects?select=id&level_id=eq.${enc(levelId)}&is_active=eq.true"))
            .map { it.id }
        if (subjectIds.isEmpty()) return@withContext
        val lessons = subjectIds.chunked(CHUNK).flatMap { chunk ->
            items(getBody("chapters?select=id,title&status=eq.published&subject_id=in.(${inList(chunk)})"))
        }
        val banners = loadBanners(levelId) // null = table absente ou erreur : on garde l'ancien état
        val lessonIds = lessons.map { it.id }
        val totalCards = lessonIds.chunked(CHUNK).sumOf { chunk ->
            countOf("cards?select=id&chapter_id=in.(${inList(chunk)})")
        }

        val state = readState(prefs, levelId)
        if (state == null) {
            // Premier passage (ou changement de classe) : on mémorise sans notifier
            writeState(
                prefs, levelId, lessonIds.toSet(), banners?.map { it.id }?.toSet().orEmpty(),
                totalCards, fetchPerChapter(lessonIds)
            )
            Log.i(TAG, "État initial : ${lessonIds.size} leçon(s), $totalCards carte(s)")
            return@withContext
        }

        // ----- Différences -----
        val newLessons = lessons.filter { it.id !in state.lessonIds }
        val newBanners = banners?.filter { it.id !in state.bannerIds }.orEmpty()

        var perChapter = state.perChapter
        var gained: List<Pair<Item, Int>> = emptyList()
        if (totalCards != state.totalCards) {
            perChapter = fetchPerChapter(lessonIds)
            // Les cartes d'une toute nouvelle leçon sont annoncées avec la leçon, pas séparément
            gained = lessons.mapNotNull { lesson ->
                val delta = (perChapter[lesson.id] ?: 0) - (state.perChapter[lesson.id] ?: 0)
                if (delta > 0 && lesson.id in state.lessonIds) lesson to delta else null
            }
        }

        // ----- Notifications -----
        if (newLessons.isNotEmpty()) {
            val first = newLessons.first().title.ifBlank { "Nouvelle leçon" }
            val more = newLessons.size - 1
            val sent = StudyReminders.post(
                context, ID_LESSON, CHANNEL,
                title = if (newLessons.size == 1) "Nouvelle leçon disponible" else "${newLessons.size} nouvelles leçons disponibles",
                text = if (more == 0) "« $first » vient d'être publiée. Viens la découvrir !"
                else "« $first » et $more autre(s) viennent d'être publiées. Viens les découvrir !",
                openDojo = false
            )
            Log.i(TAG, "Nouvelles leçons : ${newLessons.size}, envoyée = $sent")
        }

        if (gained.isNotEmpty()) {
            val completed = KalynoDatabase.get(context).lessonProgressDao()
                .completedLessonIds(StudentId.get(context)).toSet()
            val total = gained.sumOf { it.second }
            val (top, _) = gained.maxByOrNull { it.second }!!
            val inDojo = top.id in completed
            val where = if (inDojo) "dans le Dojo · ${top.title}" else "dans la leçon « ${top.title} »"
            val sent = StudyReminders.post(
                context, ID_CARDS, CHANNEL,
                title = if (total == 1) "Nouvelle carte disponible" else "$total nouvelles cartes disponibles",
                text = where + if (gained.size > 1) " (et d'autres leçons)" else "",
                openDojo = inDojo
            )
            Log.i(TAG, "Nouvelles cartes : $total, envoyée = $sent")
        }

        if (newBanners.isNotEmpty()) {
            val first = newBanners.first().title.ifBlank { "Une nouvelle annonce t'attend" }
            val more = newBanners.size - 1
            val sent = StudyReminders.post(
                context, ID_BANNER, CHANNEL,
                title = "À la une sur KALYNO",
                text = if (more == 0) first else "$first (+ $more autre(s) annonce(s))",
                openDojo = false
            )
            Log.i(TAG, "Nouvelles annonces : ${newBanners.size}, envoyée = $sent")
        }

        writeState(
            prefs, levelId, lessonIds.toSet(),
            banners?.map { it.id }?.toSet() ?: state.bannerIds,
            totalCards, perChapter
        )
    }

    // ------------------------------------------------------------------ État mémorisé

    private fun readState(prefs: android.content.SharedPreferences, levelId: String): State? {
        if (prefs.getString(K_LEVEL, null) != levelId) return null
        val perChapter = try {
            val json = JSONObject(prefs.getString(K_PER_CHAPTER, "{}") ?: "{}")
            json.keys().asSequence().associateWith { json.optInt(it, 0) }
        } catch (e: Exception) {
            emptyMap()
        }
        return State(
            lessonIds = prefs.getStringSet(K_LESSONS, emptySet()).orEmpty().toSet(),
            bannerIds = prefs.getStringSet(K_BANNERS, emptySet()).orEmpty().toSet(),
            totalCards = prefs.getInt(K_TOTAL, 0),
            perChapter = perChapter
        )
    }

    private fun writeState(
        prefs: android.content.SharedPreferences,
        levelId: String,
        lessonIds: Set<String>,
        bannerIds: Set<String>,
        totalCards: Int,
        perChapter: Map<String, Int>
    ) {
        prefs.edit()
            .putString(K_LEVEL, levelId)
            .putStringSet(K_LESSONS, HashSet(lessonIds))
            .putStringSet(K_BANNERS, HashSet(bannerIds))
            .putInt(K_TOTAL, totalCards)
            .putString(K_PER_CHAPTER, JSONObject(perChapter as Map<*, *>).toString())
            .apply()
    }

    // ------------------------------------------------------------------ Requêtes légères

    private fun loadBanners(levelId: String): List<Item>? = try {
        items(
            getBody(
                "explorer_banners?select=id,title&is_active=eq.true" +
                    "&or=(level_id.is.null,level_id.eq.${enc(levelId)})&order=position.asc&limit=10"
            )
        )
    } catch (e: IOException) {
        null
    }

    /** Nombre de cartes par leçon, en pages de [PAGE] lignes (le serveur plafonne une réponse à 1000 lignes). */
    private fun fetchPerChapter(lessonIds: List<String>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        lessonIds.chunked(CHUNK).forEach { chunk ->
            var offset = 0
            while (true) {
                val rows = JSONArray(
                    getBody(
                        "cards?select=chapter_id&chapter_id=in.(${inList(chunk)})" +
                            "&order=id.asc&limit=$PAGE&offset=$offset"
                    )
                )
                for (i in 0 until rows.length()) {
                    val id = rows.getJSONObject(i).optString("chapter_id")
                    if (id.isNotBlank()) counts[id] = (counts[id] ?: 0) + 1
                }
                if (rows.length() < PAGE) break
                offset += PAGE
            }
        }
        return counts
    }

    private fun items(body: String): List<Item> {
        val rows = JSONArray(body)
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.getJSONObject(i)
            val id = row.optString("id")
            if (id.isBlank()) null else Item(id, row.optString("title", "").trim())
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun inList(ids: List<String>) = ids.joinToString(",") { enc(it) }

    private fun getBody(path: String): String = request(path, countOnly = false).first

    /** Nombre total de lignes, lu dans l'en-tête Content-Range (aucune donnée téléchargée). */
    private fun countOf(path: String): Int =
        request(path, countOnly = true).second?.substringAfter('/', "")?.toIntOrNull() ?: 0

    private fun request(path: String, countOnly: Boolean): Pair<String, String?> {
        val connection = URL("${SupabaseConfig.BASE_URL}/rest/v1/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            if (countOnly) {
                connection.setRequestProperty("Prefer", "count=exact")
                connection.setRequestProperty("Range-Unit", "items")
                connection.setRequestProperty("Range", "0-0")
            }
            val code = connection.responseCode
            // 416 = « page demandée hors limites » : sur une liste vide, le total (0) est quand même fourni
            val ok = code in 200..299 || (countOnly && code == 416)
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!ok) throw IOException("Supabase HTTP $code: $text")
            return text to connection.getHeaderField("Content-Range")
        } finally {
            connection.disconnect()
        }
    }
}

class ContentWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val levelId = AppPrefs(context).level ?: return Result.success() // classe pas encore choisie
        if (!StudyReminders.canNotify(context)) return Result.success()   // rien à annoncer sans autorisation

        ContentWatcher.createChannel(context)
        return try {
            ContentWatcher.check(context, levelId)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("KalynoContent", "Contrôle reporté : ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
