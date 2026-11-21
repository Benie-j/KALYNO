package ci.kalyno.app.data.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Vérifie strictement la réponse de la fonction Edge. Une réponse malformée ne devient jamais une
 * correction : le résultat est null et l'appelant traite cela comme une erreur à réessayer.
 */
object GeminiResultParser {

    /** Enveloppe `{ok:true, kind:"grade", result:{...}}` -> correction, ou null si invalide. */
    fun parseGradeEnvelope(body: String): GradeResult? = try {
        val root = JSONObject(body)
        if (!root.optBoolean("ok", false) || root.optString("kind") != "grade") null
        else parseGrade(root.optJSONObject("result"))
    } catch (e: JSONException) {
        null
    }

    fun parseGrade(o: JSONObject?): GradeResult? {
        o ?: return null
        val verdict = when (o.optString("verdict")) {
            "CORRECT" -> GeminiVerdict.CORRECT
            "PARTIAL" -> GeminiVerdict.PARTIAL
            "INCORRECT" -> GeminiVerdict.INCORRECT
            else -> return null
        }
        val score = percent(o, "score") ?: return null
        val confidence = percent(o, "confidence") ?: return null
        val feedback = o.optString("feedback").trim()
        if (feedback.isEmpty()) return null
        return GradeResult(
            verdict = verdict,
            score = score,
            confidence = confidence,
            feedback = feedback,
            strengths = strings(o.optJSONArray("strengths")),
            errors = strings(o.optJSONArray("errors")),
            missingElements = strings(o.optJSONArray("missing_elements")),
            correctedAnswer = o.optString("corrected_answer").trim()
        )
    }

    /** `{ok:true, kind:"assist", result:{text}}` -> texte, ou null. */
    fun parseAssistEnvelope(body: String): String? = try {
        val root = JSONObject(body)
        if (!root.optBoolean("ok", false) || root.optString("kind") != "assist") null
        else root.optJSONObject("result")?.optString("text")?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: JSONException) {
        null
    }

    /** `{ok:false, error:{code,message,retryable}}` -> exception ; null si le corps n'a pas cette forme. */
    fun parseError(body: String): GeminiException? = try {
        val e = JSONObject(body).optJSONObject("error")
        if (e == null) null
        else GeminiException(
            code = e.optString("code", "unknown"),
            message = e.optString("message").ifBlank { "Le service IA a échoué." },
            retryable = e.optBoolean("retryable", true)
        )
    } catch (ex: JSONException) {
        null
    }

    /** Entier 0..100 obligatoire : absent, non numérique ou hors bornes = invalide. */
    private fun percent(o: JSONObject, key: String): Int? {
        if (!o.has(key) || o.isNull(key)) return null
        val v = o.opt(key) as? Number ?: return null
        val d = v.toDouble()
        if (!d.isFinite() || d < 0.0 || d > 100.0) return null
        return Math.round(d).toInt()
    }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList()
        else (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf { s -> s.isNotEmpty() } }
}
