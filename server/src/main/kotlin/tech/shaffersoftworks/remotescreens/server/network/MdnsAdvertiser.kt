package tech.shaffersoftworks.remotescreens.server.network

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
import tech.shaffersoftworks.remotescreens.shared.Protocol
import java.io.File
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicInteger
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

private const val DEBUG_LOG_PATH =
    "/Users/michaelshaffer/Downloads/Android Remote Compose/.cursor/debug-1c2553.log"
private const val DEBUG_SESSION_ID = "1c2553"
private const val MDNS_PORT = 5353
private const val MAX_LOGGED_SENDS = 12
private const val MAX_LOGGED_SEND_FAILURES = 20
private const val STACK_FRAME_LIMIT = 6
private const val EPHEMERAL_PORT = 0

private object DebugLogLock
private val loggedSendCount = AtomicInteger(0)
private val loggedSendFailureCount = AtomicInteger(0)

/** Lets tablets on the network find the add-on without typing an address. */
class MdnsAdvertiser(private val playerPort: Int, private val serverVersion: String) {
    private val logger = LoggerFactory.getLogger(MdnsAdvertiser::class.java)
    private var jmdns: JmDNS? = null

    fun start() {
        runCatching {
            val address = siteLocalAddress()
            // #region agent log
            writeDebugLog(
                hypothesisId = "C",
                location = "MdnsAdvertiser.start",
                message = "chosen advertise address",
                data = describeChosenAddress(address),
            )
            // #endregion
            val mdns = JmDNS.create(address, "remote-screens")
            // #region agent log
            installMdnsDebugProbe(mdns)
            // #endregion
            val properties = mapOf(
                "version" to serverVersion,
                "protocol" to Protocol.VERSION.toString(),
            )
            mdns.registerService(
                ServiceInfo.create(
                    Protocol.MDNS_TYPE,
                    "Remote Screens",
                    playerPort,
                    0,
                    0,
                    properties,
                ),
            )
            logger.info(
                "Advertised {} on {}:{}",
                Protocol.MDNS_TYPE,
                address?.hostAddress,
                playerPort,
            )
            jmdns = mdns
        }.onFailure { logger.warn("mDNS not available: {}", it.message) }
    }

    fun stop() {
        jmdns?.unregisterAllServices()
        jmdns?.close()
        jmdns = null
    }
}

// #region agent log
private fun describeChosenAddress(address: InetAddress?): Map<String, String> {
    val networkInterface = address?.let { candidate ->
        runCatching { NetworkInterface.getByInetAddress(candidate) }.getOrNull()
    }
    return mapOf(
        "host" to (address?.hostAddress ?: "null"),
        "addressClass" to (address?.javaClass?.simpleName ?: "null"),
        "interfaceName" to (networkInterface?.name ?: "null"),
        "supportsMulticast" to (networkInterface?.supportsMulticast()?.toString() ?: "null"),
        "osName" to System.getProperty("os.name").orEmpty(),
        "preferIpv4Stack" to (System.getProperty("java.net.preferIPv4Stack") ?: "unset"),
        "interfaces" to describeUpInterfaces(),
        "ipv6Bind" to probeIpv6Bind(),
    )
}

private fun describeUpInterfaces(): String = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { networkInterface -> networkInterface.isUp && !networkInterface.isLoopback }
        .joinToString(separator = ";") { networkInterface ->
            val families = networkInterface.inetAddresses.toList().joinToString(separator = ",") { address ->
                address.javaClass.simpleName + "=" + address.hostAddress
            }
            networkInterface.name + "(multicast=" + networkInterface.supportsMulticast() + "," + families + ")"
        }
}.getOrElse { failure -> failure.javaClass.simpleName + ":" + failure.message }

private fun probeIpv6Bind(): String = runCatching {
    DatagramSocket(null as SocketAddress?).use { socket ->
        socket.bind(InetSocketAddress(InetAddress.getByName("::"), EPHEMERAL_PORT))
        "bound " + socket.localAddress.javaClass.simpleName
    }
}.getOrElse { failure -> failure.javaClass.simpleName + ":" + failure.message }

private fun installMdnsDebugProbe(mdns: JmDNS) {
    attachResponderWarningProbe()
    runCatching { attachSendProbe(mdns) }
        .onFailure { failure ->
            writeDebugLog(
                hypothesisId = "E",
                location = "MdnsAdvertiser.installMdnsDebugProbe",
                message = "send probe not installed",
                data = mapOf("error" to (failure.message ?: failure.javaClass.simpleName)),
            )
        }
}

private fun attachResponderWarningProbe() {
    val responderLogger = LoggerFactory.getLogger("javax.jmdns.impl.tasks.Responder") as? Logger ?: return
    val appender = object : AppenderBase<ILoggingEvent>() {
        override fun append(eventObject: ILoggingEvent) {
            val throwable = eventObject.throwableProxy ?: return
            val stack = throwable.stackTraceElementProxyArray
                .take(STACK_FRAME_LIMIT)
                .joinToString(separator = " | ") { frame -> frame.steAsString }
            writeDebugLog(
                hypothesisId = "A",
                location = "Responder.run",
                message = "library warning",
                data = mapOf(
                    "formatted" to eventObject.formattedMessage,
                    "exception" to (throwable.className + ": " + throwable.message),
                    "stack" to stack,
                ),
            )
        }
    }
    appender.context = responderLogger.loggerContext
    appender.start()
    responderLogger.addAppender(appender)
}

private fun attachSendProbe(mdns: JmDNS) {
    val implementationClass = Class.forName("javax.jmdns.impl.JmDNSImpl")
    val sendField = implementationClass.getDeclaredField("_sendSocket").apply { isAccessible = true }
    val socketField = implementationClass.getDeclaredField("_socket").apply { isAccessible = true }
    val groupField = implementationClass.getDeclaredField("_group").apply { isAccessible = true }
    val sendSocket = sendField.get(mdns) as? MulticastSocket
    val receiveSocket = socketField.get(mdns) as? MulticastSocket
    val group = groupField.get(mdns) as? InetAddress
    writeDebugLog(
        hypothesisId = "E",
        location = "MdnsAdvertiser.attachSendProbe",
        message = "jmdns sockets",
        data = mapOf(
            "group" to (group?.hostAddress ?: "null"),
            "groupClass" to (group?.javaClass?.simpleName ?: "null"),
            "receiveLocal" to describeSocket(receiveSocket),
            "sendLocal" to describeSocket(sendSocket),
            "sendSocketPresent" to (sendSocket != null).toString(),
        ),
    )
    val targetField = if (sendSocket != null) sendField else socketField
    val targetSocket = sendSocket ?: receiveSocket ?: return
    targetField.set(mdns, DebugSendSocket(targetSocket))
}

private fun describeSocket(socket: MulticastSocket?): String {
    if (socket == null) {
        return "null"
    }
    return runCatching { socket.localSocketAddress?.toString() ?: "unbound" }
        .getOrElse { failure -> failure.javaClass.simpleName }
}

private class DebugSendSocket(
    private val delegate: MulticastSocket,
) : MulticastSocket(null as SocketAddress?) {
    override fun send(packet: DatagramPacket) {
        try {
            delegate.send(packet)
            logSend(packet, error = null)
        } catch (exception: IOException) {
            logSend(packet, error = exception)
            throw exception
        }
    }

    override fun close() {
        runCatching { delegate.close() }
        super.close()
    }

    override fun isClosed(): Boolean = delegate.isClosed
}

private fun logSend(packet: DatagramPacket, error: IOException?) {
    val destination = packet.address ?: return
    val isFailure = error != null
    val shouldLog = if (isFailure) {
        loggedSendFailureCount.incrementAndGet() <= MAX_LOGGED_SEND_FAILURES
    } else {
        loggedSendCount.incrementAndGet() <= MAX_LOGGED_SENDS
    }
    if (!shouldLog) {
        return
    }
    val hypothesisId = when {
        packet.port != MDNS_PORT && destination is Inet6Address -> "D"
        destination is Inet6Address -> "A"
        packet.port != MDNS_PORT -> "D"
        else -> "B"
    }
    writeDebugLog(
        hypothesisId = hypothesisId,
        location = "DebugSendSocket.send",
        message = if (isFailure) "send failed" else "send",
        data = mapOf(
            "destClass" to destination.javaClass.simpleName,
            "dest" to destination.hostAddress,
            "mappedV4" to destination.hostAddress.contains("::ffff:").toString(),
            "port" to packet.port.toString(),
            "error" to (error?.javaClass?.simpleName ?: ""),
            "errorMessage" to (error?.message ?: ""),
        ),
    )
}

private fun writeDebugLog(
    hypothesisId: String,
    location: String,
    message: String,
    data: Map<String, String>,
) {
    val dataJson = data.entries.joinToString(separator = ",") { (key, value) ->
        "\"${escapeDebugJson(key)}\":\"${escapeDebugJson(value)}\""
    }
    val line = buildString {
        append("{\"sessionId\":\"")
        append(DEBUG_SESSION_ID)
        append("\",\"hypothesisId\":\"")
        append(escapeDebugJson(hypothesisId))
        append("\",\"location\":\"")
        append(escapeDebugJson(location))
        append("\",\"message\":\"")
        append(escapeDebugJson(message))
        append("\",\"data\":{")
        append(dataJson)
        append("},\"timestamp\":")
        append(System.currentTimeMillis())
        append("}")
    }
    LoggerFactory.getLogger(MdnsAdvertiser::class.java).info("mdns-debug {}", line)
    synchronized(DebugLogLock) {
        runCatching { File(DEBUG_LOG_PATH).appendText(line + "\n") }
    }
}

private fun escapeDebugJson(value: String): String = buildString(value.length) {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(character)
        }
    }
}
// #endregion
