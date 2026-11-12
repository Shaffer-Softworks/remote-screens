package tech.shaffersoftworks.remotescreens.server.data.security

import java.util.concurrent.ConcurrentHashMap

/** Left-column section of the security page. */
enum class SecuritySection(val key: String) {
    OVERVIEW("overview"),
    LOCKS("locks"),
    OPENINGS("openings"),
    ;

    companion object {
        fun of(key: String?): SecuritySection? = entries.firstOrNull { it.key == key }
    }
}

/** What a full security page shows besides Home Assistant's states: the chosen section. */
data class SecurityUiState(
    val selectedSection: SecuritySection = SecuritySection.OVERVIEW,
)

/** Session-wide security page UI state keyed by tile id. */
class SecurityUiStore {
    private val byTile = ConcurrentHashMap<String, SecurityUiState>()

    fun state(tileId: String): SecurityUiState = byTile[tileId] ?: SecurityUiState()

    fun snapshot(): Map<String, SecurityUiState> = HashMap(byTile)

    fun selectSection(tileId: String, section: SecuritySection) {
        byTile[tileId] = state(tileId).copy(selectedSection = section)
    }
}
