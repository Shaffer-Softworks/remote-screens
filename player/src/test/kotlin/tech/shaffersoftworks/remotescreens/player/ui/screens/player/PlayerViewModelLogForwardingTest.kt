package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionRepository
import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionStatus
import tech.shaffersoftworks.remotescreens.shared.Protocol
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import org.junit.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class PlayerViewModelLogForwardingTest : PlayerViewModelTestBase() {
    @Test
    fun `logs are forwarded only on the current connection to a paired add-on that accepts them`() {
        val accepted = ServerMessage.Welcome(features = listOf(Protocol.CAPABILITY_APP_LOG))
        connectionState.value = ConnectionRepository.ConnectionState(2L, ConnectionStatus.CONNECTED)
        receive(accepted.copy(pairingCode = "123456"), connectionId = 2L)
        receive(accepted.copy(features = listOf(Protocol.CAPABILITY_TELEMETRY)), connectionId = 2L)
        verify(appLog, times(2)).forwardOn(null)

        receive(accepted, connectionId = 1L)
        verify(appLog, never()).forwardOn(1L)

        receive(accepted, connectionId = 2L)
        verify(appLog).forwardOn(2L)
    }

    @Test
    fun `log forwarding stops when the connection drops`() {
        connectionState.value = ConnectionRepository.ConnectionState(1L, ConnectionStatus.CONNECTED)
        runCurrent()
        verify(appLog, never()).forwardOn(null)

        connectionState.value = ConnectionRepository.ConnectionState(1L, ConnectionStatus.DISCONNECTED)
        runCurrent()
        verify(appLog).forwardOn(null)
    }
}
