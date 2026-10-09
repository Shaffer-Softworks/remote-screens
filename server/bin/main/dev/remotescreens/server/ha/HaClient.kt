package dev.remotescreens.server.ha

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

/** Home Assistant over its WebSocket API, reconnecting with backoff. States are kept from `state_changed`. */
class HaClient(
    private val wsUrl: String,
    private val restBase: String,
    private val token: String,
    private val scope: CoroutineScope,
) : HomeAssistant {
    private val log = LoggerFactory.getLogger(HaClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO) {
        install(WebSockets) { pingIntervalMillis = 30_000 }
        engine { requestTimeout = 20_000 }
    }

    private val _connected = MutableStateFlow(false)
    private val _states = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    private val _events = MutableSharedFlow<HaEvent>(extraBufferCapacity = 64)
    private val _config = MutableStateFlow(HaConfig())
    override val connected: StateFlow<Boolean> = _connected
    override val states: StateFlow<Map<String, EntityState>> = _states
    override val events: SharedFlow<HaEvent> = _events
    override val config: StateFlow<HaConfig> = _config

    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val sendLock = Mutex()
    @Volatile private var session: DefaultClientWebSocketSession? = null

    fun start() {
        scope.launch {
            var backoff = 1_000L
            while (isActive) {
                try {
                    run()
                    backoff = 1_000L
                } catch (e: Exception) {
                    log.warn("Home Assistant connection lost: {}", e.message)
                }
                _connected.value = false
                session = null
                pending.values.forEach { it.completeExceptionally(IllegalStateException("disconnected")) }
                pending.clear()
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    private suspend fun run() {
        http.webSocket(urlString = wsUrl) {
            val required = readJson()
            check(required["type"]?.jsonPrimitive?.content == "auth_required") { "unexpected greeting" }
            send(Frame.Text(buildJsonObject { put("type", "auth"); put("access_token", token) }.toString()))
            val answer = readJson()
            check(answer["type"]?.jsonPrimitive?.content == "auth_ok") { "authentication failed: ${answer["message"]}" }
            session = this
            log.info("Connected to Home Assistant {}", answer["ha_version"]?.jsonPrimitive?.contentOrNull)

            val reader = launch {
                for (frame in incoming) if (frame is Frame.Text) handle(json.parseToJsonElement(frame.readText()).jsonObject)
            }
            prime()
            _connected.value = true
            reader.join()
        }
    }

    /** Config, every state, then the events we follow. */
    private suspend fun prime() {
        command("get_config")?.jsonObject?.let { c ->
            val units = c["unit_system"]?.jsonObject
            _config.value = HaConfig(
                language = c["language"]?.jsonPrimitive?.contentOrNull ?: "en",
                country = c["country"]?.jsonPrimitive?.contentOrNull,
                timeZone = c["time_zone"]?.jsonPrimitive?.contentOrNull ?: "UTC",
                temperatureUnit = units?.get("temperature")?.jsonPrimitive?.contentOrNull ?: "°C",
                locationName = c["location_name"]?.jsonPrimitive?.contentOrNull ?: "Home",
                version = c["version"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }
        val all = command("get_states")?.jsonArray.orEmpty().mapNotNull { parseState(it.jsonObject) }
        _states.value = all.associateBy { it.entityId }
        command("subscribe_events", buildJsonObject { put("event_type", "state_changed") })
        for (type in FOLLOWED_EVENTS) command("subscribe_events", buildJsonObject { put("event_type", type) })
    }

    private fun handle(msg: JsonObject) {
        val id = msg["id"]?.jsonPrimitive?.int
        when (msg["type"]?.jsonPrimitive?.content) {
            "result" -> id?.let { pending.remove(it)?.complete(msg) }
            "event" -> {
                val event = msg["event"]?.jsonObject ?: return
                val type = event["event_type"]?.jsonPrimitive?.content ?: return
                val data = event["data"]?.jsonObject ?: JsonObject(emptyMap())
                if (type == "state_changed") {
                    val entityId = data["entity_id"]?.jsonPrimitive?.content ?: return
                    val newState = (data["new_state"] as? JsonObject)?.let(::parseState)
                    _states.update { if (newState == null) it - entityId else it + (entityId to newState) }
                }
                _events.tryEmit(HaEvent(type, data))
            }
        }
    }

    override suspend fun command(type: String, payload: JsonObject): JsonElement? {
        val s = session ?: throw IllegalStateException("not connected to Home Assistant")
        val id = ids.getAndIncrement()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val body = JsonObject(payload + mapOf("id" to JsonPrimitive(id), "type" to JsonPrimitive(type)))
        sendLock.withLock { s.send(Frame.Text(body.toString())) }
        val result = withTimeout(30_000) { deferred.await() }
        if (result["success"]?.jsonPrimitive?.content != "true") {
            val error = result["error"]?.jsonObject
            throw HaError(error?.get("code")?.jsonPrimitive?.contentOrNull ?: "error", error?.get("message")?.jsonPrimitive?.contentOrNull ?: "failed")
        }
        return result["result"]
    }

    override suspend fun callService(domain: String, service: String, data: JsonObject, returnResponse: Boolean): JsonElement? {
        val target = data["entity_id"]
        val serviceData = JsonObject(data - "entity_id")
        val payload = buildJsonObject {
            put("domain", domain)
            put("service", service)
            put("service_data", serviceData)
            if (target != null) put("target", buildJsonObject { put("entity_id", target) })
            if (returnResponse) put("return_response", true)
        }
        return command("call_service", payload)
    }

    override suspend fun fetch(path: String): ByteArray? {
        val response = http.get(restBase.removeSuffix("/api") + path) { bearerAuth(token) }
        return if (response.status.isSuccess()) response.readRawBytes() else null
    }

    private suspend fun DefaultClientWebSocketSession.readJson(): JsonObject {
        val frame = incoming.receive()
        return json.parseToJsonElement((frame as Frame.Text).readText()).jsonObject
    }

    private fun parseState(o: JsonObject): EntityState? {
        val id = o["entity_id"]?.jsonPrimitive?.content ?: return null
        return EntityState(
            entityId = id,
            state = o["state"]?.jsonPrimitive?.content ?: "unknown",
            attributes = o["attributes"]?.jsonObject ?: JsonObject(emptyMap()),
            lastChanged = o["last_changed"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    companion object {
        /** Events an automation fires for the screens, for setups without the custom integration. */
        val FOLLOWED_EVENTS = listOf("remote_screens_alert", "remote_screens_open_page", "remote_screens_wake", "remote_screens_close")
    }
}

class HaError(val code: String, message: String) : Exception(message)
