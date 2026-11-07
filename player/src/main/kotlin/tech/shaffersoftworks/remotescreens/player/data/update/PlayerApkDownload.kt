package tech.shaffersoftworks.remotescreens.player.data.update

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File

private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 10_000L
private const val DOWNLOAD_IDLE_TIMEOUT_MS = 30_000L

/** Large APKs can take minutes; bound connection and idle time, not total transfer time. */
internal suspend fun HttpClient.downloadPlayerApk(url: String, destination: File): Boolean =
    prepareGet(url) {
        timeout {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = DOWNLOAD_CONNECT_TIMEOUT_MS
            socketTimeoutMillis = DOWNLOAD_IDLE_TIMEOUT_MS
        }
    }.execute { response ->
        if (!response.status.isSuccess()) return@execute false
        destination.outputStream().use { output -> response.bodyAsChannel().copyTo(output) }
        true
    }
