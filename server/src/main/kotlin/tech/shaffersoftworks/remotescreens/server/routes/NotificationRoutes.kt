package tech.shaffersoftworks.remotescreens.server.routes

import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tech.shaffersoftworks.remotescreens.server.player.PlayerHub
import tech.shaffersoftworks.remotescreens.shared.NotificationRequest

/** Mounted under both existing authenticated API groups. */
fun Route.notificationRoutes(playerHub: PlayerHub) {
    post("/notifications") {
        val request = call.receive<NotificationRequest>()
        val result = try {
            playerHub.notify(request)
        } catch (invalid: IllegalArgumentException) {
            throw BadRequestException(invalid.message ?: "Invalid notification", invalid)
        }
        call.respond(result)
    }
}
