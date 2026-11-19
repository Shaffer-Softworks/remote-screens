package tech.shaffersoftworks.remotescreens.server.network

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val SUPERVISOR_URL = "http://supervisor"
private const val DISCOVERY_LOG_LENGTH = 200
private const val DISCOVERY_SERVICE = "remote_screens"

/** The add-on reaches the integration's host on loopback; Home Assistant shares the host network with it. */
private const val LOOPBACK = "127.0.0.1"

private val SUPERVISOR_JSON = Json { ignoreUnknownKeys = true }

/** `GET /addons/self/info`; only the fields the add-on reads. */
@Serializable
internal data class AddonInfoResponse(val data: AddonInfo? = null)

@Serializable
internal data class AddonInfo(@SerialName("ingress_port") val ingressPort: Int? = null)

/** `POST /discovery`: how the custom integration reaches the add-on. */
@Serializable
internal data class DiscoveryMessage(val service: String, val config: DiscoveryConfig)

@Serializable
internal data class DiscoveryConfig(
    val host: String,
    val port: Int,
    @SerialName("api_key") val apiKey: String,
)

/** The Home Assistant Supervisor's API, for an add-on running under it. */
class SupervisorClient(
    private val httpClient: HttpClient,
    private val supervisorToken: String,
) {
    private val logger = LoggerFactory.getLogger(SupervisorClient::class.java)

    /**
     * On the host network a fixed ingress port could be taken by another add-on, so `ingress_port: 0` lets the
     * Supervisor choose a free one, which it then tells us here.
     */
    suspend fun ingressPort(): Int? = runCatching {
        val body = httpClient.get(
            "$SUPERVISOR_URL/addons/self/info",
        ) {
            bearerAuth(supervisorToken)
        }.bodyAsText()

        SUPERVISOR_JSON.decodeFromString(AddonInfoResponse.serializer(), body).data?.ingressPort?.takeIf { it > 0 }
    }.onFailure { logger.warn(
        "Could not ask the Supervisor for the ingress port: {}",
        it.message,
    ) }.getOrNull()

    /** Tells Home Assistant the add-on is here, so the integration sets itself up with the key. */
    suspend fun announce(
        playerPort: Int,
        apiKey: String,
    ) {
        val body = SUPERVISOR_JSON.encodeToString(
            DiscoveryMessage.serializer(),
            DiscoveryMessage(DISCOVERY_SERVICE, DiscoveryConfig(host = LOOPBACK, port = playerPort, apiKey = apiKey)),
        )

        val response = httpClient.post("$SUPERVISOR_URL/discovery") {
            bearerAuth(supervisorToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        logger.info(
            "Discovery sent: {} {}",
            response.status,
            response.bodyAsText().take(DISCOVERY_LOG_LENGTH),
        )
    }
}
