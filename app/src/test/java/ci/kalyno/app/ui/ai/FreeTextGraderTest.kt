package ci.kalyno.app.ui.ai

import ci.kalyno.app.data.ai.AiExerciseContext
import ci.kalyno.app.data.ai.AiGateway
import ci.kalyno.app.data.ai.AiPhase
import ci.kalyno.app.data.ai.ChatTurn
import ci.kalyno.app.data.ai.GeminiException
import ci.kalyno.app.data.ai.GeminiVerdict
import ci.kalyno.app.data.ai.GradeResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTextGraderTest {

    private class FakeGateway : AiGateway {
        var signedIn = true
        var calls = 0
        var next = CompletableDeferred<GradeResult>()
        override fun isAvailable() = signedIn
        override suspend fun grade(context: AiExerciseContext, answer: String): GradeResult {
            calls++
            return next.await()
        }
        override suspend fun assist(message: String, context: AiExerciseContext?, history: List<ChatTurn>) = ""
    }

    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val gateway = FakeGateway()
    private val grader = FreeTextGrader(gateway, scope)
    private val ctx = AiExerciseContext(question = "Q", reference = "R")

    @After fun tearDown() = job.cancel()

    private fun ok(v: GeminiVerdict = GeminiVerdict.CORRECT) = GradeResult(v, 90, 90, "ok")

    @Test fun `correction reussie appelle onGraded une seule fois`() {
        var got: GradeResult? = null
        var count = 0
        grader.grade(ctx, "a") { got = it; count++ }
        assertEquals(AiPhase.LOADING, grader.state.value.phase)
        gateway.next.complete(ok())
        assertEquals(AiPhase.SUCCESS, grader.state.value.phase)
        assertEquals(GeminiVerdict.CORRECT, got!!.verdict)
        assertEquals(1, count)
    }

    @Test fun `double soumission pendant le chargement est ignoree`() {
        var count = 0
        assertTrue(grader.grade(ctx, "a") { count++ })
        assertFalse(grader.grade(ctx, "a") { count++ })
        assertEquals(1, gateway.calls)
        gateway.next.complete(ok())
        assertEquals(1, count)
    }

    @Test fun `erreur reseau ne donne aucun verdict et permet de reessayer`() {
        var graded = 0
        grader.grade(ctx, "a") { graded++ }
        gateway.next.completeExceptionally(GeminiException("network", "Connexion impossible", true))
        assertEquals(AiPhase.ERROR, grader.state.value.phase)
        assertEquals("Connexion impossible", grader.state.value.error)
        assertEquals(0, graded)

        gateway.next = CompletableDeferred()
        assertTrue(grader.grade(ctx, "a") { graded++ })
        assertEquals(AiPhase.RETRYING, grader.state.value.phase)
        gateway.next.complete(ok(GeminiVerdict.PARTIAL))
        assertEquals(AiPhase.SUCCESS, grader.state.value.phase)
        assertEquals(1, graded)
        assertEquals(2, gateway.calls)
    }

    @Test fun `timeout est une erreur sans verdict`() {
        var graded = 0
        grader.grade(ctx, "a") { graded++ }
        gateway.next.completeExceptionally(GeminiException("timeout", "Trop long", true))
        assertTrue(grader.state.value.failed)
        assertEquals(0, graded)
    }

    @Test fun `erreur inattendue reste recuperable`() {
        grader.grade(ctx, "a") { }
        gateway.next.completeExceptionally(IllegalStateException("boom"))
        assertTrue(grader.state.value.failed)
        assertFalse(grader.state.value.busy)
    }

    @Test fun `disponibilite suit la connexion du compte`() {
        gateway.signedIn = false
        assertFalse(grader.available)
    }

    @Test fun `reset remet l'etat a zero`() {
        grader.grade(ctx, "a") { }
        gateway.next.completeExceptionally(GeminiException("network", "x", true))
        grader.reset()
        assertEquals(AiPhase.IDLE, grader.state.value.phase)
    }
}
