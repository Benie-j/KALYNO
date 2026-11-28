package ci.kalyno.app.ui.exercises

import ci.kalyno.app.ui.components.rememberCountUp
import ci.kalyno.app.ui.components.entrance
import ci.kalyno.app.ui.components.KalynoRing
import ci.kalyno.app.ui.components.DriftingBlob
import ci.kalyno.app.ui.components.ConfettiBurst
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import ci.kalyno.app.ui.theme.KalynoLightBlue
import ci.kalyno.app.ui.theme.KalynoYellow

import ci.kalyno.app.data.SupabaseLearningRepository
import ci.kalyno.app.ui.explorer.LearningContentCache
import ci.kalyno.app.ui.explorer.RichText
import android.content.Context
import ci.kalyno.app.data.SupabaseConfig
import ci.kalyno.app.data.auth.AuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch

private class GeneratedExerciseApi(private val context: Context) {

    private val authStore = AuthStore(context)

    private class HttpStatusException(
        val status: Int,
        message: String
    ) : Exception(message)

    private suspend fun refreshSession(): ci.kalyno.app.data.auth.AuthSession {
        val oldSession = authStore.session()
            ?: throw IllegalStateException("Reconnecte-toi à ton compte pour accéder aux exercices.")

        return try {
            val renewed = ci.kalyno.app.data.auth.SupabaseAuthApi()
                .refresh(oldSession.refreshToken)
            authStore.saveSession(renewed)
            renewed
        } catch (e: Exception) {
            throw IllegalStateException(
                "Ta session ne peut plus être renouvelée. Déconnecte-toi puis reconnecte-toi.",
                e
            )
        }
    }

    private suspend fun request(body: JSONObject): JSONObject {
        var session = authStore.session()
            ?: throw IllegalStateException("Connecte-toi à ton compte pour accéder aux exercices.")

        // Renouvellement préventif si le jeton approche de son expiration.
        if (session.expiresAtMillis <= System.currentTimeMillis() + 60_000L) {
            session = refreshSession()
        }

        try {
            return requestWithToken(body, session.accessToken)
        } catch (e: HttpStatusException) {
            if (e.status != 401) throw e

            // Le serveur refuse le jeton : renouvellement puis une seule nouvelle tentative.
            session = refreshSession()
            return requestWithToken(body, session.accessToken)
        }
    }

    private suspend fun requestWithToken(
        body: JSONObject,
        accessToken: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL("${SupabaseConfig.BASE_URL}/functions/v1/kalyno-generate")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                throw HttpStatusException(code, "Erreur serveur ($code) : $response")
            }

            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun list(): JSONObject =
        request(JSONObject().put("action", "list"))

    suspend fun submit(
        exerciseId: String,
        answer: JSONObject,
        attemptId: String
    ): JSONObject = request(
        JSONObject()
            .put("action", "submit")
            .put("exercise_id", exerciseId)
            .put("client_attempt_id", attemptId)
            .put("submitted_answer", answer)
    )
}

@Composable
fun GeneratedExercisesScreen(levelId: String? = null) {
    val context = LocalContext.current
    val api = remember { GeneratedExerciseApi(context) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    var exercises by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var selectedChoices by remember { mutableStateOf(setOf<Int>()) }
    var freeAnswer by remember { mutableStateOf("") }
    var reasoningAnswer by remember { mutableStateOf("") }
    var tableAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var filter by remember { mutableStateOf("all") }
    // Disciplines dépliées : tout est plié par défaut ; l'état survit à l'ouverture d'un exercice
    var expandedSubjects by remember { mutableStateOf(setOf<String>()) }

    val cache = remember {
        context.getSharedPreferences("generated_exercises_cache", Context.MODE_PRIVATE)
    }

    fun saveCache(items: List<JSONObject>) {
        cache.edit().putString(
            "items",
            JSONArray().apply { items.forEach { put(it) } }.toString()
        ).apply()
    }

    fun loadCache(): List<JSONObject> {
        val raw = cache.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    fun isCompleted(exercise: JSONObject): Boolean =
        exercise.optString("status").lowercase() in setOf(
            "completed", "done", "submitted", "corrected", "finished"
        )

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                val response = api.list()
                val array = response.optJSONArray("exercises") ?: JSONArray()
                val items = (0 until array.length()).map { array.getJSONObject(it) }
                exercises = items
                saveCache(items)
            } catch (e: Exception) {
                error = "Impossible de récupérer tes exercices. Réessaie dans un instant."
                if (exercises.isEmpty()) exercises = loadCache()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // Lecture du cache hors du fil principal : l'écran s'ouvre sans attendre
        exercises = withContext(Dispatchers.IO) { loadCache() }
        refresh()
    }

    // Noms des disciplines, leçons et séances : cache local d'abord, réseau seulement si une leçon reste inconnue
    var catalog by remember { mutableStateOf(ExerciseCatalog.EMPTY) }
    var catalogFetched by remember { mutableStateOf(false) }

    LaunchedEffect(levelId) {
        val level = levelId ?: return@LaunchedEffect
        val cached = withContext(Dispatchers.IO) { LearningContentCache(context).read(level) }
        if (!cached.isNullOrEmpty()) catalog = ExerciseCatalog(cached)
    }

    LaunchedEffect(levelId, exercises, catalog) {
        val level = levelId ?: return@LaunchedEffect
        if (catalogFetched || exercises.isEmpty() || exercises.all { catalog.knows(it) }) return@LaunchedEffect
        catalogFetched = true
        val fresh = withContext(Dispatchers.IO) {
            runCatching { SupabaseLearningRepository().loadPublishedContent(level) }.getOrNull()
        }
        if (!fresh.isNullOrEmpty()) catalog = ExerciseCatalog(fresh)
    }

    fun openExercise(exercise: JSONObject) {
        selected = exercise
        result = null
        selectedChoices = emptySet()
        freeAnswer = ""
        reasoningAnswer = ""
        tableAnswers = emptyMap()
        error = null
    }

    fun sendAnswer() {
        val exercise = selected ?: return
        val kind = exercise.optString("kind")

        val answer = when (kind) {
            "qcm", "multiple_choice" -> JSONObject().put(
                "selected_indices", JSONArray(selectedChoices.sorted())
            )
            "table" -> JSONObject().put(
                "cells", JSONObject().apply {
                    tableAnswers.forEach { (key, value) -> put(key, value) }
                }
            )
            else -> JSONObject()
                .put("text", freeAnswer.trim())
                .put("reasoning", reasoningAnswer.trim())
        }

        if ((kind == "qcm" || kind == "multiple_choice") && selectedChoices.isEmpty()) {
            error = "Choisis au moins une réponse."
            return
        }
        if (kind == "table") {
            val payload = exercise.optJSONObject("payload") ?: JSONObject()
            val front = payload.optJSONObject("table_front") ?: JSONObject()
            val rows = front.optJSONArray("rows") ?: JSONArray()
            val blankKeys = linkedSetOf<String>()

            for (r in 0 until rows.length()) {
                val row = rows.optJSONArray(r) ?: JSONArray()

                for (c in 0 until row.length()) {
                    val cell = row.optJSONObject(c) ?: JSONObject()

                    if (cell.optString("type") == "blank") {
                        val key = cell.optString("key")
                        if (key.isNotBlank()) blankKeys.add(key)
                    }
                }
            }

            if (blankKeys.isEmpty()) {
                error = "Ce tableau ne contient aucune cellule à compléter."
                return
            }

            val missingCells = blankKeys.count {
                tableAnswers[it].isNullOrBlank()
            }

            if (missingCells > 0) {
                error = "Il te reste $missingCells cellule(s) à compléter."
                return
            }
        }

        if (kind == "free_text" && freeAnswer.isBlank()) {
            error = "Écris ta réponse avant de continuer."
            return
        }

        scope.launch {
            loading = true
            error = null
            try {
                result = api.submit(
                    exercise.optString("id"),
                    answer,
                    UUID.randomUUID().toString()
                )
                val response = api.list()
                val array = response.optJSONArray("exercises") ?: JSONArray()
                exercises = (0 until array.length()).map { array.getJSONObject(it) }
                saveCache(exercises)
            } catch (e: Exception) {
                error = "Ta réponse n'a pas pu être envoyée. Vérifie ta connexion puis réessaie."
            } finally {
                loading = false
            }
        }
    }

    val current = selected
    if (current == null) {
        ExerciseListContent(
            exercises = exercises,
            loading = loading,
            error = error,
            filter = filter,
            onFilterChange = { filter = it },
            isCompleted = { isCompleted(it) },
            onRefresh = { refresh() },
            onOpen = { openExercise(it) },
            catalog = catalog,
            expandedSubjects = expandedSubjects,
            onToggleSubject = { key ->
                expandedSubjects =
                    if (key in expandedSubjects) expandedSubjects - key else expandedSubjects + key
            }
        )
    } else {
        val currentId = current.optString("id")
        val position = (exercises.indexOfFirst { it.optString("id") == currentId } + 1).coerceAtLeast(1)
        val next = exercises.firstOrNull { !isCompleted(it) && it.optString("id") != currentId }

        ExerciseSolveContent(
            exercise = current,
            position = position,
            total = exercises.size.coerceAtLeast(1),
            result = result,
            loading = loading,
            error = error,
            selectedChoices = selectedChoices,
            onToggleChoice = { index ->
                selectedChoices =
                    if (index in selectedChoices) selectedChoices - index
                    else selectedChoices + index
            },
            tableAnswers = tableAnswers,
            onTableAnswer = { key, value -> tableAnswers = tableAnswers + (key to value) },
            freeAnswer = freeAnswer,
            onFreeAnswer = { freeAnswer = it },
            reasoningAnswer = reasoningAnswer,
            onReasoningAnswer = { reasoningAnswer = it },
            onSubmit = { sendAnswer() },
            onBack = {
                selected = null
                result = null
                error = null
            },
            onNext = next?.let { target -> { openExercise(target) } }
        )
    }
}
