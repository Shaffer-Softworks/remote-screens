package tech.shaffersoftworks.remotescreens.server.data.music

/** One Music Assistant library, search, or queue row drawn on the music page or search overlay. */
data class MusicListRow(
    val title: String,
    val subtitle: String?,
    val uri: String,
    /** HA/MA art path or absolute URL. */
    val imagePathOrUrl: String? = null,
    val isCurrentPlayback: Boolean = false,
)

/** Snapshot from `music_assistant.get_queue`. */
data class MusicQueueSnapshot(
    val rows: List<MusicListRow>,
    /** Extra tracks not returned in [rows] when HA only exposes current + next. */
    val moreCount: Int = 0,
)

/** Per-tile Music Assistant UI: browse type, listen tab, and browse pagination. */
data class MusicUiState(
    val browseMediaType: String = DEFAULT_BROWSE_TYPE,
    val listenMode: String = LISTEN_SEARCH,
    /** Narrow layout only: Browse | Listen (RPI keypad / phone tabs). */
    val primarySection: String = PRIMARY_BROWSE,
    val browseOffset: Int = 0,
) {
    companion object {
        /** Matches RPI MusicAssistantViewModel default. */
        const val DEFAULT_BROWSE_TYPE = "artist"
        const val LISTEN_SEARCH = "search"
        const val LISTEN_QUEUE = "queue"
        const val PRIMARY_BROWSE = "browse"
        const val PRIMARY_LISTEN = "listen"
        val BROWSE_TYPES = listOf("artist", "album", "track", "playlist", "radio")
    }
}
