package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TypeSpec

/** What a tap is about: a tile of the screen, or an entity named by a card. */
data class TapTarget(val tileId: String, val entityId: String?, val kind: String, val tile: Tile?) {
    val spec: TypeSpec? get() = Catalogue.of(kind)
}

/** The per-player memory taps need: a code being typed, an action waiting for its second tap. */
class TapMemory {
    val codes = HashMap<String, String>()
    var pendingConfirm: PendingConfirm? = null
}

/** An action, keyed by tile and verb, waiting since [sinceMs] for its second tap. */
data class PendingConfirm(val key: String, val sinceMs: Long)
