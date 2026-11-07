package tech.shaffersoftworks.remotescreens.player.data.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import tech.shaffersoftworks.remotescreens.player.BuildConfig
import tech.shaffersoftworks.remotescreens.player.di.IoScope
import tech.shaffersoftworks.remotescreens.shared.SentrySettings
import timber.log.Timber

/** Owns the SDK for the whole process, including when the add-on cannot be reached. */
@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var applied: SentrySettings? = null

    fun start() {
        scope.launch {
            mutex.withLock {
                val stored = preferences().getString("settings", null)
                val settings = stored?.let {
                    runCatching { json.decodeFromString<SentrySettings>(it).validated() }.getOrNull()
                } ?: SentrySettings()
                apply(settings)
            }
        }
    }

    suspend fun configure(settings: SentrySettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val clean = runCatching { settings.validated() }.getOrElse {
                Timber.w("Ignoring invalid crash reporting settings")
                return@withLock
            }
            if (clean == applied) return@withLock
            // Persist before enabling so a crash or offline restart keeps the same configuration.
            if (!preferences().edit().putString("settings", json.encodeToString(clean)).commit()) {
                Timber.w("Could not persist crash reporting settings")
                return@withLock
            }
            apply(clean)
        }
    }

    private fun preferences() = context.getSharedPreferences("crash_reporting", Context.MODE_PRIVATE)

    private fun apply(settings: SentrySettings) {
        if (settings == applied) return
        runCatching {
            Sentry.close()
            if (settings.enabled) {
                SentryAndroid.init(context) { options ->
                    options.dsn = settings.dsn
                    options.environment = settings.environment
                    options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                    options.isAnrEnabled = settings.anrEnabled
                    options.isEnableNdk = true
                    options.isSendDefaultPii = false
                    options.isAttachScreenshot = false
                    options.isAttachViewHierarchy = false
                    options.isEnableAutoSessionTracking = false
                    options.tracesSampleRate = 0.0
                    options.profilesSampleRate = 0.0
                    options.maxBreadcrumbs = 0
                    // Never send cached reports for one project's DSN to a different project.
                    val key = MessageDigest.getInstance("SHA-256")
                        .digest(settings.dsn.toByteArray()).joinToString("") { "%02x".format(it) }
                    options.cacheDirPath = File(context.cacheDir, "sentry/$key").absolutePath
                    options.setTag("component", "android-player")
                }
            }
            applied = settings
        }.onFailure {
            // A reporting failure must never take the player down or reconnect its socket.
            Timber.w(it, "Could not configure crash reporting")
        }
    }
}
