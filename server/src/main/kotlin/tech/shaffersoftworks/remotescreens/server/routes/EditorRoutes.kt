package tech.shaffersoftworks.remotescreens.server.routes

import tech.shaffersoftworks.remotescreens.server.data.apk.PlayerApkRepository
import tech.shaffersoftworks.remotescreens.server.di.ServerDependencies
import tech.shaffersoftworks.remotescreens.server.player.PlayerHub
import tech.shaffersoftworks.remotescreens.server.render.Alert
import tech.shaffersoftworks.remotescreens.server.render.widgets.Icons
import tech.shaffersoftworks.remotescreens.server.routes.model.AdoptPlayerRequest
import tech.shaffersoftworks.remotescreens.server.routes.model.EnrollmentRequest
import tech.shaffersoftworks.remotescreens.server.routes.model.ErrorResponse
import tech.shaffersoftworks.remotescreens.server.routes.model.RenamePlayerRequest
import tech.shaffersoftworks.remotescreens.server.routes.model.ServerInfoResponse
import tech.shaffersoftworks.remotescreens.server.service.CreateScreenRequest
import tech.shaffersoftworks.remotescreens.server.service.EnrollmentService
import tech.shaffersoftworks.remotescreens.server.service.EnrollmentUnavailableException
import tech.shaffersoftworks.remotescreens.server.service.HomeAssistantCatalogService
import tech.shaffersoftworks.remotescreens.server.service.ScreenFile
import tech.shaffersoftworks.remotescreens.server.service.ScreenService
import tech.shaffersoftworks.remotescreens.server.service.ThemeService
import tech.shaffersoftworks.remotescreens.server.service.validation.ScreenValidator
import tech.shaffersoftworks.remotescreens.shared.Grid
import tech.shaffersoftworks.remotescreens.shared.Preferences
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.Theme
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail

private const val MAX_ICON_PATHS = 400
private const val MAX_PLAYER_NAME_LENGTH = 60

/** The editor's API, under ingress. */
fun Route.editorRoutes(dependencies: ServerDependencies) {
    route("/api") {
        infoRoutes(dependencies)
        screenRoutes(dependencies.screenService)
        themeRoutes(dependencies.themeService)
        playerManagementRoutes(dependencies.playerHub, dependencies.playerApkRepository)
        enrollmentRoutes(dependencies.enrollmentService)
        homeAssistantRoutes(dependencies.homeAssistantCatalogService)
        post("/alert") {
            dependencies.playerHub.showAlert(call.receive<Alert>())
            call.respond(HttpStatusCode.NoContent)
        }
        get("/grid") {
            val width = call.request.queryParameters["w"]?.toFloatOrNull() ?: throw BadRequestException(
                "w",
            )
            val height = call.request.queryParameters["h"]?.toFloatOrNull() ?: throw BadRequestException(
                "h",
            )
            call.respond(Grid.suggest(width, height))
        }
    }
}

private fun Route.infoRoutes(dependencies: ServerDependencies) {
    get("/info") {
        val config = dependencies.config
        val homeAssistant = dependencies.homeAssistant
        val homeAssistantConfig = homeAssistant.config.value
        val info = ServerInfoResponse(
            version = config.version,
            isDemo = config.isDemo,
            isConnected = homeAssistant.isConnected.value,
            language = dependencies.screenService.preferences().language ?: homeAssistantConfig.language,
            haVersion = homeAssistantConfig.version,
            locationName = homeAssistantConfig.locationName,
            playerApp = dependencies.playerApkRepository.current()?.displayVersion,
        )
        call.respond(info)
    }
}

private fun Route.screenRoutes(screenService: ScreenService) {
    get("/layout") { call.respond(screenService.redactedLayout()) }
    put("/preferences") {
        call.respond(screenService.updatePreferences(call.receive<Preferences>()))
    }
    post("/screens") {
        call.respond(
            HttpStatusCode.Created,
            screenService.createScreen(call.receive<CreateScreenRequest>()),
        )
    }
    post("/screens/import") {
        call.respond(
            HttpStatusCode.Created,
            screenService.importScreen(call.receive<ScreenFile>()),
        )
    }
    put("/screens/{id}") {
        val screenId = call.parameters.getOrFail("id")
        val updated = screenService.updateScreen(screenId, call.receive<Screen>())
        if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
    }
    delete("/screens/{id}") {
        screenService.deleteScreen(call.parameters.getOrFail("id"))
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.themeRoutes(themeService: ThemeService) {
    get("/themes") { call.respond(themeService.allThemes()) }
    put("/themes/{id}") {
        call.respond(themeService.saveTheme(call.parameters.getOrFail("id"), call.receive<Theme>()))
    }
    delete("/themes/{id}") {
        themeService.deleteTheme(call.parameters.getOrFail("id"))
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.playerManagementRoutes(playerHub: PlayerHub, playerApkRepository: PlayerApkRepository) {
    get("/players") { call.respond(playerHub.status()) }
    get("/player.apk") {
        val apk = playerApkRepository.current()
        if (apk == null) call.respond(HttpStatusCode.NotFound) else call.respondApk(apk.file)
    }
    post("/pair") {
        val request = call.receive<AdoptPlayerRequest>()
        val code = request.code ?: throw BadRequestException("No code")
        val isAdopted = playerHub.adopt(code, request.screenId, request.name)
        call.respond(if (isAdopted) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }
    route("/players/{id}") {
        post("/adopt") {
            val request = call.receive<AdoptPlayerRequest>()
            val isAdopted = playerHub.adopt(
                call.parameters.getOrFail("id"),
                request.screenId,
                request.name,
            )
            call.respond(if (isAdopted) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
        patch {
            val name = call.receive<RenamePlayerRequest>().name.trim().take(MAX_PLAYER_NAME_LENGTH)
            playerHub.rename(call.parameters.getOrFail("id"), name)
            call.respond(HttpStatusCode.NoContent)
        }
        delete {
            playerHub.forget(call.parameters.getOrFail("id"))
            call.respond(HttpStatusCode.NoContent)
        }
        post("/identify") {
            playerHub.identify(call.parameters.getOrFail("id"))
            call.respond(HttpStatusCode.NoContent)
        }
        post("/preview") {
            val screen = ScreenValidator.validate(call.receive<Screen>())
            val isShown = playerHub.preview(call.parameters.getOrFail("id"), screen)
            call.respond(if (isShown) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
    }
}

private fun Route.enrollmentRoutes(enrollmentService: EnrollmentService) {
    post("/enrollment") {
        val request = call.receive<EnrollmentRequest>()
        try {
            call.respond(enrollmentService.createEnrollment(request))
        } catch (exception: EnrollmentUnavailableException) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse(exception.message.orEmpty()))
        }
    }
}

private fun Route.homeAssistantRoutes(catalogService: HomeAssistantCatalogService) {
    get("/entities") { call.respond(catalogService.entities()) }
    get("/areas") { call.respond(catalogService.areas()) }
    get("/icons") {
        call.respond(Icons.search(call.request.queryParameters["q"].orEmpty()))
    }
    get("/icons/paths") {
        val names = call.request.queryParameters["names"].orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .take(MAX_ICON_PATHS)
        call.respond(names.associateWith { Icons.path(it) })
    }
}
