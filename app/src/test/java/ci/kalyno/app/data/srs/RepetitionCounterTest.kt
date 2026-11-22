package ci.kalyno.app.data.srs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/** Fige le comportement actuel du compteur `repetitions` utilisé par le seuil de maîtrise (3). */
class RepetitionCounterTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val day = 24 * 60 * 60 * 1000L
    private val t0 = 1_700_000_000_000L

    private fun review(card: CardProgressEntity, rating: ReviewRating, at: Long): CardProgressEntity {
        val fsrs = SrsEngine.computeNextProgress(card, rating, at, utc)
        return LearningBehavior.afterReview(card, fsrs, rating, 0L, at, utc)
    }

    @Test fun troisReponsesCorrectesDonnentTroisRepetitions() {
        var card = CardProgressEntity(studentId = "s", flashcardId = "c", dueDate = t0)
        repeat(3) { i -> card = review(card, ReviewRating.SU, t0 + i * 3 * day) }
        assertEquals(3, card.repetitions)
    }

    @Test fun uneReponseIncorrecteRemetLeCompteurAZero() {
        var card = CardProgressEntity(studentId = "s", flashcardId = "c", dueDate = t0)
        repeat(4) { i -> card = review(card, ReviewRating.SU, t0 + i * 3 * day) }
        card = review(card, ReviewRating.OUBLIE, t0 + 15 * day)
        assertEquals(0, card.repetitions)
    }

    @Test fun hesiteCompteCommeUneRepetitionReussieDansLeMoteurActuel() {
        // Constat : SrsEngine ajoute 1 pour toute note autre que « Oublié », « Hésité » compris.
        var card = CardProgressEntity(studentId = "s", flashcardId = "c", dueDate = t0)
        card = review(card, ReviewRating.HESITE, t0)
        assertEquals(1, card.repetitions)
    }
}
