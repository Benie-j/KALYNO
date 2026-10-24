package ci.kalyno.app.data.dashboard

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Date d'examen de chaque classe, publiée par l'admin (table `exam_dates` de Supabase).
 *
 * Rien n'est écrit en dur dans l'appli : la date est lue en ligne puis gardée sur le téléphone,
 * pour que le calcul des révisions marche aussi hors connexion. Si l'admin retire la date,
 * le plafond disparaît au prochain chargement.
 */
class ExamSchedule(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kalyno_exam", Context.MODE_PRIVATE)

    /** Dernière date connue pour cette classe, ou null. */
    fun cached(levelId: String): LocalDate? =
        prefs.getString(key(levelId), null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Interroge Supabase ; en cas d'erreur réseau, garde la date déjà connue. */
    fun refresh(levelId: String): LocalDate? = try {
        val rows = JSONArray(
            SupabaseHttp.get(
                "exam_dates?select=exam_date&level_id=eq.${URLEncoder.encode(levelId, "UTF-8")}&limit=1"
            )
        )
        val date = if (rows.length() == 0) null else LocalDate.parse(rows.getJSONObject(0).getString("exam_date"))
        val editor = prefs.edit()
        if (date == null) editor.remove(key(levelId)) else editor.putString(key(levelId), date.toString())
        editor.apply()
        date
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        cached(levelId)
    }

    private fun key(levelId: String) = "exam_$levelId"
}
