package tech.shaffersoftworks.remotescreens.server.routes

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tech.shaffersoftworks.remotescreens.server.data.apk.PlayerApkRepository
import tech.shaffersoftworks.remotescreens.server.plugins.configureSerialization
import tech.shaffersoftworks.remotescreens.server.service.AdbService
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AdbRouteTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun screenshotIsBinaryAndInvalidTargetIsRejected() = testApplication {
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 0, -1)
        val adb = AdbService(temporary.root, PlayerApkRepository(null), commandRunner = { _, _ -> png })
        application {
            configureSerialization()
            routing { adbRoutes(adb) }
        }
        val screenshot = client.post("/adb/screenshot") {
            contentType(ContentType.Application.Json)
            setBody("""{"serial":"tablet:1234"}""")
        }
        assertEquals(HttpStatusCode.OK, screenshot.status)
        assertEquals(ContentType.Image.PNG, screenshot.contentType())
        assertContentEquals(png, screenshot.bodyAsBytes())
        val invalid = client.post("/adb/restart") {
            contentType(ContentType.Application.Json)
            setBody("""{"serial":"-e"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
    }

    @Test
    fun unavailableAdbReturnsServiceUnavailable() = testApplication {
        val adb = AdbService(
            temporary.root,
            PlayerApkRepository(null),
            commandRunner = { _, _ -> throw java.io.IOException("not installed") },
        )
        application {
            configureSerialization()
            routing { adbRoutes(adb) }
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/adb").status)
    }
}
