package tech.shaffersoftworks.remotescreens.server.service

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.shaffersoftworks.remotescreens.server.data.apk.PlayerApkRepository
import tech.shaffersoftworks.remotescreens.shared.SentrySettings

private const val MAPPING_POLL_MS = 30_000L
private const val MAX_TOKEN_LENGTH = 4096

@Serializable
data class MappingUploadSettings(val organization: String = "", val token: String = "")

@Serializable
data class MappingBuild(
    val uuid: String,
    val version: String,
    val apkSha256: String,
    val mappingSha256: String,
)

@Serializable
data class MappingUploadStatus(
    val organization: String = "",
    val tokenConfigured: Boolean = false,
    val status: String = "unconfigured",
    val message: String = "Configure a Sentry upload token to enable deobfuscation.",
    val builds: List<MappingBuildStatus> = emptyList(),
)

@Serializable
data class MappingBuildStatus(val version: String, val uuid: String, val uploaded: Boolean)

/** Uploads only locally packaged mappings. Credentials never enter layouts or player settings. */
class SentryMappingService(
    dataDir: File,
    apks: PlayerApkRepository,
    private val settings: () -> SentrySettings,
    private val uploader: suspend (String, String, String, MappingBuild, File) -> Boolean = ::uploadMapping,
) {
    private val archive = SentryMappingArchive(dataDir, apks)
    private val credentialsFile = File(archive.directory, "credentials.json")
    private val uploadedFile = File(archive.directory, "uploaded.json")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var credentials = runCatching {
        json.decodeFromString<MappingUploadSettings>(credentialsFile.readText())
    }.getOrDefault(MappingUploadSettings())
    private val uploaded = runCatching {
        json.decodeFromString<Set<String>>(uploadedFile.readText()).toMutableSet()
    }.getOrDefault(mutableSetOf())
    private val attempted = mutableSetOf<String>()
    @Volatile private var current = MappingUploadStatus(
        organization = credentials.organization,
        tokenConfigured = credentials.token.isNotBlank(),
    )

    fun status(): MappingUploadStatus = current

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                reconcile()
                delay(MAPPING_POLL_MS)
            }
        }
    }

    suspend fun configure(request: MappingUploadSettings): MappingUploadStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            val organization = request.organization.trim()
            require(organization.matches(Regex("[a-zA-Z0-9_-]{1,100}"))) { "Enter your Sentry organization slug" }
            val token = request.token.trim().ifEmpty { credentials.token }
            require(token.length <= MAX_TOKEN_LENGTH && token.none { it.isWhitespace() }) { "Invalid Sentry token" }
            require(token.isNotEmpty()) { "Enter a Sentry upload token" }
            require(organization == credentials.organization || request.token.isNotBlank()) {
                "Enter a token when changing organization"
            }
            val updated = MappingUploadSettings(organization, token)
            privateMappingWrite(credentialsFile, json.encodeToString(updated))
            credentials = updated
            attempted.clear()
        }
        reconcile()
    }

    suspend fun clear(): MappingUploadStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            privateMappingWrite(credentialsFile, json.encodeToString(MappingUploadSettings()))
            credentials = MappingUploadSettings()
            attempted.clear()
            current = MappingUploadStatus()
        }
        current
    }

    suspend fun reconcile(force: Boolean = false): MappingUploadStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                syncMappings(force)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception) {
                // Exception details and process output may contain credentials; expose only a safe message.
                current = current.copy(
                    status = "failed",
                    message = "Could not prepare or upload mappings. Check server storage and retry.",
                )
            }
            current
        }
    }

    private suspend fun syncMappings(force: Boolean) {
        archive.archiveCurrent()
        val builds = archive.builds()
        val sentry = settings()
        val project = cloudProject(sentry.dsn)
        val prerequisite = when {
            !sentry.enabled -> "disabled" to "Enable and save crash reporting to upload mappings."
            credentials.token.isBlank() -> "unconfigured" to "Configure a Sentry upload token to enable deobfuscation."
            project == null -> "unsupported" to "Mapping uploads currently support Sentry Cloud DSNs only."
            builds.isEmpty() -> "missing" to "This APK has no bundled mapping. Install a release with mapping support."
            else -> null
        }
        if (prerequisite != null) {
            report(builds, project, prerequisite.first, prerequisite.second)
            return
        }
        checkNotNull(project)
        val pending = builds.filter { key(it, project) !in uploaded }
        val candidates = pending.filter { force || key(it, project) !in attempted }
        for (build in candidates) {
            attempted.add(key(build, project))
            report(builds, project, "uploading", "Uploading mapping for ${build.version}…")
            if (!upload(build, project)) {
                report(
                    builds,
                    project,
                    "failed",
                    "Upload failed. Check the token's upload scope, organization, network, and quota, then retry.",
                )
                return
            }
        }
        val complete = builds.all { key(it, project) in uploaded }
        report(
            builds,
            project,
            if (complete) "uploaded" else "failed",
            if (complete) "Mappings uploaded for ${builds.size} player build(s)." else "Some mappings need a retry.",
        )
    }

    private suspend fun upload(build: MappingBuild, project: String): Boolean {
        val mapping = archive.verifiedMapping(build)
        val success = uploader(credentials.organization, project, credentials.token, build, mapping)
        if (success) {
            val updated = uploaded + key(build, project)
            privateMappingWrite(uploadedFile, json.encodeToString(updated))
            uploaded.add(key(build, project))
        }
        return success
    }

    private fun key(build: MappingBuild, project: String?): String =
        "${credentials.organization}/$project/${build.uuid}/${build.mappingSha256}"

    private fun report(builds: List<MappingBuild>, project: String?, status: String, message: String) {
        current = MappingUploadStatus(
            organization = credentials.organization,
            tokenConfigured = credentials.token.isNotBlank(),
            status = status,
            message = message,
            builds = builds.map { MappingBuildStatus(it.version, it.uuid, key(it, project) in uploaded) },
        )
    }

    companion object {
        /** The project ID is the DSN's final path segment, never the organization ID in its hostname. */
        internal fun cloudProject(dsn: String): String? = runCatching {
            val uri = java.net.URI(dsn)
            val host = uri.host.orEmpty()
            val cloud = listOf(".ingest.sentry.io", ".ingest.us.sentry.io", ".ingest.de.sentry.io")
                .any(host::endsWith)
            uri.path.substringAfterLast('/').takeIf {
                cloud && uri.scheme == "https" && it.matches(Regex("[0-9]+"))
            }
        }.getOrNull()
    }
}
