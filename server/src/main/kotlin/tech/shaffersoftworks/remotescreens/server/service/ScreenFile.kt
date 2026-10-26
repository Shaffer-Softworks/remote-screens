package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.shared.SCHEMA_VERSION
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.Theme
import kotlinx.serialization.Serializable

/** A screen downloaded from the editor, plus the custom themes it names. */
@Serializable
data class ScreenFile(
    val kind: String,
    val schemaVersion: Int = SCHEMA_VERSION,
    val screen: Screen,
    val themes: List<Theme> = emptyList(),
) {
    companion object {
        const val KIND = "remote-screens.screen"
    }
}
