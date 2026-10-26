package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import tech.shaffersoftworks.remotescreens.shared.Protocol

/** The signaling socket for the add-on a player already talks to on port 8100. */
internal fun intercomSignalingUrl(playerServer: String): String {
    val host = playerServer.substringBefore('/').substringBefore(':')
    return "ws://$host:${Protocol.INTERCOM_PORT}/webrtc"
}

/** A station id unique to this tablet. RPI's 8-character prefix can collide; the full device id cannot. */
internal fun intercomClientId(deviceId: String): String = "station-$deviceId"
