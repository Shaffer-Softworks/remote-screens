package tech.shaffersoftworks.remotescreens.player.data.device.telemetry

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.os.Build
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.shaffersoftworks.remotescreens.player.receiver.LockTaskDeviceAdminReceiver
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** How often a high-accuracy request would update. Only used to ask Android to enable that mode. */
private const val ACCURACY_REQUEST_INTERVAL_MS = 300_000L

/**
 * Turns on the location switch, and asks Android to turn on Location accuracy (Wi-Fi and cell, which a wall tablet
 * needs indoors). Positions themselves still come from [LocationMonitor].
 */
interface LocationAccuracy {
    /** Device-owner tablets can flip the location switch without leaving the kiosk. */
    fun enableLocationServices()

    /**
     * A system prompt that turns Location accuracy on, or null when it is already on or this tablet cannot show one.
     * Call from the main thread.
     */
    suspend fun findAccuracyPrompt(): IntentSender?
}

@Singleton
class GoogleLocationAccuracy @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationAccuracy {
    private val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
    private val adminComponent = ComponentName(context, LockTaskDeviceAdminReceiver::class.java)

    override fun enableLocationServices() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) return
        runCatching { devicePolicyManager.setLocationEnabled(adminComponent, true) }
            .onFailure { exception -> Timber.w(exception, "Could not turn location services on") }
    }

    override suspend fun findAccuracyPrompt(): IntentSender? = suspendCancellableCoroutine { continuation ->
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, ACCURACY_REQUEST_INTERVAL_MS).build()
        val settings = LocationSettingsRequest.Builder()
            .addLocationRequest(request)
            .setAlwaysShow(true)
            .build()
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(settings)
            .addOnCompleteListener { completed ->
                if (!continuation.isActive) return@addOnCompleteListener
                val exception = completed.exception
                if (exception != null && exception !is ResolvableApiException) {
                    Timber.w(exception, "Location accuracy cannot be changed from the app")
                }
                continuation.resume(
                    if (exception is ResolvableApiException) exception.resolution.intentSender else null,
                )
            }
    }
}
