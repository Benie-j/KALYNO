package ci.kalyno.app.ui.exercises

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
fun GeneratedExercisesScreen() {
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
    var tableAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var filter by remember { mutableStateOf("all") }

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
                error = when {
                    e.message.orEmpty().contains("Erreur serveur") ->
                        "Nous n'avons pas pu récupérer tes exercices. Réessaie dans un instant."
                    else -> e.message ?: "Une erreur est survenue. Réessaie."
                }
                if (exercises.isEmpty()) exercises = loadCache()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        exercises = loadCache()
        refresh()
    }

    fun isCompleted(exercise: JSONObject): Boolean {
        return exercise.optString("status").lowercase() in setOf(
            "completed", "done", "submitted", "corrected", "finished"
        )
    }

    val completedCount = exercises.count { isCompleted(it) }
    val todoCount = exercises.size - completedCount

    val visibleExercises = exercises.filter {
        when (filter) {
            "todo" -> !isCompleted(it)
            "done" -> isCompleted(it)
            else -> true
        }
    }

    fun openExercise(exercise: JSONObject) {
        selected = exercise
        result = null
        selectedChoices = emptySet()
        freeAnswer = ""
        tableAnswers = emptyMap()
        error = null
    }

    fun sendAnswer() {
        val exercise = selected ?: return
        val kind = exercise.optString("kind")
        val answer = when (kind) {
            "qcm", "multiple_choice" -> JSONObject().put(
                "selected_indices",
                JSONArray(selectedChoices.sorted())
            )
            "table" -> JSONObject().put(
                "cells",
                JSONObject().apply {
                    tableAnswers.forEach { (key, value) -> put(key, value) }
                }
            )
            else -> JSONObject().put("text", freeAnswer.trim())
        }

        if ((kind == "qcm" || kind == "multiple_choice") && selectedChoices.isEmpty()) {
            error = "Choisis au moins une réponse."
            return
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
                refresh()
            } catch (e: Exception) {
                error = "Ta réponse n'a pas pu être envoyée. Vérifie ta connexion puis réessaie."
            } finally {
                loading = false
            }
        }
    }

    if (selected == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "TON ESPACE D'ENTRAÎNEMENT",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    "À toi de jouer !",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = colors.onBackground
                )
                Text(
                    "Renforce tes connaissances, relève de nouveaux défis et progresse à ton rythme.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                color = colors.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Ton entraînement",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onPrimaryContainer,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$todoCount",
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.onPrimaryContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                "À découvrir",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onPrimaryContainer
                            )
                        }
                        Surface(
                            modifier = Modifier.width(1.dp).height(46.dp),
                            color = colors.onPrimaryContainer.copy(alpha = 0.20f)
                        ) {}
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$completedCount",
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.onPrimaryContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                "Terminés",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tes exercices",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                TextButton(
                    onClick = { refresh() },
                    enabled = !loading
                ) {
                    Text(if (loading) "Chargement…" else "↻ Actualiser")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == "all",
                    onClick = { filter = "all" },
                    label = { Text("Tout") }
                )
                FilterChip(
                    selected = filter == "todo",
                    onClick = { filter = "todo" },
                    label = { Text("À faire") }
                )
                FilterChip(
                    selected = filter == "done",
                    onClick = { filter = "done" },
                    label = { Text("Terminés") }
                )
            }

            error?.let {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = colors.errorContainer
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(14.dp),
                        color = colors.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (loading && exercises.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (visibleExercises.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            if (exercises.isEmpty()) "Ton espace se prépare" else "Tout est à jour !",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            if (exercises.isEmpty())
                                "Tes prochains exercices apparaîtront ici. Reviens bientôt pour découvrir de nouveaux défis."
                            else if (filter == "todo")
                                "Tu as terminé tous les exercices disponibles. Bravo !"
                            else
                                "Aucun exercice dans cette catégorie pour le moment.",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                visibleExercises.forEachIndexed { index, exercise ->
                    val kind = exercise.optString("kind")
                    val difficulty = exercise.optString("difficulty").lowercase()
                    val done = isCompleted(exercise)

                    val typeLabel = when (kind) {
                        "qcm", "multiple_choice" -> "Choix multiples"
                        "free_text" -> "Réponse écrite"
                        "table" -> "Tableau à compléter"
                        else -> "Exercice"
                    }

                    val difficultyLabel = when (difficulty) {
                        "easy", "facile", "beginner" -> "Facile"
                        "hard", "difficult", "difficile", "advanced" -> "Avancé"
                        "medium", "intermediate", "moyen" -> "Intermédiaire"
                        else -> ""
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openExercise(exercise) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                    color = colors.secondaryContainer
                                ) {
                                    Text(
                                        typeLabel,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        color = colors.onSecondaryContainer,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    if (done) "Terminé ✓" else "À faire",
                                    color = if (done) colors.primary else colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                            }

                            Text(
                                exercise.optString("title", "Ton défi ${index + 1}"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = colors.onSurface
                            )

                            Text(
                                exercise.optString("statement"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    difficultyLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    if (done) "Revoir l'exercice" else "Commencer  →",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    } else {
        val exercise = selected!!
        val kind = exercise.optString("kind")
        val payload = exercise.optJSONObject("payload") ?: JSONObject()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = {
                selected = null
                result = null
                error = null
            }) {
                Text("← Tous mes exercices")
            }

            Text(
                when (kind) {
                    "qcm", "multiple_choice" -> "À toi de choisir"
                    "table" -> "Complète le tableau"
                    else -> "Prends le temps de réfléchir"
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Text(
                exercise.optString("title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                exercise.optString("statement"),
                style = MaterialTheme.typography.bodyLarge
            )

            when (kind) {
                "qcm", "multiple_choice" -> {
                    val choices = payload.optJSONArray("choices") ?: JSONArray()
                    Text(
                        "Sélectionne la ou les bonnes réponses.",
                        style = MaterialTheme.typography.titleSmall
                    )
                    for (i in 0 until choices.length()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            color = if (i in selectedChoices) colors.secondaryContainer
                                else colors.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = i in selectedChoices,
                                    onCheckedChange = { checked ->
                                        selectedChoices = if (checked) selectedChoices + i
                                        else selectedChoices - i
                                    }
                                )
                                Text(
                                    choices.optString(i),
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = colors.onSurface
                                )
                            }
                        }
                    }
                }

                "table" -> {
                    val front = payload.optJSONObject("table_front") ?: JSONObject()
                    val rows = front.optJSONArray("rows") ?: JSONArray()
                    Text("Complète les cases manquantes.")
                    for (r in 0 until rows.length()) {
                        val row = rows.optJSONArray(r) ?: JSONArray()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (c in 0 until row.length()) {
                                val cell = row.optJSONObject(c) ?: JSONObject()
                                if (cell.optString("type") == "blank") {
                                    val key = cell.optString("key")
                                    OutlinedTextField(
                                        value = tableAnswers[key] ?: "",
                                        onValueChange = {
                                            tableAnswers = tableAnswers + (key to it)
                                        },
                                        label = { Text("Ta réponse") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                } else {
                                    Text(
                                        cell.optString("value"),
                                        modifier = Modifier.weight(1f).padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    OutlinedTextField(
                        value = freeAnswer,
                        onValueChange = { freeAnswer = it },
                        label = { Text("Écris ta réponse ici") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
            }

            error?.let {
                Text(it, color = colors.error)
            }

            Button(
                onClick = { sendAnswer() },
                enabled = !loading && result == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Vérification en cours…" else "Valider ma réponse")
            }

            if (loading) {
                CircularProgressIndicator()
            }

            result?.let { correction ->
                val verdict = correction.optString("verdict")
                val score = correction.optInt("score")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.primaryContainer
                    )
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            when (verdict.lowercase()) {
                                "correct", "success", "passed" -> "Bravo, bonne réponse !"
                                "partial", "partially_correct" -> "Bon effort, continue !"
                                else -> "Voyons cela ensemble"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = colors.onPrimaryContainer
                        )
                        Text(
                            "Ton résultat : $score / 100",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onPrimaryContainer
                        )
                        Text(
                            correction.optString("feedback"),
                            color = colors.onPrimaryContainer
                        )
                        val details = correction.optJSONObject("correction")
                        if (details != null) {
                            Text(
                                details.optString("explanation"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onPrimaryContainer
                            )
                        }
                        Button(
                            onClick = {
                                selected = null
                                result = null
                                refresh()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continuer")
                        }
                    }
                }
            }
        }
    }
}
