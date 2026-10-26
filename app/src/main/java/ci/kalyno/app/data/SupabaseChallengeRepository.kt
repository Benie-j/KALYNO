package ci.kalyno.app.data

import ci.kalyno.app.data.dojo.AnswerGrader
import ci.kalyno.app.data.dojo.ExerciseKind
import ci.kalyno.app.data.dojo.mergeCorrectIndices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Premier défi de l'élève, composé et publié par l'admin (table `challenges`, kind = discovery).
 * Il est proposé juste après le pseudo. Les questions suivent les types du Dojo.
 */
class SupabaseChallengeRepository : ChallengeRepository {

    private class HttpFailure(val code: Int) : IOException("HTTP $code")

    override suspend fun firstChallenge(levelId: String): ChallengeResult =
        withContext(Dispatchers.IO) {
            try {
                val level = URLEncoder.encode(levelId, "UTF-8")
                val filter = "challenges?level_id=eq.$level&status=eq.published&kind=eq.discovery&order=position.asc&select="

                // Colonnes du Dojo (kind, correct_indices, answer, hint) ; si la migration SQL
                // n'est pas encore passée, Supabase répond 400 et on retombe sur l'ancien format.
                val body = try {
                    get(filter + SELECT_FULL)
                } catch (e: HttpFailure) {
                    if (e.code == 400) get(filter + SELECT_LEGACY) else throw e
                }

                val array = JSONArray(body)
                val challenges = (0 until array.length())
                    .map { parseChallenge(array.getJSONObject(it)) }
                    .filter { it.questions.isNotEmpty() }

                // Le premier défi est unique par classe : le premier publié (position la plus basse)
                if (challenges.isEmpty()) ChallengeResult.Empty
                else ChallengeResult.Loaded(challenges.first())
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
            if (code !in 200..299) throw HttpFailure(code)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.text(name: String): String? =
        if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() }

    private fun parseChallenge(o: JSONObject): Challenge {
        val rows = o.getJSONArray("questions")
        val parsed = ArrayList<Pair<Int, Question>>()

        for (i in 0 until rows.length()) {
            val q = rows.getJSONObject(i)
            val question = parseQuestion(q) ?: continue
            parsed += q.optInt("position", i) to question
        }

        return Challenge(
            id = o.getString("id"),
            levelId = o.getString("level_id"),
            title = o.getString("title"),
            questions = parsed.sortedBy { it.first }.map { it.second }
        )
    }

    /** Une question inexploitable est ignorée plutôt que de bloquer tout le défi. */
    private fun parseQuestion(q: JSONObject): Question? {
        val prompt = q.text("prompt") ?: return null

        val rawChoices = q.optJSONArray("choices")
        val choices = if (rawChoices == null) emptyList() else List(rawChoices.length()) { rawChoices.optString(it).trim() }

        val multi = q.optJSONArray("correct_indices")
            ?.let { a -> List(a.length()) { a.optInt(it, -1) } }
            .orEmpty()
        val single = if (q.isNull("correct_index")) null else q.optInt("correct_index", -1)
        val indices = mergeCorrectIndices(multi, single).filter { it in choices.indices }

        val kind = ExerciseKind.resolve(q.text("kind"), choices, indices)
        val accepted = AnswerGrader.acceptedAnswers(q.text("answer").orEmpty())
        if (kind == ExerciseKind.FREE_TEXT && accepted.isEmpty()) return null
        if (kind != ExerciseKind.FREE_TEXT && choices.any { it.isEmpty() }) return null

        return Question(
            id = q.getString("id"),
            prompt = prompt,
            kind = kind,
            choices = if (kind == ExerciseKind.FREE_TEXT) emptyList() else choices,
            correctIndices = if (kind == ExerciseKind.FREE_TEXT) emptySet() else indices.toSet(),
            acceptedAnswers = accepted,
            hint = q.text("hint"),
            explanation = q.text("explanation")
        )
    }

    private companion object {
        const val SELECT_FULL =
            "id,level_id,title,position,questions(id,position,prompt,kind,choices,correct_index,correct_indices,answer,hint,explanation)"
        const val SELECT_LEGACY =
            "id,level_id,title,position,questions(id,position,prompt,choices,correct_index,explanation)"
    }
}
