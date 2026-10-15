package tech.shaffersoftworks.remotescreens.player.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val SERVICE_TYPE = "_remotescreens._tcp"
private const val MULTICAST_LOCK_TAG = "remote-screens"

/** Finds the add-on on the network over mDNS (`_remotescreens._tcp`). */
@Singleton
class DiscoveryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /** Emits `host:port` for each add-on found. Discovery runs while the flow is collected. */
    fun discoverServers(): Flow<String> = callbackFlow {
        val multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.w("Discovery failed: %d", errorCode)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                resolve(service) { address -> trySend(address) }
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            multicastLock.release()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(service: NsdServiceInfo, onResolved: (String) -> Unit) {
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.w("Resolve failed: %d", errorCode)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val hostAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    serviceInfo.hostAddresses.firstOrNull()?.hostAddress
                } else {
                    serviceInfo.host?.hostAddress
                }
                if (hostAddress != null) onResolved("$hostAddress:${serviceInfo.port}")
            }
        })
    }
}
