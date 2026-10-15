package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.server.data.apk.PlayerApkRepository
import tech.shaffersoftworks.remotescreens.server.routes.model.EnrollmentRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EXTRA = "android.app.extra."
private const val ONE_HOUR_MS = 3_600_000L

class EnrollmentServiceTest {
    private var currentTime = 1_000L

    private fun apkDir(signatureChecksum: String? = "c2lnbmF0dXJl"): File {
        val dir = Files.createTempDirectory("apk").toFile()
        File(dir, PlayerApkRepository.APK_FILE_NAME).writeText("apk")
        val checksum = signatureChecksum?.let { """, "signatureChecksum": "$it"""" }.orEmpty()
        File(dir, "version.json").writeText("""{"versionCode": 100, "versionName": "0.1.0"$checksum}""")
        return dir
    }

    private fun service(apkDir: File? = apkDir(), defaultHost: String? = "192.168.1.10") = EnrollmentService(
        playerApkRepository = PlayerApkRepository(apkDir),
        playerPort = 8100,
        defaultHost = { defaultHost },
        now = { currentTime },
    )

    private fun provisioningOf(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun tokenOf(downloadUrl: String): String = downloadUrl.substringAfter("enroll=")

    @Test
    fun theCodeNamesTheAdminTheDownloadAndTheSignature() {
        val enrollment = service().createEnrollment(EnrollmentRequest())
        val provisioning = provisioningOf(enrollment.provisioning)

        assertEquals(
            EnrollmentService.ADMIN_COMPONENT,
            provisioning["${EXTRA}PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"]?.jsonPrimitive?.content,
        )
        assertEquals(
            enrollment.downloadUrl,
            provisioning["${EXTRA}PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"]?.jsonPrimitive?.content,
        )
        assertTrue(enrollment.downloadUrl.startsWith("http://192.168.1.10:8100/player/apk?enroll="))
        assertEquals(
            "c2lnbmF0dXJl",
            provisioning["${EXTRA}PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"]?.jsonPrimitive?.content,
        )
        assertTrue(provisioning["${EXTRA}PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED"]!!.jsonPrimitive.boolean)
        assertEquals(
            "192.168.1.10:8100",
            provisioning["${EXTRA}PROVISIONING_ADMIN_EXTRAS_BUNDLE"]?.jsonObject?.get("server")?.jsonPrimitive?.content,
        )
        assertNull(provisioning["${EXTRA}PROVISIONING_WIFI_SSID"])
    }

    @Test
    fun withoutASignatureTheApkItselfIsChecked() {
        val enrollment = service(apkDir = apkDir(signatureChecksum = null)).createEnrollment(EnrollmentRequest())
        val provisioning = provisioningOf(enrollment.provisioning)

        assertNull(provisioning["${EXTRA}PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"])
        // SHA-256 of "apk", URL-safe Base64 without padding.
        assertEquals(
            "3TfC1ydPfqmCy4M5DDaRj-6c6IiQc8RLaM3AC9uMPgQ",
            provisioning["${EXTRA}PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun wifiGoesIntoTheCode() {
        val request = EnrollmentRequest(wifiSsid = "Home", wifiPassword = "secret", wifiSecurity = "wpa")
        val provisioning = provisioningOf(service().createEnrollment(request).provisioning)

        assertEquals("Home", provisioning["${EXTRA}PROVISIONING_WIFI_SSID"]?.jsonPrimitive?.content)
        assertEquals("secret", provisioning["${EXTRA}PROVISIONING_WIFI_PASSWORD"]?.jsonPrimitive?.content)
        assertEquals("WPA", provisioning["${EXTRA}PROVISIONING_WIFI_SECURITY_TYPE"]?.jsonPrimitive?.content)
    }

    @Test
    fun anOpenNetworkHasNoPassword() {
        val request = EnrollmentRequest(wifiSsid = "Guest", wifiPassword = "ignored", wifiSecurity = "NONE")
        val provisioning = provisioningOf(service().createEnrollment(request).provisioning)

        assertNull(provisioning["${EXTRA}PROVISIONING_WIFI_PASSWORD"])
    }

    @Test
    fun aHostTypedInWinsOverTheOneFound() {
        val enrollment = service().createEnrollment(EnrollmentRequest(host = " homeassistant.local "))

        assertTrue(enrollment.downloadUrl.startsWith("http://homeassistant.local:8100/"))
    }

    @Test
    fun theTokenDownloadsUntilItExpires() {
        val enrollmentService = service()
        val token = tokenOf(enrollmentService.createEnrollment(EnrollmentRequest()).downloadUrl)

        assertTrue(enrollmentService.isValidToken(token))
        assertTrue(enrollmentService.isValidToken(token), "several tablets can enroll from one code")

        currentTime += ONE_HOUR_MS
        assertFalse(enrollmentService.isValidToken(token))
    }

    @Test
    fun unknownTokensDoNotDownload() {
        val enrollmentService = service()
        enrollmentService.createEnrollment(EnrollmentRequest())

        assertFalse(enrollmentService.isValidToken(null))
        assertFalse(enrollmentService.isValidToken("guess"))
    }

    @Test
    fun noCodeWithoutAnAppOrAnAddress() {
        assertFailsWith<EnrollmentUnavailableException> { service(apkDir = null).createEnrollment(EnrollmentRequest()) }
        assertFailsWith<EnrollmentUnavailableException> {
            service(defaultHost = null).createEnrollment(EnrollmentRequest())
        }
    }
}
