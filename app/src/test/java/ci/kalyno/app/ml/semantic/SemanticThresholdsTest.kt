package ci.kalyno.app.ml.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticThresholdsTest {

    @Test fun lesDefautsRestentDansLaPlageDeValidation() {
        val d = SemanticThresholds.DEFAULT
        assertTrue(d.valid in SemanticThresholds.MIN_VALID..SemanticThresholds.MAX_VALID)
        assertTrue(d.partial >= SemanticThresholds.MIN_PARTIAL)
        assertTrue(d.partial <= d.valid - SemanticThresholds.MIN_GAP)
    }

    @Test fun unAncienReglageMiniLmEstRemonteAuPlancherE5() {
        // 0,75 validerait presque toute réponse sur le sujet (fausses : jusqu'à 0,98 avec E5)
        val t = SemanticThresholds.sanitize(valid = 0.75f, partial = 0.60f)
        assertEquals(SemanticThresholds.MIN_VALID, t.valid, 0.001f)
        assertEquals(SemanticThresholds.MIN_PARTIAL, t.partial, 0.001f)
    }

    @Test fun partielResteStrictementSousLeSeuilValide() {
        val t = SemanticThresholds.sanitize(valid = 0.93f, partial = 0.93f)
        assertTrue(t.partial <= t.valid - SemanticThresholds.MIN_GAP + 0.0001f)
    }

    @Test fun valeurAbsenteOuInvalideDonneLeDefaut() {
        assertEquals(SemanticThresholds.DEFAULT, SemanticThresholds.sanitize(null, 0.9f))
        assertEquals(SemanticThresholds.DEFAULT, SemanticThresholds.sanitize(Float.NaN, null))
    }
}
