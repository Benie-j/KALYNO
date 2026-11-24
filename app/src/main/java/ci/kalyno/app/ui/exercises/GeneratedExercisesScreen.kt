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
import kotlinx.coroutines.launch

private class GeneratedExerciseApi(private val context: Context) {
    private fun request(body: JSONObject): JSONObject {
        val session = AuthStore(context).session()
            ?: throw IllegalStateException("Connecte-toi à ton compte pour accéder aux exercices.")

        val connection = URL("${SupabaseConfig.BASE_URL}/functions/v1/kalyno-generate")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException("Erreur serveur ($code) : $response")
            }
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun list(): JSONObject = withContext(Dispatchers.IO) {
        request(JSONObject().put("action", "list"))
    }

    suspend fun submit(
        exerciseId: String,
        answer: JSONObject,
        attemptId: String
    ): JSONObject = withContext(Dispatchers.IO) {
        request(
            JSONObject()
                .put("action", "submit")
                .put("exercise_id", exerciseId)
                .put("client_attempt_id", attemptId)
                .put("submitted_answer", answer)
        )
    }
}

@Composable
fun GeneratedExercisesScreen() {
    val context = LocalContext.current
    val api = remember { GeneratedExerciseApi(context) }
    val scope = rememberCoroutineScope()

    var exercises by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var selectedChoices by remember { mutableStateOf(setOf<Int>()) }
    var freeAnswer by remember { mutableStateOf("") }
    var tableAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

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
                error = e.message ?: "Impossible de charger les exercices."
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

    fun openExercise(exercise: JSONObject) {
        selected = exercise
        result = null
        selectedChoices = emptySet()
        freeAnswer = ""
        tableAnswers = emptyMap()
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

        if (kind == "qcm" && selectedChoices.isEmpty()) {
            error = "Sélectionne au moins une réponse."
            return
        }
        if (kind == "free_text" && freeAnswer.isBlank()) {
            error = "Écris une réponse avant de valider."
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
                error = e.message ?: "La correction a échoué."
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mes exercices IA", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Des exercices générés à partir de tes cours. Tes réponses ne modifient pas ta progression FSRS.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (selected == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { refresh() }, enabled = !loading) {
                    Text("Actualiser")
                }
                if (loading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (exercises.isEmpty() && !loading) {
                Text("Aucun exercice disponible pour le moment.")
            }

            exercises.forEach { exercise ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openExercise(exercise) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            exercise.optString("title", "Exercice"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(exercise.optString("statement"))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${exercise.optString("kind")} • ${exercise.optString("difficulty")} • ${exercise.optString("status")}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        } else {
            val exercise = selected!!
            val kind = exercise.optString("kind")
            val payload = exercise.optJSONObject("payload") ?: JSONObject()

            TextButton(onClick = {
                selected = null
                result = null
                error = null
            }) { Text("← Retour aux exercices") }

            Text(
                exercise.optString("title"),
                style = MaterialTheme.typography.titleLarge
            )
            Text(exercise.optString("statement"))

            when (kind) {
                "qcm", "multiple_choice" -> {
                    val choices = payload.optJSONArray("choices") ?: JSONArray()
                    Text("Choisis la ou les bonnes réponses.")
                    for (i in 0 until choices.length()) {
                        val label = choices.optString(i)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Checkbox(
                                checked = i in selectedChoices,
                                onCheckedChange = { checked ->
                                    selectedChoices = if (checked) selectedChoices + i
                                    else selectedChoices - i
                                }
                            )
                            Text(label, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }

                "table" -> {
                    val front = payload.optJSONObject("table_front") ?: JSONObject()
                    val rows = front.optJSONArray("rows") ?: JSONArray()
                    Text("Complète les cellules vides.")

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
                                        label = { Text("Réponse") },
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
                        label = { Text("Ta réponse") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { sendAnswer() },
                enabled = !loading && result == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Correction en cours…" else "Valider ma réponse")
            }

            if (loading) CircularProgressIndicator()

            result?.let { correction ->
                val verdict = correction.optString("verdict")
                val score = correction.optInt("score")
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Résultat : $verdict", style = MaterialTheme.typography.titleMedium)
                        Text("Note : $score / 100")
                        Text(correction.optString("feedback"))
                        val details = correction.optJSONObject("correction")
                        if (details != null) {
                            Text(
                                details.optString("explanation"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        TextButton(onClick = {
                            selected = null
                            result = null
                            refresh()
                        }) {
                            Text("Retour à la liste")
                        }
                    }
                }
            }
        }
    }
}
