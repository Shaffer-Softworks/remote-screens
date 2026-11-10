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
import tech.shaffersoftworks.remotescreens.shared.AppLogLevel
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
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
        level = AppLogLevel.WARNING,
        tag = "Player",
        message = "low_memory",
    )
    private val batch = PlayerMessage.AppLog(listOf(entry.toEntry()))
    private val pending = MutableStateFlow(listOf(entry))
    private val entryAck = ConnectionRepository.ReceivedMessage(1L, ServerMessage.AppLogAck(listOf(entry.eventId)))
    private val welcome = ServerMessage.Welcome(screenId = "screen", features = listOf(Protocol.CAPABILITY_APP_LOG))

    private suspend fun setUp(acknowledge: Boolean = false, acceptSend: Boolean = true) {
        whenever(connection.connectionState).thenReturn(state)
        whenever(connection.receivedMessages).thenReturn(messages)
        whenever(logs.pending(Protocol.MAX_APP_LOG_BATCH)).thenReturn(pending)
        whenever(connection.trySend(batch, 1L)).thenAnswer {
            if (acknowledge) {
                messages.tryEmit(entryAck)
            }
            acceptSend
        }
        whenever(logs.acknowledge(listOf(entry.eventId))).thenAnswer { pending.value = emptyList(); Unit }
    }

    private suspend fun emit(message: ServerMessage, connectionId: Long = 1L) {
        messages.emit(ConnectionRepository.ReceivedMessage(connectionId, message))
    }

    @Test
    fun welcomeBeforeConnectedStateStillStartsAndLateOldWelcomeCannotStopIt() = runTest {
        setUp()
        state.value = ConnectionRepository.ConnectionState(2L, ConnectionStatus.CONNECTING)
        whenever(connection.trySend(batch, 2L)).thenReturn(true)
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome, connectionId = 2L)
        runCurrent()
        verify(connection, never()).trySend(any(), any())
        state.value = ConnectionRepository.ConnectionState(2L, ConnectionStatus.CONNECTED)
        runCurrent()
        verify(connection).trySend(batch, 2L)
        emit(welcome.copy(pairingCode = "old"), connectionId = 1L)
        runCurrent()
        advanceTimeBy(6.seconds)
        runCurrent()
        verify(connection, times(2)).trySend(batch, 2L)
    }

    @Test
    fun aBlockedSocketSendTimesOutAndRetriesWithoutRemovingTheEntry() = runTest {
        setUp()
        org.mockito.kotlin.doSuspendableAnswer { kotlinx.coroutines.awaitCancellation() }
            .whenever(connection).trySend(batch, 1L)
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome)
        runCurrent()
        verify(connection).trySend(batch, 1L)
        advanceTimeBy(6.seconds)
        runCurrent()
        verify(connection, times(2)).trySend(batch, 1L)
        verify(logs, never()).acknowledge(any())
    }

    @Test
    fun anAcknowledgementFromAnotherConnectionCannotDeleteTheEntry() = runTest {
        setUp()
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome)
        runCurrent()
        emit(ServerMessage.AppLogAck(listOf(entry.eventId)), connectionId = 2L)
        runCurrent()
        verify(logs, never()).acknowledge(any())
        emit(ServerMessage.AppLogAck(listOf(entry.eventId)))
        runCurrent()
        verify(logs).acknowledge(listOf(entry.eventId))
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
        emit(welcome.copy(features = listOf(Protocol.CAPABILITY_TELEMETRY)))
        runCurrent()
        verify(connection, never()).trySend(any(), any())
        emit(welcome)
        runCurrent()
        verify(connection).trySend(batch, 1L)
        verify(logs).acknowledge(listOf(entry.eventId))
    }

    @Test
    fun missingOrWrongAckKeepsEntryAndRetries() = runTest {
        setUp()
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome)
        runCurrent()
        emit(ServerMessage.AppLogAck(listOf("other-event")))
        runCurrent()
        verify(logs, never()).acknowledge(any())
        advanceTimeBy(6.seconds)
        runCurrent()
        verify(connection, times(2)).trySend(batch, 1L)
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
        verify(connection, times(2)).trySend(batch, 1L)
        whenever(connection.trySend(batch, 1L)).thenAnswer {
            messages.tryEmit(entryAck)
            true
        }
        advanceTimeBy(2.seconds)
        runCurrent()
        verify(connection, times(3)).trySend(batch, 1L)
        verify(logs).acknowledge(listOf(entry.eventId))
    }

    @Test
    fun queuedEntriesGoInOneBatchAndAPartialAckDeletesOnlyThoseListed() = runTest {
        val second = entry.copy(sequence = 2, eventId = "event-2", level = AppLogLevel.ERROR)
        val twoEntries = PlayerMessage.AppLog(listOf(entry.toEntry(), second.toEntry()))
        setUp()
        pending.value = listOf(entry, second)
        whenever(connection.trySend(twoEntries, 1L)).thenAnswer {
            messages.tryEmit(
                ConnectionRepository.ReceivedMessage(1L, ServerMessage.AppLogAck(listOf("event-2", "unrelated"))),
            )
            true
        }
        AppLog(connection, backgroundScope, logs).start()
        runCurrent()
        emit(welcome)
        runCurrent()
        verify(connection).trySend(twoEntries, 1L)
        verify(logs).acknowledge(listOf("event-2"))
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
        verify(connection).trySend(batch, 1L)
        verify(logs, never()).acknowledge(any())
        state.value = ConnectionRepository.ConnectionState(2L, ConnectionStatus.CONNECTED)
        runCurrent()
        verify(connection).trySend(batch, 1L)
        whenever(connection.trySend(batch, 2L)).thenReturn(true)
        emit(welcome, connectionId = 2L)
        runCurrent()
        verify(connection).trySend(batch, 2L)
    }
}
