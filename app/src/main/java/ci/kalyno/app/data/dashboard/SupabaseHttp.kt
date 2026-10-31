package ci.kalyno.app.data.dashboard

import ci.kalyno.app.data.SupabaseConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal object SupabaseHttp {

    /** POST sans session (rôle anonyme) : la clé publique seule, les règles RLS décident de ce qui passe. */
    fun post(path: String, body: String, prefer: String? = null): String {
        val connection = URL("${SupabaseConfig.BASE_URL}/rest/v1/$path")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (prefer != null) connection.setRequestProperty("Prefer", prefer)
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Supabase HTTP $code: $text")
            return text
        } finally {
            connection.disconnect()
        }
    }

    fun get(path: String): String {
        val connection = URL("${SupabaseConfig.BASE_URL}/rest/v1/$path")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Supabase HTTP $code: $detail")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
