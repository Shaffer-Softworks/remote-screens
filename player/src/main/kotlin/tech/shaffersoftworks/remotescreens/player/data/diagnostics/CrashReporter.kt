package tech.shaffersoftworks.remotescreens.player.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.hints.SubmissionResult
import io.sentry.protocol.Message
import io.sentry.protocol.SentryId
import io.sentry.util.HintUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import tech.shaffersoftworks.remotescreens.shared.CrashTestResult
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

private const val TEST_TIMEOUT_MS = 8_000L
private const val MAX_EXIT_HISTORY = 32
private const val EXIT_LOG_TAG = "ProcessExitDiagnostics"
private const val REPORTED_EXIT_KEY = "last_reported_exit_timestamp"
private const val LOGGED_EXIT_KEY = "last_logged_exit_timestamp"

/**
 * The checkpoint after sending exits before [firstUnsent] in ascending [timestamps]. Later exits are compared
 * with `>`, so an accepted exit sharing the first unsent exit's timestamp stays pending and may repeat.
 */
internal fun exitCheckpoint(timestamps: List<Long>, firstUnsent: Int, previous: Long): Long {
    val unsentAt = timestamps.getOrNull(firstUnsent)
    val sent = timestamps.take(firstUnsent)
    return (if (unsentAt == null) sent else sent.filter { it < unsentAt }).lastOrNull() ?: previous
}

/** Owns the SDK for the whole process, including when the add-on cannot be reached. */
@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoScope private val scope: CoroutineScope,
    private val appLog: AppLog,
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        logPreviousExits()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                        Timber.tag(EXIT_LOG_TAG).w(error, "Could not log previous process exits")
                    }
                }
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

    /** Waits for the test event's transport acknowledgement rather than assuming flush means delivery. */
    suspend fun test(): CrashTestResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (applied?.enabled != true || !Sentry.isEnabled()) return@withLock CrashTestResult("disabled")
            try {
                val submitted = CompletableDeferred<Boolean>()
                val successful = java.util.concurrent.atomic.AtomicBoolean()
                val hint = HintUtils.createWithTypeCheckHint(object : SubmissionResult {
                    override fun isSuccess(): Boolean = successful.get()
                    override fun setResult(success: Boolean) {
                        successful.set(success)
                        submitted.complete(success)
                    }
                })
                val event = SentryEvent().apply {
                    level = SentryLevel.INFO
                    message = Message().apply { formatted = "Remote Screens connectivity test (nonfatal)" }
                    setTag("diagnostic", "connectivity-test")
                }
                val id = Sentry.captureEvent(event, hint)
                if (id == SentryId.EMPTY_ID) return@withLock CrashTestResult("dropped")
                val status = when (withTimeoutOrNull(TEST_TIMEOUT_MS) { submitted.await() }) {
                    true -> "accepted"
                    false -> "failed"
                    null -> "timeout"
                }
                CrashTestResult(status, id.toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                // SDK failures must return a diagnostic result rather than take down the player.
                Timber.w(error, "Sentry connectivity test failed")
                CrashTestResult("failed")
            }
        }
    }

    /** Android retains only a bounded exit history, oldest first here. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun exitHistory(): List<ApplicationExitInfo> = context.getSystemService(ActivityManager::class.java)
        ?.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXIT_HISTORY)
        .orEmpty()
        .sortedBy { it.timestamp }

    /** Retrospective diagnostics, not crash events from the currently installed release. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun reportPreviousExits() {
        val prefs = preferences()
        val lastReported = prefs.getLong(REPORTED_EXIT_KEY, 0L)
        val exits = exitHistory().filter { it.timestamp > lastReported }
        // A nonempty ID means the SDK accepted the event for processing, not delivery.
        val firstRejected = exits.indexOfFirst { exit ->
            val reason = exitReason(exit.reason)
            reason != null && Sentry.captureEvent(exitEvent(exit, reason)) == SentryId.EMPTY_ID
        }
        val checkpoint = exitCheckpoint(
            timestamps = exits.map { it.timestamp },
            firstUnsent = if (firstRejected < 0) exits.size else firstRejected,
            previous = lastReported,
        )
        if (checkpoint != lastReported && !prefs.edit().putLong(REPORTED_EXIT_KEY, checkpoint).commit()) {
            Timber.w("Could not persist previous process exit checkpoint")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun exitEvent(exit: ApplicationExitInfo, reason: String) = SentryEvent().apply {
        level = SentryLevel.WARNING
        message = Message().apply { formatted = "Previous player process exit: $reason" }
        fingerprints = listOf("player-process-exit", reason)
        setTag("diagnostic", "previous-process-exit")
        setTag("exit_reason", reason)
        setExtra("exit_timestamp_ms", exit.timestamp)
        setExtra("exit_pid", exit.pid)
        setExtra("exit_process", exit.processName)
        setExtra("exit_importance", exit.importance)
        setExtra("exit_pss_kb", exit.pss)
        setExtra("exit_rss_kb", exit.rss)
        setExtra("exit_status", exit.status)
        setExtra("retrospective", true)
    }

    /** Writes to Logcat and the add-on queue whether or not Sentry is configured. */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun logPreviousExits() {
        val prefs = preferences()
        val lastLogged = prefs.getLong(LOGGED_EXIT_KEY, 0L)
        val unlogged = exitHistory().filter { it.timestamp > lastLogged }
        for (exit in unlogged) {
            val reason = exitReason(exit.reason) ?: continue
            appLog.persistWarning(
                tag = EXIT_LOG_TAG,
                message = "Previous player process exit: reason=$reason timestampMs=${exit.timestamp} " +
                    "pid=${exit.pid} process=${exit.processName} importance=${exit.importance} " +
                    "pssKb=${exit.pss} rssKb=${exit.rss} status=${exit.status}",
                eventId = "$PROCESS_EXIT_EVENT_PREFIX${exit.timestamp}:${exit.pid}",
            )
        }
        unlogged.lastOrNull()?.let { exit ->
            if (!prefs.edit().putLong(LOGGED_EXIT_KEY, exit.timestamp).commit()) {
                appLog.warning(EXIT_LOG_TAG, "Could not persist process exit log checkpoint")
            }
        }
    }

    private fun exitReason(reason: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_CRASH -> "jvm_crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash"
        else -> null
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
                    options.release = "${BuildConfig.APPLICATION_ID}@" +
                        "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
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
            if (settings.enabled && Sentry.isEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { reportPreviousExits() }
                    .onFailure { Timber.tag(EXIT_LOG_TAG).w(it, "Could not report previous process exits") }
            }
        }.onFailure {
            // A reporting failure must never take the player down or reconnect its socket.
            Timber.w(it, "Could not configure crash reporting")
        }
    }
}
