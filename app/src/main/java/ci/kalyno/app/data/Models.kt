package ci.kalyno.app.data

data class Question(
    val id: String,
    val prompt: String,
    val choices: List<String>,
    val correctIndex: Int,
    val explanation: String? = null
)

data class Challenge(
    val id: String,
    val levelId: String,
    val title: String,
    val questions: List<Question>
)

object Levels {
    const val BEPC = "3E"
    val bacSeries = listOf("TLE_A" to "Tle A", "TLE_C" to "Tle C", "TLE_D" to "Tle D")

    fun label(id: String): String = when (id) {
        BEPC -> "BEPC · 3e"
        else -> bacSeries.firstOrNull { it.first == id }?.let { "BAC · ${it.second}" } ?: id
    }
}
