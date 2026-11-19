@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.freeform

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcBorderShape
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcEnterExitAnimation
import androidx.compose.remote.creation.dsl.animationSpec
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.server.render.tiles.tapRef
import tech.shaffersoftworks.remotescreens.server.render.widgets.at
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Node

/**
 * A free-form page: [root] laid out in the content area at ([xDp], [yDp]), [wDp] by [hDp]. Containers become the
 * player's rows, columns and boxes; leaves reuse the widgets the tiles draw with.
 */
fun DocContext.freeForm(
    root: Node,
    xDp: Float,
    yDp: Float,
    wDp: Float,
    hDp: Float,
    pageIndex: (String) -> Int?,
    pageTitle: (String) -> String? = { null },
) {
    val pages = Pages(pageIndex, pageTitle)

    at(xDp, yDp) {
        rcScope.Box(Modifier.size(units.px(wDp), units.px(hDp))) {
            this@freeForm.node(
                node = root,
                self = this@freeForm.resolve(
                    node = root,
                    avail = Dims(wDp, hDp),
                    axis = Axis.BOX,
                    share = Dims(wDp, hDp),
                ),
                axis = Axis.BOX,
                pages = pages,
            )
        }
    }
}

/** The screen's pages: the index a page tap goes to, and the title a navigation card shows when it names none. */
internal class Pages(val index: (String) -> Int?, val title: (String) -> String?)

/** The size of [node] within its parent's inner space [avail]; [share] is what a weight of it comes to. */
internal fun DocContext.sized(node: Node, axis: Axis): Modifier {
    var modifier: Modifier = Modifier

    modifier = when (val sizeSpec = Spec.of(node.size.width)) {
        Spec.Fill -> if (axis == Axis.ROW) {
            modifier.horizontalWeight(1f)
        } else {
            modifier.fillMaxWidth()
        }
        is Spec.Dp -> modifier.width(units.px(sizeSpec.value))
        is Spec.Weight -> if (axis == Axis.ROW) {
            modifier.horizontalWeight(
                sizeSpec.value,
            )
        } else {
            modifier.fillMaxWidth()
        }
        Spec.Wrap -> modifier
    }

    modifier = when (val sizeSpec = Spec.of(node.size.height)) {
        Spec.Fill -> if (axis == Axis.COLUMN) {
            modifier.verticalWeight(1f)
        } else {
            modifier.fillMaxHeight()
        }
        is Spec.Dp -> modifier.height(units.px(sizeSpec.value))
        is Spec.Weight -> if (axis == Axis.COLUMN) {
            modifier.verticalWeight(sizeSpec.value)
        } else {
            modifier.fillMaxHeight()
        }
        Spec.Wrap -> modifier
    }

    return modifier
}

/** Components whose keys inside take the taps, so the component as a whole takes none. */
internal val OWN_TAPS = setOf(
    "tile", "slider", "stepper", "choices", "keys", "swatches", "keypad", "dial", "tabs",
    "timerkeys", "todolist", "events", "mediabar", "presence", "thermostat", "weather", "nextevent",
)

private const val SHOW_MOTION_S = 0.25f
private const val SHOW_VISIBILITY_S = 0.2f

/** Hides a bound component until `showWhen` matches, and plays [appear] as it comes and goes. */
private fun DocContext.shownWhen(node: Node, base: Modifier): Modifier {
    val id = node.bind?.entityId
    val showWhen = node.props["showWhen"]

    if (id == null || (showWhen != "on" && showWhen != "off")) return base

    bindEntity(id)
    var modifier = base.visibility(int(Vars.entity(id, showWhen)))

    appear(node.props["animate"])?.let { (enter, exit) ->
        modifier = modifier.animationSpec(
            motionDuration = SHOW_MOTION_S,
            visibilityDuration = SHOW_VISIBILITY_S,
            enterAnimation = enter,
            exitAnimation = exit,
        )
    }

    return modifier
}

/** Clip, fill and outline, in the order a key builds them. */
private fun DocContext.framed(
    node: Node,
    base: Modifier,
    applyBorder: Boolean
): Modifier {
    var modifier = base
    val bg = node.style.background?.let { paint(it) }

    if (node.style.corner > 0f) modifier = modifier.clip(rounded(node.style.corner))

    if (bg != null) modifier = modifier.background(bg)

    if (applyBorder && node.style.border > 0f) {
        modifier = modifier.border(
            width = units.px(node.style.border),
            roundedCorner = units.px(node.style.corner),
            color = node.style.borderColor?.let { paint(it) } ?: role(Role.DIVIDER),
            shape = borderShape(node.style.corner),
        )
    }

    return modifier
}

private fun DocContext.borderShape(corner: Float): RcBorderShape =
    if (corner > 0f) {
        RcBorderShape.RoundedRectangle
    } else {
        RcBorderShape.Rectangle
    }

/**
 * The size, the rounded background and outline, the taps, then the padding: the order `key` builds its own modifier
 * in. A bound component with `showWhen` is drawn only while its entity is on (or off).
 */
internal fun DocContext.styled(
    node: Node,
    axis: Axis,
    pages: Pages,
    base: Modifier = sized(node, axis),
    applyBorder: Boolean = true,
): Modifier {
    var modifier = shownWhen(node, base)

    modifier = framed(
        node = node,
        base = modifier,
        applyBorder = applyBorder
    )

    if (node.type !in OWN_TAPS) {
        val (onTap, onHold) = refs(node, pages)

        modifier = tap(
            modifier = modifier,
            ref = onTap,
            hold = onHold
        )
    }

    val style = node.style

    if (style.padded) {
        modifier = modifier.padding(
            start = units.px(style.start),
            top = units.px(style.top),
            end = units.px(style.end),
            bottom = units.px(style.bottom),
        )
    }

    return modifier
}

/** How a component that shows and hides comes and goes: a fade unless [animate] names a slide, or `none`. */
internal fun appear(animate: String?): Pair<RcEnterExitAnimation, RcEnterExitAnimation>? = when (animate) {
    "none" -> null
    "left" -> RcEnterExitAnimation.SlideLeft to RcEnterExitAnimation.SlideLeft
    "right" -> RcEnterExitAnimation.SlideRight to RcEnterExitAnimation.SlideRight
    "up" -> RcEnterExitAnimation.SlideTop to RcEnterExitAnimation.SlideTop
    "down" -> RcEnterExitAnimation.SlideBottom to RcEnterExitAnimation.SlideBottom
    else -> RcEnterExitAnimation.FadeIn to RcEnterExitAnimation.FadeOut
}

/** What a tap and a hold on [node] send, through the same router as a tile's. */
internal fun DocContext.refs(node: Node, pages: Pages): Pair<ActionRef?, ActionRef?> {
    val tile = node.asTile() ?: return null to null

    val spec = Catalogue.of(tile.effectiveKind())

    val tap = tapRef(
        tile = tile,
        action = tile.tap,
        spec = spec,
        pageIndex = pages.index,
        hold = false
    )

    val hold = tapRef(
        tile = tile,
        action = tile.hold,
        spec = spec,
        pageIndex = pages.index,
        hold = true
    )

    val id = tile.entityId

    if (tap != null && id != null && spec?.predict?.containsKey(tap.verb) == true) predict(tap, id)

    return tap to hold
}

/** A theme role (`card`, `ink_soft`), a pastel key (`yellow`) or a `#RRGGBB` / `#AARRGGBB` colour, or null for none. */
internal fun DocContext.paint(key: String): RcColor? =
    Pastel.of(
        key,
    )?.let { pastel(it) } ?: Themes.parse(key)?.let { fixed(it) } ?: Role.of(key)?.let { role(it) }

/** The ARGB a tile lights up in: its pastel key or own hex colour, else [fallback]. */
internal fun DocContext.accent(key: String?, fallback: Pastel): Int =
    Pastel.of(key)?.argb ?: key?.let(Themes::parse) ?: fallback.argb

/** ARGB of a layout colour: a pastel, a role such as `ink_dim`, or `#RRGGBB`. [fallback] when [key] is blank. */
internal fun DocContext.inkArgb(key: String?, fallback: Int): Int {
    if (key.isNullOrBlank()) return fallback

    return Pastel.of(key)?.argb ?: Themes.parse(key) ?: Role.of(key)?.argb ?: fallback
}

private val CONTAINER_NODE_TYPES = setOf("column", "row", "box", "flow", "tabs")

private val SHAPE_NODE_TYPES = setOf(
    "text", "icon", "button", "spacer", "divider", "clock", "badge", "avatar", "progress", "graph",
)

private val CONTROL_NODE_TYPES = setOf(
    "slider", "image", "tile", "switch", "stepper", "choices", "gauge", "analog", "iconbutton",
)

internal fun DocContext.node(node: Node, self: Dims, axis: Axis, pages: Pages) {
    if (node.hidden) return

    when (node.type) {
        in CONTAINER_NODE_TYPES -> containerNode(node = node, self = self, axis = axis, pages = pages)
        in SHAPE_NODE_TYPES -> shapeNode(node = node, self = self, axis = axis, pages = pages)
        in CONTROL_NODE_TYPES -> controlNode(node = node, self = self, axis = axis, pages = pages)
        else -> extraNode(node = node, self = self, axis = axis, pages = pages)
    }
}

private fun DocContext.containerNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    when (node.type) {
        "column", "row", "box", "flow" -> container(node = node, given = self, axis = axis, pages = pages)
        else -> tabsNode(node = node, self = self, axis = axis, pages = pages)
    }
}

private fun DocContext.shapeNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    when (node.type) {
        "text" -> textNode(node = node, axis = axis, pages = pages)
        "icon" -> iconNode(node = node, axis = axis, pages = pages)
        "button" -> buttonNode(node = node, axis = axis, pages = pages)
        "spacer" -> rcScope.Spacer(styled(node = node, axis = axis, pages = pages, base = spacerSize(node, axis)))
        "divider" -> rcScope.Box(
            styled(
                node = node,
                axis = axis,
                pages = pages,
                base = dividerSize(node, axis),
            ).background(paint(node.props["color"] ?: "divider") ?: role(Role.DIVIDER)),
        )
        "clock" -> clockNode(node = node, axis = axis, pages = pages)
        "badge" -> badgeNode(node = node, axis = axis, pages = pages)
        "avatar" -> avatarNode(node = node, axis = axis, pages = pages)
        "progress" -> progressNode(node = node, self = self, axis = axis, pages = pages)
        else -> graphNode(node = node, self = self, axis = axis, pages = pages)
    }
}

private fun DocContext.controlNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    when (node.type) {
        "slider" -> sliderNode(node = node, self = self, axis = axis, pages = pages)
        "image" -> imageNode(node = node, self = self, axis = axis, pages = pages)
        "tile" -> tileNode(node = node, self = self, axis = axis, pages = pages)
        "switch" -> switchNode(node = node, axis = axis, pages = pages)
        "stepper" -> stepperNode(node = node, axis = axis, pages = pages)
        "choices" -> choicesNode(node = node, self = self, axis = axis, pages = pages)
        "gauge" -> gaugeNode(node = node, axis = axis, pages = pages)
        "analog" -> analogNode(node = node, axis = axis, pages = pages)
        else -> iconButtonNode(node = node, axis = axis, pages = pages)
    }
}

private fun DocContext.extraNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    when (node.type) {
        "keys" -> keysNode(node = node, axis = axis, pages = pages)
        "swatches" -> swatchesNode(node = node, axis = axis, pages = pages)
        "keypad" -> keypadNode(node = node, axis = axis, pages = pages)
        "dial" -> dialNode(node = node, axis = axis, pages = pages)
        "countdown" -> countdownNode(node = node, axis = axis, pages = pages)
        "qr" -> qrNode(node = node, axis = axis, pages = pages)
        "markdown" -> markdownNode(node = node, axis = axis, pages = pages)
        else -> blockNode(node = node, self = self, axis = axis, pages = pages)
    }
}
