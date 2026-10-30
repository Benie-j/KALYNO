package ci.kalyno.app.ui.progress

import ci.kalyno.app.data.progress.DojoAttemptEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Une ligne de la chronologie : un titre de jour ou une tentative. */
sealed interface HistoryRow {
    val key: String

    data class Day(val epochDay: Long, val label: String) : HistoryRow {
        override val key: String get() = "day-$epochDay"
    }

    data class Attempt(val item: AttemptUi) : HistoryRow {
        override val key: String get() = "attempt-${item.id}"
    }
}

/** Tentative prête à afficher : tous les textes sont déjà calculés, l'écran ne décide de rien. */
data class AttemptUi(
    val id: Long,
    val lessonTitle: String,
    val subjectName: String,
    val success: Boolean,
    /** « Réussi à la 3e tentative · 2 échecs avant » ; null si le rang est inconnu (historique ancien). */
    val attemptLine: String?,
    val time: String,
    val question: String,
    /** null = non conservée (historique antérieur à la mise à jour). */
    val studentAnswer: String?,
    val referenceAnswer: String,
    val isLegacy: Boolean
)

/** Textes et regroupement de l'historique (fonctions pures, testées sans Android). */
internal object HistoryFormat {

    private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> date.format(DAY_FORMAT)
    }

    fun timeLabel(epochMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(TIME_FORMAT)

    /**
     * Succès : « Réussi du premier coup » ou « Réussi à la 3e tentative · 2 échecs avant ».
     * Échec : « Tentative n°2 ». Rang inconnu (0) : aucune ligne plutôt qu'un chiffre inventé.
     */
    fun attemptLine(success: Boolean, rank: Int): String? = when {
        rank <= 0 -> null
        !success -> "Tentative n°$rank"
        rank == 1 -> "Réussi du premier coup"
        else -> {
            val failures = rank - 1
            "Réussi à la ${rank}e tentative · $failures ${if (failures == 1) "échec" else "échecs"} avant"
        }
    }

    fun toUi(a: DojoAttemptEntity, zone: ZoneId) = AttemptUi(
        id = a.id,
        lessonTitle = a.lessonTitle,
        subjectName = a.subjectName,
        success = a.isSuccess,
        attemptLine = attemptLine(a.isSuccess, a.attemptNumber),
        time = timeLabel(a.attemptedAt, zone),
        question = a.question,
        studentAnswer = a.studentAnswer?.takeIf { it.isNotBlank() },
        referenceAnswer = a.referenceAnswer,
        isLegacy = a.isLegacy
    )

    /** Regroupe une liste déjà triée du plus récent au plus ancien en « titre de jour + tentatives ». */
    fun group(
        attempts: List<DojoAttemptEntity>,
        today: LocalDate,
        zone: ZoneId
    ): List<HistoryRow> {
        val rows = ArrayList<HistoryRow>(attempts.size + 8)
        var currentDay: LocalDate? = null
        for (a in attempts) {
            val day = Instant.ofEpochMilli(a.attemptedAt).atZone(zone).toLocalDate()
            if (day != currentDay) {
                currentDay = day
                rows += HistoryRow.Day(day.toEpochDay(), dayLabel(day, today))
            }
            rows += HistoryRow.Attempt(toUi(a, zone))
        }
        return rows
    }
}
