package ci.kalyno.app.ui.icons

/**
 * Vocabulaire d'icônes vectorielles (fichiers assets/icons/<nom>.svg).
 *
 * L'admin choisit une clé (colonne `icon_key`) ; l'app ne fait que la traduire en icône.
 * Clé absente ou inconnue : icône par défaut, jamais d'emoji.
 */
private val KNOWN_ICONS = setOf(
    "backpack", "graduation-cap", "book-open", "pen-line", "calculator", "flask-conical",
    "globe", "brain", "layers", "target", "lightbulb", "rocket", "trophy", "dumbbell"
)

fun iconAssetFor(key: String?, fallback: String = "book-open"): String {
    val normalized = key?.trim()?.lowercase()
    return if (normalized != null && normalized in KNOWN_ICONS) normalized else fallback
}

/** Icône par défaut d'une classe quand l'admin n'en a pas choisi : selon le groupe d'examen publié. */
fun levelIconFallback(cycle: String): String =
    if (cycle.equals("BEPC", ignoreCase = true)) "backpack" else "graduation-cap"
