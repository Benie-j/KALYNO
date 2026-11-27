package ci.kalyno.app.data.lessons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {

    private val lesson = "L1"

    // Séance 1 : 2 exercices, séance 2 : 3 exercices, séance 3 : 1 exercice
    private val cards = listOf(
        CardLink("a1", "math", lesson, "S1"), CardLink("a2", "math", lesson, "S1"),
        CardLink("b1", "math", lesson, "S2"), CardLink("b2", "math", lesson, "S2"), CardLink("b3", "math", lesson, "S2"),
        CardLink("c1", "math", lesson, "S3")
    )

    private fun unlocked(sessions: Set<String> = emptySet(), lessons: Set<String> = emptySet(), introduced: Set<String> = emptySet(), all: List<CardLink> = cards) =
        SessionRules.unlockedCardIds(all, sessions, lessons, introduced)

    // ---- Scénario 1 : affichage
    @Test fun lessonWithThreeSessionsListsThreeAndSessionTwoShowsOnlyItsBlocks() {
        val inputs = listOf(
            SessionInput("S1", "Intro", 3, 2), SessionInput("S2", "Cœur", 4, 3), SessionInput("S3", "Bilan", 2, 1)
        )
        val entries = SessionOverview.entries(lesson, inputs, 0, 0, emptySet(), false)
        assertEquals(listOf("S1", "S2", "S3"), entries.map { it.id })
        assertEquals(listOf(1, 2, 3), entries.map { it.number })
        assertTrue(entries.all { it.status == SessionStatus.AVAILABLE })
        assertEquals(4, entries[1].blockCount) // la séance 2 ne compte que ses propres blocs
    }

    @Test fun emptySessionsAreNotListed() {
        val entries = SessionOverview.entries(lesson, listOf(SessionInput("S1", "A", 0, 0), SessionInput("S2", "B", 2, 0)), 0, 0, emptySet(), false)
        assertEquals(listOf("S2"), entries.map { it.id })
    }

    // ---- Scénario 2 : validation indépendante
    @Test fun completingSessionOneDoesNotCompleteOthersNorLesson() {
        val inputs = listOf(SessionInput("S1", "A", 1, 2), SessionInput("S2", "B", 1, 3), SessionInput("S3", "C", 1, 1))
        val entries = SessionOverview.entries(lesson, inputs, 0, 0, setOf("S1"), lessonCompleted = false)
        assertEquals(listOf(SessionStatus.COMPLETED, SessionStatus.AVAILABLE, SessionStatus.AVAILABLE), entries.map { it.status })
        assertEquals(1, SessionOverview.summary(entries).completed)
        assertEquals(3, SessionOverview.summary(entries).total)
    }

    // ---- Scénario 3 : exercices
    @Test fun onlySessionOneExercisesUnlockedAfterSessionOne() {
        assertEquals(setOf("a1", "a2"), unlocked(sessions = setOf("S1")))
    }

    @Test fun nothingUnlockedWithoutCompletedSession() {
        assertTrue(unlocked().isEmpty())
    }

    @Test fun sessionTwoUnlocksItsThreeOnly() {
        assertEquals(setOf("b1", "b2", "b3"), unlocked(sessions = setOf("S2")))
    }

    // ---- Scénario 7 : compatibilité
    @Test fun legacyCardsWithoutSessionFollowLessonCompletion() {
        val legacy = listOf(CardLink("o1", "math", lesson, null), CardLink("o2", "math", "L2", null))
        assertTrue(unlocked(all = legacy).isEmpty())
        assertEquals(setOf("o1"), unlocked(lessons = setOf(lesson), all = legacy))
    }

    @Test fun lessonCompletionDoesNotUnlockSessionCards() {
        assertTrue(unlocked(lessons = setOf(lesson)).isEmpty())
    }

    @Test fun alreadyIntroducedCardStaysPlayableWhenLessonWasCompleted() {
        assertEquals(setOf("b1"), unlocked(lessons = setOf(lesson), introduced = setOf("b1")))
        // introduite mais leçon non terminée : pas débloquée par cette règle
        assertTrue(unlocked(introduced = setOf("b1")).isEmpty())
    }

    @Test fun cardWithoutAnyLinkIsNeverAttributed() {
        val orphan = listOf(CardLink("x", "math", null, null))
        assertTrue(unlocked(sessions = setOf("S1"), lessons = setOf(lesson), all = orphan).isEmpty())
    }

    @Test fun unavailableSessionCardsAreNeverUnlocked() {
        val hidden = listOf(CardLink("h", "math", lesson, SessionRules.SESSION_UNAVAILABLE))
        assertTrue(unlocked(sessions = setOf(SessionRules.SESSION_UNAVAILABLE), lessons = setOf(lesson), introduced = setOf("h"), all = hidden).isEmpty())
    }

    @Test fun freeExercisesAlwaysUnlocked() {
        assertEquals(setOf("f"), unlocked(all = listOf(CardLink("f", "libre_maths", null, null))))
    }

    @Test fun cardSessionResolution() {
        val map = mapOf("cap1" to "S1", "capLoose" to null, "capUnknown" to "SX")
        val known = setOf("S1", "S2")
        assertEquals("S1", SessionRules.cardSessionId("cap1", map, known))
        assertNull(SessionRules.cardSessionId("capLoose", map, known))
        assertNull(SessionRules.cardSessionId("capUnknown", map, known))
        assertNull(SessionRules.cardSessionId(null, map, known))
        assertEquals(SessionRules.SESSION_UNAVAILABLE, SessionRules.cardSessionId("capMissing", map, known))
    }

    @Test fun looseBlocksFormGeneralEntryOnlyWhenLessonHasSessions() {
        val withSessions = SessionOverview.entries(lesson, listOf(SessionInput("S1", "A", 1, 0)), 2, 1, emptySet(), true)
        assertTrue(withSessions.first().isGeneral)
        assertTrue(withSessions.first().isCompleted)
        assertFalse(withSessions.last().isCompleted)
        assertTrue(SessionOverview.entries(lesson, emptyList(), 2, 1, emptySet(), false).isEmpty())
    }
}
