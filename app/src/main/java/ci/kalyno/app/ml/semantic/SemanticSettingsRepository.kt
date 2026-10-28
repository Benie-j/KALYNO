package ci.kalyno.app.ml.semantic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Seuils d'évaluation pilotés par l'admin (table Supabase `semantic_settings`).
 *
 * Offline-first : la lecture ([thresholdsFor]) est locale et immédiate (SharedPreferences) ;
 * [refresh] met le cache à jour quand le réseau est disponible et échoue en silence sinon.
 * Aucune dépendance supplémentaire (HttpURLConnection + org.json).
 */
class SemanticSettingsRepository(
    context: Context,
    private val supabaseUrl: String,       // ex. BuildConfig.SUPABASE_URL
    private val publishableKey: String     // clé publique sb_publishable (jamais la clé secrète)
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("kalyno_semantic_settings", Context.MODE_PRIVATE)

    /**
     * Seuils pour une matière (`scope` = identifiant de la matière), sinon ligne globale, sinon défaut.
     * Lecture locale uniquement : sûr à appeler depuis n'importe quel thread.
     */
    fun thresholdsFor(scope: String? = null): SemanticThresholds {
        val rows = runCatching { JSONArray(prefs.getString(KEY_ROWS, "[]")) }.getOrDefault(JSONArray())
        var global: SemanticThresholds? = null
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val t = SemanticThresholds.sanitize(
                r.optDouble("valid_threshold").toFloat(),
                r.optDouble("partial_threshold").toFloat().takeIf { !it.isNaN() }
            )
            when (r.optString("scope")) {
                scope -> if (scope != null) return t
                GLOBAL_SCOPE -> global = t
            }
        }
        return global ?: SemanticThresholds.DEFAULT
    }

    /** Télécharge les seuils publiés. Retourne true si le cache a été mis à jour. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("${supabaseUrl.trimEnd('/')}/rest/v1/semantic_settings" +
                "?select=scope,valid_threshold,partial_threshold")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Authorization", "Bearer $publishableKey")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "Seuils non mis à jour : HTTP ${conn.responseCode}")
                return@withContext false
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONArray(body) // validation du format avant d'écraser le cache
            prefs.edit().putString(KEY_ROWS, body).apply()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Seuils non mis à jour (hors-ligne ?) : ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    private companion object {
        const val TAG = "SemanticSettings"
        const val KEY_ROWS = "rows"
        const val GLOBAL_SCOPE = "global"
    }
}
