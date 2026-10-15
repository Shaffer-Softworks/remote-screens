package tech.shaffersoftworks.remotescreens.server.network

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

private const val SUPERVISOR_URL = "http://supervisor"
private const val DISCOVERY_LOG_LENGTH = 200

/** The Home Assistant Supervisor's API, for an add-on running under it. */
class SupervisorClient(private val httpClient: HttpClient, private val supervisorToken: String) {
    private val logger = LoggerFactory.getLogger(SupervisorClient::class.java)

    /**
     * On the host network a fixed ingress port could be taken by another add-on, so `ingress_port: 0` lets the
     * Supervisor choose a free one, which it then tells us here.
     */
    suspend fun ingressPort(): Int? = runCatching {
        val body = httpClient.get("$SUPERVISOR_URL/addons/self/info") { bearerAuth(supervisorToken) }.bodyAsText()
        Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            ?.get("ingress_port")?.jsonPrimitive?.int
            ?.takeIf { it > 0 }
    }.onFailure { logger.warn("Could not ask the Supervisor for the ingress port: {}", it.message) }.getOrNull()

    /** Tells Home Assistant the add-on is here, so the integration sets itself up with the key. */
    suspend fun announce(playerPort: Int, apiKey: String) {
        val body = buildJsonObject {
            put("service", "remote_screens")
            put(
                "config",
                buildJsonObject {
                    put("host", "127.0.0.1")
                    put("port", playerPort)
                    put("api_key", apiKey)
                },
            )
        }
        val response = httpClient.post("$SUPERVISOR_URL/discovery") {
            bearerAuth(supervisorToken)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        logger.info("Discovery sent: {} {}", response.status, response.bodyAsText().take(DISCOVERY_LOG_LENGTH))
    }
}
