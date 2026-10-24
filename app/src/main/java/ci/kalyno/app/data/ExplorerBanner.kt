package ci.kalyno.app.data

/**
 * Bannière « à la une » publiée par l'admin (table facultative `explorer_banners`).
 * Colonnes attendues : id, title, subtitle, button_label, chapter_id, level_id (vide = toutes
 * les classes), position, is_active. Si la table n'existe pas, aucune bannière n'est affichée.
 */
data class ExplorerBanner(
    val id: String,
    val title: String,
    val subtitle: String?,
    val buttonLabel: String?,
    /** Chapitre ouvert au toucher ; null = bannière purement informative. */
    val chapterId: String?
)
