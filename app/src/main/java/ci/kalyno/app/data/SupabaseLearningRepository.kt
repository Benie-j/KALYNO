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
    val explanation: String?,
    /** Bonnes réponses d'un QCM à choix multiples (colonne `correct_indices`, optionnelle). */
    val correctIndices: List<Int> = emptyList()
)

data class LearningCapsule(
    val id: String,
    val title: String,
    val body: String,
    val minutes: Int,
    val cards: List<LearningCard>,
    /**
     * Type de bloc configuré par l'admin : "intro", "text", "rule" (formule / règle d'or),
     * "tip" (astuce) ou "pitfall" (piège à éviter). "text" par défaut : si la colonne `kind`
     * n'existe pas encore côté back-end, l'app reste 100 % fonctionnelle.
     */
    val kind: String = "text",
    /** Séance de la leçon à laquelle appartient le bloc ; null = bloc hors séance (leçons créées avant les séances). */
    val sessionId: String? = null
)

/** Séance d'une leçon (Séance 1, Séance 2…) : étape chronologique regroupant plusieurs blocs. */
data class LearningSession(val id: String, val title: String)

data class LearningChapter(
    val id: String,
    val title: String,
    val capsules: List<LearningCapsule>,
    val cards: List<LearningCard>,
    /** Smart Tag choisi par l'admin ("urgent", "populaire", texte libre…) ; null si absent. */
    val tag: String? = null,
    /** Thème (chapitre global) auquel appartient la leçon ; null tant que l'admin ne l'a pas rangée. */
    val themeId: String? = null,
    /** Séances de la leçon, dans l'ordre chronologique défini par l'admin (vide pour les anciennes leçons). */
    val sessions: List<LearningSession> = emptyList(),
    /** Notes et ressources complémentaires facultatives saisies par l'admin ; null si absentes. */
    val complement: String? = null
)

/** Thème = chapitre global d'une discipline, qui regroupe plusieurs leçons. */
data class LearningTheme(val id: String, val title: String)

data class LearningSubject(
    val id: String,
    val name: String,
    val chapters: List<LearningChapter>,
    /** Clé d'icône vectorielle choisie par l'admin ; null = icône par défaut. */
    val iconKey: String? = null,
    val themes: List<LearningTheme> = emptyList(),
    /** Hérité, plus jamais lu ni affiché (l'interface n'utilise plus d'emoji). */
    val emoji: String = ""
)

class SupabaseLearningRepository {

    suspend fun loadPublishedContent(levelId: String): List<LearningSubject> =
        withContext(Dispatchers.IO) {
            try {
                val subjects = loadSubjectRows(levelId)

                buildList {
                    for (i in 0 until subjects.length()) {
                        val subject = subjects.getJSONObject(i)
                        val subjectId = subject.getString("id")

                        val chapterRows = loadChapterRows(subjectId)

                        val chapters = buildList {
                            for (j in 0 until chapterRows.length()) {
                                val chapter = chapterRows.getJSONObject(j)
                                val chapterId = chapter.getString("id")

                                val capsuleRows = loadCapsuleRows(chapterId)
                                val sessions = loadSessions(chapterId)

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
                                                },
                                                kind = capsule.optString("kind", "text")
                                                    .takeUnless {
                                                        it.isBlank() || it.equals("null", ignoreCase = true)
                                                    } ?: "text",
                                                sessionId = capsule.textOrNull("session_id")
                                            )
                                        )
                                    }
                                }

                                add(
                                    LearningChapter(
                                        id = chapterId,
                                        title = chapter.optString("title", ""),
                                        capsules = capsules,
                                        cards = cards,
                                        tag = chapter.textOrNull("tag"),
                                        themeId = chapter.textOrNull("theme_id"),
                                        sessions = sessions,
                                        complement = chapter.textOrNull("complement")
                                    )
                                )
                            }
                        }

                        val themeRows = loadThemeRows(subjectId)
                        val themes = buildList {
                            for (t in 0 until themeRows.length()) {
                                val row = themeRows.getJSONObject(t)
                                add(LearningTheme(id = row.getString("id"), title = row.optString("title", "")))
                            }
                        }

                        add(
                            LearningSubject(
                                id = subjectId,
                                name = subject.optString("name", ""),
                                chapters = chapters,
                                iconKey = subject.textOrNull("icon_key"),
                                themes = themes
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }

    /** Bannière « à la une » de l'admin pour cette classe. Table facultative : jamais d'erreur visible. */
    suspend fun loadBanner(levelId: String): ExplorerBanner? = withContext(Dispatchers.IO) {
        try {
            val rows = getArray(
                "explorer_banners?select=id,title,subtitle,button_label,chapter_id,level_id" +
                    "&is_active=eq.true&or=(level_id.is.null,level_id.eq.${enc(levelId)})" +
                    "&order=position.asc&limit=1"
            )
            if (rows.length() == 0) {
                null
            } else {
                val row = rows.getJSONObject(0)
                val title = row.textOrNull("title")
                if (title == null) {
                    null
                } else {
                    ExplorerBanner(
                        id = row.optString("id"),
                        title = title,
                        subtitle = row.textOrNull("subtitle"),
                        buttonLabel = row.textOrNull("button_label"),
                        chapterId = row.textOrNull("chapter_id")
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Niveau de colonnes que la table `chapters` sait servir : 3 = tag + theme_id + complement, 2 = tag + theme_id,
     * 1 = tag, 0 = rien.
     * On descend d'un cran dès que le back-end répond HTTP 400 (colonne inconnue) : l'app reste fonctionnelle
     * même si la migration SQL n'est pas encore passée.
     */
    @Volatile
    private var chapterColumnsLevel = 3

    private fun loadChapterRows(subjectId: String): JSONArray {
        val filter = "&subject_id=eq.${enc(subjectId)}&status=eq.published&order=position.asc"
        while (true) {
            val columns = when (chapterColumnsLevel) {
                3 -> "id,title,position,tag,theme_id,complement"
                2 -> "id,title,position,tag,theme_id"
                1 -> "id,title,position,tag"
                else -> "id,title,position"
            }
            try {
                return getArray("chapters?select=$columns$filter")
            } catch (e: IOException) {
                if (e.message?.startsWith("Supabase HTTP 400") == true && chapterColumnsLevel > 0) {
                    chapterColumnsLevel--
                } else {
                    throw e
                }
            }
        }
    }

    /** Faux dès qu'on sait que la table `subjects` n'a pas (encore) la colonne `icon_key`. */
    @Volatile
    private var subjectIconSupported = true

    private fun loadSubjectRows(levelId: String): JSONArray {
        val filter = "&level_id=eq.${enc(levelId)}&is_active=eq.true&order=position.asc"
        if (subjectIconSupported) {
            try {
                return getArray("subjects?select=id,name,icon_key,position$filter")
            } catch (e: IOException) {
                if (e.message?.startsWith("Supabase HTTP 400") == true) subjectIconSupported = false else throw e
            }
        }
        return getArray("subjects?select=id,name,position$filter")
    }

    /** Thèmes publiés d'une discipline. Table facultative : absente (404) ou non migrée (400) = aucun thème. */
    @Volatile
    private var themesSupported = true

    private fun loadThemeRows(subjectId: String): JSONArray {
        if (!themesSupported) return JSONArray()
        return try {
            getArray(
                "themes?select=id,title,position&subject_id=eq.${enc(subjectId)}" +
                    "&status=eq.published&order=position.asc"
            )
        } catch (e: IOException) {
            val message = e.message.orEmpty()
            if (message.startsWith("Supabase HTTP 404") || message.startsWith("Supabase HTTP 400")) {
                themesSupported = false
                JSONArray()
            } else {
                throw e
            }
        }
    }

    /** Faux dès qu'on sait que la table `levels` n'a pas (encore) les colonnes cycle / icon_key / tagline. */
    @Volatile
    private var levelExtrasSupported = true

    /** Classes et séries actives publiées par l'admin (Tle A1, Tle A2, Tle C…). */
    suspend fun loadLevels(): List<LevelInfo> = withContext(Dispatchers.IO) {
        val filter = "&is_active=eq.true&order=position.asc"
        val rows = if (levelExtrasSupported) {
            try {
                getArray("levels?select=id,label,position,cycle,icon_key,tagline$filter")
            } catch (e: IOException) {
                if (e.message?.startsWith("Supabase HTTP 400") == true) {
                    levelExtrasSupported = false
                    getArray("levels?select=id,label,position$filter")
                } else {
                    throw e
                }
            }
        } else {
            getArray("levels?select=id,label,position$filter")
        }

        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val id = row.getString("id")
                add(
                    LevelInfo(
                        id = id,
                        label = row.textOrNull("label") ?: id,
                        // Repli d'affichage uniquement : le vrai groupe vient de la colonne `cycle` de l'admin
                        cycle = row.textOrNull("cycle") ?: if (id.startsWith("TLE", ignoreCase = true)) "BAC" else "BEPC",
                        iconKey = row.textOrNull("icon_key"),
                        tagline = row.textOrNull("tagline"),
                        position = row.optInt("position", i)
                    )
                )
            }
        }
    }

    private fun JSONObject.textOrNull(key: String): String? =
        optString(key).takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }

    /** Colonnes que la table `capsules` sait servir : 2 = kind + session_id, 1 = kind, 0 = rien (HTTP 400 = colonne inconnue). */
    @Volatile
    private var capsuleColumnsLevel = 2

    /**
     * Charge les blocs d'une leçon. On demande d'abord `kind` et `session_id` ; si le back-end ne les
     * connaît pas encore (HTTP 400), on descend d'un cran sans casser l'écran.
     */
    private fun loadCapsuleRows(chapterId: String): JSONArray {
        val filter = "&chapter_id=eq.${enc(chapterId)}&order=position.asc"
        while (true) {
            val columns = when (capsuleColumnsLevel) {
                2 -> "id,title,body,minutes,position,kind,session_id"
                1 -> "id,title,body,minutes,position,kind"
                else -> "id,title,body,minutes,position"
            }
            try {
                return getArray("capsules?select=$columns$filter")
            } catch (e: IOException) {
                if (e.message?.startsWith("Supabase HTTP 400") == true && capsuleColumnsLevel > 0) {
                    capsuleColumnsLevel--
                } else {
                    throw e
                }
            }
        }
    }

    /** Séances d'une leçon. Table facultative : absente (404) ou non migrée (400) = aucune séance, rien ne casse. */
    @Volatile
    private var sessionsSupported = true

    private fun loadSessions(chapterId: String): List<LearningSession> {
        if (!sessionsSupported) return emptyList()
        return try {
            val rows = getArray(
                "lesson_sessions?select=id,title,position&chapter_id=eq.${enc(chapterId)}&order=position.asc"
            )
            List(rows.length()) { i ->
                val row = rows.getJSONObject(i)
                LearningSession(id = row.getString("id"), title = row.optString("title", "").trim())
            }
        } catch (e: IOException) {
            val message = e.message.orEmpty()
            if (message.startsWith("Supabase HTTP 404") || message.startsWith("Supabase HTTP 400")) {
                sessionsSupported = false
                emptyList()
            } else {
                throw e
            }
        }
    }

    /** Faux dès qu'on sait que la table `cards` n'a pas (encore) la colonne `correct_indices`. */
    @Volatile
    private var cardMultiAnswerSupported = true

    private fun readIndices(array: JSONArray?): List<Int> =
        if (array == null) emptyList() else List(array.length()) { array.optInt(it, -1) }.filter { it >= 0 }

    /** Cartes d'un chapitre ; repli sans `correct_indices` si le back-end ne la connaît pas (HTTP 400). */
    private fun loadCardRows(chapterId: String): JSONArray {
        val base = "cards?select=id,capsule_id,kind,front,back,choices,correct_index,explanation,position"
        val filter = "&chapter_id=eq.${enc(chapterId)}&order=position.asc"
        if (cardMultiAnswerSupported) {
            try {
                return getArray("$base,correct_indices$filter")
            } catch (e: IOException) {
                if (e.message?.startsWith("Supabase HTTP 400") == true) {
                    cardMultiAnswerSupported = false
                } else {
                    throw e
                }
            }
        }
        return getArray("$base$filter")
    }

    private fun loadCards(chapterId: String): List<LearningCard> {
        val rows = loadCardRows(chapterId)

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
                        },
                        correctIndices = readIndices(row.optJSONArray("correct_indices"))
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
