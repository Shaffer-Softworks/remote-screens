package dev.remotescreens.server.routes

import dev.remotescreens.server.data.apk.PlayerApkRepository
import dev.remotescreens.server.player.PlayerHub
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.websocket.webSocket
import java.io.File

/** What tablets reach on the network: the health check, their WebSocket, and the app update. */
fun Route.playerRoutes(playerHub: PlayerHub, playerApkRepository: PlayerApkRepository) {
    get("/health") { call.respondText("ok") }
    webSocket("/player") { playerHub.serve(this, call.request.origin.remoteAddress) }
    get("/player/apk") {
        val apk = playerApkRepository.current()
        if (apk == null || !playerHub.isTrustedToken(call.request.queryParameters["token"])) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respondApk(apk.file)
        }
    }
}

internal suspend fun ApplicationCall.respondApk(file: File) {
    val disposition = ContentDisposition.Attachment
        .withParameter(ContentDisposition.Parameters.FileName, PlayerApkRepository.APK_FILE_NAME)
    response.header(HttpHeaders.ContentDisposition, disposition.toString())
    respondFile(file)
}
