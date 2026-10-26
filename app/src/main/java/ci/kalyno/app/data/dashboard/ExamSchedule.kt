package ci.kalyno.app.data.dashboard

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Date d'examen de chaque classe, publiée par l'admin (table `exam_dates` de Supabase).
 *
 * Rien n'est écrit en dur dans l'appli : la date et son intitulé sont lus en ligne puis gardés
 * sur le téléphone, pour que le compte à rebours et le calcul des révisions marchent aussi hors
 * connexion. Si l'admin retire la date, le plafond disparaît au prochain chargement.
 */
class ExamSchedule(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kalyno_exam", Context.MODE_PRIVATE)

    /** Dernière date connue pour cette classe, ou null. */
    fun cached(levelId: String): LocalDate? =
        prefs.getString(key(levelId), null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Intitulé de l'examen publié par l'admin (affiché sous le compte à rebours), ou null. */
    fun label(levelId: String): String? = prefs.getString(labelKey(levelId), null)

    /** Interroge Supabase ; en cas d'erreur réseau, garde la date déjà connue. */
    fun refresh(levelId: String): LocalDate? = try {
        val rows = JSONArray(
            SupabaseHttp.get(
                "exam_dates?select=exam_date,label&level_id=eq.${URLEncoder.encode(levelId, "UTF-8")}&limit=1"
            )
        )
        val row = if (rows.length() == 0) null else rows.getJSONObject(0)
        val date = row?.let { LocalDate.parse(it.getString("exam_date")) }
        val label = row
            ?.takeUnless { it.isNull("label") }
            ?.optString("label")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val editor = prefs.edit()
        if (date == null) editor.remove(key(levelId)) else editor.putString(key(levelId), date.toString())
        if (label == null) editor.remove(labelKey(levelId)) else editor.putString(labelKey(levelId), label)
        editor.apply()
        date
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        cached(levelId)
    }

    private fun key(levelId: String) = "exam_$levelId"

    private fun labelKey(levelId: String) = "exam_label_$levelId"
}
