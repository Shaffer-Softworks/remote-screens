package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class IntercomCoordinatorTest {
    @Test
    fun aRecreatedPlayerReopensTheSessionAfterItsOldScopeWasCancelled() = runTest {
        val link = CountingLink()
        val coordinator = IntercomCoordinator(mock(), link, mock())
        val oldScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        coordinator.startForTest(oldScope)
        runCurrent()
        assertEquals(1, link.opened)

        oldScope.cancel()
        runCurrent()
        coordinator.startForTest(backgroundScope)
        runCurrent()

        assertEquals(2, link.opened)
        coordinator.stop()
    }

    @Test
    fun repeatedConfigurationInTheSamePlayerKeepsTheLiveSession() = runTest {
        val link = CountingLink()
        val coordinator = IntercomCoordinator(mock(), link, mock())
        coordinator.startForTest(backgroundScope)
        runCurrent()
        coordinator.startForTest(backgroundScope)
        runCurrent()

        assertEquals(1, link.opened)
        coordinator.stop()
    }

    private fun IntercomCoordinator.startForTest(scope: CoroutineScope) {
        start(
            scope = scope,
            url = "ws://server:8766/webrtc",
            clientId = "station-garage",
            displayName = "Garage",
            onReport = {},
            onWake = {},
        )
    }

    private class CountingLink : SignalingLink {
        var opened = 0

        override suspend fun conversation(url: String, block: suspend SignalingConversation.() -> Unit) {
            opened += 1
            awaitCancellation()
        }
    }
}
