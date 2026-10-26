package tech.shaffersoftworks.remotescreens.player.data.device.telemetry

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tech.shaffersoftworks.remotescreens.player.data.device.ProximityScale
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
) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
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
    private var isListening = false
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
        if (manager == null || isListening) return
        isListening = true
        listOfNotNull(lightSensor, pressureSensor, temperatureSensor, humiditySensor, proximitySensor)
            .forEach { sensor -> manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
        Timber.d("Environment sensors started: %s", hardware)
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(listener)
        isListening = false
        announcedLux = null
        _readings.value = EnvironmentTelemetry()
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

    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values.firstOrNull() ?: return
            when (event.sensor.type) {
                Sensor.TYPE_LIGHT -> onLight(value)
                Sensor.TYPE_PRESSURE -> _readings.update { it.copy(pressureHpa = value) }
                Sensor.TYPE_AMBIENT_TEMPERATURE -> _readings.update { it.copy(temperatureC = value) }
                Sensor.TYPE_RELATIVE_HUMIDITY -> _readings.update { it.copy(humidityPercent = value) }
                Sensor.TYPE_PROXIMITY -> onProximity(
                    value,
                    event.sensor.maximumRange,
                    event.sensor.resolution,
                )
            }
        }
    }
}
