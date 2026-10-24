package ci.kalyno.app.data.auth

import android.util.Log
import ci.kalyno.app.data.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Erreur d'authentification avec un message prêt à être affiché à l'élève. */
class AuthException(message: String) : Exception(message)

class SupabaseAuthApi {

    /**
     * Échange le jeton Google contre une session Supabase.
     * Le compte est créé automatiquement à la première connexion (inscription = connexion).
     */
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String): AuthSession =
        requestSession(
            grantType = "id_token",
            body = JSONObject()
                .put("provider", "google")
                .put("id_token", idToken)
                .put("nonce", rawNonce)
        )

    /** Renouvelle la session avant l'expiration du jeton d'accès. */
    suspend fun refresh(refreshToken: String): AuthSession =
        requestSession(
            grantType = "refresh_token",
            body = JSONObject().put("refresh_token", refreshToken)
        )

    private suspend fun requestSession(grantType: String, body: JSONObject): AuthSession =
        withContext(Dispatchers.IO) {
            val connection = URL("${SupabaseConfig.BASE_URL}/auth/v1/token?grant_type=$grantType")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (code !in 200..299) {
                    Log.w(TAG, "Supabase auth HTTP $code: $text")
                    throw AuthException(friendlyMessage(code, text))
                }
                parseSession(text)
            } finally {
                connection.disconnect()
            }
        }

    private fun friendlyMessage(code: Int, text: String): String {
        val detail = try {
            val json = JSONObject(text)
            listOf("error_description", "msg", "message", "error")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } }
                .orEmpty()
        } catch (e: JSONException) {
            ""
        }
        return when {
            detail.contains("provider is not enabled", ignoreCase = true) ->
                "La connexion Google n'est pas encore activée sur le serveur."
            detail.contains("nonce", ignoreCase = true) ->
                "Vérification de sécurité échouée. Réessaie."
            detail.contains("audience", ignoreCase = true) || detail.contains("client", ignoreCase = true) ->
                "La configuration Google ne correspond pas au serveur."
            else -> "Connexion refusée par le serveur (code $code)."
        }
    }

    private fun parseSession(text: String): AuthSession {
        try {
            val json = JSONObject(text)
            val user = json.getJSONObject("user")
            val meta = user.optJSONObject("user_metadata")
            val name = meta?.cleanString("full_name") ?: meta?.cleanString("name")
            return AuthSession(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAtMillis = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L,
                userId = user.getString("id"),
                email = user.cleanString("email"),
                name = name
            )
        } catch (e: JSONException) {
            Log.w(TAG, "Réponse d'authentification illisible", e)
            throw AuthException("Réponse inattendue du serveur.")
        }
    }

    private fun JSONObject.cleanString(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private companion object {
        const val TAG = "KalynoAuth"
    }
}
