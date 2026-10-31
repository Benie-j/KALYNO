package ci.kalyno.app.data.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRulesTest {

    @Test fun lesMotifsSontFiltresParSource() {
        assertEquals(
            listOf(ReportCategory.TYPO, ReportCategory.UNCLEAR_EXPLANATION, ReportCategory.OTHER),
            ReportCategory.forSource(ReportSourceType.LESSON)
        )
        assertEquals(
            listOf(
                ReportCategory.WRONG_OFFICIAL_ANSWER,
                ReportCategory.SEMANTIC_GRADING,
                ReportCategory.AMBIGUOUS_STATEMENT
            ),
            ReportCategory.forSource(ReportSourceType.EXERCISE)
        )
        assertEquals(
            listOf(ReportCategory.APP_BUG, ReportCategory.SYNC_PROBLEM),
            ReportCategory.forSource(ReportSourceType.SETTINGS)
        )
    }

    @Test fun sansMotifOnNePeutPasEnvoyer() = assertFalse(ReportRules.isValid(null, "Une description assez longue"))

    @Test fun descriptionFacultativeAcceptee() {
        assertTrue(ReportRules.isValid(ReportCategory.TYPO, ""))
        assertTrue(ReportRules.isValid(ReportCategory.AMBIGUOUS_STATEMENT, "   "))
    }

    @Test fun descriptionObligatoireAvecMinimum() {
        assertFalse(ReportRules.isValid(ReportCategory.OTHER, ""))
        assertFalse(ReportRules.isValid(ReportCategory.APP_BUG, "court"))
        assertFalse(ReportRules.isValid(ReportCategory.SYNC_PROBLEM, "   court   "))
        assertTrue(ReportRules.isValid(ReportCategory.SEMANTIC_GRADING, "x".repeat(ReportRules.MIN_REQUIRED_DESCRIPTION)))
    }

    @Test fun descriptionTronqueeAuMaximum() {
        assertEquals(ReportRules.MAX_DESCRIPTION, ReportRules.clean("a".repeat(5000)).length)
        assertEquals("texte", ReportRules.clean("  texte \n"))
    }

    @Test fun codeInconnuDonneNull() {
        assertEquals(ReportCategory.TYPO, ReportCategory.fromCode("TYPO"))
        assertEquals(null, ReportCategory.fromCode("INCONNU"))
        assertEquals(null, ReportCategory.fromCode(null))
    }
}
