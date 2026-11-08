package tech.shaffersoftworks.remotescreens.player.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Exercise the real engine: MockEngine cannot reproduce CIO's own request deadline. */
class PlayerApkCioDownloadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun legacyDownloadTimesOutWhileCurrentDownloadFinishesTheSameSlowBody() = runBlocking {
        val apkBytes = byteArrayOf(1, 2)
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true, name = "slow-apk-test") {
            repeat(2) {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) {
                        // Consume the request headers before sending the response.
                    }
                    val output = socket.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(apkBytes, 0, 1)
                    output.flush()
                    Thread.sleep(1_000)
                    // The legacy client may have closed its socket by now.
                    runCatching {
                        output.write(apkBytes, 1, 1)
                        output.flush()
                    }
                }
            }
        }
        val url = "http://127.0.0.1:${server.localPort}/player/apk"
        // Shorten the production CIO default (15 seconds) to keep this regression test fast.
        val legacyClient = HttpClient(CIO) { engine { requestTimeout = 200 } }
        val currentClient = HttpClient(CIO) {
            engine { requestTimeout = 200 }
            install(HttpTimeout) { requestTimeoutMillis = 200 }
        }
        try {
            val legacyFile = temporaryFolder.newFile()
            val failure = runCatching {
                legacyClient.prepareGet(url).execute { response ->
                    legacyFile.outputStream().use { response.bodyAsChannel().copyTo(it) }
                }
            }.exceptionOrNull()
            assertTrue(
                "The legacy download must fail with a timeout, got $failure",
                generateSequence(failure) { it.cause }.any { it.javaClass.simpleName.contains("Timeout") },
            )
            val currentFile = temporaryFolder.newFile()
            assertTrue(currentClient.downloadPlayerApk(url, currentFile))
            assertArrayEquals(apkBytes, currentFile.readBytes())
        } finally {
            legacyClient.close()
            currentClient.close()
            server.close()
            worker.join(2_000)
        }
    }
}
