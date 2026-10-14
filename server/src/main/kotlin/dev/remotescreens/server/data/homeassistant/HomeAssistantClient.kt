package dev.remotescreens.server.data.homeassistant

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
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
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val WEBSOCKET_PING_INTERVAL_MS = 30_000L
private const val REQUEST_TIMEOUT_MS = 20_000L
private const val COMMAND_TIMEOUT_MS = 30_000L
private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L
private const val EVENT_BUFFER_CAPACITY = 64

/** Home Assistant over its WebSocket API, reconnecting with backoff. States are kept from `state_changed`. */
class HomeAssistantClient(
    private val webSocketUrl: String,
    private val restBaseUrl: String,
    private val accessToken: String,
    private val scope: CoroutineScope,
) : HomeAssistant {
    private val logger = LoggerFactory.getLogger(HomeAssistantClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(CIO) {
        install(WebSockets) { pingIntervalMillis = WEBSOCKET_PING_INTERVAL_MS }
        engine { requestTimeout = REQUEST_TIMEOUT_MS }
    }

    private val _isConnected = MutableStateFlow(false)
    private val _states = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    private val _events = MutableSharedFlow<HomeAssistantEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    private val _config = MutableStateFlow(HomeAssistantConfig())
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val states: StateFlow<Map<String, EntityState>> = _states.asStateFlow()
    override val events: SharedFlow<HomeAssistantEvent> = _events.asSharedFlow()
    override val config: StateFlow<HomeAssistantConfig> = _config.asStateFlow()

    private val nextCommandId = AtomicInteger(1)
    private val pendingCommands = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val sendMutex = Mutex()

    @Volatile
    private var session: DefaultClientWebSocketSession? = null

    // Any failure, a startup command timing out included, must end in a reconnect rather than ending the loop.
    @Suppress("TooGenericExceptionCaught")
    fun start() {
        scope.launch {
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive) {
                try {
                    connectAndListen()
                    backoffMs = INITIAL_BACKOFF_MS
                } catch (e: Exception) {
                    logger.warn("Home Assistant connection lost: {}", e.message)
                }
                _isConnected.value = false
                session = null
                pendingCommands.values.forEach { it.completeExceptionally(IllegalStateException("disconnected")) }
                pendingCommands.clear()
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private suspend fun connectAndListen() {
        httpClient.webSocket(urlString = webSocketUrl) {
            val greeting = readJson()
            check(greeting.type == "auth_required") { "unexpected greeting" }
            val auth = buildJsonObject {
                put("type", "auth")
                put("access_token", accessToken)
            }
            send(Frame.Text(auth.toString()))
            val answer = readJson()
            check(answer.type == "auth_ok") { "authentication failed: ${answer["message"]}" }
            session = this
            logger.info("Connected to Home Assistant {}", answer["ha_version"]?.jsonPrimitive?.contentOrNull)

            val reader = launch {
                for (frame in incoming) {
                    if (frame is Frame.Text) handleMessage(json.parseToJsonElement(frame.readText()).jsonObject)
                }
            }
            loadInitialState()
            _isConnected.value = true
            reader.join()
        }
    }

    /** Config, every state, then the events we follow. */
    private suspend fun loadInitialState() {
        command("get_config")?.jsonObject?.let { haConfig -> _config.value = parseConfig(haConfig) }
        val allStates = command("get_states")?.jsonArray.orEmpty().mapNotNull { parseState(it.jsonObject) }
        _states.value = allStates.associateBy { it.entityId }
        command("subscribe_events", buildJsonObject { put("event_type", "state_changed") })
        for (eventType in FOLLOWED_EVENTS) command("subscribe_events", buildJsonObject { put("event_type", eventType) })
    }

    private fun handleMessage(message: JsonObject) {
        when (message.type) {
            "result" -> message["id"]?.jsonPrimitive?.int?.let { id -> pendingCommands.remove(id)?.complete(message) }
            "event" -> message["event"]?.jsonObject?.let(::handleEvent)
        }
    }

    private fun handleEvent(event: JsonObject) {
        val eventType = event["event_type"]?.jsonPrimitive?.content ?: return
        val data = event["data"]?.jsonObject ?: JsonObject(emptyMap())
        if (eventType == "state_changed") {
            val entityId = data["entity_id"]?.jsonPrimitive?.content ?: return
            val newState = (data["new_state"] as? JsonObject)?.let(::parseState)
            _states.update { states -> if (newState == null) states - entityId else states + (entityId to newState) }
        }
        _events.tryEmit(HomeAssistantEvent(eventType, data))
    }

    override suspend fun command(type: String, payload: JsonObject): JsonElement? {
        val activeSession = checkNotNull(session) { "not connected to Home Assistant" }
        val id = nextCommandId.getAndIncrement()
        val response = CompletableDeferred<JsonObject>()
        pendingCommands[id] = response
        val body = JsonObject(payload + mapOf("id" to JsonPrimitive(id), "type" to JsonPrimitive(type)))
        sendMutex.withLock { activeSession.send(Frame.Text(body.toString())) }
        val result = withTimeout(COMMAND_TIMEOUT_MS) { response.await() }
        if (result["success"]?.jsonPrimitive?.content != "true") {
            val error = result["error"]?.jsonObject
            throw HomeAssistantException(
                code = error?.get("code")?.jsonPrimitive?.contentOrNull ?: "error",
                message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "failed",
            )
        }
        return result["result"]
    }

    override suspend fun callService(
        domain: String,
        service: String,
        data: JsonObject,
        returnResponse: Boolean,
    ): JsonElement? {
        val target = data["entity_id"]
        val payload = buildJsonObject {
            put("domain", domain)
            put("service", service)
            put("service_data", JsonObject(data - "entity_id"))
            if (target != null) put("target", buildJsonObject { put("entity_id", target) })
            if (returnResponse) put("return_response", true)
        }
        return command("call_service", payload)
    }

    override suspend fun fetch(path: String): ByteArray? {
        val response = httpClient.get(restBaseUrl.removeSuffix("/api") + path) { bearerAuth(accessToken) }
        return if (response.status.isSuccess()) response.readRawBytes() else null
    }

    private suspend fun DefaultClientWebSocketSession.readJson(): JsonObject {
        val frame = incoming.receive()
        return json.parseToJsonElement((frame as Frame.Text).readText()).jsonObject
    }

    private val JsonObject.type: String? get() = this["type"]?.jsonPrimitive?.content

    private fun parseConfig(haConfig: JsonObject): HomeAssistantConfig {
        fun text(key: String) = haConfig[key]?.jsonPrimitive?.contentOrNull
        val unitSystem = haConfig["unit_system"]?.jsonObject
        return HomeAssistantConfig(
            language = text("language") ?: "en",
            country = text("country"),
            timeZone = text("time_zone") ?: "UTC",
            temperatureUnit = unitSystem?.get("temperature")?.jsonPrimitive?.contentOrNull ?: "°C",
            locationName = text("location_name") ?: "Home",
            version = text("version").orEmpty(),
        )
    }

    private fun parseState(stateObject: JsonObject): EntityState? {
        val entityId = stateObject["entity_id"]?.jsonPrimitive?.content ?: return null
        return EntityState(
            entityId = entityId,
            state = stateObject["state"]?.jsonPrimitive?.content ?: "unknown",
            attributes = stateObject["attributes"]?.jsonObject ?: JsonObject(emptyMap()),
            lastChanged = stateObject["last_changed"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    companion object {
        /** Events an automation fires for the screens, for setups without the custom integration. */
        val FOLLOWED_EVENTS = listOf(
            "remote_screens_alert",
            "remote_screens_open_page",
            "remote_screens_wake",
            "remote_screens_close",
        )
    }
}
