package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.srs.ReviewRating
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.Normalizer

/*
 * Exercice « Tableau à compléter » (kind = "table").
 *
 * Données (aucune table Supabase en plus) :
 *  - `front`           : JSON {"type":"kalyno_table_v1","statement":"…","rows":[[{"type":"text","value":"…"},{"type":"blank","key":"r2c2"}]]}
 *                        = ce que l'élève voit. Il ne contient AUCUNE réponse.
 *  - `admin_reference` : JSON {"type":"kalyno_table_answers_v1","answers":{"r2c2":["réponse","variante"]}}
 *                        = réponses attendues, lues seulement pour corriger (jamais affichées avant la validation).
 *
 * La correction est locale et déterministe, cellule par cellule : ni le correcteur sémantique E5 ni l'IA n'y touchent.
 */

/** Une cellule du tableau tel que l'élève le voit. */
sealed interface TableCell {
    /** Cellule fixe : contenu visible, jamais modifiable. */
    data class Fixed(val text: String) : TableCell

    /** Cellule à compléter : un champ de saisie identifié par [key] (ex. « r2c3 » = ligne 2, colonne 3). */
    data class Blank(val key: String) : TableCell
}

/** Le tableau côté élève : énoncé + cellules. Ne contient jamais les réponses attendues. */
data class TableExercise(
    val statement: String,
    val rows: List<List<TableCell>>
) {
    /** Clés des cellules à compléter, dans l'ordre de lecture (ligne par ligne). */
    val blankKeys: List<String> = rows.flatMap { row -> row.filterIsInstance<TableCell.Blank>().map { it.key } }

    val columnCount: Int = rows.maxOfOrNull { it.size } ?: 0

    /** Longueur de texte à lire (sert au seuil « réponse trop rapide » du moteur FSRS). */
    val textLength: Int =
        statement.length + rows.sumOf { row -> row.sumOf { (it as? TableCell.Fixed)?.text?.length ?: 0 } }
}

/** Réponses attendues par cellule : la première est la réponse principale, les suivantes sont des variantes acceptées. */
data class TableAnswers(val byKey: Map<String, List<String>>)

enum class CellResult { CORRECT, WRONG, EMPTY }

enum class TableOutcome { GOOD, PARTIAL, BAD }

/** Résultat complet d'une validation. */
data class TableGrade(
    val cells: Map<String, CellResult>,
    val correct: Int,
    val total: Int
) {
    /** correct / total, entre 0 et 1 (0 si le tableau n'a aucune cellule à compléter). */
    val score: Double get() = if (total <= 0) 0.0 else correct.toDouble() / total

    /** Score arrondi à l'entier, pour l'affichage (75 pour 3 cellules sur 4). */
    val percent: Int get() = Math.round(score * 100.0).toInt()

    val wrong: Int get() = cells.values.count { it == CellResult.WRONG }
    val empty: Int get() = cells.values.count { it == CellResult.EMPTY }

    /** Tout juste = bonne ; rien de juste = mauvaise ; entre les deux = partielle. */
    val outcome: TableOutcome
        get() = when {
            total <= 0 || correct <= 0 -> TableOutcome.BAD
            correct >= total -> TableOutcome.GOOD
            else -> TableOutcome.PARTIAL
        }
}

/** Lecture des deux JSON stockés par l'admin. Un contenu illisible donne null : jamais une exception, jamais une saisie libre. */
object TableCodec {
    const val FRONT_TYPE = "kalyno_table_v1"
    const val ANSWERS_TYPE = "kalyno_table_answers_v1"

    /** Vrai si [front] a la forme d'un tableau (même invalide), pour ne pas afficher du JSON brut comme une question. */
    fun looksLikeTable(front: String?): Boolean = front.orEmpty().trimStart().startsWith("{") && front.orEmpty().contains(FRONT_TYPE)

    fun parseFront(raw: String?): TableExercise? {
        val text = raw?.trim().orEmpty()
        if (!text.startsWith("{")) return null
        return try {
            val root = JSONObject(text)
            if (root.optString("type") != FRONT_TYPE) return null
            val rowsJson = root.optJSONArray("rows") ?: return null
            if (rowsJson.length() == 0) return null
            val rows = ArrayList<List<TableCell>>(rowsJson.length())
            val seen = HashSet<String>()
            for (i in 0 until rowsJson.length()) {
                val rowJson = rowsJson.optJSONArray(i) ?: return null
                val cells = ArrayList<TableCell>(rowJson.length())
                for (j in 0 until rowJson.length()) {
                    val cell = rowJson.optJSONObject(j) ?: return null
                    when (cell.optString("type")) {
                        "text" -> cells.add(TableCell.Fixed(if (cell.isNull("value")) "" else cell.optString("value", "")))
                        "blank" -> {
                            val key = if (cell.isNull("key")) "" else cell.optString("key", "").trim()
                            // Une clé vide ou répétée rendrait deux champs indissociables : tableau refusé
                            if (key.isEmpty() || !seen.add(key)) return null
                            cells.add(TableCell.Blank(key))
                        }
                        else -> return null
                    }
                }
                if (cells.isEmpty()) return null
                rows.add(cells)
            }
            TableExercise(
                statement = if (root.isNull("statement")) "" else root.optString("statement", "").trim(),
                rows = rows
            )
        } catch (e: JSONException) {
            null
        }
    }

    fun parseAnswers(raw: String?): TableAnswers? {
        val text = raw?.trim().orEmpty()
        if (!text.startsWith("{")) return null
        return try {
            val root = JSONObject(text)
            if (root.optString("type") != ANSWERS_TYPE) return null
            val answers = root.optJSONObject("answers") ?: return null
            val map = LinkedHashMap<String, List<String>>()
            for (key in answers.keys()) {
                val value = answers.opt(key)
                val list: List<String> = when (value) {
                    is JSONArray -> List(value.length()) { value.optString(it, "") }
                    is String -> listOf(value)
                    else -> emptyList()
                }
                map[key] = list.map { it.trim() }.filter { it.isNotEmpty() }
            }
            TableAnswers(map)
        } catch (e: JSONException) {
            null
        }
    }

    /** Un tableau n'est jouable que s'il a au moins une cellule à compléter et une réponse attendue pour CHACUNE. */
    fun isPlayable(table: TableExercise, answers: TableAnswers): Boolean =
        table.blankKeys.isNotEmpty() && table.blankKeys.all { !answers.byKey[it].isNullOrEmpty() }

    /** Texte court enregistré comme « réponse » de la carte dans Room (réponses principales, dans l'ordre de lecture). */
    fun summaryAnswer(table: TableExercise, answers: TableAnswers): String =
        table.blankKeys.mapNotNull { answers.byKey[it]?.firstOrNull() }.joinToString(" · ")

    /** Texte de la carte pour les écrans qui affichent l'énoncé seul (historique, signalement) : jamais le JSON. */
    fun statementOf(front: String): String = parseFront(front)?.statement?.takeIf { it.isNotBlank() } ?: "Tableau à compléter"
}

/** Correction déterministe. Aucun modèle, aucune approximation : seule une égalité après normalisation est une réponse juste. */
object TableGrader {

    private val MARKS_SPACES = Regex("[\\s\\p{Z}]+")
    private val DECIMAL_COMMA = Regex("(?<=\\d),(?=\\d)")

    /**
     * Mêmes règles que `normalizeAnswer` de l'administration : espaces de bord retirés, espaces multiples réduits à un,
     * majuscules ignorées, apostrophes et tirets typographiques unifiés, virgule décimale = point, ponctuation finale ignorée.
     * Les accents restent significatifs (« ou » ≠ « où ») et aucune distance d'édition n'est appliquée.
     */
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC)
            .lowercase()
            .replace('\u2019', '\'')
            .replace('\u2018', '\'')
            .replace('\u02BC', '\'')
            .replace('\u2212', '-')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace(DECIMAL_COMMA, ".")
            .replace(MARKS_SPACES, " ")
            .trim()
            .trimEnd('.', '!', '?', ';', ':')
            .trim()

    fun cellResult(typed: String?, accepted: List<String>): CellResult {
        val value = normalize(typed.orEmpty())
        if (value.isEmpty()) return CellResult.EMPTY // une cellule vide n'est jamais juste
        return if (accepted.any { normalize(it) == value }) CellResult.CORRECT else CellResult.WRONG
    }

    /** Ne vérifie QUE les cellules à compléter ; les saisies hors de ces clés sont ignorées. */
    fun grade(table: TableExercise, answers: TableAnswers, typed: Map<String, String>): TableGrade {
        val cells = LinkedHashMap<String, CellResult>()
        for (key in table.blankKeys) {
            cells[key] = cellResult(typed[key], answers.byKey[key].orEmpty())
        }
        return TableGrade(cells = cells, correct = cells.values.count { it == CellResult.CORRECT }, total = cells.size)
    }

    /** Note FSRS : bonne = comme une bonne réponse, partielle = « hésité », mauvaise = « oublié ». */
    fun verdict(grade: TableGrade, ctx: AnswerContext): Verdict = when (grade.outcome) {
        TableOutcome.GOOD -> Verdict(correct = true, rating = AnswerGrader.goodRating(ctx))
        TableOutcome.PARTIAL -> Verdict(correct = false, rating = ReviewRating.HESITE, partial = true)
        TableOutcome.BAD -> Verdict(correct = false, rating = ReviewRating.OUBLIE)
    }
}

/**
 * Trace d'une tentative dans `dojo_attempts` sans nouvelle colonne : `studentAnswer` reçoit ce JSON (les anciennes lignes
 * restent de simples chaînes), `referenceAnswer` reçoit un texte lisible. L'écran d'historique relit ce JSON via [describe].
 */
object TableAttempt {
    const val TYPE = "kalyno_table_attempt_v1"
    const val KIND_NAME = "TABLE"

    fun encode(typed: Map<String, String>, grade: TableGrade): String {
        val answers = JSONObject()
        val results = JSONObject()
        for ((key, result) in grade.cells) {
            answers.put(key, typed[key].orEmpty().trim())
            results.put(key, result.name)
        }
        return JSONObject()
            .put("type", TYPE)
            .put("answers", answers)
            .put("results", results)
            .put("correct", grade.correct)
            .put("total", grade.total)
            .toString()
    }

    /** Réponses attendues, lisibles (« Ligne 3, colonne 2 : Variable / variable »). */
    fun expectedText(table: TableExercise, answers: TableAnswers): String =
        table.blankKeys.joinToString("\n") { key ->
            "${positionLabel(key)} : ${answers.byKey[key].orEmpty().joinToString(" / ")}"
        }

    /** Texte lisible d'une tentative enregistrée ; null si [raw] n'est pas une tentative de tableau (ancienne ligne). */
    fun describe(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (!text.startsWith("{") || !text.contains(TYPE)) return null
        return try {
            val root = JSONObject(text)
            if (root.optString("type") != TYPE) return null
            val answers = root.optJSONObject("answers") ?: return null
            val results = root.optJSONObject("results")
            val keys = answers.keys().asSequence().toList().sortedWith(compareBy({ rowOf(it) }, { colOf(it) }, { it }))
            val lines = keys.map { key ->
                val typed = answers.optString(key, "").ifBlank { "(non renseignée)" }
                val mark = when (results?.optString(key)) {
                    CellResult.CORRECT.name -> "✓"
                    CellResult.WRONG.name -> "✗"
                    else -> "–"
                }
                "${positionLabel(key)} : $typed $mark"
            }
            val summary = "${root.optInt("correct")} / ${root.optInt("total")} cellules justes"
            (lines + summary).joinToString("\n")
        } catch (e: JSONException) {
            null
        }
    }

    private val KEY = Regex("^r(\\d+)c(\\d+)$")
    private fun rowOf(key: String): Int = KEY.matchEntire(key)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
    private fun colOf(key: String): Int = KEY.matchEntire(key)?.groupValues?.get(2)?.toIntOrNull() ?: Int.MAX_VALUE

    fun positionLabel(key: String): String {
        val m = KEY.matchEntire(key) ?: return key
        return "Ligne ${m.groupValues[1]}, colonne ${m.groupValues[2]}"
    }
}

/**
 * Réponse « texte » d'une carte au moment de la synchronisation (colonne Room `reponse`).
 * QCM et saisie libre : comportement inchangé ([answerText]). Tableau : réponses principales ; null si le tableau ou ses
 * réponses sont illisibles, la carte est alors ignorée plutôt que jouée comme une saisie libre.
 */
fun cardAnswerText(
    kind: String?,
    front: String,
    choices: List<String>,
    correctIndices: List<Int>,
    back: String?,
    adminReference: String?
): String? {
    if (ExerciseKind.resolve(kind, choices, correctIndices) != ExerciseKind.TABLE) {
        return answerText(choices, correctIndices, back)
    }
    val table = TableCodec.parseFront(front) ?: return null
    val answers = TableCodec.parseAnswers(adminReference) ?: return null
    return if (TableCodec.isPlayable(table, answers)) TableCodec.summaryAnswer(table, answers) else null
}
