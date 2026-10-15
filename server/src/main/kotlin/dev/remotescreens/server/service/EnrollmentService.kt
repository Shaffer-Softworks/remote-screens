package dev.remotescreens.server.service

import dev.remotescreens.server.data.apk.PlayerApk
import dev.remotescreens.server.data.apk.PlayerApkRepository
import dev.remotescreens.server.player.PlayerTokens
import dev.remotescreens.server.routes.model.EnrollmentRequest
import dev.remotescreens.server.routes.model.EnrollmentResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private const val ENROLLMENT_TOKEN_TTL_MINUTES = 60L
private const val MILLISECONDS_PER_MINUTE = 60_000L
private const val EXTRA = "android.app.extra."
private val WIFI_SECURITY_TYPES = setOf("NONE", "WPA", "WEP")

/** Why an enrollment QR code cannot be made. */
class EnrollmentUnavailableException(message: String) : RuntimeException(message)

/**
 * QR enrollment: the code a factory-reset tablet scans to download the player from the add-on and make it its device
 * owner. The download is allowed with a short-lived token, so several tablets can be enrolled from one code.
 */
class EnrollmentService(
    private val playerApkRepository: PlayerApkRepository,
    private val playerPort: Int,
    private val playerTokens: PlayerTokens = PlayerTokens(),
    private val defaultHost: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Token to the time it expires. */
    private val tokens = ConcurrentHashMap<String, Long>()

    fun createEnrollment(request: EnrollmentRequest): EnrollmentResponse {
        val apk = playerApkRepository.current()
            ?: throw EnrollmentUnavailableException("The add-on has no player app to hand out")
        val host = request.host?.trim()?.ifEmpty { null } ?: defaultHost()
            ?: throw EnrollmentUnavailableException("The add-on's address on the network is unknown; enter it")
        val token = playerTokens.newToken()
        val expiresAt = now() + ENROLLMENT_TOKEN_TTL_MINUTES * MILLISECONDS_PER_MINUTE
        tokens[token] = expiresAt
        val server = "$host:$playerPort"
        val downloadUrl = "http://$server/player/apk?enroll=$token"
        return EnrollmentResponse(
            provisioning = provisioningJson(apk, downloadUrl, server, request).toString(),
            downloadUrl = downloadUrl,
            expiresAt = expiresAt,
        )
    }

    fun isValidToken(token: String?): Boolean {
        val currentTime = now()
        tokens.entries.removeIf { (_, expiresAt) -> expiresAt <= currentTime }
        return token != null && tokens.containsKey(token)
    }

    private fun provisioningJson(
        apk: PlayerApk,
        downloadUrl: String,
        server: String,
        request: EnrollmentRequest,
    ): JsonObject = buildJsonObject {
        put("${EXTRA}PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", ADMIN_COMPONENT)
        put("${EXTRA}PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", downloadUrl)
        val signatureChecksum = apk.signatureChecksum
        if (signatureChecksum != null) {
            put("${EXTRA}PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM", signatureChecksum)
        } else {
            put("${EXTRA}PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM", packageChecksum(apk))
        }
        put("${EXTRA}PROVISIONING_SKIP_ENCRYPTION", true)
        put("${EXTRA}PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", true)
        val wifiSsid = request.wifiSsid?.trim()?.ifEmpty { null }
        if (wifiSsid != null) {
            put("${EXTRA}PROVISIONING_WIFI_SSID", wifiSsid)
            val security = request.wifiSecurity?.uppercase()?.takeIf { it in WIFI_SECURITY_TYPES } ?: "WPA"
            put("${EXTRA}PROVISIONING_WIFI_SECURITY_TYPE", security)
            request.wifiPassword?.takeIf { it.isNotEmpty() && security != "NONE" }
                ?.let { password -> put("${EXTRA}PROVISIONING_WIFI_PASSWORD", password) }
        }
        putJsonObject("${EXTRA}PROVISIONING_ADMIN_EXTRAS_BUNDLE") { put(EXTRA_SERVER, server) }
    }

    /** The APK's own SHA-256, for an APK packaged without its signature checksum; it changes with every build. */
    private fun packageChecksum(apk: PlayerApk): String {
        val digest = MessageDigest.getInstance("SHA-256")
        apk.file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
    }

    companion object {
        const val ADMIN_COMPONENT =
            "dev.remotescreens.player/dev.remotescreens.player.receiver.LockTaskDeviceAdminReceiver"

        /** The add-on's `host:port`, handed to the player in the admin extras so it connects without searching. */
        const val EXTRA_SERVER = "server"
    }
}
