package tech.shaffersoftworks.remotescreens.player.data.device.telemetry

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import tech.shaffersoftworks.remotescreens.shared.DeviceTelemetry
import tech.shaffersoftworks.remotescreens.shared.EnvironmentTelemetry
import javax.inject.Inject
import javax.inject.Singleton

/** Where [TelemetryReporter] gets the tablet's readings. */
interface TelemetrySource {
    /** Something changed enough to report before the next scheduled report. */
    val changes: Flow<Unit>

    fun start(isLocationShared: Boolean)

    fun stop()

    fun hasLocationPermission(): Boolean

    /** Reads everything now; blocking reads such as storage, so call it off the main thread. */
    fun snapshot(isLocationShared: Boolean): DeviceTelemetry
}

@Singleton
class DeviceTelemetrySource @Inject constructor(
    private val diagnosticsReader: DiagnosticsReader,
    private val environmentSensorMonitor: EnvironmentSensorMonitor,
    private val motionMonitor: MotionMonitor,
    private val locationMonitor: LocationMonitor,
) : TelemetrySource {
    override val changes: Flow<Unit> = merge(
        environmentSensorMonitor.changes,
        motionMonitor.changes,
        locationMonitor.changes,
    )

    override fun start(isLocationShared: Boolean) {
        environmentSensorMonitor.start()
        motionMonitor.start()
        if (isLocationShared) locationMonitor.start() else locationMonitor.stop()
    }

    override fun stop() {
        environmentSensorMonitor.stop()
        motionMonitor.stop()
        locationMonitor.stop()
    }

    override fun hasLocationPermission(): Boolean = locationMonitor.hasPermission()

    override fun snapshot(isLocationShared: Boolean): DeviceTelemetry = DeviceTelemetry(
        hardware = environmentSensorMonitor.hardware + motionMonitor.hardware + locationMonitor.hardware,
        battery = diagnosticsReader.readBattery(),
        network = diagnosticsReader.readNetwork(),
        system = diagnosticsReader.readSystem(),
        environment = environmentSensorMonitor.readings.value.takeIf { it != EMPTY_ENVIRONMENT },
        motion = motionMonitor.snapshot(),
        location = if (isLocationShared) locationMonitor.location.value else null,
    )

    private companion object {
        val EMPTY_ENVIRONMENT = EnvironmentTelemetry()
    }
}
