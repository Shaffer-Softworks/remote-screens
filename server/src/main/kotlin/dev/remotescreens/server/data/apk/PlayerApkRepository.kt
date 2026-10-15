package dev.remotescreens.server.data.apk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
            val meta = Json.parseToJsonElement(File(file.parentFile, "version.json").readText()).jsonObject
            PlayerApk(
                versionCode = checkNotNull(meta["versionCode"]).jsonPrimitive.int,
                versionName = meta["versionName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                file = file,
                signatureChecksum = meta["signatureChecksum"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
            )
        }.getOrNull()
    }

    companion object {
        const val APK_FILE_NAME = "remote-screens.apk"
    }
}
