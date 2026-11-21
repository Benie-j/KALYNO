package ci.kalyno.app.data.ai

import ci.kalyno.app.data.dojo.AnswerContext
import ci.kalyno.app.data.srs.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KalynoVerdictMapperTest {

    private fun result(v: KalynoVerdict) = GradeResult(v, 80, 90, "ok")
    private val fast = AnswerContext(elapsedMs = 500, questionLength = 20)
    private val slowish = AnswerContext(elapsedMs = 12_000, questionLength = 20)

    @Test fun `CORRECT rapide donne Trop facile comme le correcteur local`() {
        val v = KalynoVerdictMapper.toVerdict(result(KalynoVerdict.CORRECT), fast)
        assertTrue(v.correct)
        assertEquals(ReviewRating.TROP_FACILE, v.rating)
    }

    @Test fun `CORRECT avec indice reste Hesite selon les regles existantes`() {
        val v = KalynoVerdictMapper.toVerdict(result(KalynoVerdict.CORRECT), fast.copy(hintUsed = true))
        assertEquals(ReviewRating.HESITE, v.rating)
    }

    @Test fun `CORRECT plus lent donne Su ou Hesite jamais Oublie`() {
        val v = KalynoVerdictMapper.toVerdict(result(KalynoVerdict.CORRECT), slowish)
        assertTrue(v.correct)
        assertTrue(v.rating == ReviewRating.SU || v.rating == ReviewRating.HESITE)
    }

    @Test fun `PARTIAL donne Hesite et partiel`() {
        val v = KalynoVerdictMapper.toVerdict(result(KalynoVerdict.PARTIAL), fast)
        assertFalse(v.correct)
        assertTrue(v.partial)
        assertEquals(ReviewRating.HESITE, v.rating)
    }

    @Test fun `INCORRECT donne Oublie`() {
        val v = KalynoVerdictMapper.toVerdict(result(KalynoVerdict.INCORRECT), fast)
        assertFalse(v.correct)
        assertEquals(ReviewRating.OUBLIE, v.rating)
    }
}
