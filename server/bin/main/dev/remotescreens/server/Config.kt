package dev.remotescreens.server

import dev.remotescreens.shared.Protocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Where the add-on runs. Inside the Supervisor everything comes from the environment the Supervisor sets; outside
 * it (Docker, a clone) from `HA_URL` and `HA_TOKEN_FILE`, with the editor on 127.0.0.1 only.
 */
data class Config(
    val supervisor: Boolean,
    val demo: Boolean,
    val haWebSocket: String,
    val haRest: String,
    val token: String,
    val dataDir: File,
    val staticDir: File?,
    val apkDir: File?,
    val ingressHost: String,
    val ingressPort: Int,
    val playerPort: Int,
    val hostname: String,
    val version: String,
    /** Map tile URL with `{z}`, `{x}` and `{y}`. */
    val mapTiles: String = OSM_TILES,
) {
    companion object {
        const val OSM_TILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            val supervisorToken = env["SUPERVISOR_TOKEN"]
            val demo = env["REMOTE_SCREENS_DEMO"] == "1"
            val supervisor = supervisorToken != null && !demo
            val haUrl = (env["HA_URL"] ?: "http://127.0.0.1:8123").trimEnd('/')
            val token = when {
                supervisor -> supervisorToken!!
                env["HA_TOKEN_FILE"] != null -> File(env["HA_TOKEN_FILE"]!!).readText().trim()
                else -> env["HA_TOKEN"].orEmpty()
            }
            val dataDir = File(env["REMOTE_SCREENS_DATA"] ?: if (supervisor) "/data" else "data").also { it.mkdirs() }
            val options = addonOptions(File(dataDir, "options.json"))
            return Config(
                supervisor = supervisor,
                demo = demo || (!supervisor && token.isEmpty()),
                haWebSocket = if (supervisor) "ws://supervisor/core/websocket" else haUrl.replaceFirst("http", "ws") + "/api/websocket",
                haRest = if (supervisor) "http://supervisor/core/api" else "$haUrl/api",
                token = token,
                dataDir = dataDir,
                staticDir = (env["REMOTE_SCREENS_STATIC"]?.let(::File) ?: File("/app/static")).takeIf { it.isDirectory },
                apkDir = (env["REMOTE_SCREENS_APK"]?.let(::File) ?: File("/app/apk")).takeIf { it.isDirectory },
                ingressHost = if (supervisor) "0.0.0.0" else env["REMOTE_SCREENS_BIND"] ?: "127.0.0.1",
                ingressPort = env["REMOTE_SCREENS_INGRESS_PORT"]?.toIntOrNull() ?: Protocol.INGRESS_PORT,
                playerPort = env["REMOTE_SCREENS_PLAYER_PORT"]?.toIntOrNull() ?: Protocol.PORT,
                hostname = env["HOSTNAME"] ?: "localhost",
                version = Config::class.java.`package`?.implementationVersion ?: "dev",
                mapTiles = mapTiles(env["REMOTE_SCREENS_MAP_TILES"] ?: options["map_tiles"]),
            )
        }

        /** A tile URL needs all three placeholders and http(s); anything else falls back to OpenStreetMap. */
        fun mapTiles(raw: String?): String {
            val url = raw?.trim().orEmpty()
            val ok = (url.startsWith("https://") || url.startsWith("http://")) && listOf("{z}", "{x}", "{y}").all { it in url }
            return if (ok) url else OSM_TILES
        }

        /**
         * Whether an editor request comes from the Supervisor's network (172.30.32.0/23), which ingress uses.
         * On the host network the peer isn't always the Supervisor's own 172.30.32.2, and Java may report it
         * IPv6-mapped.
         */
        fun isIngressPeer(address: String): Boolean {
            val parts = address.removePrefix("::ffff:").split('.').mapNotNull { it.toIntOrNull() }
            return parts.size == 4 && parts[0] == 172 && parts[1] == 30 && parts[2] in 32..33
        }

        /** The options the Supervisor writes from the add-on's configuration tab. */
        private fun addonOptions(file: File): Map<String, String> = runCatching {
            Json.parseToJsonElement(file.readText()).jsonObject.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }.toMap()
        }.getOrDefault(emptyMap())
    }
}
