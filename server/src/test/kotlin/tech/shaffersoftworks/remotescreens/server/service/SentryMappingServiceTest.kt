package tech.shaffersoftworks.remotescreens.server.service

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tech.shaffersoftworks.remotescreens.server.data.apk.PlayerApkRepository
import tech.shaffersoftworks.remotescreens.shared.SentrySettings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentryMappingServiceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val uuid = "3538ea8c-caa8-4166-bc50-beaa617b8125"
    private val token = "test-secret-not-for-responses"

    @Test
    fun uploadsExactMappingOnceAndRetainsItAfterRestartAndApkUpdate() = runBlocking {
        val data = temporary.newFolder("data")
        val apkDir = temporary.newFolder("apk")
        bundle(apkDir, uuid, "1.0.0")
        var calls = 0
        val service = SentryMappingService(
            data,
            PlayerApkRepository(apkDir),
            { enabled() },
        ) { org, project, secret, build, file ->
            assertEquals("my-org", org)
            assertEquals("456", project)
            assertEquals(token, secret)
            assertEquals(uuid, build.uuid)
            assertTrue(file.readText().contains("Example"))
            calls++
            true
        }
        val result = service.configure(MappingUploadSettings("my-org", token))
        assertEquals("uploaded", result.status)
        assertFalse(Json.encodeToString(result).contains(token))
        service.reconcile()
        service.reconcile(force = true)
        assertEquals(1, calls)
        val credentials = File(data, "sentry-mappings/credentials.json")
        assertEquals(
            PosixFilePermissions.fromString("rw-------"),
            Files.getPosixFilePermissions(credentials.toPath()),
        )
        bundle(apkDir, UUID.randomUUID().toString(), "1.0.1")
        // Use a fresh service to exercise startup with the new bundled APK.
        val restarted = SentryMappingService(data, PlayerApkRepository(apkDir), { enabled() }) { _, _, _, _, _ ->
            calls++
            true
        }
        assertEquals("uploaded", restarted.reconcile().status)
        assertEquals(2, restarted.status().builds.size)
        assertEquals(2, calls)
        assertTrue(restarted.status().builds.all { it.uploaded })
    }

    @Test
    fun disabledReportingRetainsMappingWithoutUploadingAndProjectChangeUploadsAgain() = runBlocking {
        val data = temporary.newFolder("data")
        val apkDir = temporary.newFolder("apk")
        bundle(apkDir, uuid, "1.0.0")
        var sentry = enabled().copy(enabled = false)
        val projects = mutableListOf<String>()
        val service = SentryMappingService(data, PlayerApkRepository(apkDir), { sentry }) { _, project, _, _, _ ->
            projects.add(project)
            true
        }
        assertEquals("disabled", service.configure(MappingUploadSettings("my-org", token)).status)
        assertTrue(projects.isEmpty())
        sentry = enabled()
        assertEquals("uploaded", service.reconcile().status)
        sentry = enabled("789")
        assertEquals("uploaded", service.reconcile().status)
        assertEquals(listOf("456", "789"), projects)
        service.clear()
        assertFalse(service.status().tokenConfigured)
        assertEquals("unconfigured", service.reconcile().status)
    }

    @Test
    fun failureRequiresRetryAndSecretIsAbsentFromStatus() = runBlocking {
        val data = temporary.newFolder("data")
        val apkDir = temporary.newFolder("apk")
        bundle(apkDir, uuid, "1.0.0")
        var calls = 0
        var success = false
        val service = SentryMappingService(data, PlayerApkRepository(apkDir), { enabled() }) { _, _, _, _, _ ->
            calls++
            success
        }
        assertEquals("failed", service.configure(MappingUploadSettings("my-org", token)).status)
        service.reconcile()
        assertEquals(1, calls)
        success = true
        assertEquals("uploaded", service.reconcile(force = true).status)
        assertEquals(2, calls)
        assertFalse(Json.encodeToString(service.status()).contains(token))
    }

    @Test
    fun rejectsReplacedApkAndTamperedArchivedMapping() = runBlocking {
        val data = temporary.newFolder("data")
        val apkDir = temporary.newFolder("apk")
        bundle(apkDir, uuid, "1.0.0")
        var calls = 0
        var sentry = enabled().copy(enabled = false)
        val service = SentryMappingService(data, PlayerApkRepository(apkDir), { sentry }) { _, _, _, _, _ ->
            calls++
            true
        }
        service.configure(MappingUploadSettings("my-org", token))
        File(data, "sentry-mappings/$uuid.txt").appendText("tampered")
        sentry = enabled()
        assertEquals("failed", service.reconcile().status)
        assertEquals(0, calls)
        File(apkDir, "remote-screens.apk").appendText("different APK")
        assertEquals("failed", service.reconcile(force = true).status)
        assertEquals(0, calls)
    }

    @Test
    fun cloudDsnUsesProjectPathAndRejectsUntrustedHosts() {
        assertEquals("456", SentryMappingService.cloudProject("https://key@o123.ingest.us.sentry.io/456"))
        assertEquals(null, SentryMappingService.cloudProject("https://key@o123.ingest.us.sentry.io.evil.test/456"))
        assertEquals(null, SentryMappingService.cloudProject("http://key@o123.ingest.us.sentry.io/456"))
    }

    private fun enabled(project: String = "456") =
        SentrySettings(true, "https://key@o123.ingest.us.sentry.io/$project")

    private fun bundle(directory: File, id: String, version: String) {
        val apk = File(directory, "remote-screens.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("assets/sentry-debug-meta.properties"))
            zip.write("io.sentry.ProguardUuids=$id\n".toByteArray())
            zip.closeEntry()
        }
        File(directory, "mapping.txt").writeText("com.example.Example -> a:\n    void method() -> b\n")
        File(directory, "mapping-apk.sha256").writeText(mappingSha256(apk))
        File(directory, "version.json").writeText("""{"versionCode":1,"versionName":"$version"}""")
    }
}
