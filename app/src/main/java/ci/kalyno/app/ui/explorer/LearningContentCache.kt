package ci.kalyno.app.ui.explorer

import android.content.Context
import ci.kalyno.app.data.LearningCapsule
import ci.kalyno.app.data.LearningCard
import ci.kalyno.app.data.LearningChapter
import ci.kalyno.app.data.LearningSession
import ci.kalyno.app.data.LearningSubject
import ci.kalyno.app.data.LearningTheme
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cache local de secours du contenu publié par l'admin.
 *
 * Online-first : le réseau est toujours interrogé en premier ; ce cache n'est lu que pour
 * afficher immédiatement quelque chose au lancement ou tolérer une micro-coupure.
 * Stocké en JSON dans filesDir (aucune migration Room nécessaire).
 */
class LearningContentCache(context: Context) {

    private val dir: File = context.applicationContext.filesDir

    private fun fileFor(levelId: String): File =
        File(dir, "learning_cache_${levelId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    fun write(levelId: String, subjects: List<LearningSubject>) {
        runCatching {
            val target = fileFor(levelId)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(encode(subjects).toString())
            if (!tmp.renameTo(target)) {
                target.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    fun read(levelId: String): List<LearningSubject>? = runCatching {
        val file = fileFor(levelId)
        if (!file.exists()) return@runCatching null
        decode(JSONArray(file.readText()))
    }.getOrNull()

    // ----- JSON -----

    private fun encode(subjects: List<LearningSubject>): JSONArray = JSONArray().apply {
        subjects.forEach { s ->
            put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("iconKey", s.iconKey ?: JSONObject.NULL)
                    put("themes", JSONArray().apply {
                        s.themes.forEach { t ->
                            put(JSONObject().apply {
                                put("id", t.id)
                                put("title", t.title)
                            })
                        }
                    })
                    put("chapters", JSONArray().apply {
                        s.chapters.forEach { ch ->
                            put(
                                JSONObject().apply {
                                    put("id", ch.id)
                                    put("title", ch.title)
                                    put("tag", ch.tag ?: JSONObject.NULL)
                                    put("themeId", ch.themeId ?: JSONObject.NULL)
                                    put("complement", ch.complement ?: JSONObject.NULL)
                                    put("sessions", JSONArray().apply {
                                        ch.sessions.forEach { se ->
                                            put(JSONObject().apply {
                                                put("id", se.id)
                                                put("title", se.title)
                                            })
                                        }
                                    })
                                    put("capsules", JSONArray().apply {
                                        ch.capsules.forEach { cap ->
                                            put(
                                                JSONObject().apply {
                                                    put("id", cap.id)
                                                    put("title", cap.title)
                                                    put("body", cap.body)
                                                    put("minutes", cap.minutes)
                                                    put("kind", cap.kind)
                                                    put("sessionId", cap.sessionId ?: JSONObject.NULL)
                                                }
                                            )
                                        }
                                    })
                                    put("cards", JSONArray().apply {
                                        ch.cards.forEach { card -> put(encodeCard(card)) }
                                    })
                                }
                            )
                        }
                    })
                }
            )
        }
    }

    private fun encodeCard(card: LearningCard): JSONObject = JSONObject().apply {
        put("id", card.id)
        put("capsuleId", card.capsuleId ?: JSONObject.NULL)
        put("kind", card.kind)
        put("front", card.front)
        put("back", card.back ?: JSONObject.NULL)
        put("choices", JSONArray().apply { card.choices.forEach { put(it) } })
        put("correctIndex", card.correctIndex ?: JSONObject.NULL)
        put("explanation", card.explanation ?: JSONObject.NULL)
        put("correctIndices", JSONArray().apply { card.correctIndices.forEach { put(it) } })
    }

    private fun decode(array: JSONArray): List<LearningSubject> =
        List(array.length()) { i ->
            val s = array.getJSONObject(i)
            val chaptersJson = s.optJSONArray("chapters") ?: JSONArray()
            val themesJson = s.optJSONArray("themes") ?: JSONArray()
            LearningSubject(
                id = s.getString("id"),
                name = s.optString("name", ""),
                chapters = List(chaptersJson.length()) { j -> decodeChapter(chaptersJson.getJSONObject(j)) },
                iconKey = if (s.isNull("iconKey")) null else s.optString("iconKey").takeIf { it.isNotBlank() },
                themes = List(themesJson.length()) { k ->
                    val t = themesJson.getJSONObject(k)
                    LearningTheme(id = t.getString("id"), title = t.optString("title", ""))
                }
            )
        }

    private fun decodeChapter(ch: JSONObject): LearningChapter {
        val cards = ch.optJSONArray("cards") ?: JSONArray()
        val capsules = ch.optJSONArray("capsules") ?: JSONArray()
        val sessionsJson = ch.optJSONArray("sessions") ?: JSONArray()
        val cardList = List(cards.length()) { decodeCard(cards.getJSONObject(it)) }
        return LearningChapter(
            id = ch.getString("id"),
            title = ch.optString("title", ""),
            tag = if (ch.isNull("tag")) null else ch.optString("tag").takeIf { it.isNotBlank() },
            themeId = if (ch.isNull("themeId")) null else ch.optString("themeId").takeIf { it.isNotBlank() },
            complement = if (ch.isNull("complement")) null else ch.optString("complement").takeIf { it.isNotBlank() },
            sessions = List(sessionsJson.length()) { n ->
                val se = sessionsJson.getJSONObject(n)
                LearningSession(id = se.getString("id"), title = se.optString("title", ""))
            },
            capsules = List(capsules.length()) { k ->
                val cap = capsules.getJSONObject(k)
                val capId = cap.getString("id")
                LearningCapsule(
                    id = capId,
                    title = cap.optString("title", ""),
                    body = cap.optString("body", ""),
                    minutes = cap.optInt("minutes", 0),
                    cards = cardList.filter { it.capsuleId == capId },
                    kind = cap.optString("kind", "text"),
                    sessionId = if (cap.isNull("sessionId")) null else cap.optString("sessionId").takeIf { it.isNotBlank() }
                )
            },
            cards = cardList
        )
    }

    private fun decodeCard(o: JSONObject): LearningCard {
        val rawChoices = o.optJSONArray("choices") ?: JSONArray()
        return LearningCard(
            id = o.getString("id"),
            capsuleId = if (o.isNull("capsuleId")) null else o.optString("capsuleId"),
            kind = o.optString("kind", "flashcard"),
            front = o.optString("front", ""),
            back = if (o.isNull("back")) null else o.optString("back"),
            choices = List(rawChoices.length()) { rawChoices.optString(it) },
            correctIndex = if (o.isNull("correctIndex")) null else o.optInt("correctIndex"),
            explanation = if (o.isNull("explanation")) null else o.optString("explanation"),
            correctIndices = o.optJSONArray("correctIndices")
                ?.let { a -> List(a.length()) { a.optInt(it, -1) }.filter { it >= 0 } }
                ?: emptyList()
        )
    }
}
