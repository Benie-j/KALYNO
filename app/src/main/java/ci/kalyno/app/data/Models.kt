package ci.kalyno.app.data

/**
 * Une question du premier défi, publiée par l'admin. Même moule que les exercices du Dojo :
 * une bonne réponse, plusieurs bonnes réponses ou saisie libre.
 */
data class Question(
    val id: String,
    val prompt: String,
    val kind: ci.kalyno.app.data.dojo.ExerciseKind,
    val choices: List<String>,
    val correctIndices: Set<Int>,
    /** Saisie libre : réponses acceptées (variantes séparées par « | » côté admin). */
    val acceptedAnswers: List<String> = emptyList(),
    val hint: String? = null,
    val explanation: String? = null
)

data class Challenge(
    val id: String,
    val levelId: String,
    val title: String,
    val questions: List<Question>
)

/** Classe ou série telle que publiée par l'admin (table `levels`). Rien n'est écrit en dur dans l'app. */
data class LevelInfo(
    val id: String,
    val label: String,
    /** Groupe d'examen publié par l'admin ("BEPC", "BAC"…) ; vide si l'admin n'en définit pas. */
    val cycle: String,
    /** Clé d'icône vectorielle (voir KalynoIcons) ; null = icône par défaut du groupe. */
    val iconKey: String?,
    /** Courte description facultative (utile pour distinguer Tle A1 et Tle A2). */
    val tagline: String?,
    val position: Int
)

object Levels {
    @Volatile
    private var known: Map<String, LevelInfo> = emptyMap()

    /** Appelé par le catalogue Room dès que les classes sont connues. */
    fun register(levels: Collection<LevelInfo>) {
        known = levels.associateBy { it.id }
    }

    fun info(id: String): LevelInfo? = known[id]

    /** Libellé complet ("BAC · Tle A1"). Avant le premier chargement, on dérive un libellé lisible de l'identifiant. */
    fun label(id: String): String {
        val info = known[id] ?: return prettify(id)
        return if (info.cycle.isBlank()) info.label else "${info.cycle} · ${info.label}"
    }

    /** "TLE_A1" -> "Tle A1" ; "3E" -> "3E". Simple repli d'affichage, sans liste de classes. */
    private fun prettify(id: String): String {
        val parts = id.split('_').filter { it.isNotBlank() }
        if (parts.isEmpty()) return id
        return parts.mapIndexed { index, part ->
            if (index == 0 && part.length > 2 && part.any { it.isLetter() }) {
                part.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                part.uppercase()
            }
        }.joinToString(" ")
    }
}
