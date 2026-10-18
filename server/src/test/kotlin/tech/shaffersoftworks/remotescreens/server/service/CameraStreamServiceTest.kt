package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantConfig
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantEvent
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantException
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val TOKEN = "abc123"
private const val FIVE_MINUTES_MS = 300_000L

/** A Home Assistant whose cameras stream behind [TOKEN], except `camera.still`, which cannot. */
class StreamingHome : HomeAssistant {
    override val isConnected: StateFlow<Boolean> = MutableStateFlow(true)
    override val states: StateFlow<Map<String, EntityState>> = MutableStateFlow(emptyMap())
    override val events: SharedFlow<HomeAssistantEvent> = MutableSharedFlow()
    override val config: StateFlow<HomeAssistantConfig> = MutableStateFlow(HomeAssistantConfig())
    val commands = mutableListOf<Pair<String, JsonObject>>()
    val fetched = mutableListOf<String>()

    override suspend fun callService(
        domain: String,
        service: String,
        data: JsonObject,
        returnResponse: Boolean,
    ): JsonElement? = null

    override suspend fun command(type: String, payload: JsonObject): JsonElement {
        commands += type to payload
        if (payload["entity_id"]?.jsonPrimitive?.content == "camera.still") {
            throw HomeAssistantException("home_assistant_error", "Camera does not support stream")
        }
        return buildJsonObject { put("url", "/api/hls/$TOKEN/master_playlist.m3u8") }
    }

    override suspend fun fetch(path: String): ByteArray? = null

    override suspend fun stream(
        path: String,
        onBody: suspend (contentType: String?, body: ByteReadChannel) -> Unit,
    ): Int {
        fetched += path
        if (!path.startsWith("/api/hls/$TOKEN/")) return 404
        onBody("application/vnd.apple.mpegurl", ByteReadChannel("#EXTM3U\n"))
        return 200
    }
}

class CameraStreamServiceTest {
    private var currentTime = 1_000L
    private val home = StreamingHome()
    private val service = CameraStreamService(home) { currentTime }

    private suspend fun relay(token: String, file: String, query: String = ""): Pair<Int?, String?> {
        var body: String? = null
        val status = service.relay(token, file, query) { _, channel ->
            body = channel.readRemaining().readByteArray().decodeToString()
        }
        return status to body
    }

    @Test
    fun aCameraStreamsFromThePlayerPort() = runTest {
        assertEquals(
            "/stream/hls/$TOKEN/master_playlist.m3u8",
            service.hlsPath("camera.front_door"),
        )

        val (type, payload) = home.commands.single()
        assertEquals("camera/stream", type)
        assertEquals("camera.front_door", payload["entity_id"]?.jsonPrimitive?.content)
        assertEquals("hls", payload["format"]?.jsonPrimitive?.content)
    }

    @Test
    fun aWarmStreamIsReusedWithoutCallingCameraStreamAgain() = runTest {
        assertEquals(
            "/stream/hls/$TOKEN/master_playlist.m3u8",
            service.hlsPath("camera.front_door"),
        )
        assertEquals(1, home.commands.size)

        assertEquals(
            "/stream/hls/$TOKEN/master_playlist.m3u8",
            service.hlsPath("camera.front_door"),
        )
        assertEquals(1, home.commands.size, "reopening the card should reuse the warm token")
    }

    @Test
    fun aCameraThatCannotStreamHasNoPath() = runTest {
        assertNull(service.hlsPath("camera.still"))
    }

    @Test
    fun segmentsAndLowLatencyQueriesAreRelayed() = runTest {
        service.hlsPath("camera.front_door")

        assertEquals(200 to "#EXTM3U\n", relay(TOKEN, "playlist.m3u8", "_HLS_msn=12&_HLS_part=3"))
        assertEquals(200, relay(TOKEN, "segment/12.m4s").first)
        // LL-HLS parts are named segment/{seq}.{part}.m4s — a second dot in the file name.
        assertEquals(200, relay(TOKEN, "segment/12.0.m4s").first)
        assertEquals(
            listOf(
                "/api/hls/$TOKEN/playlist.m3u8?_HLS_msn=12&_HLS_part=3",
                "/api/hls/$TOKEN/segment/12.m4s",
                "/api/hls/$TOKEN/segment/12.0.m4s",
            ),
            home.fetched,
        )
    }

    @Test
    fun onlyTokensHandedOutAreRelayed() = runTest {
        assertNull(relay(TOKEN, "playlist.m3u8").first)
        assertEquals(emptyList(), home.fetched)
    }

    @Test
    fun pathsOutsideTheStreamAreRefused() = runTest {
        service.hlsPath("camera.front_door")

        assertNull(relay(TOKEN, "../../states").first)
        assertNull(relay(TOKEN, "segment/../../config.m3u8").first)
        assertEquals(emptyList(), home.fetched)
    }

    @Test
    fun aStreamNobodyWatchesIsForgotten() = runTest {
        service.hlsPath("camera.front_door")
        currentTime += FIVE_MINUTES_MS - 1
        assertEquals(200, relay(TOKEN, "playlist.m3u8").first, "watching keeps it")

        currentTime += FIVE_MINUTES_MS + 1
        assertNull(relay(TOKEN, "playlist.m3u8").first)
    }
}
