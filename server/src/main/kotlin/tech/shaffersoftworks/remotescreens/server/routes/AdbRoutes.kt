package tech.shaffersoftworks.remotescreens.server.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import tech.shaffersoftworks.remotescreens.server.routes.model.ErrorResponse
import tech.shaffersoftworks.remotescreens.server.service.AdbService

@Serializable
data class AdbRequest(
    val endpoint: String = "",
    val code: String = "",
    val serial: String = "",
    val playerId: String = "",
)

@Serializable
data class AdbResult(val message: String)

/** Mounted only on the editor server, inside its ingress protection. */
fun Route.adbRoutes(adb: AdbService) {
    route("/adb") {
        get { call.adbOperation { call.respond(adb.status()) } }
        listOf("pair", "connect", "disconnect", "associate", "restart", "install", "screenshot", "logs")
            .forEach { operation ->
                post("/$operation") {
                    val request = call.receive<AdbRequest>()
                    call.adbOperation {
                        when (operation) {
                            "screenshot" -> call.respondBytes(adb.screenshot(request.serial), ContentType.Image.PNG)
                            "logs" -> call.respondBytes(adb.logs(request.serial), ContentType.Text.Plain)
                            else -> call.respond(AdbResult(adb.action(operation, request)))
                        }
                    }
                }
            }
    }
}

private suspend fun AdbService.action(operation: String, request: AdbRequest): String = when (operation) {
    "pair" -> pair(request.endpoint, request.code)
    "connect" -> connect(request.endpoint)
    "disconnect" -> disconnect(request.serial)
    "restart" -> restart(request.serial)
    "install" -> install(request.serial)
    else -> {
        associate(request.playerId, request.serial)
        "Tablet association saved"
    }
}

private suspend fun ApplicationCall.adbOperation(block: suspend () -> Unit) {
    try {
        block()
    } catch (exception: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(exception.message.orEmpty()))
    } catch (exception: IllegalStateException) {
        respond(HttpStatusCode.Conflict, ErrorResponse(exception.message.orEmpty()))
    } catch (exception: java.io.IOException) {
        respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("ADB is unavailable: ${exception.message}"))
    }
}
