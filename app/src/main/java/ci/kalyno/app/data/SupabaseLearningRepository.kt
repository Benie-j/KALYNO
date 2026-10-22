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

data class LearningCard(
    val id: String,
    val capsuleId: String?,
    val kind: String,
    val front: String,
    val back: String?,
    val choices: List<String>,
    val correctIndex: Int?,
    val explanation: String?
)

data class LearningCapsule(
    val id: String,
    val title: String,
    val body: String,
    val minutes: Int,
    val cards: List<LearningCard>
)

data class LearningChapter(
    val id: String,
    val title: String,
    val capsules: List<LearningCapsule>,
    val cards: List<LearningCard>
)

data class LearningSubject(
    val id: String,
    val name: String,
    val emoji: String,
    val chapters: List<LearningChapter>
)

class SupabaseLearningRepository {

    suspend fun loadPublishedContent(levelId: String): List<LearningSubject> =
        withContext(Dispatchers.IO) {
            try {
                val subjects = getArray(
                    "subjects?select=id,name,emoji,position" +
                        "&level_id=eq.${enc(levelId)}" +
                        "&is_active=eq.true&order=position.asc"
                )

                buildList {
                    for (i in 0 until subjects.length()) {
                        val subject = subjects.getJSONObject(i)
                        val subjectId = subject.getString("id")

                        val chapterRows = getArray(
                            "chapters?select=id,title,position" +
                                "&subject_id=eq.${enc(subjectId)}" +
                                "&status=eq.published&order=position.asc"
                        )

                        val chapters = buildList {
                            for (j in 0 until chapterRows.length()) {
                                val chapter = chapterRows.getJSONObject(j)
                                val chapterId = chapter.getString("id")

                                val capsuleRows = getArray(
                                    "capsules?select=id,title,body,minutes,position" +
                                        "&chapter_id=eq.${enc(chapterId)}" +
                                        "&order=position.asc"
                                )

                                val cards = loadCards(chapterId)

                                val capsules = buildList {
                                    for (k in 0 until capsuleRows.length()) {
                                        val capsule = capsuleRows.getJSONObject(k)
                                        val capsuleId = capsule.getString("id")

                                        add(
                                            LearningCapsule(
                                                id = capsuleId,
                                                title = capsule.optString("title", ""),
                                                body = capsule.optString("body", ""),
                                                minutes = capsule.optInt("minutes", 0),
                                                cards = cards.filter {
                                                    it.capsuleId == capsuleId
                                                }
                                            )
                                        )
                                    }
                                }

                                add(
                                    LearningChapter(
                                        id = chapterId,
                                        title = chapter.optString("title", ""),
                                        capsules = capsules,
                                        cards = cards
                                    )
                                )
                            }
                        }

                        add(
                            LearningSubject(
                                id = subjectId,
                                name = subject.optString("name", ""),
                                emoji = subject.optString("emoji", "📘")
                                    .ifBlank { "📘" },
                                chapters = chapters
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }

    private fun loadCards(chapterId: String): List<LearningCard> {
        val rows = getArray(
            "cards?select=id,capsule_id,kind,front,back,choices," +
                "correct_index,explanation,position" +
                "&chapter_id=eq.${enc(chapterId)}&order=position.asc"
        )

        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val rawChoices = row.optJSONArray("choices")
                val choices = if (rawChoices == null) {
                    emptyList()
                } else {
                    List(rawChoices.length()) { index ->
                        rawChoices.optString(index)
                    }
                }

                add(
                    LearningCard(
                        id = row.getString("id"),
                        capsuleId = if (row.isNull("capsule_id")) {
                            null
                        } else {
                            row.optString("capsule_id").takeIf { it.isNotBlank() }
                        },
                        kind = row.optString("kind", "flashcard"),
                        front = row.optString("front", ""),
                        back = if (row.isNull("back")) {
                            null
                        } else {
                            row.optString("back")
                        },
                        choices = choices,
                        correctIndex = if (row.isNull("correct_index")) {
                            null
                        } else {
                            row.optInt("correct_index")
                        },
                        explanation = if (row.isNull("explanation")) {
                            null
                        } else {
                            row.optString("explanation")
                        }
                    )
                )
            }
        }
    }

    private fun getArray(path: String): JSONArray =
        JSONArray(get(path))

    private fun get(path: String): String {
        val connection = URL(
            "${SupabaseConfig.BASE_URL}/rest/v1/$path"
        ).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty(
                "apikey",
                SupabaseConfig.PUBLISHABLE_KEY
            )
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                throw IOException("Supabase HTTP $code: $detail")
            }

            return connection.inputStream
                .bufferedReader()
                .use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8")
}
