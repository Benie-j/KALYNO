package ci.kalyno.app.data.dojo

/** Les trois types d'exercices que l'admin peut publier. */
enum class ExerciseKind {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    FREE_TEXT;

    companion object {
        private val SINGLE_NAMES = setOf("SINGLE_CHOICE", "SINGLE", "QCU", "QCM", "MCQ", "CHOICE")
        private val MULTIPLE_NAMES = setOf(
            "MULTIPLE_CHOICE", "MULTIPLE", "MULTI_CHOICE", "QCM_MULTIPLE", "MCQ_MULTIPLE", "QCM_MULTI"
        )
        private val FREE_NAMES = setOf("FREE_TEXT", "TEXT", "OPEN", "INPUT", "SAISIE", "SAISIE_LIBRE")

        /**
         * Déduit le type d'exercice : le champ `kind` de l'admin d'abord, les données ensuite.
         * Un QCM sans choix ou sans bonne réponse exploitable devient une saisie libre.
         */
        fun resolve(rawKind: String?, choices: List<String>, correctIndices: List<Int>): ExerciseKind {
            val name = rawKind.orEmpty().trim().uppercase().replace('-', '_').replace(' ', '_')
            val hasChoices = choices.size >= 2 && correctIndices.isNotEmpty()
            return when {
                name in FREE_NAMES -> FREE_TEXT
                !hasChoices -> FREE_TEXT
                name in MULTIPLE_NAMES -> MULTIPLE_CHOICE
                name in SINGLE_NAMES -> SINGLE_CHOICE
                correctIndices.size > 1 -> MULTIPLE_CHOICE
                else -> SINGLE_CHOICE
            }
        }
    }
}

/** Bonnes réponses : la liste multiple de l'admin si elle existe, sinon l'indice unique. */
fun mergeCorrectIndices(multi: List<Int>, single: Int?): List<Int> =
    (if (multi.isNotEmpty()) multi else listOfNotNull(single))
        .filter { it >= 0 }
        .distinct()
        .sorted()

/** Indices enregistrés dans Room sous la forme "0,2". */
fun parseIndexList(csv: String?): List<Int> =
    csv.orEmpty().split(',', ';', ' ').mapNotNull { it.trim().toIntOrNull() }

/** Texte de la bonne réponse (affiché à la correction et enregistré dans Room). */
fun answerText(choices: List<String>, indices: List<Int>, back: String?): String? {
    val picked = indices.mapNotNull { choices.getOrNull(it) }
    return when {
        picked.size > 1 -> picked.joinToString(" · ")
        picked.size == 1 -> picked.first()
        else -> back
    }
}

/**
 * Calculatrice proposée en évidence quand l'énoncé contient du calcul (chiffres + opérateurs,
 * racines, puissances, fonctions). Détection par le contenu : aucune matière écrite en dur.
 */
object CalculatorPolicy {
    private val MATH = Regex(
        "[√π²³∑∫]|\\d\\s*[+×÷*/^=%]\\s*[\\d(a-z]|\\b(sin|cos|tan|ln|log)\\b",
        RegexOption.IGNORE_CASE
    )

    fun suggested(texts: List<String>): Boolean = MATH.containsMatchIn(texts.joinToString(" "))
}
