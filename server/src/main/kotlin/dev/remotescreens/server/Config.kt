package dev.remotescreens.server

import dev.remotescreens.shared.Protocol
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
) {
    companion object {
        /** The Supervisor's address for ingress; editor requests must come from it. */
        const val INGRESS_PEER = "172.30.32.2"

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
            return Config(
                supervisor = supervisor,
                demo = demo || (!supervisor && token.isEmpty()),
                haWebSocket = if (supervisor) "ws://supervisor/core/websocket" else haUrl.replaceFirst("http", "ws") + "/api/websocket",
                haRest = if (supervisor) "http://supervisor/core/api" else "$haUrl/api",
                token = token,
                dataDir = File(env["REMOTE_SCREENS_DATA"] ?: if (supervisor) "/data" else "data").also { it.mkdirs() },
                staticDir = (env["REMOTE_SCREENS_STATIC"]?.let(::File) ?: File("/app/static")).takeIf { it.isDirectory },
                apkDir = (env["REMOTE_SCREENS_APK"]?.let(::File) ?: File("/app/apk")).takeIf { it.isDirectory },
                ingressHost = if (supervisor) "0.0.0.0" else env["REMOTE_SCREENS_BIND"] ?: "127.0.0.1",
                ingressPort = env["REMOTE_SCREENS_INGRESS_PORT"]?.toIntOrNull() ?: Protocol.INGRESS_PORT,
                playerPort = env["REMOTE_SCREENS_PLAYER_PORT"]?.toIntOrNull() ?: Protocol.PORT,
                hostname = env["HOSTNAME"] ?: "localhost",
                version = Config::class.java.`package`?.implementationVersion ?: "dev",
            )
        }
    }
}
