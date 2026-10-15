package tech.shaffersoftworks.remotescreens.server.plugins

import tech.shaffersoftworks.remotescreens.server.routes.model.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

private const val BAD_REQUEST_MESSAGE = "Bad request"

/** A request the editor should not have made is answered with 400 and why; anything else with 500. */
fun Application.configureErrorHandling() {
    val logger = LoggerFactory.getLogger("tech.shaffersoftworks.remotescreens.server.ErrorHandling")
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: BAD_REQUEST_MESSAGE))
        }
        exception<ContentTransformationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: BAD_REQUEST_MESSAGE))
        }
        exception<SerializationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: BAD_REQUEST_MESSAGE))
        }
        exception<Throwable> { call, cause ->
            logger.error("Request failed", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Error"))
        }
    }
}
