package ci.kalyno.app.ui.explorer

import ci.kalyno.app.data.LearningCapsule
import ci.kalyno.app.data.LearningSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisciplineProgressTest {

    private fun block(id: String, sessionId: String?) =
        LearningCapsule(id = id, title = id, body = "", minutes = 3, cards = emptyList(), kind = "text", sessionId = sessionId)

    private fun lesson(
        id: String,
        subject: String = "maths",
        sessions: List<LearningSession> = emptyList(),
        capsules: List<LearningCapsule> = emptyList()
    ) = ExplorerChapter(
        id = id, subjectId = subject, subjectName = subject, subjectEmoji = "", title = id,
        minutes = 5, discovered = 0, capsules = capsules, items = emptyList(), sessions = sessions
    )

    @Test fun anciennesLeconsCompteesParLecon() {
        val result = disciplineProgressOf(
            chapters = listOf(lesson("l1"), lesson("l2")),
            completedLessons = setOf("l1"),
            completedSessions = emptySet()
        )
        assertEquals(DisciplineProgress(1, 2), result["maths"])
        assertEquals(0.5f, result.getValue("maths").fraction, 0.0001f)
    }

    @Test fun leconAvecSeancesCompteSesSeancesPubliees() {
        val chapter = lesson(
            id = "l1",
            sessions = listOf(
                LearningSession("s1", "Séance 1", isAvailable = true),
                LearningSession("s2", "Séance 2", isAvailable = true),
                LearningSession("s3", "Séance 3", isAvailable = false),
                LearningSession("s4", "Séance 4 vide", isAvailable = true)
            ),
            capsules = listOf(block("b1", "s1"), block("b2", "s2"), block("b3", "s3"))
        )
        val result = disciplineProgressOf(listOf(chapter), emptySet(), setOf("s1", "s3"))
        // s3 non publiée et s4 sans bloc ne comptent pas : 1 séance terminée sur 2
        assertEquals(DisciplineProgress(1, 2), result["maths"])
    }

    @Test fun leconTermineeNeValidePasSesSeances() {
        val chapter = lesson(
            id = "l1",
            sessions = listOf(LearningSession("s1", "Séance 1")),
            capsules = listOf(block("b1", "s1"))
        )
        val result = disciplineProgressOf(listOf(chapter), setOf("l1"), emptySet())
        assertEquals(DisciplineProgress(0, 1), result["maths"])
    }

    @Test fun progressionParDiscipline() {
        val result = disciplineProgressOf(
            chapters = listOf(lesson("a1", "maths"), lesson("b1", "svt"), lesson("b2", "svt")),
            completedLessons = setOf("a1", "b2"),
            completedSessions = emptySet()
        )
        assertEquals(1f, result.getValue("maths").fraction, 0.0001f)
        assertEquals(0.5f, result.getValue("svt").fraction, 0.0001f)
    }

    @Test fun rienAFaireDonneZeroPourCent() {
        assertTrue(disciplineProgressOf(emptyList(), emptySet(), emptySet()).isEmpty())
        assertEquals(0f, DisciplineProgress(0, 0).fraction, 0.0001f)
    }
}
