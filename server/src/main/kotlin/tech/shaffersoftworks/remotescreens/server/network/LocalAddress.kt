package tech.shaffersoftworks.remotescreens.server.network

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

private val IGNORED_INTERFACE_PREFIXES = listOf("docker", "veth", "hassio")

/** The add-on's address on the home network, as tablets reach it; Docker's own networks are left out. */
fun siteLocalAddress(): InetAddress? = NetworkInterface.getNetworkInterfaces().toList()
    .filter { networkInterface ->
        networkInterface.isUp &&
            !networkInterface.isLoopback &&
            !networkInterface.isVirtual &&
            IGNORED_INTERFACE_PREFIXES.none { networkInterface.name.startsWith(it) }
    }
    .flatMap { it.inetAddresses.toList() }
    .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
