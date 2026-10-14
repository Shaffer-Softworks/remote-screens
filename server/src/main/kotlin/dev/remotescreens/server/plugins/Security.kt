package dev.remotescreens.server.plugins

import dev.remotescreens.server.config.ServerConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

class OnlyIngressConfig {
    /** Off outside the Supervisor, where the editor listens on 127.0.0.1 only. */
    var isEnforced: Boolean = false
}

/** Refuses editor requests that do not come through Home Assistant's ingress. */
val OnlyIngress = createRouteScopedPlugin("OnlyIngress", ::OnlyIngressConfig) {
    val logger = LoggerFactory.getLogger("dev.remotescreens.server.OnlyIngress")
    val isEnforced = pluginConfig.isEnforced
    onCall { call ->
        val peer = call.request.origin.remoteAddress
        if (isEnforced && !ServerConfig.isIngressPeer(peer)) {
            logger.warn("Refused an editor request from {}", peer)
            call.respond(HttpStatusCode.Forbidden)
        }
    }
}

class IntegrationKeyConfig {
    var apiKey: String = ""
}

/** Refuses integration requests without the add-on's API key as their bearer token. */
val IntegrationKey = createRouteScopedPlugin("IntegrationKey", ::IntegrationKeyConfig) {
    val expectedHeader = "Bearer ${pluginConfig.apiKey}"
    onCall { call ->
        if (call.request.header(HttpHeaders.Authorization) != expectedHeader) {
            call.respond(HttpStatusCode.Unauthorized)
        }
    }
}
