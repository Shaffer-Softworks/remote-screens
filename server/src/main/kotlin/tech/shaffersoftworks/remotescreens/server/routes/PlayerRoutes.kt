package tech.shaffersoftworks.remotescreens.server.routes

import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.server.data.apk.PlayerApkRepository
import tech.shaffersoftworks.remotescreens.server.player.PlayerHub
import tech.shaffersoftworks.remotescreens.server.service.CameraStreamService
import tech.shaffersoftworks.remotescreens.server.service.EnrollmentService
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.queryString
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.getOrFail
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.copyTo
import java.io.File

/**
 * What tablets reach on the network: the health check, their WebSocket, the app, for an update (with a player's
 * token) or for QR enrollment (with an enrollment token), and cameras' live video (with a player's token).
 */
fun Route.playerRoutes(
    playerHub: PlayerHub,
    playerApkRepository: PlayerApkRepository,
    enrollmentService: EnrollmentService,
    cameraStreamService: CameraStreamService,
) {
    get("/health") { call.respondText("ok") }
    webSocket("/player") { playerHub.serve(this, call.request.origin.remoteAddress) }
    playerUpdateRoute(playerHub, playerApkRepository)
    get("/player/apk") {
        val apk = playerApkRepository.current()
        val parameters = call.request.queryParameters
        val isAllowed = playerHub.isTrustedToken(parameters["token"]) ||
            enrollmentService.isValidToken(parameters["enroll"])
        if (apk == null || !isAllowed) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respondApk(apk.file)
        }
    }
    get("${CameraStreamService.PLAYER_PATH}/{token}/{file...}") {
        val playerToken = call.request.headers[HttpHeaders.Authorization]?.removePrefix(
            "Bearer ",
        )?.trim()
        if (!playerHub.isTrustedToken(playerToken)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }
        val token = call.parameters.getOrFail("token")
        val file = call.parameters.getAll("file").orEmpty().joinToString("/")
        val status = cameraStreamService.relay(
            token,
            file,
            call.request.queryString(),
        ) { contentType, body ->
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondBytesWriter(
                contentType?.let(ContentType::parse),
                HttpStatusCode.OK,
            ) { body.copyTo(this) }
        }
        when {
            status == null -> call.respond(HttpStatusCode.NotFound)
            !HttpStatusCode.fromValue(
                status,
            ).isSuccess() -> call.respond(HttpStatusCode.fromValue(status))
        }
    }
}

private fun Route.playerUpdateRoute(playerHub: PlayerHub, playerApkRepository: PlayerApkRepository) {
    get("/player/update") {
        if (!playerHub.isTrustedToken(call.request.queryParameters["token"])) {
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }
        val apk = playerApkRepository.current()
        if (apk == null) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(
                ServerMessage.UpdateAvailable(
                    apk.versionCode,
                    apk.versionName,
                    "/player/apk",
                ),
            )
        }
    }
}

internal suspend fun ApplicationCall.respondApk(file: File) {
    val disposition = ContentDisposition.Attachment
        .withParameter(ContentDisposition.Parameters.FileName, PlayerApkRepository.APK_FILE_NAME)
    response.header(HttpHeaders.ContentDisposition, disposition.toString())
    respondFile(file)
}
