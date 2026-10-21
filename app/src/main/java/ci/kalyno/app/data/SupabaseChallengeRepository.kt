package ci.kalyno.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SupabaseChallengeRepository : ChallengeRepository {

    override suspend fun firstChallenge(levelId: String): ChallengeResult =
        withContext(Dispatchers.IO) {
            try {
                val level = URLEncoder.encode(levelId, "UTF-8")
                val select = "id,level_id,title,questions(id,position,prompt,choices,correct_index,explanation)"
                val body = get("challenges?level_id=eq.$level&status=eq.published&kind=eq.discovery&select=$select")

                val array = JSONArray(body)
                val challenges = (0 until array.length())
                    .map { parseChallenge(array.getJSONObject(it)) }
                    .filter { it.questions.isNotEmpty() }

                if (challenges.isEmpty()) ChallengeResult.Empty
                else ChallengeResult.Loaded(challenges.random())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                ChallengeResult.Failed("Vérifie ta connexion internet et réessaie.")
            } catch (e: Exception) {
                ChallengeResult.Failed("Impossible de charger le défi.")
            }
        }

    private fun get(path: String): String {
        val connection = URL("${SupabaseConfig.BASE_URL}/rest/v1/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseChallenge(o: JSONObject): Challenge {
        val rows = o.getJSONArray("questions")
        val parsed = ArrayList<Pair<Int, Question>>()

        for (i in 0 until rows.length()) {
            val q = rows.getJSONObject(i)
            val raw = q.getJSONArray("choices")
            val choices = List(raw.length()) { raw.getString(it) }
            val correct = q.getInt("correct_index")
            if (choices.size != 4 || correct !in 0..3) continue

            parsed += q.getInt("position") to Question(
                id = q.getString("id"),
                prompt = q.getString("prompt"),
                choices = choices,
                correctIndex = correct,
                explanation = if (q.isNull("explanation")) null else q.getString("explanation")
            )
        }

        return Challenge(
            id = o.getString("id"),
            levelId = o.getString("level_id"),
            title = o.getString("title"),
            questions = parsed.sortedBy { it.first }.map { it.second }
        )
    }
}
