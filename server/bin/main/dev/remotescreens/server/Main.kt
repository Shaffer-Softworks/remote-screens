package dev.remotescreens.server

import dev.remotescreens.server.ha.DemoHome
import dev.remotescreens.server.ha.DemoLayout
import dev.remotescreens.server.ha.HaClient
import dev.remotescreens.server.ha.HomeAssistant
import dev.remotescreens.server.store.LayoutStore
import dev.remotescreens.shared.Protocol
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Base64
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("RemoteScreens")

fun main() {
    val http = HttpClient(ClientCIO) { engine { requestTimeout = 15_000 } }
    val config = Config.fromEnv().let { c ->
        if (!c.supervisor || System.getenv("REMOTE_SCREENS_INGRESS_PORT") != null) c
        else c.copy(ingressPort = runBlocking { supervisorIngressPort(http, c.token) } ?: c.ingressPort)
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val ha: HomeAssistant = if (config.demo) DemoHome() else HaClient(config.haWebSocket, config.haRest, config.token, scope).also { it.start() }
    val store = LayoutStore(config.dataDir)
    if (config.demo && store.data.value.screens.isEmpty()) store.update { it.copy(screens = listOf(DemoLayout.screen())) }
    val languages = Languages(ha, store)
    val hub = PlayerHub(config, ha, store, DataFetcher(ha), Pictures(ha, http, config.mapTiles), languages, scope, apk = { apk(config) })
    val apiKey = apiKey(config.dataDir)
    hub.start()
    scope.launch { ha.connected.collect { if (it) languages.loadHomeAssistantWords() } }
    log.info("Remote Screens {} ({})", config.version, when {
        config.demo -> "demo home"
        config.supervisor -> "Home Assistant add-on"
        else -> "connecting to ${config.haRest}"
    })

    val mdns = runCatching { advertise(config) }.onFailure { log.warn("mDNS not available: {}", it.message) }.getOrNull()
    if (config.supervisor) scope.launch { runCatching { discovery(config, http, apiKey) }.onFailure { log.warn("Discovery failed: {}", it.message) } }

    // One server per port: Ktor routes `port(...)` by the Host header, which ingress sets to Home Assistant's.
    val editor = embeddedServer(CIO, host = config.ingressHost, port = config.ingressPort) { editorModule(config, ha, store, hub) }
    val players = embeddedServer(CIO, host = "0.0.0.0", port = config.playerPort) { playerModule(config, store, hub, apiKey) }
    Runtime.getRuntime().addShutdownHook(Thread {
        mdns?.unregisterAllServices()
        mdns?.close()
        editor.stop(500, 2000)
        players.stop(500, 2000)
    })
    editor.start(wait = false)
    players.start(wait = true)
}

private fun Application.common() {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }) }
    install(StatusPages) {
        exception<BadRequest> { call, e -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message)) }
        exception<ContentTransformationException> { call, e -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request"))) }
        exception<SerializationException> { call, e -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request"))) }
        exception<Throwable> { call, e ->
            log.error("Request failed", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error")))
        }
    }
}

/** The editor, reached through ingress. */
fun Application.editorModule(config: Config, ha: HomeAssistant, store: LayoutStore, hub: PlayerHub) {
    common()
    val onlyIngress = createRouteScopedPlugin("OnlyIngress") {
        onCall { call ->
            val peer = call.request.origin.remoteAddress
            if (config.supervisor && !Config.isIngressPeer(peer)) {
                log.warn("Refused an editor request from {}", peer)
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
    routing {
        install(onlyIngress)
        editorApi(config, ha, store, hub) { apk(config) }
        val static = config.staticDir
        if (static != null) {
            staticFiles("/", static) {
                default("index.html")
                // Built assets have hashed names; index.html must be refetched so an update reaches the browser.
                cacheControl { file ->
                    if (file.parentFile?.name == "assets") listOf(CacheControl.MaxAge(31_536_000, visibility = CacheControl.Visibility.Public))
                    else listOf(CacheControl.NoCache(null))
                }
            }
        } else {
            get("/") { call.respondText("Remote Screens ${config.version}: the editor is not built. Run `npm run build` in editor/.", ContentType.Text.Plain) }
        }
    }
}

/** Tablets and the integration, on the network. */
fun Application.playerModule(config: Config, store: LayoutStore, hub: PlayerHub, apiKey: String) {
    common()
    install(WebSockets) { pingPeriod = 20.seconds; maxFrameSize = 32L * 1024 * 1024 }
    val integrationKey = createRouteScopedPlugin("IntegrationKey") {
        onCall { call ->
            if (call.request.header(HttpHeaders.Authorization) != "Bearer $apiKey") call.respond(HttpStatusCode.Unauthorized)
        }
    }
    routing {
        get("/health") { call.respondText("ok") }
        webSocket("/player") { hub.serve(this, call.request.origin.remoteAddress) }
        get("/player/apk") {
            val info = apk(config)
            if (info == null || !PlayerHub.trusted(store, call.request.queryParameters["token"])) return@get call.respond(HttpStatusCode.NotFound)
            call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=remote-screens.apk")
            call.respondFile(info.file)
        }
        route("/integration") {
            install(integrationKey)
            integrationApi(store, hub)
        }
    }
}

/**
 * On the host network a fixed ingress port could be taken by another add-on, so `ingress_port: 0` lets the
 * Supervisor choose a free one, which it then tells us here.
 */
private suspend fun supervisorIngressPort(http: HttpClient, token: String): Int? = runCatching {
    val body = http.get("http://supervisor/addons/self/info") { bearerAuth(token) }.bodyAsText()
    Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("ingress_port")?.jsonPrimitive?.int?.takeIf { it > 0 }
}.onFailure { log.warn("Could not ask the Supervisor for the ingress port: {}", it.message) }.getOrNull()

/** The key the integration sends; made once and kept with the layouts. */
private fun apiKey(dir: File): String {
    val f = File(dir, "api_key")
    if (f.exists()) return f.readText().trim()
    val key = ByteArray(24).also(SecureRandom()::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    f.writeText(key)
    f.setReadable(false, false)
    f.setReadable(true, true)
    return key
}

/** The player app bundled in the image, with `apk/version.json` saying which version it is. */
private fun apk(config: Config): ApkInfo? {
    val dir = config.apkDir ?: return null
    val file = File(dir, "remote-screens.apk").takeIf { it.isFile } ?: return null
    val meta = runCatching { Json.parseToJsonElement(File(dir, "version.json").readText()).jsonObject }.getOrNull() ?: return null
    return ApkInfo(meta["versionCode"]!!.jsonPrimitive.int, meta["versionName"]?.jsonPrimitive?.contentOrNull ?: "", file)
}

/** Lets tablets on the network find the add-on without typing an address. */
private fun advertise(config: Config): JmDNS {
    val address = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.name.startsWith("docker") && !it.name.startsWith("veth") && !it.name.startsWith("hassio") }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    val mdns = JmDNS.create(address, "remote-screens")
    val info = ServiceInfo.create(Protocol.MDNS_TYPE, "Remote Screens", config.playerPort, 0, 0, mapOf("version" to config.version, "protocol" to Protocol.VERSION.toString()))
    mdns.registerService(info)
    log.info("Advertised {} on {}:{}", Protocol.MDNS_TYPE, address?.hostAddress, config.playerPort)
    return mdns
}

/** Tells Home Assistant the add-on is here, so the integration sets itself up with the key. */
private suspend fun discovery(config: Config, http: HttpClient, apiKey: String) {
    val body = buildJsonObject {
        put("service", "remote_screens")
        put("config", buildJsonObject {
            put("host", "127.0.0.1")
            put("port", config.playerPort)
            put("api_key", apiKey)
        })
    }
    val r = http.post("http://supervisor/discovery") {
        bearerAuth(config.token)
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }
    log.info("Discovery sent: {} {}", r.status, r.bodyAsText().take(200))
}
