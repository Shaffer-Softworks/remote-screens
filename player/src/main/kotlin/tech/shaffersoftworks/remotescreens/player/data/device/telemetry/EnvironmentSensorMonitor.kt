package tech.shaffersoftworks.remotescreens.player.data.device.telemetry

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.shaffersoftworks.remotescreens.player.data.device.ProximityScale
import tech.shaffersoftworks.remotescreens.player.di.MainDispatcher
import tech.shaffersoftworks.remotescreens.shared.EnvironmentTelemetry
import tech.shaffersoftworks.remotescreens.shared.TelemetryHardware
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Weight of a new reading in the running average, so a passing shadow does not swing the light level. */
private const val SMOOTHING = 0.2f

/** A light change worth reporting at once: this share of the last reported level... */
private const val LIGHT_CHANGE_RATIO = 0.2f

/** ...and at least this many lux, so a dark room's noise does not count. */
private const val LIGHT_CHANGE_MIN_LUX = 5f

/**
 * Ambient light, pressure, temperature, humidity and proximity, for the tablets that have them. Missing sensors are
 * left out; nothing here needs a permission.
 */
@Singleton
class EnvironmentSensorMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var listenJob: Job? = null
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val temperatureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
    private val humiditySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _readings = MutableStateFlow(EnvironmentTelemetry())
    val readings: StateFlow<EnvironmentTelemetry> = _readings.asStateFlow()

    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** A reading moved enough to report before the next scheduled report. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private var announcedLux: Float? = null
    private val proximityScale = ProximityScale()

    val hardware: List<String>
        get() = listOfNotNull(
            lightSensor?.let { TelemetryHardware.LIGHT },
            pressureSensor?.let { TelemetryHardware.PRESSURE },
            temperatureSensor?.let { TelemetryHardware.AMBIENT_TEMPERATURE },
            humiditySensor?.let { TelemetryHardware.HUMIDITY },
            proximitySensor?.let { TelemetryHardware.PROXIMITY },
        )

    fun start() {
        val manager = sensorManager
        if (manager == null || listenJob?.isActive == true) return
        val sensors = listOfNotNull(
            lightSensor,
            pressureSensor,
            temperatureSensor,
            humiditySensor,
            proximitySensor,
        )
        listenJob = scope.launch {
            callbackFlow {
                val sensorListener = object : SensorEventListener {
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                    override fun onSensorChanged(event: SensorEvent) {
                        val value = event.values.firstOrNull() ?: return
                        trySend(
                            EnvironmentSample(
                                type = event.sensor.type,
                                value = value,
                                maximumRange = event.sensor.maximumRange,
                                resolution = event.sensor.resolution,
                            ),
                        )
                    }
                }
                sensors.forEach { sensor ->
                    manager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }
                awaitClose { manager.unregisterListener(sensorListener) }
            }.collect { sample -> onSample(sample) }
        }
        Timber.d("Environment sensors started: %s", hardware)
    }

    fun stop() {
        if (listenJob == null) return
        listenJob?.cancel()
        listenJob = null
        announcedLux = null
        _readings.value = EnvironmentTelemetry()
    }

    private fun onSample(sample: EnvironmentSample) {
        when (sample.type) {
            Sensor.TYPE_LIGHT -> onLight(sample.value)
            Sensor.TYPE_PRESSURE -> _readings.update { it.copy(pressureHpa = sample.value) }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> _readings.update { it.copy(temperatureC = sample.value) }
            Sensor.TYPE_RELATIVE_HUMIDITY -> _readings.update { it.copy(humidityPercent = sample.value) }
            Sensor.TYPE_PROXIMITY -> onProximity(sample.value, sample.maximumRange, sample.resolution)
        }
    }

    private fun onLight(lux: Float) {
        val smoothedLux = _readings.value.illuminanceLux?.let { it + SMOOTHING * (lux - it) } ?: lux
        _readings.update { it.copy(illuminanceLux = smoothedLux) }
        val previousLux = announcedLux
        val isBigChange = previousLux == null ||
            abs(smoothedLux - previousLux) >= maxOf(previousLux * LIGHT_CHANGE_RATIO, LIGHT_CHANGE_MIN_LUX)
        if (isBigChange) {
            announcedLux = smoothedLux
            if (previousLux != null) _changes.tryEmit(Unit)
        }
    }

    private fun onProximity(distance: Float, maximumRange: Float, resolution: Float) {
        val isNear = proximityScale.isNear(distance, maximumRange, resolution)
        val wasNear = _readings.value.isNear
        _readings.update { it.copy(isNear = isNear) }
        if (wasNear != null && wasNear != isNear) _changes.tryEmit(Unit)
    }
}

private data class EnvironmentSample(
    val type: Int,
    val value: Float,
    val maximumRange: Float,
    val resolution: Float,
)
