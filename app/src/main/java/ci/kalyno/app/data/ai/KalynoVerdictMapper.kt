package ci.kalyno.app.data.ai

import ci.kalyno.app.data.dojo.AnswerContext
import ci.kalyno.app.data.dojo.AnswerGrader
import ci.kalyno.app.data.dojo.Verdict
import ci.kalyno.app.data.srs.ReviewRating

/**
 * Traduit un verdict Kalyno en [Verdict] FSRS, sans toucher aux règles existantes :
 * CORRECT -> note « juste » habituelle (Su / Trop facile selon temps et aides), PARTIAL -> Hésité, INCORRECT -> Oublié.
 */
object KalynoVerdictMapper {
    fun toVerdict(result: GradeResult, context: AnswerContext): Verdict = when (result.verdict) {
        KalynoVerdict.CORRECT -> Verdict(true, AnswerGrader.goodRating(context))
        KalynoVerdict.PARTIAL -> Verdict(false, ReviewRating.HESITE, partial = true)
        KalynoVerdict.INCORRECT -> Verdict(false, ReviewRating.OUBLIE)
    }
}
