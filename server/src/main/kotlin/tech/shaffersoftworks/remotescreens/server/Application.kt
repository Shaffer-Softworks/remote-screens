package tech.shaffersoftworks.remotescreens.server

import tech.shaffersoftworks.remotescreens.server.config.ServerConfig
import tech.shaffersoftworks.remotescreens.server.di.ServerDependencies
import tech.shaffersoftworks.remotescreens.server.network.MdnsAdvertiser
import tech.shaffersoftworks.remotescreens.server.network.SupervisorClient
import tech.shaffersoftworks.remotescreens.server.plugins.IntegrationKey
import tech.shaffersoftworks.remotescreens.server.plugins.OnlyIngress
import tech.shaffersoftworks.remotescreens.server.plugins.configureErrorHandling
import tech.shaffersoftworks.remotescreens.server.plugins.configureSerialization
import tech.shaffersoftworks.remotescreens.server.routes.editorRoutes
import tech.shaffersoftworks.remotescreens.server.routes.integrationRoutes
import tech.shaffersoftworks.remotescreens.server.routes.playerRoutes
import tech.shaffersoftworks.remotescreens.server.intercom.SignalingService
import tech.shaffersoftworks.remotescreens.server.intercom.signalingRoutes
import io.ktor.client.HttpClient
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO

private const val HTTP_CLIENT_TIMEOUT_MS = 15_000L
private const val SHUTDOWN_GRACE_PERIOD_MS = 500L
private const val SHUTDOWN_TIMEOUT_MS = 2_000L
private const val WEBSOCKET_PING_SECONDS = 20
private const val INTERCOM_PING_SECONDS = 30
private const val INTERCOM_MAX_FRAME_BYTES = 1L * 1024 * 1024
private const val MAX_WEBSOCKET_FRAME_BYTES = 32L * 1024 * 1024
private const val ONE_YEAR_SECONDS = 31_536_000

private val logger = LoggerFactory.getLogger("RemoteScreens")

fun main() {
    val httpClient = HttpClient(ClientCIO) { engine { requestTimeout = HTTP_CLIENT_TIMEOUT_MS } }
    val environmentConfig = ServerConfig.fromEnvironment()
    val supervisorClient = SupervisorClient(httpClient, environmentConfig.accessToken)
    val config = resolveIngressPort(environmentConfig, supervisorClient)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val dependencies = ServerDependencies(config, httpClient, scope)
    dependencies.start()
    logger.info("Remote Screens {} ({})", config.version, describeMode(config))

    val mdnsAdvertiser = MdnsAdvertiser(config.playerPort, config.version).also { it.start() }
    if (config.isSupervisor) {
        scope.launch {
            runCatching { supervisorClient.announce(
                config.playerPort,
                dependencies.apiKeyRepository.apiKey,
            ) }
                .onFailure { logger.warn("Discovery failed: {}", it.message) }
        }
    }

    // One server per port: Ktor routes `port(...)` by the Host header, which ingress sets to Home Assistant's.
    val editorServer = embeddedServer(CIO, host = config.ingressHost, port = config.ingressPort) {
        editorModule(dependencies)
    }
    val playerServer = embeddedServer(CIO, host = "0.0.0.0", port = config.playerPort) {
        playerModule(dependencies)
    }
    val intercomServer = embeddedServer(CIO, host = "0.0.0.0", port = config.intercomPort) {
        intercomModule(dependencies.signaling)
    }
    Runtime.getRuntime().addShutdownHook(
        Thread {
            mdnsAdvertiser.stop()
            editorServer.stop(SHUTDOWN_GRACE_PERIOD_MS, SHUTDOWN_TIMEOUT_MS)
            playerServer.stop(SHUTDOWN_GRACE_PERIOD_MS, SHUTDOWN_TIMEOUT_MS)
            intercomServer.stop(SHUTDOWN_GRACE_PERIOD_MS, SHUTDOWN_TIMEOUT_MS)
        },
    )
    editorServer.start(wait = false)
    intercomServer.start(wait = false)
    playerServer.start(wait = true)
}

/** Asks the Supervisor for the ingress port it chose, unless the environment fixes one. */
private fun resolveIngressPort(config: ServerConfig, supervisorClient: SupervisorClient): ServerConfig {
    val isPortFixed = System.getenv("REMOTE_SCREENS_INGRESS_PORT") != null
    if (!config.isSupervisor || isPortFixed) return config
    val ingressPort = runBlocking { supervisorClient.ingressPort() } ?: config.ingressPort
    return config.copy(ingressPort = ingressPort)
}

private fun describeMode(config: ServerConfig): String = when {
    config.isDemo -> "demo home"
    config.isSupervisor -> "Home Assistant add-on"
    else -> "connecting to ${config.homeAssistantRestUrl}"
}

/** The editor, reached through ingress. */
fun Application.editorModule(dependencies: ServerDependencies) {
    configureSerialization()
    configureErrorHandling()
    install(WebSockets) {
        pingPeriod = INTERCOM_PING_SECONDS.seconds
        maxFrameSize = INTERCOM_MAX_FRAME_BYTES
    }
    val config = dependencies.config
    routing {
        install(OnlyIngress) { isEnforced = config.isSupervisor }
        signalingRoutes(dependencies.signaling)
        editorRoutes(dependencies)
        val staticDir = config.staticDir
        if (staticDir != null) {
            serveEditor(staticDir)
        } else {
            get("/") {
                call.respondText(
                    "Remote Screens ${config.version}: the editor is not built. Run `npm run build` in editor/.",
                    ContentType.Text.Plain,
                )
            }
        }
    }
}

private fun Route.serveEditor(staticDir: File) {
    staticFiles("/", staticDir) {
        default("index.html")
        // Built assets have hashed names; index.html must be refetched so an update reaches the browser.
        cacheControl { file ->
            if (file.parentFile?.name == "assets") {
                listOf(
                    CacheControl.MaxAge(
                        ONE_YEAR_SECONDS,
                        visibility = CacheControl.Visibility.Public,
                    ),
                )
            } else {
                listOf(CacheControl.NoCache(null))
            }
        }
    }
}

/** Intercom signaling for tablets. The panel uses the same hub through [editorModule]. */
fun Application.intercomModule(signaling: SignalingService) {
    install(WebSockets) {
        pingPeriod = INTERCOM_PING_SECONDS.seconds
        maxFrameSize = INTERCOM_MAX_FRAME_BYTES
    }
    routing { signalingRoutes(signaling) }
}

/** Tablets and the integration, on the network. */
fun Application.playerModule(dependencies: ServerDependencies) {
    configureSerialization()
    configureErrorHandling()
    install(WebSockets) {
        pingPeriod = WEBSOCKET_PING_SECONDS.seconds
        maxFrameSize = MAX_WEBSOCKET_FRAME_BYTES
    }
    routing {
        playerRoutes(
            dependencies.playerHub,
            dependencies.playerApkRepository,
            dependencies.enrollmentService,
            dependencies.cameraStreamService,
        )
        route("/integration") {
            install(IntegrationKey) { apiKey = dependencies.apiKeyRepository.apiKey }
            integrationRoutes(dependencies.screenService, dependencies.playerHub)
        }
    }
}
