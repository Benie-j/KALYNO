package ci.kalyno.app.data.ai

import android.content.Context
import ci.kalyno.app.data.SupabaseConfig
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.cloud.CloudSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Seul point d'entrée IA de l'application : Dojo, Challenge et assistant passent tous par ici. */
interface AiGateway {
    /** Vrai si un compte est connecté (la fonction Edge refuse les anonymes). */
    fun isAvailable(): Boolean
    suspend fun grade(context: AiExerciseContext, answer: String): GradeResult
    suspend fun assist(message: String, context: AiExerciseContext?, history: List<ChatTurn>): String
}

class KalynoRepository private constructor(context: Context) : AiGateway {

    private val appContext = context.applicationContext
    private val store = AuthStore(appContext)
    private val cloud = CloudSync(appContext)

    override fun isAvailable(): Boolean = store.isSignedIn()

    override suspend fun grade(context: AiExerciseContext, answer: String): GradeResult {
        val body = JSONObject()
            .put("action", "grade")
            .put("answer", answer.take(MAX_ANSWER))
            .put("context", contextJson(context))
        val text = post(body.toString())
        return KalynoResultParser.parseGradeEnvelope(text)
            ?: throw KalynoException("malformed_result", "Correction illisible, réessaie.", retryable = true)
    }

    override suspend fun assist(message: String, context: AiExerciseContext?, history: List<ChatTurn>): String {
        val turns = JSONArray()
        history.takeLast(MAX_HISTORY).forEach {
            turns.put(JSONObject().put("role", if (it.fromUser) "user" else "model").put("text", it.text.take(1_500)))
        }
        val body = JSONObject()
            .put("action", "assist")
            .put("message", message.take(1_200))
            .put("history", turns)
        if (context != null) body.put("context", contextJson(context))
        val text = post(body.toString())
        return KalynoResultParser.parseAssistEnvelope(text)
            ?: throw KalynoException("malformed_result", "Réponse illisible, réessaie.", retryable = true)
    }

    private fun contextJson(c: AiExerciseContext): JSONObject = JSONObject()
        .put("question", c.question)
        .put("reference", c.reference)
        .put("accepted", JSONArray(c.accepted))
        .put("choices", JSONArray(c.choices))
        .put("explanation", c.explanation.orEmpty())
        .put("hint", c.hint.orEmpty())
        .put("subject", c.subject.orEmpty())
        .put("level", c.level.orEmpty())
        .put("lesson", c.lesson.orEmpty())
        .put("student_answer", c.studentAnswer.orEmpty())
        .put("locked", c.locked)

    /** POST vers la fonction Edge avec le jeton de l'élève (renouvelé si besoin). Jamais de clé Kalyno ici. */
    private suspend fun post(json: String): String = withContext(Dispatchers.IO) {
        val token = try {
            cloud.validAccessToken()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw KalynoException("network", "Connexion impossible. Vérifie ta connexion et réessaie.", true)
        } ?: throw KalynoException("unauthenticated", "Connecte-toi pour utiliser l'assistant IA.", false)

        val connection = URL("${SupabaseConfig.BASE_URL}/functions/v1/$FUNCTION").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 40_000
            connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) return@withContext text
            throw KalynoResultParser.parseError(text) ?: KalynoException(
                code = "http_$code",
                message = if (code >= 500) "Le service IA est indisponible. Réessaie." else "Requête refusée.",
                retryable = code >= 500 || code == 429
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: KalynoException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw KalynoException("timeout", "Kalyno met trop de temps à répondre. Réessaie.", true)
        } catch (e: IOException) {
            throw KalynoException("network", "Connexion impossible. Vérifie ta connexion et réessaie.", true)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val FUNCTION = "kalyno-gemini"
        private const val MAX_ANSWER = 600
        private const val MAX_HISTORY = 10

        @Volatile private var instance: KalynoRepository? = null

        fun get(context: Context): KalynoRepository =
            instance ?: synchronized(this) { instance ?: KalynoRepository(context).also { instance = it } }
    }
}
