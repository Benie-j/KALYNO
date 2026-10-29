package ci.kalyno.app.ml.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticThresholdsLessonTest {

    @Test fun assouplitLesDeuxSeuils() {
        val relaxed = SemanticThresholds(valid = 0.75f, partial = 0.60f).relaxedForLesson()
        assertEquals(0.67f, relaxed.valid, 0.001f)
        assertEquals(0.52f, relaxed.partial, 0.001f)
    }

    @Test fun neDescendJamaisSousLesPlanchers() {
        val relaxed = SemanticThresholds(valid = 0.60f, partial = 0.45f).relaxedForLesson()
        assertTrue(relaxed.valid >= 0.55f)
        assertTrue(relaxed.partial >= 0.40f)
    }

    @Test fun partielResteStrictementSousLeSeuilValide() {
        val relaxed = SemanticThresholds(valid = 0.60f, partial = 0.58f).relaxedForLesson()
        assertTrue(relaxed.partial < relaxed.valid)
    }
}
