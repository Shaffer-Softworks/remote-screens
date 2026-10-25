package tech.shaffersoftworks.remotescreens.player.data.device.telemetry

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.shaffersoftworks.remotescreens.player.di.IoDispatcher
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import timber.log.Timber
import javax.inject.Inject

/** A full report this often, even when nothing changed, so Home Assistant sees the tablet is alive. */
internal const val REPORT_INTERVAL_MS = 60_000L

/** Changes that come quicker than this are folded into one report. */
internal const val MIN_REPORT_GAP_MS = 5_000L

/** Sends [PlayerMessage.Telemetry] on a schedule and whenever a reading changes enough to matter. */
class TelemetryReporter @Inject constructor(
    private val source: TelemetrySource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private var job: Job? = null
    private var isLocationShared: Boolean? = null
    private var wantsLocation: Boolean = false

    val isRunning: Boolean get() = job?.isActive == true

    fun hasLocationPermission(): Boolean = source.hasLocationPermission()

    /**
     * Starts, restarts or stops reporting. [isEnabled] is the add-on accepting telemetry and the screen's
     * `reportTelemetry` setting; [wantsLocation] is `shareLocation`, which also needs the permission.
     */
    fun update(
        scope: CoroutineScope,
        isEnabled: Boolean,
        wantsLocation: Boolean,
        send: suspend (PlayerMessage.Telemetry) -> Unit,
    ) {
        if (!isEnabled) {
            stop()
            return
        }
        this.wantsLocation = wantsLocation
        val shouldShareLocation = currentLocationShare()
        if (isRunning && isLocationShared == shouldShareLocation) {
            if (shouldShareLocation) source.start(true)
            return
        }
        stop()
        this.wantsLocation = wantsLocation
        isLocationShared = shouldShareLocation
        source.start(shouldShareLocation)
        Timber.d("Telemetry started, location %b", shouldShareLocation)
        job = scope.launch {
            merge(scheduledReports(), source.changes).conflate().collect {
                val shareNow = currentLocationShare()
                if (shareNow != isLocationShared) {
                    isLocationShared = shareNow
                    source.start(shareNow)
                    Timber.d("Telemetry location sharing is now %b", shareNow)
                } else if (shareNow) {
                    source.start(true)
                }
                val telemetry = withContext(ioDispatcher) { source.snapshot(shareNow) }
                send(PlayerMessage.Telemetry(telemetry))
                delay(MIN_REPORT_GAP_MS)
            }
        }
    }

    private fun currentLocationShare(): Boolean = wantsLocation && source.hasLocationPermission()

    fun stop() {
        job?.cancel()
        job = null
        if (isLocationShared != null) source.stop()
        isLocationShared = null
    }

    private fun scheduledReports(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(REPORT_INTERVAL_MS)
        }
    }
}
