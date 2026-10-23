package ci.kalyno.app.data.dashboard

import ci.kalyno.app.data.SupabaseConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal object SupabaseHttp {
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
