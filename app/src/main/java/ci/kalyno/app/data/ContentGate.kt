package ci.kalyno.app.data

import android.content.Context
import ci.kalyno.app.ui.explorer.LearningContentCache

/**
 * Évite de retélécharger tout le programme à chaque rafraîchissement.
 *
 * Supabase tient un numéro de version du contenu (table content_meta) que la base augmente toute seule
 * à chaque modification de l'admin. Ici :
 * - version inchangée et contenu en cache : aucun téléchargement (une seule petite requête) ;
 * - version différente (ou première fois) : téléchargement complet, puis la version est mémorisée.
 *
 * Si content_meta n'existe pas encore (SQL pas lancé), l'ancien comportement est conservé, mais le contenu
 * n'est pas retéléchargé plus d'une fois par [FALLBACK_MIN_INTERVAL_MS].
 */
class ContentGate(context: Context) {

    class Loaded(val subjects: List<LearningSubject>, val changed: Boolean)

    private val remote = SupabaseLearningRepository()
    private val cache = LearningContentCache(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("kalyno_content_gate", Context.MODE_PRIVATE)

    suspend fun load(levelId: String): Loaded {
        val now = System.currentTimeMillis()
        val cached = cache.read(levelId)?.takeIf { it.isNotEmpty() }
        val version = remote.loadContentVersion() // lue AVANT le téléchargement : une modification en cours sera reprise au tour suivant

        if (cached != null) {
            val known = prefs.getLong(versionKey(levelId), NONE)
            if (version != null && version == known) return Loaded(cached, changed = false)
            val fetchedAt = prefs.getLong(fetchedKey(levelId), 0L)
            if (version == null && now - fetchedAt < FALLBACK_MIN_INTERVAL_MS) return Loaded(cached, changed = false)
        }

        val fresh = remote.loadPublishedContent(levelId)
        cache.write(levelId, fresh)
        prefs.edit()
            .putLong(versionKey(levelId), version ?: NONE)
            .putLong(fetchedKey(levelId), now)
            .apply()
        return Loaded(fresh, changed = true)
    }

    private fun versionKey(levelId: String) = "v$FORMAT:version:$levelId"
    private fun fetchedKey(levelId: String) = "v$FORMAT:fetched:$levelId"

    private companion object {
        const val NONE = -1L
        /** À changer si le format du cache évolue : force un nouveau téléchargement. */
        const val FORMAT = 1
        const val FALLBACK_MIN_INTERVAL_MS = 10 * 60 * 1000L
    }
}
