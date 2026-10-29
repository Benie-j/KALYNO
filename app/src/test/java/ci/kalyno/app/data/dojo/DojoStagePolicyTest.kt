package ci.kalyno.app.data.dojo

import ci.kalyno.app.data.dashboard.SrsConfig
import ci.kalyno.app.data.srs.CardProgressEntity
import ci.kalyno.app.data.srs.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DojoStagePolicyTest {

    private val threshold = SrsConfig.DOJO_FREE_INPUT_STAGE

    @Test fun carteNeuveEstUnQcm() = assertFalse(DojoStagePolicy.requiresFreeInput(0))

    @Test fun basculeExactementAuSeuil() {
        assertFalse(DojoStagePolicy.requiresFreeInput(threshold - 1))
        assertTrue(DojoStagePolicy.requiresFreeInput(threshold))
    }

    @Test fun reussiteFaitMonter() {
        assertEquals(1, DojoStagePolicy.next(0, ReviewRating.SU))
        assertEquals(1, DojoStagePolicy.next(0, ReviewRating.TROP_FACILE))
    }

    @Test fun hesitationNeFaitPasMonter() = assertEquals(2, DojoStagePolicy.next(2, ReviewRating.HESITE))

    @Test fun oubliEnQcmRecule() {
        assertEquals(1, DojoStagePolicy.next(2, ReviewRating.OUBLIE))
        assertEquals(0, DojoStagePolicy.next(0, ReviewRating.OUBLIE))
    }

    @Test fun oubliEnSaisieLibreRetombeEnQcm() {
        val after = DojoStagePolicy.next(SrsConfig.DOJO_MAX_STAGE, ReviewRating.OUBLIE)
        assertEquals(threshold - 1, after)
        assertFalse(DojoStagePolicy.requiresFreeInput(after))
    }

    @Test fun niveauPlafonne() =
        assertEquals(SrsConfig.DOJO_MAX_STAGE, DojoStagePolicy.next(SrsConfig.DOJO_MAX_STAGE, ReviewRating.SU))

    @Test fun troisReussitesDebloquentLaSaisieLibre() {
        var stage = 0
        repeat(threshold) { stage = DojoStagePolicy.next(stage, ReviewRating.SU) }
        assertTrue(DojoStagePolicy.requiresFreeInput(stage))
    }

    @Test fun reprendUnNiveauCoherentDepuisLesRepetitions() {
        assertEquals(0, DojoStagePolicy.fromRepetitions(-4))
        assertEquals(SrsConfig.DOJO_MAX_STAGE, DojoStagePolicy.fromRepetitions(99))
    }

    @Test fun regleDeriveeSurLEntite() {
        val young = CardProgressEntity(studentId = "s", flashcardId = "c", dueDate = 0L, dojoStage = 0)
        val mature = young.copy(dojoStage = threshold)
        assertFalse(young.requiresFreeInput)
        assertTrue(mature.requiresFreeInput)
    }
}
