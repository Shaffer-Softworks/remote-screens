@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.render.freeform.resolve

import tech.shaffersoftworks.remotescreens.server.render.freeform.Axis
import tech.shaffersoftworks.remotescreens.server.render.freeform.Dims
import tech.shaffersoftworks.remotescreens.server.render.freeform.Spec
import tech.shaffersoftworks.remotescreens.server.render.freeform.analogNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.avatarNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.badgeNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.buttonNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.choicesNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.clockNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.container
import tech.shaffersoftworks.remotescreens.server.render.freeform.countdownNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.dialNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.dividerSize
import tech.shaffersoftworks.remotescreens.server.render.freeform.gaugeNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.graphNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.iconButtonNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.iconNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.imageNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.keypadNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.keysNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.markdownNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.progressNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.qrNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.sliderNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.spacerSize
import tech.shaffersoftworks.remotescreens.server.render.freeform.stepperNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.swatchesNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.switchNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.tabsNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.textNode
import tech.shaffersoftworks.remotescreens.server.render.freeform.tileNode
import tech.shaffersoftworks.remotescreens.server.render.tiles.tapRef

import androidx.compose.remote.core.operations.layout.animation.AnimationSpec
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.animationSpec
import androidx.compose.remote.creation.dsl.RcBorderShape
import androidx.compose.remote.creation.dsl.RcColor
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
        scope.Box(Modifier.size(units.px(wDp), units.px(hDp))) {
            this@freeForm.node(
                root,
                this@freeForm.resolve(root, Dims(wDp, hDp), Axis.BOX, Dims(wDp, hDp)),
                Axis.BOX,
                pages,
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
            modifier.horizontalWeight(
                1f,
            )
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
            modifier.verticalWeight(
                1f,
            )
        } else {
            modifier.fillMaxHeight()
        }
        is Spec.Dp -> modifier.height(units.px(sizeSpec.value))
        is Spec.Weight -> if (axis == Axis.COLUMN) {
            modifier.verticalWeight(
                sizeSpec.value,
            )
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

/**
 * The size, the rounded background and outline, the taps, then the padding: the order `key` builds its own modifier
 * in. A bound component with `showWhen` is drawn only while its entity is on (or off).
 */
internal fun DocContext.styled(node: Node, axis: Axis, pages: Pages, base: Modifier = sized(
    node,
    axis,
),): Modifier {
    var modifier = base
    val id = node.bind?.entityId
    val showWhen = node.props["showWhen"]
    if (id != null && (showWhen == "on" || showWhen == "off")) {
        bindEntity(id)
        modifier = modifier.visibility(int(Vars.entity(id, showWhen)))
        appear(
            node.props["animate"],
        )?.let { (enter, exit) -> modifier = modifier.animationSpec(
            enterAnimation = enter,
            exitAnimation = exit,
        ) }
    }
    val bg = node.style.background?.let { paint(it) }
    if (node.style.corner > 0f) modifier = modifier.clip(rounded(node.style.corner))
    if (bg != null) modifier = modifier.background(bg)
    if (node.style.border > 0f) {
        val ink = node.style.borderColor?.let { paint(it) } ?: role(Role.DIVIDER)
        modifier = modifier.border(
            units.px(node.style.border),
            units.px(node.style.corner),
            ink,
            if (node.style.corner > 0f) RcBorderShape.RoundedRectangle else RcBorderShape.Rectangle,
        )
    }
    if (node.type !in OWN_TAPS) {
        val (onTap, onHold) = refs(node, pages)
        modifier = tap(modifier, onTap, onHold)
    }
    val style = node.style
    if (style.padded) {
        modifier = modifier.padding(
            units.px(style.start),
            units.px(style.top),
            units.px(style.end),
            units.px(style.bottom),
        )
    }
    return modifier
}

/** How a component that shows and hides comes and goes: a fade unless [animate] names a slide, or `none`. */
internal fun appear(animate: String?): Pair<AnimationSpec.ANIMATION, AnimationSpec.ANIMATION>? = when (animate) {
    "none" -> null
    "left" -> AnimationSpec.ANIMATION.SLIDE_LEFT to AnimationSpec.ANIMATION.SLIDE_LEFT
    "right" -> AnimationSpec.ANIMATION.SLIDE_RIGHT to AnimationSpec.ANIMATION.SLIDE_RIGHT
    "up" -> AnimationSpec.ANIMATION.SLIDE_TOP to AnimationSpec.ANIMATION.SLIDE_TOP
    "down" -> AnimationSpec.ANIMATION.SLIDE_BOTTOM to AnimationSpec.ANIMATION.SLIDE_BOTTOM
    else -> AnimationSpec.ANIMATION.FADE_IN to AnimationSpec.ANIMATION.FADE_OUT
}

/** What a tap and a hold on [node] send, through the same router as a tile's. */
internal fun DocContext.refs(node: Node, pages: Pages): Pair<ActionRef?, ActionRef?> {
    val tile = node.asTile() ?: return null to null
    val spec = Catalogue.of(tile.effectiveKind())
    val tap = tapRef(tile, tile.tap, spec, pages.index, hold = false)
    val hold = tapRef(tile, tile.hold, spec, pages.index, hold = true)
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

internal fun DocContext.node(node: Node, self: Dims, axis: Axis, pages: Pages) {
    if (node.hidden) return
    when (node.type) {
        "column", "row", "box", "flow" -> container(node, self, axis, pages)
        "tabs" -> tabsNode(node, self, axis, pages)
        "text" -> textNode(node, axis, pages)
        "icon" -> iconNode(node, axis, pages)
        "button" -> buttonNode(node, axis, pages)
        "spacer" -> scope.Spacer(styled(node, axis, pages, spacerSize(node, axis)))
        "divider" -> scope.Box(
            styled(
                node,
                axis,
                pages,
                dividerSize(node, axis),
            ).background(paint(node.props["color"] ?: "divider") ?: role(Role.DIVIDER)),
        )
        "slider" -> sliderNode(node, self, axis, pages)
        "image" -> imageNode(node, self, axis, pages)
        "tile" -> tileNode(node, self, axis, pages)
        "clock" -> clockNode(node, axis, pages)
        "badge" -> badgeNode(node, axis, pages)
        "avatar" -> avatarNode(node, axis, pages)
        "progress" -> progressNode(node, self, axis, pages)
        "graph" -> graphNode(node, self, axis, pages)
        "switch" -> switchNode(node, axis, pages)
        "stepper" -> stepperNode(node, axis, pages)
        "choices" -> choicesNode(node, self, axis, pages)
        "gauge" -> gaugeNode(node, axis, pages)
        "analog" -> analogNode(node, axis, pages)
        "iconbutton" -> iconButtonNode(node, axis, pages)
        "keys" -> keysNode(node, axis, pages)
        "swatches" -> swatchesNode(node, axis, pages)
        "keypad" -> keypadNode(node, axis, pages)
        "dial" -> dialNode(node, axis, pages)
        "countdown" -> countdownNode(node, axis, pages)
        "qr" -> qrNode(node, axis, pages)
        "markdown" -> markdownNode(node, axis, pages)
        "timerkeys", "todolist", "events", "mediabar", "forecast", "presence", "led", "thermostat",
        "weather", "nextevent",
        ->
            blockNode(node, self, axis, pages)
    }
}
