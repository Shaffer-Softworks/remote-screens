package tech.shaffersoftworks.remotescreens.server.data.security

import tech.shaffersoftworks.remotescreens.server.data.ui.TileUiStore

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
class SecurityUiStore : TileUiStore<SecurityUiState>(SecurityUiState()) {
    fun selectSection(tileId: String, section: SecuritySection) {
        update(tileId) { it.copy(selectedSection = section) }
    }
}
