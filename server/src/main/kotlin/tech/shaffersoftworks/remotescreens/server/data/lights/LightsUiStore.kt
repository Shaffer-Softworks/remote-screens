package tech.shaffersoftworks.remotescreens.server.data.lights

import tech.shaffersoftworks.remotescreens.server.data.ui.TileUiStore

/** The main control of RPI's light detail dialog (`LightDetailControlMode`). Keys are sent in taps. */
enum class LightDetailMode(val key: String) {
    POWER("power"),
    BRIGHTNESS("brightness"),
    COLOR_WHEEL("wheel"),
    COLOR_TEMPERATURE("temperature"),
    ;

    companion object {
        fun of(key: String?): LightDetailMode? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What a full lights page shows besides Home Assistant's states: the chosen room and the open light dialog with
 * its mode, chosen swatch and effect menu. A null [detailMode] means the dialog picks RPI's starting mode.
 */
data class LightsUiState(
    val selectedAreaId: String? = null,
    val detailEntityId: String? = null,
    val detailMode: LightDetailMode? = null,
    val paletteIndex: Int? = null,
    val isEffectMenuOpen: Boolean = false,
)

/** Session-wide lights page UI state keyed by tile id. */
class LightsUiStore : TileUiStore<LightsUiState>(LightsUiState()) {
    fun selectRoom(tileId: String, areaId: String) {
        update(tileId) { it.copy(selectedAreaId = areaId, detailEntityId = null) }
    }

    /** Opens the dialog of [entityId] fresh, as RPI remembers mode and swatch per light. */
    fun openDetail(tileId: String, entityId: String) {
        update(tileId) {
            it.copy(
                detailEntityId = entityId,
                detailMode = null,
                paletteIndex = null,
                isEffectMenuOpen = false,
            )
        }
    }

    fun dismissDetail(tileId: String) {
        update(tileId) { it.copy(detailEntityId = null, isEffectMenuOpen = false) }
    }

    fun selectMode(tileId: String, mode: LightDetailMode) {
        update(tileId) { it.copy(detailMode = mode) }
    }

    fun selectPalette(tileId: String, index: Int) {
        update(tileId) { it.copy(paletteIndex = index) }
    }

    fun setEffectMenuOpen(tileId: String, isOpen: Boolean) {
        update(tileId) { it.copy(isEffectMenuOpen = isOpen) }
    }
}
