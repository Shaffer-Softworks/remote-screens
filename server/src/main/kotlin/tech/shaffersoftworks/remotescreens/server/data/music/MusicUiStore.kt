package tech.shaffersoftworks.remotescreens.server.data.music

import java.util.concurrent.ConcurrentHashMap

/** Session-wide Music Assistant UI state keyed by tile id. */
class MusicUiStore {
    private val byTile = ConcurrentHashMap<String, MusicUiState>()
    private val browseItems = ConcurrentHashMap<String, List<MusicListRow>>()
    private val browseHasMore = ConcurrentHashMap<String, Boolean>()

    fun state(tileId: String): MusicUiState = byTile.getOrPut(tileId) { MusicUiState() }

    fun browseItems(tileId: String): List<MusicListRow> = browseItems[tileId].orEmpty()

    fun browseHasMore(tileId: String): Boolean = browseHasMore[tileId] ?: true

    fun setBrowseItems(tileId: String, items: List<MusicListRow>, hasMore: Boolean) {
        browseItems[tileId] = items
        browseHasMore[tileId] = hasMore
    }

    fun clearBrowse(tileId: String) {
        browseItems.remove(tileId)
        browseHasMore[tileId] = true
    }

    fun update(tileId: String, transform: (MusicUiState) -> MusicUiState): MusicUiState {
        val next = transform(state(tileId))
        byTile[tileId] = next
        return next
    }

    fun setBrowseType(tileId: String, mediaType: String): MusicUiState {
        val type = mediaType.takeIf { it in MusicUiState.BROWSE_TYPES }
            ?: MusicUiState.DEFAULT_BROWSE_TYPE
        clearBrowse(tileId)
        return update(tileId) {
            it.copy(browseMediaType = type, browseOffset = 0)
        }
    }

    fun setListenMode(tileId: String, mode: String): MusicUiState {
        val listenMode = when (mode) {
            MusicUiState.LISTEN_SEARCH -> MusicUiState.LISTEN_SEARCH
            else -> MusicUiState.LISTEN_QUEUE
        }
        return update(tileId) { it.copy(listenMode = listenMode) }
    }

    fun setPrimarySection(tileId: String, section: String): MusicUiState {
        val primary = when (section) {
            MusicUiState.PRIMARY_LISTEN -> MusicUiState.PRIMARY_LISTEN
            else -> MusicUiState.PRIMARY_BROWSE
        }
        return update(tileId) { it.copy(primarySection = primary) }
    }

    fun setBrowseOffset(tileId: String, offset: Int): MusicUiState =
        update(tileId) { it.copy(browseOffset = offset.coerceAtLeast(0)) }
}
