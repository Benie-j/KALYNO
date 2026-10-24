package ci.kalyno.app.data.cloud

import ci.kalyno.app.data.SupabaseConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Appels à l'API de données Supabase au nom de l'élève connecté (protégés par les règles RLS). */
internal object SupabaseRest {

    fun request(
        method: String,
        path: String,
        accessToken: String,
        body: String? = null,
        prefer: String? = null
    ): String {
        val connection = URL("${SupabaseConfig.BASE_URL}/rest/v1/$path")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            if (prefer != null) connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Supabase HTTP $code: $text")
            return text
        } finally {
            connection.disconnect()
        }
    }
}
