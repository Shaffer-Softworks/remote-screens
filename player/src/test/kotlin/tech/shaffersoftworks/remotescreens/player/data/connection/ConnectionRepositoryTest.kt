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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
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

    @Test
    fun anOldConnectionsCleanupCannotDiscardItsReplacement() = runTest {
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val firstIncoming = Channel<Frame>(Channel.UNLIMITED)
        val secondIncoming = Channel<Frame>(Channel.UNLIMITED)
        val firstOutgoing = Channel<Frame>(Channel.UNLIMITED)
        val secondOutgoing = Channel<Frame>(Channel.UNLIMITED)
        var connections = 0
        val engine = MockEngine {
            val first = connections++ == 0
            val sent = if (first) firstOutgoing else secondOutgoing
            val outgoing = object : SendChannel<Frame> by sent {
                override suspend fun send(element: Frame) {
                    if (first && element is Frame.Close) {
                        cleanupStarted.complete(Unit)
                        withContext(NonCancellable) { releaseCleanup.await() }
                    }
                    sent.send(element)
                }
            }
            val context = currentCoroutineContext()
            val socket = object : DefaultWebSocketSession by mock<DefaultWebSocketSession>() {
                override val coroutineContext = context
                override val incoming = if (first) firstIncoming else secondIncoming
                override val outgoing = outgoing
                override suspend fun send(frame: Frame) = outgoing.send(frame)
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
        val client = HttpClient(webSocketEngine) { install(WebSockets) }
        val repository = ConnectionRepository(client, backgroundScope)
        try {
            repository.connect("server") { hello() }
            runCurrent()
            assertEquals(ConnectionStatus.CONNECTED, repository.status.value)
            assertTrue(firstOutgoing.receive() is Frame.Text)

            repository.reconnect()
            runCurrent()
            assertTrue(cleanupStarted.isCompleted)
            assertEquals(2, connections)
            assertTrue(secondOutgoing.receive() is Frame.Text)

            releaseCleanup.complete(Unit)
            runCurrent()
            val action = PlayerMessage.Action(
                slot = tech.shaffersoftworks.remotescreens.shared.Slot.MAIN,
                rev = 1,
                metadata = "test|on|",
            )
            repository.send(action)
            assertEquals(Protocol.encode(action), (secondOutgoing.receive() as Frame.Text).readText())
            assertEquals(ConnectionStatus.CONNECTED, repository.status.value)
        } finally {
            releaseCleanup.complete(Unit)
            repository.disconnect()
            client.close()
        }
    }
}
