package dev.remotescreens.server.network

import dev.remotescreens.shared.Protocol
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

private val IGNORED_INTERFACE_PREFIXES = listOf("docker", "veth", "hassio")

/** Lets tablets on the network find the add-on without typing an address. */
class MdnsAdvertiser(private val playerPort: Int, private val serverVersion: String) {
    private val logger = LoggerFactory.getLogger(MdnsAdvertiser::class.java)
    private var jmdns: JmDNS? = null

    fun start() {
        runCatching {
            val address = siteLocalAddress()
            val mdns = JmDNS.create(address, "remote-screens")
            val properties = mapOf("version" to serverVersion, "protocol" to Protocol.VERSION.toString())
            mdns.registerService(ServiceInfo.create(Protocol.MDNS_TYPE, "Remote Screens", playerPort, 0, 0, properties))
            logger.info("Advertised {} on {}:{}", Protocol.MDNS_TYPE, address?.hostAddress, playerPort)
            jmdns = mdns
        }.onFailure { logger.warn("mDNS not available: {}", it.message) }
    }

    fun stop() {
        jmdns?.unregisterAllServices()
        jmdns?.close()
        jmdns = null
    }

    private fun siteLocalAddress(): InetAddress? = NetworkInterface.getNetworkInterfaces().toList()
        .filter { networkInterface ->
            networkInterface.isUp &&
                !networkInterface.isLoopback &&
                !networkInterface.isVirtual &&
                IGNORED_INTERFACE_PREFIXES.none { networkInterface.name.startsWith(it) }
        }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
}
