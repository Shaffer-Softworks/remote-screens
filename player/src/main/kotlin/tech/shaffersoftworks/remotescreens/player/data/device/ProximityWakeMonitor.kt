package tech.shaffersoftworks.remotescreens.player.data.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val NEAR_DEBOUNCE_MS = 300L

/**
 * Wakes the screen when the tablet's proximity sensor reports something near. Many tablets have no
 * such sensor; phone-style ones only cover a few centimetres.
 */
@Singleton
class ProximityWakeMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var onNear: (() -> Unit)? = null
    private var lastNearAtMs = 0L
    private var isListening = false
    private val proximityScale = ProximityScale()

    fun isUsable(): Boolean = proximitySensor != null

    fun start(onNear: () -> Unit) {
        val sensor = proximitySensor
        if (sensor == null || isListening) {
            if (sensor == null) Timber.d("Proximity wake: no sensor")
            return
        }
        this.onNear = onNear
        isListening = true
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        Timber.d("Proximity wake started")
    }

    fun stop() {
        if (!isListening) return
        sensorManager.unregisterListener(listener)
        onNear = null
        isListening = false
        Timber.d("Proximity wake stopped")
    }

    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        override fun onSensorChanged(event: SensorEvent) {
            val sensor = proximitySensor ?: return
            val distance = event.values.firstOrNull()
            val now = android.os.SystemClock.elapsedRealtime()
            val isNear = distance != null &&
                proximityScale.isNear(distance, sensor.maximumRange, sensor.resolution)
            val isDebounced = now - lastNearAtMs >= NEAR_DEBOUNCE_MS
            if (isNear && isDebounced) {
                lastNearAtMs = now
                onNear?.invoke()
            }
        }
    }
}
