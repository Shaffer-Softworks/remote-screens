package tech.shaffersoftworks.remotescreens.server.player.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tech.shaffersoftworks.remotescreens.shared.CrashTestResult
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDiagnosticsTest {
    @Test
    fun onlyTheMatchingReplyCompletesATestAndOverlappingTestsAreRejected() = runTest {
        lateinit var request: ServerMessage.SentryTest
        val diagnostics = SessionDiagnostics { request = it as ServerMessage.SentryTest }
        val result = async { diagnostics.test() }
        runCurrent()
        assertEquals("busy", diagnostics.test().status)
        diagnostics.receive(PlayerMessage.SentryTestResult("old", CrashTestResult("accepted", "old-event")))
        assertFalse(result.isCompleted)
        val accepted = CrashTestResult("accepted", "test-event")
        diagnostics.receive(PlayerMessage.SentryTestResult(request.requestId, accepted))
        assertEquals(accepted, result.await())
    }

    @Test
    fun timeoutAndDisconnectDoNotLeaveTheSessionBusy() = runTest {
        val diagnostics = SessionDiagnostics {}
        assertEquals("timeout", diagnostics.test().status)
        val result = async { diagnostics.test() }
        runCurrent()
        diagnostics.stop()
        assertEquals("offline", result.await().status)
        assertEquals("timeout", diagnostics.test().status)
    }
}
