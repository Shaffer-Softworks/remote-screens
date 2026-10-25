package tech.shaffersoftworks.remotescreens.player.data.device.telemetry

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.shaffersoftworks.remotescreens.shared.LocationTelemetry
import tech.shaffersoftworks.remotescreens.shared.TelemetryHardware
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Wall tablets rarely move, so a fix every few minutes, and only after a real move, is plenty. */
private const val MIN_UPDATE_INTERVAL_MS = 5 * 60_000L
private const val MIN_UPDATE_DISTANCE_M = 50f

/**
 * The tablet's location from the platform location service (no Play Services): the fused provider where Android has
 * one, else network, else GPS. Only runs with location permission; the caller decides whether it may run at all.
 */
@Singleton
class LocationMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)

    private val _location = MutableStateFlow<LocationTelemetry?>(null)
    val location: StateFlow<LocationTelemetry?> = _location.asStateFlow()

    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** A new fix arrived. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private var isListening = false

    val hardware: List<String>
        get() = listOfNotNull(
            TelemetryHardware.GPS.takeIf {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
            },
        )

    fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    fun start() {
        val manager = locationManager
        if (manager == null || !hasPermission()) return
        val provider = chooseProvider(manager) ?: run {
            Timber.d("Location: no provider enabled")
            return
        }
        try {
            if (!isListening) {
                bestLastKnown(manager)?.let(::onLocation)
                manager.requestLocationUpdates(
                    provider,
                    MIN_UPDATE_INTERVAL_MS,
                    MIN_UPDATE_DISTANCE_M,
                    listener,
                    Looper.getMainLooper(),
                )
                isListening = true
                Timber.d("Location started with %s", provider)
            }
            // A wall tablet may never move 50 m, and the cached fix is often empty the first time
            // this app is allowed to read location. Ask for one position now, and again until one arrives.
            if (_location.value == null) requestCurrentFix(manager, provider)
        } catch (e: SecurityException) {
            Timber.w(e, "Location permission was revoked")
        }
    }

    fun stop() {
        if (isListening) locationManager?.removeUpdates(listener)
        isListening = false
        _location.value = null
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(manager: LocationManager): Location? =
        enabledProviders(manager)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun requestCurrentFix(manager: LocationManager, provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(
                provider,
                null,
                ContextCompat.getMainExecutor(context),
            ) { location -> if (location != null) onLocation(location) }
        } else {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }

    private fun enabledProviders(manager: LocationManager): List<String> = listOfNotNull(
        LocationManager.FUSED_PROVIDER.takeIf {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                manager.hasProvider(LocationManager.FUSED_PROVIDER) &&
                manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)
        },
        LocationManager.NETWORK_PROVIDER.takeIf { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) },
        LocationManager.GPS_PROVIDER.takeIf { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) },
    )

    private fun chooseProvider(manager: LocationManager): String? = enabledProviders(manager).firstOrNull()

    private fun onLocation(location: Location) {
        _location.value = LocationTelemetry(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyM = location.accuracy.takeIf { location.hasAccuracy() },
            altitudeM = location.altitude.takeIf { location.hasAltitude() },
            fixedAt = location.time,
        )
        _changes.tryEmit(Unit)
    }

    private val listener = LocationListener { location -> onLocation(location) }
}
