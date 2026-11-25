package ci.kalyno.app.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import ci.kalyno.app.ui.theme.KalynoLightBlue
import ci.kalyno.app.ui.theme.KalynoYellow

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
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "KALYNO / LABORATOIRE D’APPRENTISSAGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Ton espace de défis",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onBackground
                    )
                    Text(
                        "Apprends. Applique. Progresse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = { refresh() },
                    enabled = !loading,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        colors.outlineVariant
                    )
                ) {
                    Box(
                        Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "↻",
                            color = colors.primary,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.primary
                ),
                elevation = CardDefaults.cardElevation(5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(21.dp),
                    verticalArrangement = Arrangement.spacedBy(17.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Surface(
                                color = colors.onPrimary.copy(alpha = 0.14f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                            ) {
                                Text(
                                    "TON PARCOURS",
                                    Modifier.padding(
                                        horizontal = 11.dp,
                                        vertical = 6.dp
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onPrimary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Text(
                                if (remaining > 0)
                                    "Chaque défi te rapproche de la maîtrise."
                                else if (exercises.isEmpty())
                                    "Ton prochain progrès commence ici."
                                else
                                    "Parcours accompli. Bravo !",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxSize(),
                                color = colors.onPrimary,
                                trackColor = colors.onPrimary.copy(alpha = 0.2f),
                                strokeWidth = 6.dp
                            )

                            Text(
                                "${(progress * 100).toInt()}%",
                                color = colors.onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            Modifier.weight(1f),
                            color = colors.onPrimary.copy(alpha = 0.12f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "$remaining",
                                    color = colors.onPrimary,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "À relever",
                                    color = colors.onPrimary.copy(alpha = 0.82f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Surface(
                            Modifier.weight(1f),
                            color = colors.onPrimary.copy(alpha = 0.12f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "$completed",
                                    color = colors.onPrimary,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Accomplis",
                                    color = colors.onPrimary.copy(alpha = 0.82f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Progression globale",
                                color = colors.onPrimary.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "$completed / ${exercises.size}",
                                color = colors.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp),
                            color = KalynoLightBlue,
                            trackColor = colors.onPrimary.copy(alpha = 0.18f)
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
                        colors = CardDefaults.cardColors(
                            containerColor = colors.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(Modifier.fillMaxWidth()) {

                            Box(
                                Modifier
                                    .width(5.dp)
                                    .height(150.dp)
                                    .background(
                                        if (done) colors.secondary
                                        else colors.tertiary
                                    )
                            )

                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 15.dp
                                    ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                    Surface(
                                        color = if (done)
                                            colors.secondaryContainer
                                        else
                                            colors.tertiaryContainer,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            if (done) "✓ Terminé" else "À faire",
                                            Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 5.dp
                                            ),
                                            color = if (done)
                                                colors.onSecondaryContainer
                                            else
                                                colors.onTertiaryContainer,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    exercise.optString("title")
                                        .ifBlank { "Défi ${index + 1}" },
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
                                    if (level.isNotBlank()) {
                                        Surface(
                                            color = colors.surfaceVariant,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                level,
                                                Modifier.padding(
                                                    horizontal = 9.dp,
                                                    vertical = 5.dp
                                                ),
                                                color = colors.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }

                                    Text(
                                        if (done)
                                            "Revoir le défi  →"
                                        else
                                            "Entrer dans le défi  →",
                                        color = colors.primary,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
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

        val exercisePosition = (
            exercises.indexOfFirst {
                it.optString("id") == exercise.optString("id")
            } + 1
        ).coerceAtLeast(1)

        val exerciseTotal = exercises.size.coerceAtLeast(1)

        val difficultyLabel = when (
            exercise.optString("difficulty").lowercase()
        ) {
            "easy", "facile", "beginner" -> "FACILE"
            "medium", "intermediate", "moyen" -> "INTERMÉDIAIRE"
            "hard", "difficult", "difficile", "advanced" -> "AVANCÉ"
            else -> "ENTRAÎNEMENT"
        }

        val exerciseTypeLabel = when {
            isQcm -> "CHOIX MULTIPLES"
            kind == "table" -> "TABLEAU À COMPLÉTER"
            else -> "RÉPONSE ÉCRITE"
        }


        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.primary
                ),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                selected = null
                                result = null
                                error = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colors.onPrimary
                            )
                        ) {
                            Text(
                                "←  Mes exercices",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = colors.onPrimary.copy(alpha = 0.14f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                        ) {
                            Text(
                                difficultyLabel,
                                Modifier.padding(
                                    horizontal = 11.dp,
                                    vertical = 7.dp
                                ),
                                color = colors.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            "DÉFI $exerciseTypeLabel",
                            color = colors.onPrimary.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )

                        RichText(
                            html = exercise.optString("title"),
                            color = Color.White,
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                            lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "TON PARCOURS",
                                color = colors.onPrimary.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "$exercisePosition / $exerciseTotal",
                                color = colors.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        LinearProgressIndicator(
                            progress = (
                                exercisePosition.toFloat() /
                                    exerciseTotal.toFloat()
                            ).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = KalynoLightBlue,
                            trackColor = colors.onPrimary.copy(alpha = 0.18f)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface
                ),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {

                    Box(
                        Modifier
                            .width(5.dp)
                            .height(112.dp)
                            .background(KalynoLightBlue)
                    )

                    Column(
                        Modifier
                            .weight(1f)
                            .padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = colors.secondaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "ÉNONCÉ",
                                    Modifier.padding(
                                        horizontal = 9.dp,
                                        vertical = 5.dp
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSecondaryContainer,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Text(
                                "Lis attentivement",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        RichText(
                            html = exercise.optString("statement"),
                            color = colors.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        )
                    }
                }
            }

            if (result == null) {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surface
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "TA MISSION",
                                    color = colors.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Text(
                                    when {
                                        isQcm -> "Fais ton choix"
                                        kind == "table" -> "Complète le tableau"
                                        else -> "Construis ta réponse"
                                    },
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Surface(
                                color = colors.primaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    when {
                                        isQcm -> "QCM"
                                        kind == "table" -> "TABLEAU"
                                        else -> "RÉDACTION"
                                    },
                                    Modifier.padding(
                                        horizontal = 11.dp,
                                        vertical = 7.dp
                                    ),
                                    color = colors.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        HorizontalDivider(color = colors.outlineVariant)

                        when {
                            isQcm -> {
                                val choices =
                                    payload.optJSONArray("choices") ?: JSONArray()

                                Text(
                                    "Sélectionne la ou les propositions correctes.",
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "TES SÉLECTIONS",
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        "${selectedChoices.size} sélectionnée(s)",
                                        color = colors.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                for (i in 0 until choices.length()) {
                                    val checked = i in selectedChoices

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            selectedChoices =
                                                if (checked) selectedChoices - i
                                                else selectedChoices + i
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp),
                                        color = if (checked)
                                            colors.secondaryContainer
                                        else
                                            colors.surfaceVariant.copy(alpha = 0.35f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (checked) 1.5.dp else 1.dp,
                                            if (checked) colors.primary
                                            else colors.outlineVariant
                                        )
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    horizontal = 12.dp,
                                                    vertical = 10.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                color = if (checked)
                                                    colors.primary
                                                else
                                                    colors.surface,
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                                border = if (checked) null
                                                else androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    colors.outlineVariant
                                                )
                                            ) {
                                                Text(
                                                    "${i + 1}",
                                                    Modifier.padding(
                                                        horizontal = 11.dp,
                                                        vertical = 8.dp
                                                    ),
                                                    color = if (checked)
                                                        colors.onPrimary
                                                    else
                                                        colors.onSurface,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }

                                            Box(Modifier.weight(1f)) {
                                                RichText(
                                                    html = choices.optString(i),
                                                    color = colors.onSurface,
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                                                )
                                            }

                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { value ->
                                                    selectedChoices =
                                                        if (value) selectedChoices + i
                                                        else selectedChoices - i
                                                }
                                            )
                                        }
                                    }
                                }

                                if (choices.length() == 0) {
                                    Text(
                                        "Aucune proposition disponible pour cet exercice.",
                                        color = colors.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            kind == "table" -> {
                                val front =
                                    payload.optJSONObject("table_front") ?: JSONObject()
                                val rows =
                                    front.optJSONArray("rows") ?: JSONArray()

                                Text(
                                    "Renseigne les cellules manquantes pour compléter le tableau.",
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )

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

                                val filledCells = blankKeys.count {
                                    !tableAnswers[it].isNullOrBlank()
                                }

                                val tableProgress = if (blankKeys.isEmpty()) {
                                    0f
                                } else {
                                    filledCells.toFloat() / blankKeys.size.toFloat()
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.primaryContainer.copy(alpha = 0.45f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp)
                                ) {
                                    Column(
                                        Modifier.padding(13.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "PROGRESSION DU TABLEAU",
                                                color = colors.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold
                                            )

                                            Text(
                                                "$filledCells / ${blankKeys.size}",
                                                color = colors.primary,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }

                                        LinearProgressIndicator(
                                            progress = tableProgress,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(7.dp),
                                            color = KalynoLightBlue,
                                            trackColor = colors.surfaceVariant
                                        )

                                        Text(
                                            if (blankKeys.isEmpty())
                                                "Aucune cellule à compléter détectée."
                                            else if (filledCells == blankKeys.size)
                                                "Toutes les cellules sont renseignées."
                                            else
                                                "${blankKeys.size - filledCells} cellule(s) restante(s).",
                                            color = colors.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                for (r in 0 until rows.length()) {
                                    val row = rows.optJSONArray(r) ?: JSONArray()

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = colors.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(7.dp),
                                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            for (c in 0 until row.length()) {
                                                val cell =
                                                    row.optJSONObject(c) ?: JSONObject()

                                                if (cell.optString("type") == "blank") {
                                                    val key = cell.optString("key")

                                                    OutlinedTextField(
                                                        value = tableAnswers[key] ?: "",
                                                        onValueChange = {
                                                            tableAnswers =
                                                                tableAnswers + (key to it)
                                                        },
                                                        label = {
                                                            Text("Réponse")
                                                        },
                                                        placeholder = {
                                                            Text("À compléter")
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true,
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                                    )
                                                } else {
                                                    Surface(
                                                        modifier = Modifier.weight(1f),
                                                        color = colors.surface,
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            1.dp,
                                                            colors.outlineVariant
                                                        )
                                                    ) {
                                                        Box(
                                                            Modifier.padding(12.dp),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
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

                                if (rows.length() == 0) {
                                    Text(
                                        "Le tableau ne contient aucune ligne.",
                                        color = colors.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            else -> {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "1. TON RAISONNEMENT",
                                        color = colors.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                    Text(
                                        "Explique les étapes de ta réflexion. Cette partie aide à comprendre ta méthode.",
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    OutlinedTextField(
                                        value = reasoningAnswer,
                                        onValueChange = {
                                            reasoningAnswer = it
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        placeholder = {
                                            Text("Comment as-tu procédé ?")
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                    )
                                }

                                HorizontalDivider(color = colors.outlineVariant)

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "2. TA RÉPONSE FINALE",
                                        color = colors.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                    Text(
                                        "Présente clairement le résultat auquel tu es arrivé.",
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    OutlinedTextField(
                                        value = freeAnswer,
                                        onValueChange = {
                                            freeAnswer = it
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 5,
                                        placeholder = {
                                            Text("Écris ta réponse ici…")
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                error?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.errorContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            Modifier.padding(13.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "!",
                                color = colors.onErrorContainer,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                it,
                                color = colors.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Button(
                    onClick = { sendAnswer() },
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(21.dp),
                            color = colors.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Analyse en cours…")
                    } else {
                        Text(
                            "Vérifier ma réponse  →",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    "Prends ton temps : ta réponse sera analysée après validation.",
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

            } else {
                val verdict = correction.optString("verdict").lowercase()
                val score = correction.optInt("score")
                val details = correction.optJSONObject("correction")
                val reasoningAnalysis =
                    details?.optJSONObject("reasoning_analysis")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surface
                    ),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {

                        Surface(
                            color = when (verdict) {
                                "correct", "success", "passed" -> colors.secondary
                                "partial", "partially_correct" -> KalynoYellow
                                else -> colors.tertiary
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = 26.dp,
                                topEnd = 26.dp
                            )
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 19.dp,
                                        vertical = 18.dp
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        "BILAN DE TON DÉFI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.82f),
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                    Text(
                                        when (verdict) {
                                            "correct", "success", "passed" ->
                                                "Excellent travail !"
                                            "partial", "partially_correct" ->
                                                "Tu progresses, continue !"
                                            else ->
                                                "On apprend de chaque essai."
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }

                                Surface(
                                    color = Color.White.copy(alpha = 0.17f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        "$score/100",
                                        Modifier.padding(
                                            horizontal = 13.dp,
                                            vertical = 10.dp
                                        ),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        Column(
                            Modifier.padding(19.dp),
                            verticalArrangement = Arrangement.spacedBy(13.dp)
                        ) {
                            RichText(
                                html = correction.optString("feedback"),
                                color = colors.onSurface,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                            )

                            reasoningAnalysis?.let { analysis ->
                                val summary = analysis.optString("summary")
                                val steps = analysis.optJSONArray("steps")
                                val strengths = analysis.optJSONArray("strengths")
                                val errors = analysis.optJSONArray("errors")
                                val advice = analysis.optString("advice")

                                HorizontalDivider(color = colors.outlineVariant)

                                Text(
                                    "ANALYSE DE TON RAISONNEMENT",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.tertiary
                                )

                                if (summary.isNotBlank()) {
                                    Surface(
                                        color = colors.tertiaryContainer.copy(alpha = 0.55f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            summary,
                                            Modifier.padding(13.dp),
                                            color = colors.onSurface,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                if (steps != null && steps.length() > 0) {
                                    Text(
                                        "Étapes de ton raisonnement",
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurface
                                    )

                                    for (i in 0 until steps.length()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Surface(
                                                color = colors.primary,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            ) {
                                                Text(
                                                    "${i + 1}",
                                                    Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 4.dp
                                                    ),
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Text(
                                                steps.optString(i),
                                                Modifier.weight(1f),
                                                color = colors.onSurface
                                            )
                                        }
                                    }
                                }

                                if (strengths != null && strengths.length() > 0) {
                                    Surface(
                                        color = colors.secondaryContainer.copy(alpha = 0.55f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                    ) {
                                        Column(
                                            Modifier.padding(13.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            Text(
                                                "POINTS FORTS",
                                                color = colors.onSecondaryContainer,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )

                                            for (i in 0 until strengths.length()) {
                                                Text(
                                                    "✓  ${strengths.optString(i)}",
                                                    color = colors.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                if (errors != null && errors.length() > 0) {
                                    Surface(
                                        color = colors.errorContainer.copy(alpha = 0.55f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                    ) {
                                        Column(
                                            Modifier.padding(13.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            Text(
                                                "POINTS À CORRIGER",
                                                color = colors.onErrorContainer,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )

                                            for (i in 0 until errors.length()) {
                                                Text(
                                                    "•  ${errors.optString(i)}",
                                                    color = colors.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                if (advice.isNotBlank()) {
                                    Surface(
                                        color = colors.surfaceVariant,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                    ) {
                                        Column(
                                            Modifier.padding(13.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                "PROCHAINE ÉTAPE",
                                                color = colors.primary,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(advice, color = colors.onSurface)
                                        }
                                    }
                                }
                            }

                            if (
                                details != null &&
                                details.optString("explanation").isNotBlank()
                            ) {
                                HorizontalDivider(color = colors.outlineVariant)

                                Text(
                                    "À RETENIR",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                RichText(
                                    html = details.optString("explanation"),
                                    color = colors.onSurface,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                )
                            }
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
