package ci.kalyno.app.data.dashboard

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Messages du coach de l'Accueil, publiés par l'admin (table `home_messages` de Supabase).
 *
 * Aucune phrase n'est écrite dans l'appli : la liste est lue en ligne puis gardée sur le téléphone
 * (elle marche donc hors connexion). Le message du jour est choisi d'après la date, dans un ordre
 * fixe : tous les élèves d'une même classe voient le même message le même jour.
 * Table absente ou vide = aucun message affiché, jamais d'erreur visible.
 */
class HomeMessages(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kalyno_home_messages", Context.MODE_PRIVATE)

    /** Message du jour d'après la liste déjà gardée sur le téléphone, ou null. */
    fun today(levelId: String, today: LocalDate = LocalDate.now()): String? =
        pick(readAll(levelId), today)

    /** Interroge Supabase puis garde la liste ; en cas d'erreur (hors connexion), garde l'ancienne. */
    fun refresh(levelId: String, today: LocalDate = LocalDate.now()): String? {
        try {
            val level = URLEncoder.encode(levelId, "UTF-8")
            val body = SupabaseHttp.get(
                "home_messages?select=message&is_active=eq.true" +
                    "&or=(level_id.is.null,level_id.eq.$level)&order=position.asc,id.asc"
            )
            prefs.edit().putString(key(levelId), encode(parse(JSONArray(body)))).apply()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // hors connexion ou table absente : on garde la liste déjà connue
        }
        return today(levelId, today)
    }

    private fun pick(list: List<String>, today: LocalDate): String? =
        if (list.isEmpty()) null else list[today.toEpochDay().mod(list.size.toLong()).toInt()]

    private fun readAll(levelId: String): List<String> =
        runCatching { parse(JSONArray(prefs.getString(key(levelId), null) ?: return emptyList())) }
            .getOrDefault(emptyList())

    private fun parse(array: JSONArray): List<String> = buildList {
        for (i in 0 until array.length()) {
            val text = array.getJSONObject(i).optString("message").trim()
            if (text.isEmpty() || text == "null") continue
            add(text)
        }
    }

    private fun encode(list: List<String>): String = JSONArray().apply {
        list.forEach { message -> put(org.json.JSONObject().put("message", message)) }
    }.toString()

    private fun key(levelId: String) = "messages_$levelId"
}
