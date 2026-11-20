package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.render.theme.Palette
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeStyle
import kotlin.math.roundToInt

/** [key] as `RRGGBB`: a pastel, a role, or `#RRGGBB`. [fallback] when [key] names nothing. */
internal fun Palette.panelColor(key: String?, fallback: Role): String {
    val argb = when {
        key.isNullOrBlank() -> this[fallback]
        else -> Pastel.of(
            key
        )?.let { this[it] } ?: Themes.parse(key) ?: Role.of(key)?.let { this[it] } ?: this[fallback]
    }

    return Themes.hex(argb).removePrefix("#")
}

internal data class LvglSize(val token: String, val grow: Int?)

/** `wrap`, `fill`, `dp:N` or `weight:N` as a panel size. A weight fills and grows. */
internal fun lvglSize(spec: String): LvglSize = when {
    spec == "fill" -> LvglSize(token = "fill", grow = null)
    spec.isEmpty() || spec == "wrap" -> LvglSize(token = "wrap", grow = null)
    spec.startsWith("weight:") -> LvglSize(
        token = "fill",
        grow = spec.removePrefix("weight:").toFloatOrNull()?.roundToInt()?.coerceAtLeast(1) ?: 1,
    )
    spec.startsWith("dp:") -> LvglSize(
        token = spec.removePrefix("dp:").toFloatOrNull()?.roundToInt()?.coerceAtLeast(0)?.toString() ?: "wrap",
        grow = null,
    )
    else -> LvglSize(token = "wrap", grow = null)
}

internal fun padOf(style: NodeStyle): Int? =
    maxOf(style.start, style.top, style.end, style.bottom).roundToInt().takeIf { it > 0 }

internal fun radiusOf(style: NodeStyle): Int? = style.corner.roundToInt().takeIf { it > 0 }

internal fun borderOf(style: NodeStyle): Int? = style.border.roundToInt().takeIf { it > 0 }

internal fun gapOf(node: Node): Int? = node.props["gap"]?.toFloatOrNull()?.roundToInt()?.takeIf { it > 0 }

/** `start`, `center`, `end`, or a flex main-axis arrangement. */
internal fun alignOf(node: Node): String? {
    val raw = node.props["align"] ?: node.props["arrangement"] ?: return null

    return when (raw) {
        "start", "center", "end" -> raw
        "space-between", "space_between" -> "space_between"
        "space-around", "space_around" -> "space_around"
        "space-evenly", "space_evenly" -> "space_evenly"
        else -> null
    }
}

internal fun fontPx(node: Node, fallback: Int): Int =
    node.props["fontSize"]?.toFloatOrNull()?.roundToInt()?.coerceAtLeast(1) ?: fallback
