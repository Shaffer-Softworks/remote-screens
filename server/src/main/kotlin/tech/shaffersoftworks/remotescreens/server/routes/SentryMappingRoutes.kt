package tech.shaffersoftworks.remotescreens.server.routes

import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import tech.shaffersoftworks.remotescreens.server.service.MappingUploadSettings
import tech.shaffersoftworks.remotescreens.server.service.SentryMappingService

/** Registered only on the editor's authenticated ingress surface, never the player API. */
fun Route.sentryMappingRoutes(service: SentryMappingService) {
    route("/sentry/mappings") {
        get { call.respond(service.status()) }
        put {
            val request = call.receive<MappingUploadSettings>()
            val result = try {
                service.configure(request)
            } catch (error: IllegalArgumentException) {
                throw BadRequestException(error.message ?: "Invalid Sentry upload settings", error)
            }
            call.respond(result)
        }
        post { call.respond(service.reconcile(force = true)) }
        delete { call.respond(service.clear()) }
    }
}
