@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tech.shaffersoftworks.remotescreens.player.data.connection

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.client.plugins.websocket.WebSocketCapability
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import org.junit.Assert.assertFalse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.Protocol

class ConnectionRepositoryTest {
    private fun hello() = PlayerMessage.Hello(
        deviceId = "tablet",
        deviceName = "Tablet",
        widthDp = 800,
        heightDp = 480,
        density = 1f,
        appVersion = 1,
    )

    private val action = PlayerMessage.Action(
        slot = tech.shaffersoftworks.remotescreens.shared.Slot.MAIN,
        rev = 1,
        metadata = "test|on|",
    )

    /** A client whose socket number `index` reads [incomingFrames] at that index and writes [outgoingFor]. */
    private fun TestScope.socketClient(
        incomingFrames: List<Channel<Frame>>,
        outgoingFor: (index: Int) -> SendChannel<Frame>,
    ): HttpClient {
        var connections = 0
        val engine = MockEngine {
            val index = connections++
            val sender = outgoingFor(index)
            val context = currentCoroutineContext()
            val socket = object : DefaultWebSocketSession by mock<DefaultWebSocketSession>() {
                override val coroutineContext = context
                override val incoming = incomingFrames[index]
                override val outgoing = sender
                override suspend fun send(frame: Frame) = sender.send(frame)
            }
            HttpResponseData(
                HttpStatusCode.SwitchingProtocols,
                GMTDate(),
                Headers.Empty,
                HttpProtocolVersion.HTTP_1_1,
                socket,
                context,
            )
        }
        engine.config.dispatcher = StandardTestDispatcher(testScheduler)
        val webSocketEngine = object : HttpClientEngine by engine {
            override val supportedCapabilities = engine.supportedCapabilities + WebSocketCapability
        }
        return HttpClient(webSocketEngine) { install(WebSockets) }
    }

    @Test
    fun aCollectorReconnectingInsideAStateUpdateLeavesALiveConnection() = runTest {
        val outgoing = List(2) { Channel<Frame>(Channel.UNLIMITED) }
        val incoming = List(2) { Channel<Frame>(Channel.UNLIMITED) }
        val client = socketClient(incoming) { index -> outgoing[index] }
        val repository = ConnectionRepository(client, backgroundScope)
        var hasReconnected = false
        // Like viewModelScope's Main.immediate on the main thread: the collector runs inside the state update.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.connectionState.collect { state ->
                if (state.status == ConnectionStatus.CONNECTING && !hasReconnected) {
                    hasReconnected = true
                    repository.reconnect()
                }
            }
        }
        try {
            repository.connect("server") { hello() }
            runCurrent()
            assertTrue(hasReconnected)
            assertEquals(ConnectionStatus.CONNECTED, repository.connectionState.value.status)
            assertTrue(repository.trySend(action))
        } finally {
            repository.disconnect()
            client.close()
        }
    }

    @Test
    fun aCancelledHelloCannotPublishAfterItsReplacementConnects() = checkDelayedHello(reconnect = true)

    @Test
    fun aCancelledHelloCannotPublishAfterDisconnect() = checkDelayedHello(reconnect = false)

    private fun checkDelayedHello(reconnect: Boolean) = runTest {
        val outgoing = List(2) { Channel<Frame>(Channel.UNLIMITED) }
        val incoming = List(2) { Channel<Frame>(Channel.UNLIMITED) }
        var delayedHello: kotlin.coroutines.Continuation<Unit>? = null
        val client = socketClient(incoming) { index ->
            val sent = outgoing[index]
            object : SendChannel<Frame> by sent {
                override suspend fun send(element: Frame) {
                    sent.send(element)
                    if (index == 0 && element is Frame.Text) {
                        // Model IO completing after cancellation, immediately before publication.
                        kotlin.coroutines.suspendCoroutine<Unit> { delayedHello = it }
                    }
                }
            }
        }
        val repository = ConnectionRepository(client, backgroundScope)
        try {
            repository.connect("server") { hello() }
            runCurrent()
            assertTrue(delayedHello != null)
            if (reconnect) repository.reconnect() else repository.disconnect()
            runCurrent()
            val replacement = repository.connectionState.value
            val expectedStatus = if (reconnect) ConnectionStatus.CONNECTED else ConnectionStatus.IDLE
            assertEquals(expectedStatus, replacement.status)
            delayedHello?.resumeWith(Result.success(Unit))
            delayedHello = null
            runCurrent()
            assertEquals(replacement, repository.connectionState.value)
            val action = PlayerMessage.Ping
            if (reconnect) {
                val helloFrame = outgoing[1].tryReceive().getOrNull() as Frame.Text
                assertTrue(Protocol.decodePlayer(helloFrame.readText()) is PlayerMessage.Hello)
                assertTrue(repository.trySend(action, replacement.connectionId))
                val sent = outgoing[1].tryReceive().getOrNull() as Frame.Text
                assertEquals(Protocol.encode(action), sent.readText())
            } else {
                assertFalse(repository.trySend(action))
            }
        } finally {
            delayedHello?.resumeWith(Result.success(Unit))
            repository.disconnect()
            client.close()
        }
    }

    @Test
    fun anOldConnectionsCleanupCannotDiscardItsReplacement() = runTest {
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val releaseSecondHello = CompletableDeferred<Unit>()
        val (firstIncoming, secondIncoming) = List(2) { Channel<Frame>(Channel.UNLIMITED) }
        val (firstOutgoing, secondOutgoing) = List(2) { Channel<Frame>(Channel.UNLIMITED) }
        var connections = 0
        val client = socketClient(listOf(firstIncoming, secondIncoming)) { index ->
            connections++
            val sent = if (index == 0) firstOutgoing else secondOutgoing
            object : SendChannel<Frame> by sent {
                override suspend fun send(element: Frame) {
                    if (index == 0 && element is Frame.Close) {
                        cleanupStarted.complete(Unit)
                        withContext(NonCancellable) { releaseCleanup.await() }
                    }
                    sent.send(element)
                }
            }
        }
        val repository = ConnectionRepository(client, backgroundScope)
        val received = mutableListOf<ConnectionRepository.ReceivedMessage>()
        backgroundScope.launch { repository.receivedMessages.collect { received += it } }
        var hellos = 0
        try {
            repository.connect("server") {
                if (hellos++ > 0) releaseSecondHello.await()
                hello()
            }
            runCurrent()
            assertEquals(ConnectionStatus.CONNECTED, repository.connectionState.value.status)
            assertTrue(firstOutgoing.receive() is Frame.Text)
            firstIncoming.send(Frame.Text(Protocol.encode(ServerMessage.Welcome(screenId = "screen"))))
            runCurrent()
            val firstConnectionId = received.single().connectionId
            repository.reconnect()
            runCurrent()
            assertTrue(cleanupStarted.isCompleted)
            assertEquals(2, connections)
            // No outgoing application message may get ahead of the replacement Hello.
            assertFalse(repository.trySend(action))
            assertFalse(repository.trySend(action, firstConnectionId))
            assertTrue(secondOutgoing.tryReceive().isFailure)
            releaseSecondHello.complete(Unit)
            runCurrent()
            val helloFrame = secondOutgoing.receive() as Frame.Text
            assertTrue(Protocol.decodePlayer(helloFrame.readText()) is PlayerMessage.Hello)
            secondIncoming.send(Frame.Text(Protocol.encode(ServerMessage.Welcome(screenId = "screen"))))
            runCurrent()
            val secondConnectionId = received.last().connectionId
            assertTrue(secondConnectionId != firstConnectionId)
            assertFalse(repository.trySend(action, firstConnectionId))
            assertTrue(secondOutgoing.tryReceive().isFailure)
            assertTrue(repository.trySend(action, secondConnectionId))
            assertEquals(Protocol.encode(action), (secondOutgoing.receive() as Frame.Text).readText())
            releaseCleanup.complete(Unit)
            runCurrent()
            repository.send(action)
            assertEquals(Protocol.encode(action), (secondOutgoing.receive() as Frame.Text).readText())
            assertEquals(ConnectionStatus.CONNECTED, repository.connectionState.value.status)
            assertEquals(secondConnectionId, repository.connectionState.value.connectionId)
        } finally {
            releaseSecondHello.complete(Unit)
            releaseCleanup.complete(Unit)
            repository.disconnect()
            client.close()
        }
    }
}
