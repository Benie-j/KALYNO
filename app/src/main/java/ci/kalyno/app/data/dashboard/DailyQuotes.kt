package ci.kalyno.app.data.dashboard

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Citation du jour affichée sur l'Accueil. */
data class DailyQuote(val text: String, val author: String?)

/** Une citation et son jour de publication (date de création côté admin, heure locale du téléphone). */
data class QuoteDay(val date: LocalDate, val quote: DailyQuote)

/** Citation gardée sur le téléphone, avec son jour de publication (null si inconnu). */
private data class StoredQuote(val quote: DailyQuote, val publishedOn: LocalDate?)

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
            val filter = "&is_active=eq.true&or=(level_id.is.null,level_id.eq.$level)&order=position.asc,id.asc"
            val body = try {
                SupabaseHttp.get("daily_quotes?select=content,author,created_at$filter")
            } catch (e: IOException) {
                // Colonne created_at absente de la table : on retombe sur l'ancienne requête
                // (la citation du jour continue de marcher, la liste de la semaine restera vide).
                if (e.message?.contains("HTTP 400") == true) {
                    SupabaseHttp.get("daily_quotes?select=content,author$filter")
                } else {
                    throw e
                }
            }
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

    /**
     * Citations publiées pendant la semaine courante (du lundi à aujourd'hui), la plus récente d'abord.
     * Chaque citation apparaît une seule fois, à sa vraie date de publication : s'il n'y en a que deux
     * cette semaine, la liste n'en contient que deux. Sans date connue, une citation n'est pas listée.
     */
    fun week(levelId: String, today: LocalDate = LocalDate.now()): List<QuoteDay> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return readStored(levelId)
            .mapNotNull { stored ->
                stored.publishedOn
                    ?.takeIf { it in monday..today }
                    ?.let { QuoteDay(it, stored.quote) }
            }
            .sortedByDescending { it.date } // tri stable : à jour égal, l'ordre de l'admin est gardé
    }

    private fun pick(list: List<DailyQuote>, today: LocalDate): DailyQuote? =
        if (list.isEmpty()) null else list[today.toEpochDay().mod(list.size.toLong()).toInt()]

    private fun readAll(levelId: String): List<DailyQuote> = readStored(levelId).map { it.quote }

    private fun readStored(levelId: String): List<StoredQuote> =
        runCatching { parse(JSONArray(prefs.getString(key(levelId), null) ?: return emptyList())) }
            .getOrDefault(emptyList())

    private fun parse(array: JSONArray): List<StoredQuote> = buildList {
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            val text = row.optString("content").trim()
            if (text.isEmpty() || text == "null") continue
            val author = if (row.isNull("author")) null else row.optString("author").trim().takeIf { it.isNotEmpty() }
            add(StoredQuote(DailyQuote(text = text, author = author), publishedOn(row)))
        }
    }

    /** Jour de publication : `created_at` (réponse Supabase) ou `published_on` (copie gardée sur le téléphone). */
    private fun publishedOn(row: JSONObject): LocalDate? {
        if (row.has("published_on") && !row.isNull("published_on")) {
            return LocalDate.ofEpochDay(row.optLong("published_on"))
        }
        val raw = row.optString("created_at").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return runCatching {
            OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }.getOrNull()
    }

    private fun encode(list: List<StoredQuote>): String = JSONArray().apply {
        list.forEach { stored ->
            put(
                JSONObject()
                    .put("content", stored.quote.text)
                    .put("author", stored.quote.author ?: JSONObject.NULL)
                    .put("published_on", stored.publishedOn?.toEpochDay() ?: JSONObject.NULL)
            )
        }
    }.toString()

    private fun key(levelId: String) = "quotes_$levelId"
}
