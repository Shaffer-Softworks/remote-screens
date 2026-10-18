package tech.shaffersoftworks.remotescreens.server.routes

import tech.shaffersoftworks.remotescreens.server.player.PlayerHub
import tech.shaffersoftworks.remotescreens.server.render.Alert
import tech.shaffersoftworks.remotescreens.server.routes.model.IntegrationScreenResponse
import tech.shaffersoftworks.remotescreens.server.routes.model.OpenPageRequest
import tech.shaffersoftworks.remotescreens.server.routes.model.TargetsRequest
import tech.shaffersoftworks.remotescreens.server.service.ScreenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.util.getOrFail
import kotlinx.serialization.json.JsonObject

/** The API the Home Assistant integration polls and calls, under `/integration` and its key. */
fun Route.integrationRoutes(screenService: ScreenService, playerHub: PlayerHub) {
    get("/screens") {
        val players = playerHub.status()
        val screens = screenService.screens().map { screen ->
            IntegrationScreenResponse(
                id = screen.id,
                name = screen.name,
                settings = screen.settings,
                pages = screen.pages.map { it.title },
                players = players.filter { it.screenId == screen.id },
            )
        }
        call.respond(screens)
    }
    post("/screens/{id}/settings") {
        val isPatched = screenService.patchSettings(
            call.parameters.getOrFail("id"),
            call.receive<JsonObject>(),
        )
        call.respond(if (isPatched) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }
    post("/alert") {
        playerHub.showAlert(call.receive<Alert>())
        call.respond(HttpStatusCode.NoContent)
    }
    post("/open_page") {
        val request = call.receive<OpenPageRequest>()
        playerHub.openPage(request.targets, request.page)
        call.respond(HttpStatusCode.NoContent)
    }
    post("/wake") {
        playerHub.wake(call.receive<TargetsRequest>().targets)
        call.respond(HttpStatusCode.NoContent)
    }
    post("/close") {
        playerHub.closeOverlays(call.receive<TargetsRequest>().targets)
        call.respond(HttpStatusCode.NoContent)
    }
}
