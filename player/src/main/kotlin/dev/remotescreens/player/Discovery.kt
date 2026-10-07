package dev.remotescreens.player

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/** Finds the add-on on the network over mDNS (`_remotescreens._tcp`) and reports `host:port` once. */
class Discovery(context: Context, private val found: (String) -> Unit) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var lock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null
    @Volatile private var done = false

    fun start() {
        if (listener != null) return
        done = false
        lock = wifi.createMulticastLock("remote-screens").apply { setReferenceCounted(false); acquire() }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w(TAG, "Discovery failed: $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!done) resolve(service)
            }
        }
        listener = l
        nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, l)
    }

    @Suppress("DEPRECATION")
    private fun resolve(service: NsdServiceInfo) {
        nsd.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { Log.w(TAG, "Resolve failed: $errorCode") }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses.firstOrNull()?.hostAddress else info.host?.hostAddress
                if (host == null || done) return
                done = true
                found("$host:${info.port}")
                stop()
            }
        })
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        lock?.release()
        lock = null
    }

    companion object {
        private const val TAG = "Discovery"
        const val TYPE = "_remotescreens._tcp"
    }
}
