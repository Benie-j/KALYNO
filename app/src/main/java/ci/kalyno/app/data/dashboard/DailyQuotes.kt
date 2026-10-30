package ci.kalyno.app.data.dashboard

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate

/** Citation du jour affichée sur l'Accueil. */
data class DailyQuote(val text: String, val author: String?)

/** Une citation et le jour où elle était (ou sera) la citation du jour. */
data class QuoteDay(val date: LocalDate, val quote: DailyQuote)

/**
 * Citations publiées par l'admin (table `daily_quotes` de Supabase).
 *
 * Aucune citation n'est écrite dans l'appli : la liste est lue en ligne puis gardée sur le
 * téléphone (elle marche donc hors connexion). La citation du jour est choisie d'après la date,
 * dans un ordre fixe : tous les élèves d'une même classe voient la même citation le même jour.
 */
class DailyQuotes(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kalyno_quotes", Context.MODE_PRIVATE)

    /** Citation du jour d'après la liste déjà gardée sur le téléphone, ou null. */
    fun cached(levelId: String, today: LocalDate = LocalDate.now()): DailyQuote? =
        pick(readAll(levelId), today)

    /** Interroge Supabase puis garde la liste ; en cas d'erreur (hors connexion), garde l'ancienne. */
    fun refresh(levelId: String, today: LocalDate = LocalDate.now()): DailyQuote? {
        try {
            val level = URLEncoder.encode(levelId, "UTF-8")
            val body = SupabaseHttp.get(
                "daily_quotes?select=content,author&is_active=eq.true" +
                    "&or=(level_id.is.null,level_id.eq.$level)&order=position.asc,id.asc"
            )
            prefs.edit().putString(key(levelId), encode(parse(JSONArray(body)))).apply()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // hors connexion ou table absente : on garde la liste déjà connue
        }
        return cached(levelId, today)
    }

    /** Toutes les citations déjà gardées sur le téléphone pour cette classe (sert au shuffle). */
    fun all(levelId: String): List<DailyQuote> = readAll(levelId)

    /** Les 7 derniers jours, aujourd'hui en premier, dans le même ordre fixe que la citation du jour. */
    fun week(levelId: String, today: LocalDate = LocalDate.now()): List<QuoteDay> {
        val list = readAll(levelId)
        if (list.isEmpty()) return emptyList()
        return (0L..6L).mapNotNull { back ->
            val day = today.minusDays(back)
            pick(list, day)?.let { QuoteDay(day, it) }
        }
    }

    private fun pick(list: List<DailyQuote>, today: LocalDate): DailyQuote? =
        if (list.isEmpty()) null else list[today.toEpochDay().mod(list.size.toLong()).toInt()]

    private fun readAll(levelId: String): List<DailyQuote> =
        runCatching { parse(JSONArray(prefs.getString(key(levelId), null) ?: return emptyList())) }
            .getOrDefault(emptyList())

    private fun parse(array: JSONArray): List<DailyQuote> = buildList {
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            val text = row.optString("content").trim()
            if (text.isEmpty() || text == "null") continue
            val author = if (row.isNull("author")) null else row.optString("author").trim().takeIf { it.isNotEmpty() }
            add(DailyQuote(text = text, author = author))
        }
    }

    private fun encode(list: List<DailyQuote>): String = JSONArray().apply {
        list.forEach { quote ->
            put(
                JSONObject()
                    .put("content", quote.text)
                    .put("author", quote.author ?: JSONObject.NULL)
            )
        }
    }.toString()

    private fun key(levelId: String) = "quotes_$levelId"
}
