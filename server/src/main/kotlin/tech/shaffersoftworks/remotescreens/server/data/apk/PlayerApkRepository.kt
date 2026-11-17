package tech.shaffersoftworks.remotescreens.server.data.apk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A newer player app, offered to tablets that run an older one. */
data class PlayerApk(
    val versionCode: Int,
    val versionName: String,
    val file: File,
    /**
     * SHA-256 of the signing certificate, URL-safe Base64 without padding, as QR enrollment wants it. It stays the
     * same across updates; null when `version.json` was written without it.
     */
    val signatureChecksum: String? = null,
) {
    val displayVersion: String get() = versionName.ifEmpty { versionCode.toString() }
}

/** The player app bundled in the image, with `apk/version.json` saying which version it is. */
class PlayerApkRepository(private val apkDir: File?) {

    /** Read on every call, so an APK dropped in while the add-on runs is offered without a restart. */
    fun current(): PlayerApk? {
        val file = apkDir?.let { File(it, APK_FILE_NAME) }?.takeIf { it.isFile } ?: return null
        return runCatching {
            val meta = META_JSON.decodeFromString(
                ApkVersion.serializer(),
                File(file.parentFile, "version.json").readText(),
            )
            PlayerApk(
                versionCode = meta.versionCode,
                versionName = meta.versionName,
                file = file,
                signatureChecksum = meta.signatureChecksum?.ifBlank { null },
            )
        }.getOrNull()
    }

    /** `apk/version.json`, written by `tools/package_addon.sh`. */
    @Serializable
    private data class ApkVersion(
        val versionCode: Int,
        val versionName: String = "",
        val signatureChecksum: String? = null,
    )

    companion object {
        const val APK_FILE_NAME = "remote-screens.apk"
        private val META_JSON = Json { ignoreUnknownKeys = true }
    }
}
