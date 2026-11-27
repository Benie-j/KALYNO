package ci.kalyno.app.data.lessons

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stockage et serveur en mémoire : vérifie la logique de validation et de synchronisation (pas Room lui-même). */
class SessionProgressServiceTest {

    private class MemStore : SessionProgressStore {
        val rows = LinkedHashMap<Pair<String, String>, SessionProgressRow>()
        override suspend fun insertIfAbsent(row: SessionProgressRow): Boolean {
            val k = row.studentId to row.sessionId
            if (rows.containsKey(k)) return false
            rows[k] = row; return true
        }
        override suspend fun completedSessionIds(studentId: String) = rows.values.filter { it.studentId == studentId }.map { it.sessionId }.toSet()
        override suspend fun pending(studentId: String) = rows.values.filter { it.studentId == studentId && it.syncedAt == null }
        override suspend fun markSynced(studentId: String, sessionIds: Collection<String>, at: Long) {
            sessionIds.forEach { s -> rows[studentId to s]?.let { rows[studentId to s] = it.copy(syncedAt = at) } }
        }
    }

    /** Serveur : une ligne par (compte, séance), envoi idempotent, comme PRIMARY KEY + ignore-duplicates. */
    private class FakeServer { val table = LinkedHashMap<String, Long>() }
    private class Remote(val server: FakeServer, val online: () -> Boolean) : SessionProgressRemote {
        var pushCalls = 0
        override suspend fun fetch(): List<RemoteSessionProgress> {
            check(online()) { "hors ligne" }
            return server.table.map { RemoteSessionProgress(it.key, it.value) }
        }
        override suspend fun push(rows: List<SessionProgressRow>) {
            check(online()) { "hors ligne" }
            pushCalls++
            rows.forEach { server.table.putIfAbsent(it.sessionId, it.completedAt) }
        }
    }

    private var now = 1_000L
    private fun service(store: SessionProgressStore) = SessionProgressService(store) { now++ }

    // ---- Scénario 2
    @Test fun completingOneSessionLeavesOthersUntouched() = runBlocking {
        val svc = service(MemStore())
        assertTrue(svc.complete("e1", "S1", "L1"))
        assertEquals(setOf("S1"), svc.completedSessionIds("e1"))
    }

    @Test fun doubleCompletionIsIdempotentAndKeepsFirstDate() = runBlocking {
        val store = MemStore(); val svc = service(store)
        assertTrue(svc.complete("e1", "S1", "L1"))
        val first = store.rows.getValue("e1" to "S1").completedAt
        assertFalse(svc.complete("e1", "S1", "L1"))
        assertEquals(1, store.rows.size)
        assertEquals(first, store.rows.getValue("e1" to "S1").completedAt)
    }

    @Test fun generalEntryAndBlankIdsAreRejected() = runBlocking {
        val svc = service(MemStore())
        listOf(SessionRules.generalEntryId("L1"), " ").forEach {
            try { svc.complete("e1", it, "L1"); throw AssertionError("aurait dû échouer") } catch (_: IllegalArgumentException) {}
        }
    }

    // ---- Scénario 4 : persistance (nouvelle instance de service sur le même stockage)
    @Test fun completionSurvivesServiceRestartOnSameStore() = runBlocking {
        val store = MemStore()
        service(store).complete("e1", "S1", "L1")
        assertEquals(setOf("S1"), service(store).completedSessionIds("e1"))
    }

    // ---- Scénario 5 : réseau
    @Test fun offlineCompletionIsKeptThenSyncedOnceWithoutDuplicate() = runBlocking {
        val store = MemStore(); val svc = service(store)
        var online = false
        val remote = Remote(FakeServer()) { online }

        svc.complete("e1", "S1", "L1")
        try { svc.push("e1", remote); throw AssertionError("hors ligne : doit échouer") } catch (_: IllegalStateException) {}
        assertEquals(1, store.pending("e1").size)          // conservée localement, toujours en attente
        assertEquals(setOf("S1"), svc.completedSessionIds("e1"))

        online = true
        svc.push("e1", remote)
        assertTrue(store.pending("e1").isEmpty())
        assertEquals(1, remote.server.table.size)

        svc.push("e1", remote)                              // nouvelle synchro : rien à renvoyer
        svc.pull("e1", remote)
        assertEquals(1, remote.pushCalls)
        assertEquals(1, remote.server.table.size)
        assertEquals(1, store.rows.size)
    }

    @Test fun pullRestoresRemoteCompletionsOnFreshDevice() = runBlocking {
        val server = FakeServer().also { it.table["S2"] = 500L }
        val store = MemStore(); val svc = service(store)
        svc.pull("e1", Remote(server) { true })
        assertEquals(setOf("S2"), svc.completedSessionIds("e1"))
        assertTrue(store.pending("e1").isEmpty())           // reçue du serveur : rien à renvoyer
    }

    // ---- Scénario 6 : isolation
    @Test fun twoStudentsHaveIndependentProgress() = runBlocking {
        val store = MemStore(); val svc = service(store)
        svc.complete("e1", "S1", "L1")
        assertEquals(setOf("S1"), svc.completedSessionIds("e1"))
        assertTrue(svc.completedSessionIds("e2").isEmpty())
        assertTrue(store.pending("e2").isEmpty())
    }
}
