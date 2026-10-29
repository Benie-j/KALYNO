package ci.kalyno.app.ml.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticThresholdsLessonTest {

    @Test fun assouplitLesDeuxSeuils() {
        val relaxed = SemanticThresholds(valid = 0.92f, partial = 0.86f).relaxedForLesson()
        assertEquals(0.84f, relaxed.valid, 0.001f)
        assertEquals(0.78f, relaxed.partial, 0.001f)
    }

    @Test fun neDescendJamaisSousLesPlanchers() {
        val relaxed = SemanticThresholds(valid = 0.80f, partial = 0.74f).relaxedForLesson()
        assertTrue(relaxed.valid >= 0.78f)
        assertTrue(relaxed.partial >= 0.72f)
    }

    @Test fun partielResteStrictementSousLeSeuilValide() {
        val relaxed = SemanticThresholds(valid = 0.80f, partial = 0.79f).relaxedForLesson()
        assertTrue(relaxed.partial < relaxed.valid)
    }

    @Test fun unAncienReglageMiniLmEstRemonteAuPlancherE5() {
        val t = SemanticThresholds.sanitize(valid = 0.75f, partial = 0.60f)
        assertEquals(SemanticThresholds.MIN_VALID, t.valid, 0.001f)
        assertTrue(t.partial >= 0.70f && t.partial < t.valid)
    }
}
