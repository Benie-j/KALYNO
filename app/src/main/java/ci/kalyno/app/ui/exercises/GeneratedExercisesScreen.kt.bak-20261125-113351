package ci.kalyno.app.ui.exercises

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
    var reasoningAnswer by remember { mutableStateOf("") }
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
        exercises = loadCache()
        refresh()
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

    if (selected == null) {
        val completed = exercises.count { isCompleted(it) }
        val remaining = exercises.size - completed
        val progress = if (exercises.isEmpty()) 0f else completed.toFloat() / exercises.size

        val visible = exercises.filter {
            when (filter) {
                "todo" -> !isCompleted(it)
                "done" -> isCompleted(it)
                else -> true
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "KALYNO  /  APPRENDRE AUTREMENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Ton espace de défis",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onBackground
                    )
                }
                TextButton(onClick = { refresh() }, enabled = !loading) {
                    Text("↻")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = colors.primary)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "TON PARCOURS",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimary.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (remaining > 0) "Chaque défi te fait avancer."
                        else if (exercises.isEmpty()) "Prêt à commencer ?"
                        else "Beau travail. Tu avances !",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = colors.onPrimary.copy(alpha = 0.13f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(13.dp)) {
                                Text(
                                    "$remaining",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = colors.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "À relever",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onPrimary.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = colors.onPrimary.copy(alpha = 0.13f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(13.dp)) {
                                Text(
                                    "$completed",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = colors.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Accomplis",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onPrimary.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Progression", color = colors.onPrimary)
                            Text(
                                "${(progress * 100).toInt()} %",
                                color = colors.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.onPrimary,
                            trackColor = colors.onPrimary.copy(alpha = 0.22f)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "À toi de jouer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Choisis un exercice et prends le temps de réfléchir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
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
                    color = colors.errorContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                ) {
                    Text(
                        it,
                        Modifier.padding(14.dp),
                        color = colors.onErrorContainer
                    )
                }
            }

            if (loading && exercises.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (visible.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (exercises.isEmpty()) "Ton espace se prépare"
                            else "Tout est à jour !",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (exercises.isEmpty())
                                "Tes exercices apparaîtront ici dès qu'ils seront disponibles."
                            else if (filter == "todo")
                                "Tu as terminé tous les exercices disponibles. Bravo !"
                            else
                                "Aucun exercice dans cette catégorie pour le moment.",
                            color = colors.onSurfaceVariant
                        )
                        if (exercises.isEmpty()) {
                            OutlinedButton(
                                onClick = { refresh() },
                                enabled = !loading
                            ) {
                                Text("Actualiser")
                            }
                        }
                    }
                }
            } else {
                visible.forEachIndexed { index, exercise ->
                    val kind = exercise.optString("kind")
                    val done = isCompleted(exercise)

                    val type = when (kind) {
                        "qcm", "multiple_choice" -> "CHOIX MULTIPLES"
                        "free_text" -> "RÉPONSE ÉCRITE"
                        "table" -> "TABLEAU À COMPLÉTER"
                        else -> "EXERCICE"
                    }

                    val level = when (exercise.optString("difficulty").lowercase()) {
                        "easy", "facile", "beginner" -> "Facile"
                        "hard", "difficult", "difficile", "advanced" -> "Avancé"
                        "medium", "intermediate", "moyen" -> "Intermédiaire"
                        else -> ""
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openExercise(exercise) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            Modifier.padding(17.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    type,
                                    color = colors.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = if (done) colors.tertiaryContainer
                                    else colors.secondaryContainer,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                                ) {
                                    Text(
                                        if (done) "✓ Terminé" else "À faire",
                                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        color = if (done) colors.onTertiaryContainer
                                        else colors.onSecondaryContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Text(
                                exercise.optString("title").ifBlank { "Défi ${index + 1}" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            RichText(
                                html = exercise.optString("statement"),
                                color = colors.onSurfaceVariant,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    level,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    if (done) "Revoir  →" else "Commencer  →",
                                    color = colors.primary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    } else {
        val exercise = selected!!
        val kind = exercise.optString("kind")
        val payload = exercise.optJSONObject("payload") ?: JSONObject()
        val isQcm = kind == "qcm" || kind == "multiple_choice"

        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = {
                selected = null
                result = null
                error = null
            }) {
                Text("←  Mes exercices")
            }

            Text(
                when {
                    isQcm -> "DÉFI · CHOIX MULTIPLES"
                    kind == "table" -> "DÉFI · TABLEAU"
                    else -> "DÉFI · RÉPONSE ÉCRITE"
                },
                color = colors.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            RichText(
                html = exercise.optString("title"),
                color = colors.onBackground,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
                fontWeight = FontWeight.ExtraBold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "ÉNONCÉ",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                    RichText(
                        html = exercise.optString("statement"),
                        color = colors.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }
            }

            if (result == null) {
                Text(
                    when {
                        isQcm -> "Sélectionne la ou les bonnes réponses."
                        kind == "table" -> "Complète les cases manquantes."
                        else -> "Rédige ta réponse avec tes propres mots."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                when {
                    isQcm -> {
                        val choices = payload.optJSONArray("choices") ?: JSONArray()
                        for (i in 0 until choices.length()) {
                            val checked = i in selectedChoices
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    selectedChoices = if (checked)
                                        selectedChoices - i else selectedChoices + i
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                color = if (checked) colors.secondaryContainer
                                else colors.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (checked) colors.primary else colors.outlineVariant
                                )
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(
                                        horizontal = 12.dp,
                                        vertical = 7.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { value ->
                                            selectedChoices = if (value)
                                                selectedChoices + i else selectedChoices - i
                                        }
                                    )
                                    Box(Modifier.padding(vertical = 8.dp)) {
                                        RichText(
                                            html = choices.optString(i),
                                            color = colors.onSurface,
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                                        )
                                    }
                                }
                            }
                        }
                    }

                    kind == "table" -> {
                        val front = payload.optJSONObject("table_front") ?: JSONObject()
                        val rows = front.optJSONArray("rows") ?: JSONArray()

                        for (r in 0 until rows.length()) {
                            val row = rows.optJSONArray(r) ?: JSONArray()
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
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
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            color = colors.surfaceVariant,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                                        ) {
                                            Box(Modifier.padding(12.dp)) {
                                                RichText(
                                                    html = cell.optString("value"),
                                                    color = colors.onSurface,
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        OutlinedTextField(
                            value = reasoningAnswer,
                            onValueChange = { reasoningAnswer = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            label = { Text("Mon raisonnement (facultatif)") },
                            placeholder = {
                                Text("Explique comment tu as réfléchi...")
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )

                        OutlinedTextField(
                            value = freeAnswer,
                            onValueChange = { freeAnswer = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            label = { Text("Ma réponse finale") },
                            placeholder = { Text("Écris ta réponse ici…") },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                    }
                }

                error?.let {
                    Text(
                        it,
                        color = colors.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = { sendAnswer() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Text(if (loading) "Vérification…" else "Vérifier ma réponse")
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                val correction = result!!
                val verdict = correction.optString("verdict").lowercase()
                val score = correction.optInt("score")
                val details = correction.optJSONObject("correction")
                val reasoningAnalysis =
                    details?.optJSONObject("reasoning_analysis")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.primaryContainer)
                ) {
                    Column(
                        Modifier.padding(19.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            when (verdict) {
                                "correct", "success", "passed" -> "Excellent travail !"
                                "partial", "partially_correct" -> "Tu progresses, continue !"
                                else -> "On apprend de chaque essai."
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer
                        )
                        Text(
                            "$score / 100",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.onPrimaryContainer
                        )
                        RichText(
                            html = correction.optString("feedback"),
                            color = colors.onPrimaryContainer,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        )

                        reasoningAnalysis?.let { analysis ->
                            val summary = analysis.optString("summary")
                            val steps = analysis.optJSONArray("steps")
                            val strengths = analysis.optJSONArray("strengths")
                            val errors = analysis.optJSONArray("errors")
                            val advice = analysis.optString("advice")

                            HorizontalDivider(
                                color = colors.onPrimaryContainer.copy(alpha = 0.2f)
                            )

                            Text(
                                "ANALYSE DE TON RAISONNEMENT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onPrimaryContainer
                            )

                            if (summary.isNotBlank()) {
                                Text(
                                    summary,
                                    color = colors.onPrimaryContainer
                                )
                            }

                            if (steps != null && steps.length() > 0) {
                                Text(
                                    "Étapes de ton raisonnement",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                                for (i in 0 until steps.length()) {
                                    Text(
                                        "• ${steps.optString(i)}",
                                        color = colors.onPrimaryContainer
                                    )
                                }
                            }

                            if (strengths != null && strengths.length() > 0) {
                                Text(
                                    "Points forts",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                                for (i in 0 until strengths.length()) {
                                    Text(
                                        "✓ ${strengths.optString(i)}",
                                        color = colors.onPrimaryContainer
                                    )
                                }
                            }

                            if (errors != null && errors.length() > 0) {
                                Text(
                                    "Points à corriger",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                                for (i in 0 until errors.length()) {
                                    Text(
                                        "• ${errors.optString(i)}",
                                        color = colors.onPrimaryContainer
                                    )
                                }
                            }

                            if (advice.isNotBlank()) {
                                Text(
                                    "Conseil pour progresser",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                                Text(
                                    advice,
                                    color = colors.onPrimaryContainer
                                )
                            }
                        }
                        if (details != null && details.optString("explanation").isNotBlank()) {
                            HorizontalDivider(
                                color = colors.onPrimaryContainer.copy(alpha = 0.2f)
                            )
                            Text(
                                "À RETENIR",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            RichText(
                                html = details.optString("explanation"),
                                color = colors.onPrimaryContainer,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        selected = null
                        result = null
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Text("Continuer mon entraînement")
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
