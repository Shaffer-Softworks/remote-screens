package tech.shaffersoftworks.remotescreens.player.data.device.telemetry

import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import tech.shaffersoftworks.remotescreens.player.di.MainDispatcher
import tech.shaffersoftworks.remotescreens.shared.MotionTelemetry
import tech.shaffersoftworks.remotescreens.shared.TelemetryHardware
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/** How long "moving" holds after the last movement; longer than Home Assistant's 30 s poll. */
private const val MOVING_LATCH_MS = 60_000L

/** Acceleration left after taking gravity out, in m/s², that counts as the tablet being moved or knocked. */
private const val MOVEMENT_THRESHOLD = 1.2f

/** Weight of a new reading in the gravity estimate (a low-pass filter). */
private const val GRAVITY_SMOOTHING = 0.1f

private const val AXES = 3

/** Whether someone moves or knocks the tablet, from the accelerometer and the significant-motion sensor. */
@Singleton
class MotionMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var listenJob: Job? = null
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val significantMotionSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Movement started after a still spell. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val gravity = FloatArray(AXES)
    private var hasGravity = false
    private var lastMotionElapsedMs: Long? = null
    private var lastMotionAt: Long? = null

    val hardware: List<String>
        get() = listOfNotNull(
            accelerometer?.let { TelemetryHardware.ACCELEROMETER },
            significantMotionSensor?.let { TelemetryHardware.SIGNIFICANT_MOTION },
        )

    fun start() {
        val manager = sensorManager
        if (manager == null || listenJob?.isActive == true) return
        listenJob = scope.launch {
            callbackFlow {
                val samples = this
                val accelerometerListener = object : SensorEventListener {
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                    override fun onSensorChanged(event: SensorEvent) {
                        if (event.values.size < AXES) return
                        trySend(
                            MotionSample.Acceleration(
                                x = event.values[0],
                                y = event.values[1],
                                z = event.values[2],
                            ),
                        )
                    }
                }
                val triggerListener = object : TriggerEventListener() {
                    override fun onTrigger(event: TriggerEvent?) {
                        samples.trySend(MotionSample.Significant)
                        // A trigger sensor disarms after firing.
                        val sensor = significantMotionSensor ?: return
                        if (listenJob?.isActive == true) manager.requestTriggerSensor(this, sensor)
                    }
                }
                accelerometer?.let { sensor ->
                    manager.registerListener(accelerometerListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }
                significantMotionSensor?.let { sensor -> manager.requestTriggerSensor(triggerListener, sensor) }
                awaitClose {
                    manager.unregisterListener(accelerometerListener)
                    significantMotionSensor?.let { sensor -> manager.cancelTriggerSensor(triggerListener, sensor) }
                }
            }.collect { sample ->
                when (sample) {
                    is MotionSample.Acceleration -> onAcceleration(sample)
                    MotionSample.Significant -> onMotion()
                }
            }
        }
        Timber.d("Motion monitor started: %s", hardware)
    }

    fun stop() {
        if (listenJob == null) return
        listenJob?.cancel()
        listenJob = null
        hasGravity = false
    }

    fun snapshot(): MotionTelemetry {
        val lastMotionElapsed = lastMotionElapsedMs
        val isMoving = lastMotionElapsed != null && SystemClock.elapsedRealtime() - lastMotionElapsed < MOVING_LATCH_MS
        return MotionTelemetry(
            isMoving = isMoving,
            orientation = orientationName(),
            lastMotionAt = lastMotionAt,
        )
    }

    private fun orientationName(): String? = when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> "portrait"
        Configuration.ORIENTATION_LANDSCAPE -> "landscape"
        else -> null
    }

    private fun onMotion() {
        val now = SystemClock.elapsedRealtime()
        val previousMotion = lastMotionElapsedMs
        val wasStill = previousMotion == null || now - previousMotion >= MOVING_LATCH_MS
        lastMotionElapsedMs = now
        lastMotionAt = System.currentTimeMillis()
        if (wasStill) _changes.tryEmit(Unit)
    }

    private fun onAcceleration(sample: MotionSample.Acceleration) {
        if (!hasGravity) {
            gravity[0] = sample.x
            gravity[1] = sample.y
            gravity[2] = sample.z
            hasGravity = true
            return
        }
        val axes = floatArrayOf(sample.x, sample.y, sample.z)
        var linearSquared = 0f
        for (axis in 0 until AXES) {
            gravity[axis] += GRAVITY_SMOOTHING * (axes[axis] - gravity[axis])
            val linear = axes[axis] - gravity[axis]
            linearSquared += linear * linear
        }
        if (sqrt(linearSquared) >= MOVEMENT_THRESHOLD) onMotion()
    }

    private sealed interface MotionSample {
        data class Acceleration(val x: Float, val y: Float, val z: Float) : MotionSample
        data object Significant : MotionSample
    }
}
