package tech.shaffersoftworks.remotescreens.server.data.lights

import java.util.concurrent.ConcurrentHashMap

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
class LightsUiStore {
    private val byTile = ConcurrentHashMap<String, LightsUiState>()

    fun state(tileId: String): LightsUiState = byTile[tileId] ?: LightsUiState()

    fun snapshot(): Map<String, LightsUiState> = HashMap(byTile)

    fun selectRoom(tileId: String, areaId: String) {
        byTile[tileId] = state(tileId).copy(selectedAreaId = areaId, detailEntityId = null)
    }

    /** Opens the dialog of [entityId] fresh, as RPI remembers mode and swatch per light. */
    fun openDetail(tileId: String, entityId: String) {
        byTile[tileId] = state(tileId).copy(
            detailEntityId = entityId,
            detailMode = null,
            paletteIndex = null,
            isEffectMenuOpen = false,
        )
    }

    fun dismissDetail(tileId: String) {
        byTile[tileId] = state(tileId).copy(detailEntityId = null, isEffectMenuOpen = false)
    }

    fun selectMode(tileId: String, mode: LightDetailMode) {
        byTile[tileId] = state(tileId).copy(detailMode = mode)
    }

    fun selectPalette(tileId: String, index: Int) {
        byTile[tileId] = state(tileId).copy(paletteIndex = index)
    }

    fun setEffectMenuOpen(tileId: String, isOpen: Boolean) {
        byTile[tileId] = state(tileId).copy(isEffectMenuOpen = isOpen)
    }
}
