package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import tech.shaffersoftworks.remotescreens.server.player.FollowedEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

private const val WEBSOCKET_PING_INTERVAL_MS = 30_000L
private const val REQUEST_TIMEOUT_MS = 20_000L
private const val COMMAND_TIMEOUT_MS = 30_000L
private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L
private const val EVENT_BUFFER_CAPACITY = 64
private const val REMOTE_SCREENS_DOMAIN = "remote_screens"
private const val COMPONENT_LOADED_EVENT = "component_loaded"
private const val STATE_CHANGED_EVENT = "state_changed"
private const val AUTH = "auth"

/** Home Assistant over its WebSocket API, reconnecting with backoff. States are kept from `state_changed`. */
class HomeAssistantClient(
    private val webSocketUrl: String,
    private val restBaseUrl: String,
    private val accessToken: String,
    private val scope: CoroutineScope,
) : HomeAssistant {
    private val logger = LoggerFactory.getLogger(HomeAssistantClient::class.java)
    private val httpClient = HttpClient(CIO) {
        install(WebSockets) { pingIntervalMillis = WEBSOCKET_PING_INTERVAL_MS }
        engine { requestTimeout = REQUEST_TIMEOUT_MS }
    }

    private val _isConnected = MutableStateFlow(false)
    private val _states = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    private val _events = MutableSharedFlow<HomeAssistantEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
    )
    private val _config = MutableStateFlow(HomeAssistantConfig())
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val states: StateFlow<Map<String, EntityState>> = _states.asStateFlow()
    override val events: SharedFlow<HomeAssistantEvent> = _events.asSharedFlow()
    override val config: StateFlow<HomeAssistantConfig> = _config.asStateFlow()

    private val nextCommandId = AtomicInteger(1)
    private val pendingCommands = ConcurrentHashMap<Int, CompletableDeferred<HaInbound.Result>>()
    private val sendMutex = Mutex()

    @Volatile
    private var session: DefaultClientWebSocketSession? = null

    // Any failure must end in a reconnect rather than ending the loop; only cancelling the scope stops it.
    @Suppress("TooGenericExceptionCaught")
    fun start() {
        scope.launch {
            var backoffMs = INITIAL_BACKOFF_MS

            while (isActive) {
                try {
                    connectAndListen()
                    backoffMs = INITIAL_BACKOFF_MS
                } catch (timeout: TimeoutCancellationException) {
                    logger.warn("Home Assistant did not answer: {}", timeout.message)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    logger.warn("Home Assistant connection lost: {}", e.message)
                }

                _isConnected.value = false
                session = null

                pendingCommands.values.forEach {
                    it.completeExceptionally(IllegalStateException("disconnected"))
                }

                pendingCommands.clear()
                delay(backoffMs.milliseconds)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private suspend fun connectAndListen() {
        httpClient.webSocket(urlString = webSocketUrl) {
            check(readInbound() is HaInbound.AuthRequired) { "unexpected greeting" }

            send(Frame.Text(outbound(type = AUTH, payload = encodePayload(HaAuth.serializer(), HaAuth(accessToken)))))
            val answer = readInbound()

            check(
                answer is HaInbound.AuthOk,
            ) { "authentication failed: ${(answer as? HaInbound.AuthInvalid)?.message}" }

            session = this
            logger.info("Connected to Home Assistant {}", answer.haVersion)

            val reader = launch {
                for (frame in incoming) {
                    if (frame is Frame.Text) decodeInbound(frame.readText())?.let(::handleMessage)
                }
            }

            loadInitialState()
            _isConnected.value = true
            reader.join()
        }
    }

    /** Config, every state, then the events we follow. */
    private suspend fun loadInitialState() {
        send(SubscribeEvents(COMPONENT_LOADED_EVENT))
        _config.value = send(GetConfig).toConfig(REMOTE_SCREENS_DOMAIN)
        _states.value = send(GetStates).map(EntityStateDto::toEntityState).associateBy { it.entityId }
        send(SubscribeEvents(STATE_CHANGED_EVENT))

        for (eventType in FollowedEvent.TYPES) send(SubscribeEvents(eventType))
    }

    private fun handleMessage(message: HaInbound) {
        when (message) {
            is HaInbound.Result -> pendingCommands.remove(message.id)?.complete(message)
            is HaInbound.Event -> handleEvent(message.event)
            else -> Unit
        }
    }

    private fun handleEvent(event: HaEventEnvelope) {
        when (event.eventType) {
            STATE_CHANGED_EVENT -> readEventData(StateChangedData.serializer(), event)?.let { change ->
                val newState = change.newState?.toEntityState()

                _states.update { states ->
                    if (newState == null) states - change.entityId else states + (change.entityId to newState)
                }
            }
            COMPONENT_LOADED_EVENT -> if (readEventData(ComponentLoadedData.serializer(), event)?.component ==
                REMOTE_SCREENS_DOMAIN
            ) {
                _config.update { homeAssistantConfig -> homeAssistantConfig.copy(isIntegrationLoaded = true) }
            }
        }

        _events.tryEmit(HomeAssistantEvent(event.eventType, event.data))
    }

    private fun <T> readEventData(serializer: KSerializer<T>, event: HaEventEnvelope): T? =
        runCatching { HA_JSON.decodeFromJsonElement(serializer, event.data) }
            .onFailure { logger.debug("Unreadable {} event: {}", event.eventType, it.message) }
            .getOrNull()

    override suspend fun <R> send(request: HaRequest<R>): R {
        val activeSession = checkNotNull(session) { "not connected to Home Assistant" }
        val id = nextCommandId.getAndIncrement()
        val response = CompletableDeferred<HaInbound.Result>()
        pendingCommands[id] = response

        sendMutex.withLock { activeSession.send(
            Frame.Text(outbound(type = request.type, payload = request.payload(), id = id))
        ) }

        val result = withTimeout(COMMAND_TIMEOUT_MS.milliseconds) { response.await() }

        if (!result.success) {
            val error = result.error ?: HaError()
            throw HomeAssistantException(code = error.code, message = error.message)
        }

        return HA_JSON.decodeFromJsonElement(request.resultSerializer, result.result ?: JsonNull)
    }

    override suspend fun callService(
        service: ServiceRef,
        target: List<EntityId>,
        data: ServiceData,
        returnResponse: Boolean,
    ): JsonElement? {
        val entityIds = target.ifEmpty { data.entityIds }

        return send(
            CallService(
                domain = service.domain.value,
                service = service.service,
                serviceData = data.withoutTarget().fields,
                target = entityIds.takeIf { it.isNotEmpty() }?.let { ids -> ServiceTarget(ids.map { it.value }) },
                returnResponse = returnResponse.takeIf { it },
            ),
        )
    }

    override suspend fun fetch(path: String): ByteArray? {
        val response = httpClient.get(
            restBaseUrl.removeSuffix("/api") + path,
        ) { bearerAuth(accessToken) }

        return if (response.status.isSuccess()) response.readRawBytes() else null
    }

    override suspend fun stream(
        path: String,
        onBody: suspend (contentType: String?, body: ByteReadChannel) -> Unit,
    ): Int = httpClient.prepareGet(
        restBaseUrl.removeSuffix("/api") + path,
    ) { bearerAuth(accessToken) }
        .execute { response ->
            if (response.status.isSuccess()) {
                onBody(
                    response.headers[HttpHeaders.ContentType],
                    response.bodyAsChannel(),
                )
            }

            response.status.value
        }

    private suspend fun DefaultClientWebSocketSession.readInbound(): HaInbound? =
        decodeInbound((incoming.receive() as Frame.Text).readText())

    /** Null for a message type this client does not know. */
    private fun decodeInbound(text: String): HaInbound? =
        runCatching { HA_JSON.decodeFromString(HaInbound.serializer(), text) }
            .onFailure { logger.debug("Unread Home Assistant message: {}", it.message) }
            .getOrNull()

    private fun outbound(type: String, payload: JsonObject, id: Int? = null): String {
        val header = buildMap {
            id?.let { put("id", JsonPrimitive(it)) }
            put("type", JsonPrimitive(type))
        }

        return JsonObject(payload + header).toString()
    }
}
