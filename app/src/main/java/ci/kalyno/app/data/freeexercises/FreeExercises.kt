package ci.kalyno.app.data.freeexercises

import ci.kalyno.app.data.LearningCard
import ci.kalyno.app.data.dashboard.SupabaseHttp
import org.json.JSONArray
import java.net.URLEncoder

/** Un exercice libre publie par l'admin : une carte + une etiquette facultative (theme libre). */
data class FreeExercise(val card: LearningCard, val label: String?)

/**
 * Exercices libres : independants des disciplines, chapitres et lecons.
 * Ils sont ranges dans Room sous une matiere technique par classe (`libre_<classe>`), sans lecon :
 * ils ne dependent donc d'aucune progression dans les cours.
 */
object FreeExercises {
    const val SUBJECT_PREFIX = "libre_"
    const val SUBJECT_NAME = "Exercices libres"

    fun subjectId(levelId: String): String = SUBJECT_PREFIX + levelId

    fun isFreeSubject(subjectId: String): Boolean = subjectId.startsWith(SUBJECT_PREFIX)

    /**
     * Exercices publies pour [levelId] ou pour toutes les classes (level_id vide), dans l'ordre de l'admin.
     * Bloquant : a appeler hors du thread principal. Leve une exception hors ligne ou si la table n'existe pas encore.
     */
    fun fetch(levelId: String): List<FreeExercise> {
        val level = URLEncoder.encode(levelId, "UTF-8")
        val rows = JSONArray(
            SupabaseHttp.get(
                "free_exercises?select=id,label,kind,front,back,choices,correct_index,explanation,position" +
                    "&is_published=eq.true&or=(level_id.is.null,level_id.eq.$level)&order=position.asc"
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val rawChoices = row.optJSONArray("choices")
                val choices = if (rawChoices == null) emptyList() else List(rawChoices.length()) { rawChoices.optString(it) }
                add(
                    FreeExercise(
                        card = LearningCard(
                            id = row.getString("id"),
                            capsuleId = null,
                            kind = row.optString("kind", "flashcard"),
                            front = row.optString("front", ""),
                            back = if (row.isNull("back")) null else row.optString("back"),
                            choices = choices,
                            correctIndex = if (row.isNull("correct_index")) null else row.optInt("correct_index"),
                            explanation = if (row.isNull("explanation")) null else row.optString("explanation")
                        ),
                        label = if (row.isNull("label")) null else row.optString("label").trim().ifEmpty { null }
                    )
                )
            }
        }
    }
}
