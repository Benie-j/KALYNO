package ci.kalyno.app.data.faq

import ci.kalyno.app.data.dashboard.SupabaseHttp
import org.json.JSONArray
import java.io.IOException
import java.time.OffsetDateTime

/**
 * Lecture de la table `faq_items` de Supabase (clé publique + règle RLS « lecture pour tous »).
 * Aucune écriture : la FAQ se gère uniquement depuis l'admin.
 */
internal object FaqRemoteSource {

    private const val PATH =
        "faq_items?select=id,category,question,answer,order_index,updated_at&order=order_index.asc,question.asc"

    /** @throws IOException réseau coupé, serveur indisponible ou réponse illisible. */
    fun fetch(): List<FaqEntity> {
        val body = SupabaseHttp.get(PATH)
        return try {
            parse(JSONArray(body))
        } catch (e: org.json.JSONException) {
            throw IOException("Réponse FAQ illisible : ${e.message}", e)
        }
    }

    private fun parse(array: JSONArray): List<FaqEntity> {
        val items = ArrayList<FaqEntity>(array.length())
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val id = row.optString("id").trim()
            val question = row.optString("question").trim()
            val answer = row.optString("answer").trim()
            // Une ligne sans id, question ou réponse n'a rien à montrer : on l'ignore plutôt que de planter.
            if (id.isEmpty() || question.isEmpty() || answer.isEmpty()) continue
            items += FaqEntity(
                id = id,
                category = row.optString("category").trim().ifEmpty { DEFAULT_CATEGORY },
                question = question,
                answer = answer,
                orderIndex = row.optInt("order_index", 0),
                updatedAt = parseInstant(row.optString("updated_at"))
            )
        }
        return items
    }

    /** `timestamptz` Postgres (« 2026-11-01T10:00:00.123456+00:00 ») -> epoch ms ; 0 si absent ou illisible. */
    private fun parseInstant(raw: String): Long =
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrDefault(0L)

    const val DEFAULT_CATEGORY = "Général"
}
