package tech.shaffersoftworks.remotescreens.player.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlayerApkDownloadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloadOutlivesTheNormalRequestDeadlineAndStreamsTheBody() = runBlocking {
        val apkBytes = ByteArray(1024) { it.toByte() }
        val engine = MockEngine { request ->
            val timeout = requireNotNull(request.getCapabilityOrNull(HttpTimeoutCapability))
            assertEquals(HttpTimeoutConfig.INFINITE_TIMEOUT_MS, timeout.requestTimeoutMillis)
            assertEquals(10_000L, timeout.connectTimeoutMillis)
            assertEquals(30_000L, timeout.socketTimeoutMillis)
            delay(50)
            respond(apkBytes)
        }
        val client = HttpClient(engine) {
            install(HttpTimeout) { requestTimeoutMillis = 1 }
        }
        try {
            val file = temporaryFolder.newFile()
            assertTrue(client.downloadPlayerApk("http://server/player.apk", file))
            assertArrayEquals(apkBytes, file.readBytes())
        } finally {
            client.close()
        }
    }

    @Test
    fun failedResponseDoesNotWriteAnApk() = runBlocking {
        val client = HttpClient(MockEngine { respond("Not found", HttpStatusCode.NotFound) }) {
            install(HttpTimeout)
        }
        try {
            val file = temporaryFolder.newFile()
            assertFalse(client.downloadPlayerApk("http://server/missing.apk", file))
            assertEquals(0L, file.length())
        } finally {
            client.close()
        }
    }
}
