package tech.shaffersoftworks.remotescreens.player.utils

import tech.shaffersoftworks.remotescreens.shared.Protocol

/** Turns what was typed into `host:port`, adding the add-on port when none is given. Null when empty. */
fun normalizeServerAddress(input: String): String? {
    val address = input.trim().removePrefix("http://").removePrefix("ws://").trimEnd('/')
    if (address.isEmpty()) return null
    return if (':' in address) address else "$address:${Protocol.PORT}"
}
