package tech.shaffersoftworks.remotescreens.server.config

import tech.shaffersoftworks.remotescreens.shared.Protocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

private const val DEFAULT_HOME_ASSISTANT_URL = "http://127.0.0.1:8123"
private const val SUPERVISOR_WEBSOCKET_URL = "ws://supervisor/core/websocket"
private const val SUPERVISOR_REST_URL = "http://supervisor/core/api"
private const val ALL_INTERFACES = "0.0.0.0"
private const val LOOPBACK = "127.0.0.1"
private const val IPV4_OCTETS = 4
private const val INGRESS_NETWORK_FIRST_OCTET = 172
private const val INGRESS_NETWORK_SECOND_OCTET = 30
private val INGRESS_NETWORK_THIRD_OCTETS = 32..33

/**
 * Where the add-on runs. Inside the Supervisor everything comes from the environment the Supervisor sets; outside
 * it (Docker, a clone) from `HA_URL` and `HA_TOKEN_FILE`, with the editor on 127.0.0.1 only.
 */
data class ServerConfig(
    val isSupervisor: Boolean,
    val isDemo: Boolean,
    val homeAssistantWebSocketUrl: String,
    val homeAssistantRestUrl: String,
    /** The Supervisor token inside the Supervisor, else a long-lived Home Assistant token. */
    val accessToken: String,
    val dataDir: File,
    val staticDir: File?,
    val apkDir: File?,
    val ingressHost: String,
    val ingressPort: Int,
    val playerPort: Int,
    val intercomPort: Int = Protocol.INTERCOM_PORT,
    val hostname: String,
    val version: String,
    /** Map tile URL with `{z}`, `{x}` and `{y}`. */
    val mapTileUrl: String = OPENSTREETMAP_TILE_URL,
) {
    companion object {
        const val OPENSTREETMAP_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): ServerConfig {
            val supervisorToken = env["SUPERVISOR_TOKEN"]
            val isDemoRequested = env["REMOTE_SCREENS_DEMO"] == "1"
            val isSupervisor = supervisorToken != null && !isDemoRequested
            val accessToken = resolveAccessToken(env, supervisorToken.takeIf { isSupervisor })
            val dataDir = resolveDataDir(env, isSupervisor)
            val addonOptions = readAddonOptions(File(dataDir, "options.json"))
            val homeAssistantUrls = homeAssistantUrls(env, isSupervisor)
            return ServerConfig(
                isSupervisor = isSupervisor,
                isDemo = isDemoRequested || (!isSupervisor && accessToken.isEmpty()),
                homeAssistantWebSocketUrl = homeAssistantUrls.first,
                homeAssistantRestUrl = homeAssistantUrls.second,
                accessToken = accessToken,
                dataDir = dataDir,
                staticDir = existingDirectory(env["REMOTE_SCREENS_STATIC"] ?: "/app/static"),
                apkDir = existingDirectory(env["REMOTE_SCREENS_APK"] ?: "/app/apk"),
                ingressHost = if (isSupervisor) ALL_INTERFACES else env["REMOTE_SCREENS_BIND"] ?: LOOPBACK,
                ingressPort = env.intOrNull("REMOTE_SCREENS_INGRESS_PORT") ?: Protocol.INGRESS_PORT,
                playerPort = env.intOrNull("REMOTE_SCREENS_PLAYER_PORT") ?: Protocol.PORT,
                intercomPort = env.intOrNull("REMOTE_SCREENS_INTERCOM_PORT") ?: Protocol.INTERCOM_PORT,
                hostname = env["HOSTNAME"] ?: "localhost",
                version = implementationVersion(),
                mapTileUrl = mapTileUrlOrDefault(
                    env["REMOTE_SCREENS_MAP_TILES"] ?: addonOptions["map_tiles"],
                ),
            )
        }

        /** The Supervisor's token inside the Supervisor, else a token from `HA_TOKEN_FILE` or `HA_TOKEN`. */
        private fun resolveAccessToken(env: Map<String, String>, supervisorToken: String?): String {
            val tokenFile = env["HA_TOKEN_FILE"]
            return when {
                supervisorToken != null -> supervisorToken
                tokenFile != null -> File(tokenFile).readText().trim()
                else -> env["HA_TOKEN"].orEmpty()
            }
        }

        /** Home Assistant's WebSocket and REST URLs: through the Supervisor's proxy, else from `HA_URL`. */
        private fun homeAssistantUrls(env: Map<String, String>, isSupervisor: Boolean): Pair<String, String> {
            if (isSupervisor) return SUPERVISOR_WEBSOCKET_URL to SUPERVISOR_REST_URL
            val homeAssistantUrl = (env["HA_URL"] ?: DEFAULT_HOME_ASSISTANT_URL).trimEnd('/')
            return homeAssistantUrl.replaceFirst(
                "http",
                "ws",
            ) + "/api/websocket" to "$homeAssistantUrl/api"
        }

        private fun resolveDataDir(env: Map<String, String>, isSupervisor: Boolean): File {
            val path = env["REMOTE_SCREENS_DATA"] ?: if (isSupervisor) "/data" else "data"
            return File(path).also { it.mkdirs() }
        }

        /** The version in the jar's manifest; "dev" when run from the IDE or Gradle. */
        private fun implementationVersion(): String =
            ServerConfig::class.java.`package`?.implementationVersion ?: "dev"

        private fun Map<String, String>.intOrNull(key: String): Int? = this[key]?.toIntOrNull()

        /** A tile URL needs all three placeholders and http(s); anything else falls back to OpenStreetMap. */
        fun mapTileUrlOrDefault(raw: String?): String {
            val url = raw?.trim().orEmpty()
            val isHttp = url.startsWith("https://") || url.startsWith("http://")
            val hasPlaceholders = listOf("{z}", "{x}", "{y}").all { it in url }
            return if (isHttp && hasPlaceholders) url else OPENSTREETMAP_TILE_URL
        }

        /**
         * Whether an editor request comes from the Supervisor's network (172.30.32.0/23), which ingress uses.
         * On the host network the peer isn't always the Supervisor's own 172.30.32.2, and Java may report it
         * IPv6-mapped.
         */
        fun isIngressPeer(address: String): Boolean {
            val octets = address.removePrefix("::ffff:").split('.').mapNotNull { it.toIntOrNull() }
            return octets.size == IPV4_OCTETS &&
                octets[0] == INGRESS_NETWORK_FIRST_OCTET &&
                octets[1] == INGRESS_NETWORK_SECOND_OCTET &&
                octets[2] in INGRESS_NETWORK_THIRD_OCTETS
        }

        private fun existingDirectory(path: String): File? = File(path).takeIf { it.isDirectory }

        /** The options the Supervisor writes from the add-on's configuration tab. */
        private fun readAddonOptions(file: File): Map<String, String> = runCatching {
            Json.parseToJsonElement(file.readText()).jsonObject
                .mapNotNull { (key, value) -> (value as? JsonPrimitive)?.content?.let { key to it } }
                .toMap()
        }.getOrDefault(emptyMap())
    }
}
