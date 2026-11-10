@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tech.shaffersoftworks.remotescreens.player.data.diagnostics

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionRepository
import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionStatus
import tech.shaffersoftworks.remotescreens.shared.Protocol
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import kotlin.time.Duration.Companion.seconds

class AppLogTest {
    private val connection = mock<ConnectionRepository>()
    private val logs = mock<LogDao>()
    private val state = MutableStateFlow(ConnectionRepository.ConnectionState(1L, ConnectionStatus.CONNECTED))
    private val messages = MutableSharedFlow<ConnectionRepository.ReceivedMessage>(extraBufferCapacity = 16)
    private val entry = StoredLog(
        eventId = "event-1",
        timestampMs = 123L,
        level = "warning",
        tag = "Player",
        message = "low_memory",
    )
    private val oldest = MutableStateFlow<StoredLog?>(entry)
    private val welcome = ServerMessage.Welcome(screenId = "screen", features = listOf(Protocol.CAPABILITY_APP_LOG_ACK))

    private suspend fun setUp(acknowledge: Boolean = false, acceptSend: Boolean = true) {
        whenever(connection.connectionState).thenReturn(state)
        whenever(connection.receivedMessages).thenReturn(messages)
        whenever(logs.oldest()).thenReturn(oldest)
        whenever(connection.trySend(entry.toMessage(), 1L)).thenAnswer {
            if (acknowledge) {
                messages.tryEmit(ConnectionRepository.ReceivedMessage(1L, ServerMessage.AppLogAck(entry.eventId)))
            }
            acceptSend
        }
        whenever(logs.acknowledge(entry.eventId)).thenAnswer { oldest.value = null; Unit }
    }

    private suspend fun emit(message: ServerMessage, connectionId: Long = 1L) {
        messages.emit(ConnectionRepository.ReceivedMessage(connectionId, message))
    }

    @Test
    fun welcomeBeforeConnectedStateStillStartsAndLateOldWelcomeCannotStopIt() = runTest {
        setUp()
        state.value = ConnectionRepository.ConnectionState(2L, ConnectionStatus.CONNECTING)
        whenever(connection.trySend(entry.toMessage(), 2L)).thenReturn(true)
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome, connectionId = 2L)
        runCurrent()
        verify(connection, never()).trySend(any(), any())
        state.value = ConnectionRepository.ConnectionState(2L, ConnectionStatus.CONNECTED)
        runCurrent()
        verify(connection).trySend(entry.toMessage(), 2L)
        emit(welcome.copy(pairingCode = "old"), connectionId = 1L)
        runCurrent()
        advanceTimeBy(6.seconds)
        runCurrent()
        verify(connection, times(2)).trySend(entry.toMessage(), 2L)
    }

    @Test
    fun aBlockedSocketSendTimesOutAndRetriesWithoutRemovingTheEntry() = runTest {
        setUp()
        org.mockito.kotlin.doSuspendableAnswer { kotlinx.coroutines.awaitCancellation() }
            .whenever(connection).trySend(entry.toMessage(), 1L)
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome)
        runCurrent()
        verify(connection).trySend(entry.toMessage(), 1L)
        advanceTimeBy(6.seconds)
        runCurrent()
        verify(connection, times(2)).trySend(entry.toMessage(), 1L)
        verify(logs, never()).acknowledge(any())
    }

    @Test
    fun anAcknowledgementFromAnotherConnectionCannotDeleteTheEntry() = runTest {
        setUp()
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome)
        runCurrent()
        emit(ServerMessage.AppLogAck(entry.eventId), connectionId = 2L)
        runCurrent()
        verify(logs, never()).acknowledge(any())
        emit(ServerMessage.AppLogAck(entry.eventId))
        runCurrent()
        verify(logs).acknowledge(entry.eventId)
    }

    @Test
    fun requiresPairedAcknowledgementSupportAndDeletesOnlyAfterAck() = runTest {
        setUp(acknowledge = true)
        val appLog = AppLog(connection, backgroundScope, logs)
        appLog.start()
        appLog.start()
        runCurrent()
        emit(welcome.copy(pairingCode = "123456"))
        runCurrent()
        emit(welcome.copy(features = listOf(Protocol.CAPABILITY_APP_LOG)))
        runCurrent()
        verify(connection, never()).trySend(any(), any())
        emit(welcome)
        runCurrent()
        verify(connection).trySend(entry.toMessage(), 1L)
        verify(logs).acknowledge(entry.eventId)
    }

    @Test
    fun missingOrWrongAckKeepsEntryAndRetries() = runTest {
        setUp()
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome)
        runCurrent()
        emit(ServerMessage.AppLogAck("other-event"))
        runCurrent()
        verify(logs, never()).acknowledge(any())
        advanceTimeBy(6.seconds)
        runCurrent()
        verify(connection, times(2)).trySend(entry.toMessage(), 1L)
        verify(logs, never()).acknowledge(any())
    }

    @Test
    fun retriesFailedSendThenDeletesOnRecoveryWithoutAnotherWelcome() = runTest {
        setUp(acceptSend = false)
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome)
        runCurrent()
        verify(logs, never()).acknowledge(any())
        advanceTimeBy(1.seconds)
        runCurrent()
        verify(connection, times(2)).trySend(entry.toMessage(), 1L)
        whenever(connection.trySend(entry.toMessage(), 1L)).thenAnswer {
            messages.tryEmit(ConnectionRepository.ReceivedMessage(1L, ServerMessage.AppLogAck(entry.eventId)))
            true
        }
        advanceTimeBy(2.seconds)
        runCurrent()
        verify(connection, times(3)).trySend(entry.toMessage(), 1L)
        verify(logs).acknowledge(entry.eventId)
    }

    @Test
    fun disconnectedEntryRemainsForNextPairedWelcome() = runTest {
        setUp()
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome)
        runCurrent()
        state.value = ConnectionRepository.ConnectionState(1L, ConnectionStatus.DISCONNECTED)
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()
        verify(connection).trySend(entry.toMessage(), 1L)
        verify(logs, never()).acknowledge(any())
        state.value = ConnectionRepository.ConnectionState(2L, ConnectionStatus.CONNECTED)
        runCurrent()
        verify(connection).trySend(entry.toMessage(), 1L)
        whenever(connection.trySend(entry.toMessage(), 2L)).thenReturn(true)
        emit(welcome, connectionId = 2L)
        runCurrent()
        verify(connection).trySend(entry.toMessage(), 2L)
    }
}
