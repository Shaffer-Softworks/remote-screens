package tech.shaffersoftworks.remotescreens.server.service

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import tech.shaffersoftworks.remotescreens.server.data.apk.PlayerApkRepository

private const val HASH_BUFFER_BYTES = 8192
private const val UPLOAD_TIMEOUT_SECONDS = 120L

/** Retains exact build mappings in persistent add-on data before the bundled APK is replaced. */
internal class SentryMappingArchive(dataDir: File, private val apks: PlayerApkRepository) {
    val directory = File(dataDir, "sentry-mappings").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    @Suppress("ReturnCount") // Missing APK/mapping/UUID is expected for releases before mapping support.
    fun archiveCurrent() {
        val apk = apks.current() ?: return
        val mapping = File(apk.file.parentFile, "mapping.txt").takeIf { it.isFile } ?: return
        val uuid = ZipFile(apk.file).use { zip ->
            val entry = zip.getEntry("assets/sentry-debug-meta.properties") ?: return
            val properties = Properties()
            zip.getInputStream(entry).use(properties::load)
            UUID.fromString(properties.getProperty("io.sentry.ProguardUuids")).toString()
        }
        val apkHash = mappingSha256(apk.file)
        val packagedHash = File(apk.file.parentFile, "mapping-apk.sha256")
        check(packagedHash.isFile && packagedHash.readText().trim() == apkHash) { "APK does not match mapping package" }
        val build = MappingBuild(uuid, apk.displayVersion, apkHash, mappingSha256(mapping))
        val record = File(directory, "build-$apkHash.json")
        if (record.isFile) {
            check(json.decodeFromString<MappingBuild>(record.readText()) == build) { "Mapping identity changed" }
            return
        }
        val archived = File(directory, "$uuid.txt")
        check(!archived.exists() || mappingSha256(archived) == build.mappingSha256) { "Mapping UUID collision" }
        Files.copy(mapping.toPath(), archived.toPath(), StandardCopyOption.REPLACE_EXISTING)
        privateMappingWrite(record, json.encodeToString(build))
    }

    fun builds(): List<MappingBuild> = directory.listFiles().orEmpty()
        .filter { it.extension == "json" && it.name.startsWith("build-") }
        .map { json.decodeFromString<MappingBuild>(it.readText()) }
        .sortedByDescending { it.version }

    fun verifiedMapping(build: MappingBuild): File {
        val mapping = File(directory, "${UUID.fromString(build.uuid)}.txt")
        check(mapping.isFile && mappingSha256(mapping) == build.mappingSha256) { "Stored mapping verification failed" }
        return mapping
    }
}

internal fun mappingSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(HASH_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun privateMappingWrite(file: File, text: String) {
    val temporary = Files.createTempFile(file.parentFile.toPath(), ".sentry-", ".tmp")
    try {
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"))
        Files.writeString(temporary, text)
        Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

/** No shell expansion or token on the command line; never expose the CLI's raw output. */
internal suspend fun uploadMapping(
    organization: String,
    project: String,
    token: String,
    build: MappingBuild,
    mapping: File,
): Boolean = withContext(Dispatchers.IO) {
    val process = ProcessBuilder(
        "sentry-cli", "--url", "https://sentry.io", "upload-proguard",
        "--org", organization, "--project", project, "--uuid", build.uuid, "--require-one", mapping.absolutePath,
    ).apply {
        environment().keys.removeIf { it.startsWith("SENTRY_") }
        environment()["SENTRY_AUTH_TOKEN"] = token
        environment()["SENTRY_DISABLE_UPDATE_CHECK"] = "1"
        environment()["SENTRY_LOAD_DOTENV"] = "0"
        redirectOutput(ProcessBuilder.Redirect.DISCARD)
        redirectError(ProcessBuilder.Redirect.DISCARD)
    }.start()
    try {
        process.waitFor(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0
    } finally {
        if (process.isAlive) process.destroyForcibly()
    }
}
