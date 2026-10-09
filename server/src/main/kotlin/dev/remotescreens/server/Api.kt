package dev.remotescreens.server

import dev.remotescreens.server.ha.HomeAssistant
import dev.remotescreens.server.render.Alert
import dev.remotescreens.server.render.Icons
import dev.remotescreens.server.store.LayoutStore
import dev.remotescreens.shared.Grid
import dev.remotescreens.shared.GridSize
import dev.remotescreens.shared.LayoutData
import dev.remotescreens.shared.MAX_PAGES
import dev.remotescreens.shared.MAX_NODES_PER_PAGE
import dev.remotescreens.shared.MAX_NODE_DEPTH
import dev.remotescreens.shared.MAX_TILES_PER_SCREEN
import dev.remotescreens.shared.Node
import dev.remotescreens.shared.NodeTypes
import dev.remotescreens.shared.Page
import dev.remotescreens.shared.Preferences
import dev.remotescreens.shared.Screen
import dev.remotescreens.shared.ScreenSettings
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.util.UUID

@Serializable
data class Info(
    val version: String, val demo: Boolean, val connected: Boolean, val language: String, val haVersion: String, val locationName: String,
    /** The version of the player app the add-on can hand out, or null when it has none. */
    val playerApp: String? = null,
)

@Serializable
data class EntityInfo(val entityId: String, val name: String, val state: String, val domain: String, val area: String?, val icon: String?, val unit: String?)

@Serializable
data class AreaInfo(val id: String, val name: String)

@Serializable
data class NewScreen(val name: String, val grid: GridSize? = null, val widthDp: Int? = null, val heightDp: Int? = null)

@Serializable
data class Adopt(val screenId: String, val name: String? = null, val code: String? = null)

@Serializable
data class Rename(val name: String)

@Serializable
data class OpenPageRequest(val page: String, val targets: List<String> = emptyList())

@Serializable
data class TargetsRequest(val targets: List<String> = emptyList())

@Serializable
data class IntegrationScreen(val id: String, val name: String, val settings: ScreenSettings, val pages: List<String>, val players: List<PlayerStatus>)

/** Thrown for a request the editor should not have made; answered with 400 and [message]. */
class BadRequest(message: String) : Exception(message)

/** The editor's API, under ingress. */
fun Route.editorApi(config: Config, ha: HomeAssistant, store: LayoutStore, hub: PlayerHub, apk: () -> ApkInfo?) {
    route("/api") {
        get("/info") {
            val c = ha.config.value
            call.respond(Info(config.version, config.demo, ha.connected.value, store.data.value.preferences.language ?: c.language, c.version, c.locationName,
                apk()?.let { it.versionName.ifEmpty { it.versionCode.toString() } }))
        }
        get("/layout") { call.respond(store.data.value.redacted()) }
        put("/preferences") {
            val p = call.receive<Preferences>()
            store.update { it.copy(preferences = p) }
            call.respond(store.data.value.preferences)
        }

        post("/screens") {
            val req = call.receive<NewScreen>()
            val grid = req.grid ?: if (req.widthDp != null && req.heightDp != null) Grid.suggest(req.widthDp.toFloat(), req.heightDp.toFloat()) else GridSize(3, 3)
            val screen = Screen(id = newId("s"), name = req.name.trim().ifEmpty { "Screen" }.take(60), grid = grid, pages = listOf(Page("home", "Home")))
            store.update { it.copy(screens = it.screens + screen) }
            call.respond(HttpStatusCode.Created, screen)
        }
        put("/screens/{id}") {
            val id = call.parameters["id"]!!
            val screen = validate(call.receive<Screen>().copy(id = id))
            if (store.data.value.screens.none { it.id == id }) return@put call.respond(HttpStatusCode.NotFound)
            store.update { d -> d.copy(screens = d.screens.map { if (it.id == id) screen else it }) }
            call.respond(screen)
        }
        delete("/screens/{id}") {
            val id = call.parameters["id"]!!
            store.update { d -> d.copy(screens = d.screens.filterNot { it.id == id }, players = d.players.map { if (it.screenId == id) it.copy(screenId = null) else it }) }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/players") { call.respond(hub.status()) }
        get("/player.apk") {
            val file = apk()?.file ?: return@get call.respond(HttpStatusCode.NotFound)
            call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=remote-screens.apk")
            call.respondFile(file)
        }
        post("/pair") {
            val req = call.receive<Adopt>()
            val ok = hub.adopt(req.code ?: throw BadRequest("No code"), req.screenId, req.name)
            call.respond(if (ok) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
        post("/players/{id}/adopt") {
            val req = call.receive<Adopt>()
            call.respond(if (hub.adopt(call.parameters["id"]!!, req.screenId, req.name)) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
        patch("/players/{id}") {
            hub.rename(call.parameters["id"]!!, call.receive<Rename>().name.trim().take(60))
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/players/{id}") {
            hub.forget(call.parameters["id"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/players/{id}/identify") {
            hub.identify(call.parameters["id"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/players/{id}/preview") {
            val screen = validate(call.receive<Screen>())
            call.respond(if (hub.preview(call.parameters["id"]!!, screen)) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
        post("/alert") {
            hub.showAlert(call.receive<Alert>())
            call.respond(HttpStatusCode.NoContent)
        }

        get("/entities") { call.respond(entities(ha)) }
        get("/areas") { call.respond(areas(ha)) }
        get("/icons") {
            val q = call.request.queryParameters["q"].orEmpty()
            call.respond(Icons.search(q))
        }
        get("/icons/paths") {
            val names = call.request.queryParameters["names"].orEmpty().split(',').filter { it.isNotBlank() }.take(400)
            call.respond(names.associateWith { Icons.path(it) })
        }
        get("/grid") {
            val w = call.request.queryParameters["w"]?.toFloatOrNull() ?: throw BadRequest("w")
            val h = call.request.queryParameters["h"]?.toFloatOrNull() ?: throw BadRequest("h")
            call.respond(Grid.suggest(w, h))
        }
    }
}

/** The API the Home Assistant integration polls and calls, under `/integration` and its key. */
fun Route.integrationApi(store: LayoutStore, hub: PlayerHub) {
    get("/screens") {
        val players = hub.status()
        call.respond(store.data.value.screens.map { s ->
            IntegrationScreen(s.id, s.name, s.settings, s.pages.map { it.title }, players.filter { it.screenId == s.id })
        })
    }
    post("/screens/{id}/settings") {
        val id = call.parameters["id"]!!
        val patch = call.receive<JsonObject>()
        if (store.data.value.screens.none { it.id == id }) return@post call.respond(HttpStatusCode.NotFound)
        hub.updateSettings(id) { current ->
            val merged = JsonObject(LayoutStore.JSON.encodeToJsonElement(ScreenSettings.serializer(), current).jsonObject + patch)
            LayoutStore.JSON.decodeFromJsonElement(ScreenSettings.serializer(), merged)
        }
        call.respond(HttpStatusCode.NoContent)
    }
    post("/alert") {
        hub.showAlert(call.receive<Alert>())
        call.respond(HttpStatusCode.NoContent)
    }
    post("/open_page") {
        val r = call.receive<OpenPageRequest>()
        hub.openPage(r.targets, r.page)
        call.respond(HttpStatusCode.NoContent)
    }
    post("/wake") {
        hub.wake(call.receive<TargetsRequest>().targets)
        call.respond(HttpStatusCode.NoContent)
    }
    post("/close") {
        hub.closeOverlays(call.receive<TargetsRequest>().targets)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun LayoutData.redacted() = copy(players = players.map { it.copy(tokenHash = if (it.tokenHash != null) "set" else null) })

private fun newId(prefix: String) = prefix + UUID.randomUUID().toString().replace("-", "").take(10)

/** What a screen may hold. The editor keeps to it; this is for anything else that writes. */
fun validate(s: Screen): Screen {
    if (s.pages.isEmpty()) throw BadRequest("A screen needs a page")
    if (s.pages.size > MAX_PAGES) throw BadRequest("At most $MAX_PAGES pages")
    if (s.pages.map { it.id }.toSet().size != s.pages.size) throw BadRequest("Page ids must differ")
    val tiles = s.pages.flatMap { it.allTiles() }
    if (s.pages.sumOf { if (it.root == null) it.tiles.size else 0 } > MAX_TILES_PER_SCREEN) throw BadRequest("At most $MAX_TILES_PER_SCREEN tiles")
    if (tiles.map { it.id }.toSet().size != tiles.size) throw BadRequest("Tile ids must differ")
    for (p in s.pages) p.root?.let { validateTree(it, p.title) }
    val g = s.grid
    if (g.cols !in Grid.MIN_CELLS..Grid.MAX_CELLS || g.rows !in Grid.MIN_CELLS..Grid.MAX_CELLS) throw BadRequest("Grid out of range")
    return s.copy(
        name = s.name.trim().ifEmpty { "Screen" }.take(60),
        homePage = s.homePage.takeIf { h -> s.pages.any { it.id == h } } ?: s.pages.first().id,
        settings = s.settings.validated(),
        pages = s.pages.map { p ->
            p.copy(title = p.title.take(40), tiles = p.tiles.map { t ->
                t.copy(w = t.w.coerceIn(1, g.cols), h = t.h.coerceIn(1, g.rows), col = t.col.coerceIn(0, g.cols - 1), row = t.row.coerceIn(0, g.rows - 1))
            })
        },
    )
}

/** What a free-form page may hold: known components, ids that differ, a bounded size and depth. */
fun validateTree(root: Node, page: String) {
    val nodes = root.walk().toList()
    if (nodes.size > MAX_NODES_PER_PAGE) throw BadRequest("$page: at most $MAX_NODES_PER_PAGE components")
    if (root.depth() > MAX_NODE_DEPTH) throw BadRequest("$page: components nest at most $MAX_NODE_DEPTH deep")
    if (nodes.map { it.id }.toSet().size != nodes.size) throw BadRequest("$page: component ids must differ")
    for (n in nodes) {
        if (n.type !in NodeTypes.ALL) throw BadRequest("$page: unknown component ${n.type}")
        if (n.children.isNotEmpty() && n.type !in NodeTypes.CONTAINERS) throw BadRequest("$page: a ${n.type} holds no components")
        if (n.type == "tile" && n.tile == null) throw BadRequest("$page: tile ${n.id} has no card")
        for (s in listOf(n.size.width, n.size.height)) if (!SIZE.matches(s)) throw BadRequest("$page: size $s of ${n.id}")
    }
}

private val SIZE = Regex("^(wrap|fill|dp:\\d+(\\.\\d+)?|weight:\\d+(\\.\\d+)?)$")

private suspend fun areas(ha: HomeAssistant): List<AreaInfo> =
    (runCatching { ha.command("config/area_registry/list") }.getOrNull() as? JsonArray).orEmpty().mapNotNull { a ->
        val o = a as? JsonObject ?: return@mapNotNull null
        val id = (o["area_id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        AreaInfo(id, (o["name"] as? JsonPrimitive)?.contentOrNull ?: id)
    }.sortedBy { it.name.lowercase() }

private suspend fun entities(ha: HomeAssistant): List<EntityInfo> {
    val registry = runCatching { ha.command("config/entity_registry/list_for_display") as? JsonObject }.getOrNull()
    val areaOf = HashMap<String, String>()
    val hidden = HashSet<String>()
    (registry?.get("entities") as? JsonArray).orEmpty().forEach { e ->
        val o = e as? JsonObject ?: return@forEach
        val id = (o["ei"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
        (o["ai"] as? JsonPrimitive)?.contentOrNull?.let { areaOf[id] = it }
        if (o["hb"] != null || o["ec"] != null) hidden += id
    }
    return ha.states.value.values.filter { it.entityId !in hidden }.map { e ->
        EntityInfo(e.entityId, e.name, e.state, e.domain, areaOf[e.entityId], e.attr("icon"), e.attr("unit_of_measurement"))
    }.sortedBy { it.name.lowercase() }
}
