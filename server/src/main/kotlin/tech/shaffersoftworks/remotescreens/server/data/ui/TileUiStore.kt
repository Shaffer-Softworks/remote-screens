package tech.shaffersoftworks.remotescreens.server.data.ui

import java.util.concurrent.ConcurrentHashMap

/**
 * Session-wide page UI state keyed by tile id, for what a page shows besides Home Assistant's states.
 * Tiles never touched read as [initial]. Subclasses expose the page's taps as named transitions over [update].
 */
open class TileUiStore<S : Any>(private val initial: S) {
    private val byTile = ConcurrentHashMap<String, S>()

    fun state(tileId: String): S = byTile[tileId] ?: initial

    fun snapshot(): Map<String, S> = HashMap(byTile)

    /** Atomically replaces [tileId]'s state; [transform] must not touch this store. */
    protected fun update(tileId: String, transform: (S) -> S): S {
        lateinit var updated: S

        byTile.compute(tileId) { _, current ->
            transform(current ?: initial).also { updated = it }
        }

        return updated
    }
}
