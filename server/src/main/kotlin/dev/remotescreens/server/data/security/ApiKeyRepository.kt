package dev.remotescreens.server.data.security

import java.io.File
import java.security.SecureRandom
import java.util.Base64

private const val API_KEY_BYTES = 24

/** The key the integration sends; made once and kept with the layouts, readable by the add-on only. */
class ApiKeyRepository(dataDir: File) {
    private val file = File(dataDir, "api_key")

    val apiKey: String by lazy { if (file.exists()) file.readText().trim() else createKey() }

    private fun createKey(): String {
        val bytes = ByteArray(API_KEY_BYTES).also(SecureRandom()::nextBytes)
        val key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        file.writeText(key)
        file.setReadable(false, false)
        file.setReadable(true, true)
        return key
    }
}
