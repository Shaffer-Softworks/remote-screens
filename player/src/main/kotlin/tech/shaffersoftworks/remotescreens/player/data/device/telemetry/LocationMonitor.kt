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
import kotlinx.coroutines.launch
import tech.shaffersoftworks.remotescreens.player.di.MainDispatcher
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
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var listenJob: Job? = null
    private var updatesListener: LocationListener? = null

    private val _location = MutableStateFlow<LocationTelemetry?>(null)
    val location: StateFlow<LocationTelemetry?> = _location.asStateFlow()

    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** A new fix arrived. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

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
        val provider = chooseProvider(manager)
        if (provider == null) {
            Timber.d("Location: no provider enabled")
            return
        }
        if (listenJob?.isActive != true) {
            listenJob = scope.launch { listen(manager, provider) }
        } else {
            requestAnotherFix(manager, provider)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestAnotherFix(manager: LocationManager, provider: String) {
        val listener = updatesListener
        if (_location.value != null || listener == null) return
        try {
            requestCurrentFix(manager, provider, listener)
        } catch (error: SecurityException) {
            Timber.w(error, "Location permission was revoked")
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        _location.value = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun listen(manager: LocationManager, provider: String) {
        callbackFlow {
            val fixes = this
            val listener = LocationListener { location -> fixes.trySend(readingOf(location)) }
            updatesListener = listener
            try {
                bestLastKnown(manager)?.let { fixes.trySend(readingOf(it)) }
                // These LocationManager methods require a Looper. There is no Executor overload here.
                manager.requestLocationUpdates(
                    provider,
                    MIN_UPDATE_INTERVAL_MS,
                    MIN_UPDATE_DISTANCE_M,
                    listener,
                    Looper.getMainLooper(),
                )
                Timber.d("Location started with %s", provider)
                // A wall tablet may never move 50 m, and the cached fix is often empty the first time
                // this app is allowed to read location. Ask for one position now, and again until one arrives.
                if (_location.value == null) requestCurrentFix(manager, provider, listener)
                awaitClose { unregister(manager, listener) }
            } catch (error: SecurityException) {
                Timber.w(error, "Location permission was revoked")
                unregister(manager, listener)
                close()
            }
        }.collect { reading -> publish(reading) }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(manager: LocationManager): Location? =
        enabledProviders(manager)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun requestCurrentFix(manager: LocationManager, provider: String, listener: LocationListener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(
                provider,
                null,
                ContextCompat.getMainExecutor(context),
            ) { location -> if (location != null) publish(readingOf(location)) }
        } else {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }

    private fun unregister(manager: LocationManager, listener: LocationListener) {
        manager.removeUpdates(listener)
        if (updatesListener === listener) updatesListener = null
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

    private fun readingOf(location: Location): LocationTelemetry = LocationTelemetry(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyM = location.accuracy.takeIf { location.hasAccuracy() },
        altitudeM = location.altitude.takeIf { location.hasAltitude() },
        fixedAt = location.time,
    )

    private fun publish(reading: LocationTelemetry) {
        _location.value = reading
        _changes.tryEmit(Unit)
    }
}
