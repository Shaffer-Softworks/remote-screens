package tech.shaffersoftworks.remotescreens.server.render.freeform

import kotlinx.serialization.Serializable
import tech.shaffersoftworks.remotescreens.server.render.theme.Palette
import tech.shaffersoftworks.remotescreens.shared.Node

/** How many widgets a panel page may hold, and the sizes used when a component names none. */
internal object LvglLimits {
    const val MAX_WIDGETS = 48
    const val MAX_DEPTH = 8
    const val PAYLOAD_VERSION = 1
    const val DEFAULT_TEXT_PX = 16
    const val DEFAULT_BUTTON_PX = 14
    const val DEFAULT_CLOCK_PX = 44
    const val DEFAULT_BADGE_PX = 14
    const val DEFAULT_ICON_PX = 24
    const val DEFAULT_RANGE = 100
    const val BUTTON_HEIGHT_PX = 48
    const val BADGE_RADIUS_PX = 12
    const val SPACER_PX = 8
    const val DIVIDER_PX = 1
    const val YAML_INDENT = 2
    const val CLOCK_PLACEHOLDER = "00:00"
}

/** Text, icon name, level (0..1) and on/off for one component, already resolved from Home Assistant. */
class LvglSources(
    val text: (Node) -> String,
    val icon: (Node) -> String,
    val level: (Node) -> Float?,
    val isOn: (Node) -> Boolean,
)

/** One free-form page to draw as LVGL. [widthPx] and [heightPx] are the panel's pixels. */
data class LvglPage(
    val root: Node,
    val palette: Palette,
    val widthPx: Int,
    val heightPx: Int,
    val screenId: String,
    val pageId: String,
    val sources: LvglSources,
)

/** YAML to paste into ESPHome, the payload the panel component applies, and what was left off. */
@Serializable
data class LvglDocument(
    val yaml: String,
    val payload: String,
    val skipped: List<String>,
)

/** One LVGL widget. Absent fields are left at the panel's default. [k] is the kind. */
@Serializable
internal data class LvglWidget(
    val id: String,
    val k: String,
    val text: String? = null,
    val color: String? = null,
    val bg: String? = null,
    val edge: String? = null,
    val size: Int? = null,
    val value: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    val w: String? = null,
    val h: String? = null,
    val grow: Int? = null,
    val gap: Int? = null,
    val pad: Int? = null,
    val radius: Int? = null,
    val border: Int? = null,
    val align: String? = null,
    val tap: Boolean? = null,
    val scroll: Boolean? = null,
    val tabs: List<String>? = null,
    val c: List<LvglWidget>? = null,
)
