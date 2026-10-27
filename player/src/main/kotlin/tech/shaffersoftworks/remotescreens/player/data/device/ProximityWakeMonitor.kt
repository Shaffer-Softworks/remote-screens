package tech.shaffersoftworks.remotescreens.player.data.device

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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import tech.shaffersoftworks.remotescreens.player.di.MainDispatcher
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val NEAR_DEBOUNCE_MS = 300L

/**
 * Wakes the screen when the tablet's proximity sensor reports something near. Many tablets have no
 * such sensor; phone-style ones only cover a few centimetres.
 *
 * The listener stays registered whenever wake-on-proximity is on. The PX30 sensor is on-change, and a
 * near pulse is often under a second, so registering only after the screen has locked misses the edge
 * and the next sample is already far.
 *
 * Same shape as RPI's `ProximitySensor`: a [callbackFlow] collected on a scope, unregistered when that
 * collection is cancelled. Samples are read with [ProximityScale]. RPI's `distance < maximumRange`
 * treats this panel's far reading as near.
 */
@Singleton
class ProximityWakeMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    /** A wake-up sensor can still deliver a near edge after the panel has been locked. */
    private val proximitySensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var onNear: (() -> Unit)? = null
    private var lastNearAtMs = 0L
    private var listenJob: Job? = null
    private val proximityScale = ProximityScale()

    fun isUsable(): Boolean = proximitySensor != null

    fun start(onNear: () -> Unit) {
        val manager = sensorManager
        val sensor = proximitySensor
        if (manager == null || sensor == null) {
            Timber.d("Proximity wake: no sensor")
            return
        }
        this.onNear = onNear
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            callbackFlow {
                val sensorListener = object : SensorEventListener {
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                    override fun onSensorChanged(event: SensorEvent) {
                        event.values.firstOrNull()?.let { distance -> trySend(distance) }
                    }
                }
                manager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                awaitClose { manager.unregisterListener(sensorListener) }
            }.collect { distance -> onDistance(distance, sensor) }
        }
        Timber.d("Proximity wake started")
    }

    fun stop() {
        if (listenJob == null) return
        listenJob?.cancel()
        listenJob = null
        onNear = null
        Timber.d("Proximity wake stopped")
    }

    private fun onDistance(distance: Float, sensor: Sensor) {
        val now = android.os.SystemClock.elapsedRealtime()
        val isNear = proximityScale.isNear(distance, sensor.maximumRange, sensor.resolution)
        val isDebounced = now - lastNearAtMs >= NEAR_DEBOUNCE_MS
        if (isNear && isDebounced) {
            lastNearAtMs = now
            onNear?.invoke()
        }
    }
}
