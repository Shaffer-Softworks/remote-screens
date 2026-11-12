package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.shared.SCHEMA_VERSION
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.Theme
import kotlinx.serialization.Serializable

/** A page wallpaper embedded in a [ScreenFile] as base64 (JPEG or PNG). */
@Serializable
data class ScreenBackgroundAsset(
    val pageId: String,
    val mime: String,
    val data: String,
)

/** A screen downloaded from the editor, plus the custom themes it names. */
@Serializable
data class ScreenFile(
    val kind: String,
    val schemaVersion: Int = SCHEMA_VERSION,
    val screen: Screen,
    val themes: List<Theme> = emptyList(),
    /** Wallpapers keyed by page id; restored on import when present. */
    val backgrounds: List<ScreenBackgroundAsset> = emptyList(),
) {
    companion object {
        const val KIND = "remote-screens.screen"
    }
}
