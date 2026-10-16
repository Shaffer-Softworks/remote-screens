@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.core.operations.layout.animation.AnimationSpec
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.animationSpec
import androidx.compose.remote.creation.dsl.RcBorderShape
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcStrokeCap
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalScroll
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.marquee
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Natural sizes, chrome metrics and drawing ratios for free-form leaves and widgets. */
private object Metrics {
    const val TEXT_NATURAL_W = 120f
    const val TEXT_LINE_HEIGHT = 1.4f
    const val TEXT_MAX_LINES = 20
    const val ICON_DEFAULT_DP = 32f
    const val BUTTON_NATURAL_W = 140f
    const val BUTTON_NATURAL_H = 48f
    const val BUTTON_CORNER_DIVISOR = 2.6f
    const val BUTTON_PAD_H_DP = 12f
    const val BUTTON_ICON_ONLY_DP = 24f
    const val BUTTON_ICON_WITH_TEXT_DP = 20f
    const val BUTTON_ICON_GAP_DP = 8f
    const val SPACER_DEFAULT_DP = 8f
    const val DIVIDER_THICKNESS_DP = 1f
    const val SLIDER_NATURAL_W = 240f
    const val SLIDER_NATURAL_H = 28f
    const val SLIDER_DEFAULT_STEPS = 10
    const val SLIDER_MIN_STEPS = 2
    const val SLIDER_MAX_STEPS = 20
    const val IMAGE_NATURAL_W = 200f
    const val IMAGE_NATURAL_H = 160f
    const val CLOCK_MIN_W = 160f
    const val CLOCK_WIDTH_PER_SP = 3.2f
    const val CLOCK_DATE_EXTRA_H = 24f
    const val CLOCK_DATE_SCALE = 0.33f
    const val BADGE_NATURAL_W = 120f
    const val BADGE_NATURAL_H = 32f
    const val BADGE_CORNER_DP = 16f
    const val BADGE_PAD_H_DP = 12f
    const val BADGE_ICON_DP = 18f
    const val BADGE_ICON_GAP_DP = 6f
    const val AVATAR_DEFAULT_DP = 56f
    const val PROGRESS_NATURAL_W = 240f
    const val PROGRESS_NATURAL_H = 8f
    const val GRAPH_NATURAL_W = 280f
    const val GRAPH_NATURAL_H = 120f
    const val GRAPH_REFRESH_SEC = 300
    const val SWITCH_NATURAL_W = 52f
    const val SWITCH_NATURAL_H = 30f
    const val SWITCH_CORNER_DP = 15f
    const val SWITCH_KNOB_PAD_DP = 3f
    const val SWITCH_KNOB_DP = 24f
    const val SWITCH_KNOB_CORNER_DP = 12f
    const val STEPPER_NATURAL_W = 200f
    const val STEPPER_NATURAL_H = 48f
    const val STEPPER_KEY_DP = 44f
    const val STEPPER_KEY_CORNER_DP = 22f
    const val STEPPER_KEY_ICON_DP = 22f
    const val STEPPER_MIDDLE_W = 112f
    const val CHOICES_NATURAL_W = 320f
    const val CHOICES_NATURAL_H = 44f
    const val CHOICES_EMPTY_CORNER_DP = 12f
    const val CHOICES_DEFAULT_MAX = 4
    const val CHOICES_MIN = 2
    const val CHOICES_MAX = 8
    const val CHOICES_MIN_H = 28f
    const val GAUGE_DEFAULT_DP = 140f
    const val GAUGE_STROKE_RATIO = 0.1f
    const val GAUGE_ARC_START_DEG = 135f
    const val GAUGE_ARC_SWEEP_DEG = 270f
    const val GAUGE_CAPTION_SIZE_RATIO = 0.085f
    const val GAUGE_CAPTION_Y_RATIO = 0.06f
    const val GAUGE_VALUE_SIZE_RATIO = 0.16f
    const val ANALOG_DEFAULT_DP = 160f
    const val ANALOG_TICK_STROKE_RATIO = 0.02f
    const val ANALOG_HOUR_MARKS = 12
    const val ANALOG_QUARTER_3 = 3
    const val ANALOG_QUARTER_6 = 6
    const val ANALOG_QUARTER_9 = 9
    const val ANALOG_QUARTER_12 = 12
    const val ANALOG_QUARTER_STEP = 3
    const val ANALOG_TICK_INNER_MAJOR = 0.74f
    const val ANALOG_TICK_INNER_MINOR = 0.82f
    const val ANALOG_TICK_OUTER = 0.9f
    const val ANALOG_NUMBER_SIZE_RATIO = 0.1f
    const val ANALOG_NUMBER_RADIUS = 0.6f
    const val ANALOG_HOUR_HAND_LEN = 0.5f
    const val ANALOG_HOUR_HAND_WIDTH = 0.05f
    const val ANALOG_MINUTE_HAND_LEN = 0.75f
    const val ANALOG_MINUTE_HAND_WIDTH = 0.035f
    const val ANALOG_SECOND_HAND_LEN = 0.82f
    const val ANALOG_SECOND_HAND_WIDTH = 0.012f
    const val ANALOG_CENTER_DOT_RATIO = 0.04f
    const val ANALOG_HOUR_DEG_PER_MIN = 0.5f
    const val ANALOG_MINUTE_DEG_PER_SEC = 0.1f
    const val ANALOG_SECOND_DEG = 6f
    const val ICON_BUTTON_DEFAULT_DP = 64f
    const val ICON_BUTTON_ICON_RATIO = 0.45f
    const val KEYS_DEFAULT_DP = 48f
    const val KEYS_COLS = 3
    const val KEYS_GAP_TOTAL_DP = 20f
    const val KEYS_GAP_DP = 10f
    const val KEYS_EMPTY_CORNER_DP = 12f
    const val SWATCH_DEFAULT_DP = 40f
    const val SWATCH_WHITE_COUNT = 6
    const val SWATCH_HUE_COUNT = 8
    const val SWATCH_GAP_DP = 10f
    const val KEYPAD_DEFAULT_DP = 56f
    const val KEYPAD_KEY_WIDTH_RATIO = 1.5f
    const val KEYPAD_COLS = 3
    const val KEYPAD_WIDTH_PAD_DP = 24f
    const val KEYPAD_ROWS = 4
    const val KEYPAD_ROW_GAP_DP = 10f
    const val KEYPAD_DISPLAY_H = 56f
    const val KEYPAD_KEY_GAP_DP = 12f
    const val AVATAR_MIN_DP = 16f
    const val AVATAR_MAX_DP = 400f
    const val GAUGE_MIN_DP = 48f
    const val GAUGE_MAX_DP = 600f
    const val ANALOG_MIN_DP = 48f
    const val ANALOG_MAX_DP = 800f
    const val ICON_BUTTON_MIN_DP = 24f
    const val ICON_BUTTON_MAX_DP = 400f
    const val KEYS_MIN_DP = 32f
    const val KEYS_MAX_DP = 200f
    const val SWATCH_MIN_DP = 20f
    const val SWATCH_MAX_DP = 160f
    const val KEYPAD_MIN_DP = 32f
    const val KEYPAD_MAX_DP = 160f
    const val STEP_BRIGHTNESS = 10.0
    const val STEP_PERCENTAGE = 10.0
    const val STEP_POSITION = 10.0
    const val STEP_VOLUME = 5.0
    const val STEP_TEMP_F = 1.0
    const val STEP_TEMP_C = 0.5
    const val HUE_DEGREES = 360f
    const val HUE_SATURATION = 0.55f
    const val LEVEL_RANGE_MAX = 100.0
    const val CAMERA_REFRESH_SEC = 10
    const val MARQUEE_GAP_PX = 1000f
    const val MARQUEE_SPACE_PX = 1200f
    const val MARQUEE_SPEED_DP = 30f
    const val OPAQUE_ARGB = 0xFF000000.toInt()
    const val ALARM_FEAT_ARM_HOME = 1
    const val ALARM_FEAT_ARM_AWAY = 2
    const val ALARM_FEAT_ARM_NIGHT = 4
}

/** The axis of the container a component sits in; a weight only counts along it. */
private enum class Axis { ROW, COLUMN, BOX }

/** A component's size in dp, as near as the server can tell before the player measures it. */
internal data class Dims(val w: Float, val h: Float)

/** How sizes are written in a layout: `wrap`, `fill`, `dp:N` or `weight:N`. */
private sealed interface Spec {
    data object Wrap : Spec
    data object Fill : Spec
    data class Dp(val value: Float) : Spec
    data class Weight(val value: Float) : Spec

    companion object {
        fun of(spec: String): Spec = when {
            spec == "fill" -> Fill
            spec.startsWith("dp:") -> spec.removePrefix("dp:").toFloatOrNull()?.let { Dp(it) } ?: Wrap
            spec.startsWith("weight:") -> spec.removePrefix("weight:").toFloatOrNull()?.takeIf { it > 0f }?.let { Weight(it) } ?: Wrap
            else -> Wrap
        }
    }
}

/** What a wrapped leaf takes when nothing else says, so a weight or a card has something to share out. */
private fun Node.natural(): Dims = when (type) {
    "text" -> Dims(Metrics.TEXT_NATURAL_W, (props["fontSize"]?.toFloatOrNull() ?: Type.TITLE) * Metrics.TEXT_LINE_HEIGHT)
    "icon" -> (props["size"]?.toFloatOrNull() ?: Metrics.ICON_DEFAULT_DP).let { Dims(it, it) }
    "button" -> Dims(Metrics.BUTTON_NATURAL_W, Metrics.BUTTON_NATURAL_H)
    "spacer" -> Dims(Metrics.SPACER_DEFAULT_DP, Metrics.SPACER_DEFAULT_DP)
    "divider" -> Dims(Metrics.DIVIDER_THICKNESS_DP, Metrics.DIVIDER_THICKNESS_DP)
    "slider" -> Dims(Metrics.SLIDER_NATURAL_W, Metrics.SLIDER_NATURAL_H)
    "image", "tile" -> Dims(Metrics.IMAGE_NATURAL_W, Metrics.IMAGE_NATURAL_H)
    "clock" -> (props["fontSize"]?.toFloatOrNull() ?: Type.DISPLAY).let { sp ->
        Dims(maxOf(Metrics.CLOCK_MIN_W, sp * Metrics.CLOCK_WIDTH_PER_SP), sp * Metrics.TEXT_LINE_HEIGHT + if (props["show"] == "both") Metrics.CLOCK_DATE_EXTRA_H else 0f)
    }
    "badge" -> Dims(Metrics.BADGE_NATURAL_W, Metrics.BADGE_NATURAL_H)
    "avatar" -> (props["size"]?.toFloatOrNull() ?: Metrics.AVATAR_DEFAULT_DP).let { Dims(it, it) }
    "progress" -> Dims(Metrics.PROGRESS_NATURAL_W, Metrics.PROGRESS_NATURAL_H)
    "graph" -> Dims(Metrics.GRAPH_NATURAL_W, Metrics.GRAPH_NATURAL_H)
    "switch" -> Dims(Metrics.SWITCH_NATURAL_W, Metrics.SWITCH_NATURAL_H)
    "stepper" -> Dims(Metrics.STEPPER_NATURAL_W, Metrics.STEPPER_NATURAL_H)
    "choices" -> Dims(Metrics.CHOICES_NATURAL_W, Metrics.CHOICES_NATURAL_H)
    "gauge" -> (props["size"]?.toFloatOrNull() ?: Metrics.GAUGE_DEFAULT_DP).let { Dims(it, it) }
    "analog" -> (props["size"]?.toFloatOrNull() ?: Metrics.ANALOG_DEFAULT_DP).let { Dims(it, it) }
    "iconbutton" -> (props["size"]?.toFloatOrNull() ?: Metrics.ICON_BUTTON_DEFAULT_DP).let { Dims(it, it) }
    "keys" -> (props["size"]?.toFloatOrNull() ?: Metrics.KEYS_DEFAULT_DP).let { Dims(it * Metrics.KEYS_COLS + Metrics.KEYS_GAP_TOTAL_DP, it) }
    "swatches" -> (props["size"]?.toFloatOrNull() ?: Metrics.SWATCH_DEFAULT_DP).let { sizeSpec ->
        val count = if (props["show"] == "white") Metrics.SWATCH_WHITE_COUNT else Metrics.SWATCH_HUE_COUNT
        Dims(sizeSpec * count + Metrics.SWATCH_GAP_DP * (count - 1), sizeSpec)
    }
    "keypad" -> (props["size"]?.toFloatOrNull() ?: Metrics.KEYPAD_DEFAULT_DP).let {
        Dims(it * Metrics.KEYPAD_KEY_WIDTH_RATIO * Metrics.KEYPAD_COLS + Metrics.KEYPAD_WIDTH_PAD_DP, Metrics.KEYPAD_DEFAULT_DP + Metrics.KEYPAD_ROWS * (it + Metrics.KEYPAD_ROW_GAP_DP))
    }
    else -> Dims(0f, 0f)
}

/** About the size [node]'s content takes: a leaf's natural size, a container's children laid end to end, plus padding. */
internal fun content(node: Node): Dims {
    fun side(spec: String, natural: Float) = (Spec.of(spec) as? Spec.Dp)?.value ?: natural
    if (node.type !in setOf("column", "row", "box", "flow")) {
        val nat = node.natural()
        val lines = if (node.type == "text") (node.props["lines"]?.toFloatOrNull() ?: 1f).coerceAtLeast(1f) else 1f
        return Dims(side(node.size.width, nat.w), side(node.size.height, nat.h * lines))
    }
    val children = node.children.filter { !it.hidden }.map(::content)
    val gap = (node.props["gap"]?.toFloatOrNull() ?: 0f) * (children.size - 1).coerceAtLeast(0)
    val inner = when (node.type) {
        "row" -> Dims(children.sumOf { it.w.toDouble() }.toFloat() + gap, children.maxOfOrNull { it.h } ?: 0f)
        "column" -> Dims(children.maxOfOrNull { it.w } ?: 0f, children.sumOf { it.h.toDouble() }.toFloat() + gap)
        else -> Dims(children.maxOfOrNull { it.w } ?: 0f, children.maxOfOrNull { it.h } ?: 0f)
    }
    return Dims(side(node.size.width, inner.w + node.style.padX), side(node.size.height, inner.h + node.style.padY))
}

private fun fills(spec: String) = spec == "fill" || spec.startsWith("weight:")

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
            this@freeForm.node(root, this@freeForm.resolve(root, Dims(wDp, hDp), Axis.BOX, Dims(wDp, hDp)), Axis.BOX, pages)
        }
    }
}

/** The screen's pages: the index a page tap goes to, and the title a navigation card shows when it names none. */
private class Pages(val index: (String) -> Int?, val title: (String) -> String?)

/** The size of [node] within its parent's inner space [avail]; [share] is what a weight of it comes to. */
private fun DocContext.resolve(node: Node, avail: Dims, axis: Axis, share: Dims): Dims {
    val nat = node.natural()
    fun one(spec: Spec, room: Float, shared: Float, natural: Float, main: Boolean) = when (spec) {
        Spec.Fill -> if (main) shared else room
        is Spec.Dp -> spec.value
        is Spec.Weight -> shared
        Spec.Wrap -> if (natural > 0f) minOf(natural, room) else room
    }
    return Dims(
        one(Spec.of(node.size.width), avail.w, if (axis == Axis.ROW) share.w else avail.w, nat.w, axis == Axis.ROW),
        one(Spec.of(node.size.height), avail.h, if (axis == Axis.COLUMN) share.h else avail.h, nat.h, axis == Axis.COLUMN),
    )
}

/**
 * The modifier for [node]'s size: weights along the parent's axis, fills and dp otherwise. A fill along a row or a
 * column is a weight of 1, so components that fill share the room, as in the editor, rather than the first taking all.
 */
private fun DocContext.sized(node: Node, axis: Axis): Modifier {
    var modifier: Modifier = Modifier
    modifier = when (val sizeSpec = Spec.of(node.size.width)) {
        Spec.Fill -> if (axis == Axis.ROW) modifier.horizontalWeight(1f) else modifier.fillMaxWidth()
        is Spec.Dp -> modifier.width(units.px(sizeSpec.value))
        is Spec.Weight -> if (axis == Axis.ROW) modifier.horizontalWeight(sizeSpec.value) else modifier.fillMaxWidth()
        Spec.Wrap -> modifier
    }
    modifier = when (val sizeSpec = Spec.of(node.size.height)) {
        Spec.Fill -> if (axis == Axis.COLUMN) modifier.verticalWeight(1f) else modifier.fillMaxHeight()
        is Spec.Dp -> modifier.height(units.px(sizeSpec.value))
        is Spec.Weight -> if (axis == Axis.COLUMN) modifier.verticalWeight(sizeSpec.value) else modifier.fillMaxHeight()
        Spec.Wrap -> modifier
    }
    return modifier
}

/** Components whose keys inside take the taps, so the component as a whole takes none. */
private val OWN_TAPS = setOf("tile", "slider", "stepper", "choices", "keys", "swatches", "keypad")

/**
 * The size, the rounded background and outline, the taps, then the padding: the order `key` builds its own modifier
 * in. A bound component with `showWhen` is drawn only while its entity is on (or off).
 */
private fun DocContext.styled(node: Node, axis: Axis, pages: Pages, base: Modifier = sized(node, axis)): Modifier {
    var modifier = base
    val id = node.bind?.entityId
    val showWhen = node.props["showWhen"]
    if (id != null && (showWhen == "on" || showWhen == "off")) {
        bindEntity(id)
        modifier = modifier.visibility(int(Vars.entity(id, showWhen)))
        appear(node.props["animate"])?.let { (enter, exit) -> modifier = modifier.animationSpec(enterAnimation = enter, exitAnimation = exit) }
    }
    val bg = node.style.background?.let { paint(it) }
    if (node.style.corner > 0f) modifier = modifier.clip(rounded(node.style.corner))
    if (bg != null) modifier = modifier.background(bg)
    if (node.style.border > 0f) {
        val ink = node.style.borderColor?.let { paint(it) } ?: role(Role.DIVIDER)
        modifier = modifier.border(units.px(node.style.border), units.px(node.style.corner), ink, if (node.style.corner > 0f) RcBorderShape.RoundedRectangle else RcBorderShape.Rectangle)
    }
    if (node.type !in OWN_TAPS) {
        val (onTap, onHold) = refs(node, pages)
        modifier = tap(modifier, onTap, onHold)
    }
    val style = node.style
    if (style.padded) modifier = modifier.padding(units.px(style.start), units.px(style.top), units.px(style.end), units.px(style.bottom))
    return modifier
}

/** How a component that shows and hides comes and goes: a fade unless [animate] names a slide, or `none`. */
private fun appear(animate: String?): Pair<AnimationSpec.ANIMATION, AnimationSpec.ANIMATION>? = when (animate) {
    "none" -> null
    "left" -> AnimationSpec.ANIMATION.SLIDE_LEFT to AnimationSpec.ANIMATION.SLIDE_LEFT
    "right" -> AnimationSpec.ANIMATION.SLIDE_RIGHT to AnimationSpec.ANIMATION.SLIDE_RIGHT
    "up" -> AnimationSpec.ANIMATION.SLIDE_TOP to AnimationSpec.ANIMATION.SLIDE_TOP
    "down" -> AnimationSpec.ANIMATION.SLIDE_BOTTOM to AnimationSpec.ANIMATION.SLIDE_BOTTOM
    else -> AnimationSpec.ANIMATION.FADE_IN to AnimationSpec.ANIMATION.FADE_OUT
}

/** What a tap and a hold on [node] send, through the same router as a tile's. */
private fun DocContext.refs(node: Node, pages: Pages): Pair<ActionRef?, ActionRef?> {
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
    Pastel.of(key)?.let { pastel(it) } ?: Themes.parse(key)?.let { fixed(it) } ?: Role.of(key)?.let { role(it) }

/** The ARGB a tile lights up in: its pastel key or own hex colour, else [fallback]. */
internal fun DocContext.accent(key: String?, fallback: Pastel): Int =
    Pastel.of(key)?.argb ?: key?.let(Themes::parse) ?: fallback.argb

private fun DocContext.node(node: Node, self: Dims, axis: Axis, pages: Pages) {
    if (node.hidden) return
    when (node.type) {
        "column", "row", "box", "flow" -> container(node, self, axis, pages)
        "text" -> textNode(node, axis, pages)
        "icon" -> iconNode(node, axis, pages)
        "button" -> buttonNode(node, axis, pages)
        "spacer" -> scope.Spacer(styled(node, axis, pages, spacerSize(node, axis)))
        "divider" -> scope.Box(styled(node, axis, pages, dividerSize(node, axis)).background(paint(node.props["color"] ?: "divider") ?: role(Role.DIVIDER)))
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
    }
}

/** [sized], with a wrapped side set to what the server worked out, for components that draw to a known size. */
private fun DocContext.measured(node: Node, axis: Axis, self: Dims): Modifier {
    var modifier = sized(node, axis)
    if (node.size.width == "wrap") modifier = modifier.width(units.px(self.w))
    if (node.size.height == "wrap") modifier = modifier.height(units.px(self.h))
    return modifier
}

private fun DocContext.spacerSize(node: Node, axis: Axis): Modifier {
    val modifier = sized(node, axis)
    return if (node.size.width == "wrap" && node.size.height == "wrap") {
        modifier.size(units.px(Metrics.SPACER_DEFAULT_DP), units.px(Metrics.SPACER_DEFAULT_DP))
    } else {
        modifier
    }
}

private fun DocContext.dividerSize(node: Node, axis: Axis): Modifier {
    var modifier = sized(node, axis)
    if (node.size.width == "wrap" && node.size.height == "wrap") {
        modifier = if (axis == Axis.ROW) {
            modifier.width(units.px(Metrics.DIVIDER_THICKNESS_DP)).fillMaxHeight()
        } else {
            modifier.fillMaxWidth().height(units.px(Metrics.DIVIDER_THICKNESS_DP))
        }
    }
    return modifier
}

/** Children of a wrapped row/column: fills become wraps so they take content size, not leftover room. */
private fun Node.looseChildren(looseW: Boolean, looseH: Boolean): List<Node> =
    children.filter { !it.hidden }.map { child ->
        when {
            looseW && fills(child.size.width) -> child.copy(size = child.size.copy(width = "wrap"))
            looseH && fills(child.size.height) -> child.copy(size = child.size.copy(height = "wrap"))
            else -> child
        }
    }

private fun DocContext.container(node: Node, given: Dims, axis: Axis, pages: Pages) {
    // A wrapped row is as wide as its children, so one that fills it takes its content's width, as in the editor,
    // rather than a weight that takes all the room the row could grow to and pushes its siblings off; a wrapped
    // column's children, likewise down it.
    val looseW = node.type == "row" && node.size.width == "wrap"
    val looseH = node.type == "column" && node.size.height == "wrap"
    val children = node.looseChildren(looseW, looseH)
    // A wrapped row whose children fill its height is as tall as its tallest child, so cards side by side match,
    // rather than the fill stretching to the page; a wrapped column whose children fill its width, likewise.
    val pinH = node.type == "row" && node.size.height == "wrap" && children.any { fills(it.size.height) }
    val pinW = node.type == "column" && node.size.width == "wrap" && children.any { fills(it.size.width) }
    val self = if (pinH || pinW) content(node).let { Dims(if (pinW) minOf(it.w, given.w) else given.w, if (pinH) minOf(it.h, given.h) else given.h) } else given
    val inner = Dims((self.w - node.style.padX).coerceAtLeast(0f), (self.h - node.style.padY).coerceAtLeast(0f))
    val gap = node.props["gap"]?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
    val mine = when (node.type) { "row" -> Axis.ROW; "column" -> Axis.COLUMN; else -> Axis.BOX }
    val sizes = childSizes(children, inner, mine, gap)
    var base = sized(node, axis)
    if (pinH) base = base.height(units.px(self.h))
    if (pinW) base = base.width(units.px(self.w))
    var modifier = styled(node, axis, pages, base)
    if (node.props["scroll"] == "true") modifier = if (mine == Axis.ROW) modifier.horizontalScroll() else if (mine == Axis.COLUMN) modifier.verticalScroll() else modifier
    val doc = this
    fun drawChildren() {
        children.forEachIndexed { i, child ->
            if (i > 0 && gap > 0f) scope.Spacer(if (mine == Axis.ROW) Modifier.width(units.px(gap)) else Modifier.height(units.px(gap)))
            val place = child.props["place"]
            if (place == null) doc.node(child, sizes[i], mine, pages) else doc.placed(child, place, sizes[i], mine, pages)
        }
    }
    val alignment = node.props["alignment"]
    val arrangement = node.props["arrangement"]
    if (node.type == "flow") {
        // A flow has no spacing of its own: each child carries the gap after it and below it.
        scope.Flow(modifier, horizontal = horizontal(alignment), vertical = RcVerticalPositioning.Top) {
            children.forEachIndexed { i, child ->
                Box(Modifier.padding(0f, 0f, units.px(gap), units.px(gap))) { doc.node(child, sizes[i], Axis.BOX, pages) }
            }
        }
        return
    }
    when (mine) {
        Axis.COLUMN -> scope.Column(modifier, horizontal = horizontal(alignment), vertical = columnArrangement(arrangement)) { drawChildren() }
        Axis.ROW -> scope.Row(modifier, horizontal = rowArrangement(arrangement), vertical = vertical(alignment)) { drawChildren() }
        Axis.BOX -> {
            val parts = (alignment ?: "top-start").split('-')
            val (vertAlign, horizAlign) = if (parts.size == 2) parts[0] to parts[1] else parts[0] to parts[0]
            scope.Box(modifier, horizontal = horizontal(horizAlign), vertical = vertical(vertAlign)) { drawChildren() }
        }
    }
}

/**
 * [child] aligned on its own within its parent ([place]): across a column (start, center, end), across a row (top,
 * center, bottom) or anywhere in a box (`top-end`). A box spanning the parent's cross axis, sized like [child] along
 * the main one, holds it where it asks.
 */
private fun DocContext.placed(child: Node, place: String, self: Dims, axis: Axis, pages: Pages) {
    val doc = this
    fun main(spec: String, weight: (Float) -> Modifier, fill: Modifier, dp: (Float) -> Modifier): Modifier =
        when (val sizeSpec = Spec.of(spec)) {
            is Spec.Weight -> weight(sizeSpec.value)
            Spec.Fill -> fill
            is Spec.Dp -> dp(sizeSpec.value)
            Spec.Wrap -> Modifier
        }
    val parts = place.split('-')
    val (vertAlign, horizAlign) = if (parts.size == 2) parts[0] to parts[1] else parts[0] to parts[0]
    when (axis) {
        Axis.COLUMN -> scope.Box(
            main(child.size.height, { Modifier.verticalWeight(it) }, Modifier.verticalWeight(1f), { Modifier.height(units.px(it)) }).fillMaxWidth(),
            horizontal = horizontal(place), vertical = RcVerticalPositioning.Top,
        ) { doc.node(child, self, Axis.BOX, pages) }
        Axis.ROW -> scope.Box(
            main(child.size.width, { Modifier.horizontalWeight(it) }, Modifier.horizontalWeight(1f), { Modifier.width(units.px(it)) }).fillMaxHeight(),
            horizontal = RcHorizontalPositioning.Start, vertical = vertical(place),
        ) { doc.node(child, self, Axis.BOX, pages) }
        Axis.BOX -> scope.Box(Modifier.fillMaxSize(), horizontal = horizontal(horizAlign), vertical = vertical(vertAlign)) { doc.node(child, self, Axis.BOX, pages) }
    }
}

/** The size of each child: fixed and wrapped ones first, then what is left shared out by weight. */
private fun DocContext.childSizes(children: List<Node>, inner: Dims, axis: Axis, gap: Float): List<Dims> {
    if (axis == Axis.BOX) return children.map { resolve(it, inner, Axis.BOX, inner) }
    val main = { dims: Dims -> if (axis == Axis.ROW) dims.w else dims.h }
    val mainSpec = { node: Node -> Spec.of(if (axis == Axis.ROW) node.size.width else node.size.height) }
    val weightOf = { node: Node -> when (val sizeSpec = mainSpec(node)) { is Spec.Weight -> sizeSpec.value; Spec.Fill -> 1f; else -> 0f } }
    val fixed = children.filter { weightOf(it) == 0f }.sumOf { main(resolve(it, inner, axis, Dims(0f, 0f))).toDouble() }.toFloat()
    val weights = children.sumOf { weightOf(it).toDouble() }.toFloat()
    val left = (main(inner) - fixed - gap * (children.size - 1).coerceAtLeast(0)).coerceAtLeast(0f)
    return children.map { child ->
        val weight = weightOf(child)
        val part = if (weights > 0f) left * weight / weights else 0f
        resolve(child, inner, axis, if (axis == Axis.ROW) Dims(part, inner.h) else Dims(inner.w, part))
    }
}

private fun horizontal(spec: String?): RcHorizontalPositioning = when (spec) {
    "center" -> RcHorizontalPositioning.Center
    "end" -> RcHorizontalPositioning.End
    else -> RcHorizontalPositioning.Start
}

private fun vertical(spec: String?): RcVerticalPositioning = when (spec) {
    "center" -> RcVerticalPositioning.Center
    "end", "bottom" -> RcVerticalPositioning.Bottom
    else -> RcVerticalPositioning.Top
}

private fun rowArrangement(spec: String?): RcRowHorizontalPositioning = when (spec) {
    "center" -> RcRowHorizontalPositioning.Center
    "end" -> RcRowHorizontalPositioning.End
    "spaceBetween" -> RcRowHorizontalPositioning.SpaceBetween
    "spaceEvenly" -> RcRowHorizontalPositioning.SpaceEvenly
    "spaceAround" -> RcRowHorizontalPositioning.SpaceAround
    else -> RcRowHorizontalPositioning.Start
}

private fun columnArrangement(spec: String?): RcColumnVerticalPositioning = when (spec) {
    "center" -> RcColumnVerticalPositioning.Center
    "end" -> RcColumnVerticalPositioning.Bottom
    "spaceBetween" -> RcColumnVerticalPositioning.SpaceBetween
    "spaceEvenly" -> RcColumnVerticalPositioning.SpaceEvenly
    "spaceAround" -> RcColumnVerticalPositioning.SpaceAround
    else -> RcColumnVerticalPositioning.Top
}

private fun align(spec: String?): RcTextAlign = when (spec) {
    "center" -> RcTextAlign.Center
    "end" -> RcTextAlign.End
    else -> RcTextAlign.Start
}

/** The name of the variable a bound component's words are sent in. */
fun nodeVar(nodeId: String) = "n.$nodeId.text"

private fun DocContext.textNode(node: Node, axis: Axis, pages: Pages) {
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.TITLE
    val weight = node.props["weight"]?.toFloatOrNull() ?: Weight.NORMAL
    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val marquee = node.props["marquee"] == "true"
    val lines = if (marquee) 1 else node.props["lines"]?.toIntOrNull()?.coerceIn(1, Metrics.TEXT_MAX_LINES) ?: 1
    val textAlign = align(node.props["align"])
    // Words wider than their space glide back and forth at 30 dp a second, forever.
    // Put taps on a Box: like Image, Text can keep a ClickModifier that still misses taps on device.
    val modifier = styled(node, axis, pages).let {
        if (marquee) it.marquee(Int.MAX_VALUE, 0, Metrics.MARQUEE_GAP_PX, Metrics.MARQUEE_SPACE_PX, 0f, Metrics.MARQUEE_SPEED_DP) else it
    }
    val binding = node.bind
    if (binding == null) {
        scope.Box(modifier) { this@textNode.label(node.props["text"].orEmpty(), sp, ink, weight, lines, textAlign) }
        return
    }
    bind(binding.entityId) { states ->
        val entityState = states[binding.entityId]
        val view = Present.view(entityState, i18n)
        val raw = when (binding.format) {
            "value" -> view.value
            "name" -> view.name
            "attribute" -> binding.attribute?.let { entityState?.attr(it) } ?: ""
            else -> view.stateText
        }
        listOf(VarValue.string(nodeVar(node.id), binding.prefix + raw + binding.suffix))
    }
    scope.Box(modifier) { this@textNode.label(this@textNode.text(nodeVar(node.id)), sp, ink, weight, lines, textAlign) }
}

/** An icon bound to an entity lights up in its pastel while the entity is on, like a tile's. */
private fun DocContext.iconNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull() ?: Metrics.ICON_DEFAULT_DP
    val id = node.bind?.entityId
    val domain = id?.substringBefore('.')
    val name = node.props["icon"] ?: id?.let { states[it]?.attr("icon") } ?: domain?.let { Catalogue.of(it)?.icon }
    val explicit = node.props["color"]?.let { paint(it) }
    val ink = when {
        explicit != null -> explicit
        id != null -> {
            bindTileColors(node.id, id, Pastel.forDomain(domain))
            color(Vars.tile(node.id, Vars.KEY_ICON))
        }
        else -> role(Role.INK)
    }
    scope.Box(styled(node, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@iconNode
        val on = node.props["iconOn"]
        if (id != null && on != null) {
            doc.bindEntity(id)
            doc.toggleIcon(id, name ?: Icons.FALLBACK, on, sizeDp, ink)
        } else {
            doc.icon(name, sizeDp, ink)
        }
    }
}

private fun DocContext.buttonNode(node: Node, axis: Axis, pages: Pages) {
    val bg = node.props["color"]?.let { paint(it) } ?: role(Role.KEY)
    val ink = node.props["ink"]?.let { paint(it) } ?: role(Role.INK)
    var base = sized(node, axis)
    if (node.size.height == "wrap") base = base.height(units.px(Metrics.BUTTON_NATURAL_H))
    base = base.clip(rounded(if (node.style.corner > 0f) node.style.corner else Metrics.BUTTON_NATURAL_H / Metrics.BUTTON_CORNER_DIVISOR))
    if (node.style.background == null) base = base.background(bg)
    val modifier = styled(node, axis, pages, base).let {
        if (!node.style.padded) it.padding(units.px(Metrics.BUTTON_PAD_H_DP), 0f, units.px(Metrics.BUTTON_PAD_H_DP), 0f) else it
    }
    val word = node.props["text"]?.takeIf { it.isNotEmpty() }
    val icon = node.props["icon"]?.takeIf { it.isNotEmpty() }
    scope.Row(modifier, horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@buttonNode
        if (icon != null) doc.icon(icon, if (word == null) Metrics.BUTTON_ICON_ONLY_DP else Metrics.BUTTON_ICON_WITH_TEXT_DP, ink)
        if (icon != null && word != null) Spacer(Modifier.width(doc.units.px(Metrics.BUTTON_ICON_GAP_DP)))
        if (word != null) doc.label(word, Type.BODY, ink, weight = Weight.MEDIUM)
    }
}

/** A stepped bar over the entity's level, as on a tile: a tap on step i sets i tenths of its range. */
private fun DocContext.sliderNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    if (id == null) {
        scope.Box(styled(node, axis, pages))
        return
    }
    bindEntity(id)
    bindTileColors(node.id, id, accent(node.props["color"], Pastel.forDomain(id.substringBefore('.'))))
    val domain = id.substringBefore('.')
    val entityState = states[id]
    val verb = when (domain) { "light" -> "brightness"; "fan" -> "percentage"; "cover" -> "position"; "media_player" -> "volume"; else -> "value" }
    val (min, max) = if (domain in setOf("number", "input_number")) {
        (entityState?.number("min") ?: 0.0) to (entityState?.number("max") ?: Metrics.LEVEL_RANGE_MAX)
    } else {
        0.0 to Metrics.LEVEL_RANGE_MAX
    }
    val steps = node.props["steps"]?.toIntOrNull()?.coerceIn(Metrics.SLIDER_MIN_STEPS, Metrics.SLIDER_MAX_STEPS) ?: Metrics.SLIDER_DEFAULT_STEPS
    val h = if (node.size.height == "wrap") Metrics.SLIDER_NATURAL_H else self.h
    scope.Box(styled(node, axis, pages)) {
        val doc = this@sliderNode
        doc.levelBar(doc.float(Vars.entity(id, Vars.KEY_LEVEL)), self.w - node.style.padX, h - node.style.padY, doc.color(Vars.tile(node.id, Vars.KEY_ICON)), doc.role(Role.TRACK), steps) { i ->
            val value = min + (max - min) * i / steps
            ActionRef(node.id, verb, if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.ROOT, value))
        }
    }
}

/** A camera's frames, or the entity's picture (a person, album art), fetched by the add-on. */
private fun DocContext.imageNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val modifier = styled(node, axis, pages)
    if (id == null) {
        scope.Box(modifier.background(role(Role.TRACK)))
        return
    }
    val w = units.px(self.w).toInt().coerceAtLeast(1)
    val h = units.px(self.h).toInt().coerceAtLeast(1)
    val camera = id.startsWith("camera.")
    val fit = node.props["fit"] == "whole"
    val slot = "img.${node.id}"
    picture(PictureSlot(slot, if (camera) "/api/camera_proxy/$id" else "attr:$id:entity_picture", w, h, if (camera) Metrics.CAMERA_REFRESH_SEC else 0, crop = !fit))
    // Put taps on a Box: Image drops onClick in the document (alpha21), so a camera never opened its card.
    scope.Box(modifier) {
        Image(
            this@imageNode.image(slot, w, h),
            Modifier.fillMaxSize(),
            contentScale = if (fit) RcContentScale.Fit else RcContentScale.Crop,
        )
    }
}

/** One of the grid's cards, at the size the layout gives it. */
private fun DocContext.tileNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val tile = node.tile
    val spec = tile?.let { Catalogue.of(it.effectiveKind()) }
    val modifier = sized(node, axis).let { if (node.size.width == "wrap" && node.size.height == "wrap") it.size(units.px(self.w), units.px(self.h)) else it }
    val lacksEntity = tile?.entityId == null && spec?.needsEntity == true
    if (tile == null || spec == null || lacksEntity) {
        scope.Box(modifier)
        return
    }
    val named = if (tile.effectiveKind() == "nav" && tile.name == null) tile.copy(name = tile.options["page"]?.let(pages.title)) else tile
    scope.Box(modifier) {
        this@tileNode.tile(named, TileBox(0f, 0f, self.w, self.h, 1, 1, full = false), pages.index)
    }
}

/** The time, the date, or the time over the date, from the values the player keeps itself. */
private fun DocContext.clockNode(node: Node, axis: Axis, pages: Pages) {
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.DISPLAY
    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val show = node.props["show"] ?: "time"
    val textAlign = align(node.props["align"])
    scope.Column(styled(node, axis, pages), horizontal = horizontal(node.props["align"])) {
        val doc = this@clockNode
        if (show != "date") doc.label(doc.text(Vars.TIME), sp, ink, Weight.LIGHT, align = textAlign)
        if (show == "date") doc.label(doc.text(Vars.DATE), sp, ink, align = textAlign, lines = 2)
        if (show == "both") doc.label(doc.text(Vars.DATE), maxOf(sp * Metrics.CLOCK_DATE_SCALE, Type.BODY), doc.role(Role.INK_SOFT), align = textAlign, lines = 2)
    }
}

/** A pill with an icon and a word; bound, it shows the entity's state and lights up while it is on. */
private fun DocContext.badgeNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val domain = id?.substringBefore('.')
    if (id != null) bindEntity(id)
    val explicit = node.props["color"]?.let { paint(it) }
    val (bg, ink) = when {
        explicit != null -> explicit to role(Role.KEY_ON_INK)
        id != null -> {
            bindTileColors(node.id, id, Pastel.forDomain(domain))
            color(Vars.tile(node.id, Vars.KEY_BG)) to color(Vars.tile(node.id, Vars.KEY_BG_INK))
        }
        else -> role(Role.KEY) to role(Role.INK)
    }
    var base = sized(node, axis)
    if (node.size.height == "wrap") base = base.height(units.px(Metrics.BADGE_NATURAL_H))
    base = base.clip(rounded(if (node.style.corner > 0f) node.style.corner else Metrics.BADGE_CORNER_DP))
    if (node.style.background == null) base = base.background(bg)
    val modifier = styled(node, axis, pages, base).let {
        if (!node.style.padded) it.padding(units.px(Metrics.BADGE_PAD_H_DP), 0f, units.px(Metrics.BADGE_PAD_H_DP), 0f) else it
    }
    val icon = node.props["icon"]?.takeIf { it.isNotEmpty() } ?: id?.let { states[it]?.attr("icon") } ?: domain?.let { Catalogue.of(it)?.icon }
    scope.Row(modifier, horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@badgeNode
        if (icon != null) {
            doc.icon(icon, Metrics.BADGE_ICON_DP, ink)
            Spacer(Modifier.width(doc.units.px(Metrics.BADGE_ICON_GAP_DP)))
        }
        if (id != null && node.props["text"].isNullOrEmpty()) doc.label(doc.text(Vars.entity(id, Vars.KEY_STATE)), Type.BODY, ink, Weight.MEDIUM)
        else doc.label(node.props["text"].orEmpty(), Type.BODY, ink, Weight.MEDIUM)
    }
}

/** A person's photo in a circle, or initials: the entity's name, or the text when it shows none. */
private fun DocContext.avatarNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(Metrics.AVATAR_MIN_DP, Metrics.AVATAR_MAX_DP) ?: Metrics.AVATAR_DEFAULT_DP
    val id = node.bind?.entityId
    scope.Box(styled(node, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@avatarNode
        if (id != null) {
            doc.bindEntity(id)
            doc.photo(doc.states[id], id, "pic.${node.id}", sizeDp)
        } else {
            doc.initials(node.props["text"]?.takeIf { it.isNotBlank() } ?: "?", sizeDp, doc.role(Role.KEY), doc.role(Role.INK))
        }
    }
}

/** The name of the variable a progress bar's fill, from 0 to 1, is sent in. */
fun nodeLevel(nodeId: String) = "n.$nodeId.level"

/** Sends [nodeLevel] of [node]: the entity's own level, or its number between `min` and `max`. */
private fun DocContext.bindLevel(node: Node, id: String) {
    bind(id) { states ->
        val entityState = states[id]
        val level = Present.view(entityState, i18n).level ?: entityState?.numericState()?.let { value ->
            val lo = node.props["min"]?.toDoubleOrNull() ?: entityState.number("min") ?: 0.0
            val hi = node.props["max"]?.toDoubleOrNull() ?: entityState.number("max") ?: Metrics.LEVEL_RANGE_MAX
            if (hi > lo) ((value - lo) / (hi - lo)).toFloat() else 0f
        } ?: 0f
        listOf(VarValue.float(nodeLevel(node.id), level.coerceIn(0f, 1f)))
    }
}

/**
 * A bar that fills with the entity's level: a light's brightness, a cover's position, or a number between [min] and
 * [max] (the entity's own limits, or 0 to 100) for anything else, such as a battery.
 */
private fun DocContext.progressNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val w = units.px((self.w - node.style.padX).coerceAtLeast(1f))
    val h = (self.h - node.style.padY).coerceAtLeast(1f)
    val fill = node.props["color"]?.let { paint(it) } ?: fixed(Pastel.forDomain(id?.substringBefore('.')).argb)
    if (id != null) bindLevel(node, id)
    scope.Box(styled(node, axis, pages, measured(node, axis, self))) {
        val doc = this@progressNode
        Box(Modifier.size(w, doc.units.px(h)).clip(doc.rounded(h / 2f)).background(doc.role(Role.TRACK))) {
            if (id != null) {
                val level = doc.float(nodeLevel(node.id))
                Canvas(Modifier.size(w, doc.units.px(h))) {
                    paint { color(fill); style(RcPaintStyle.Fill) }
                    drawRect(0f.rf, 0f.rf, level * w, doc.units.px(h).rf)
                }
            }
        }
    }
}

/** The entity's history from the recorder as a line, over the last `hours` (24 unless set). */
private fun DocContext.graphNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val modifier = styled(node, axis, pages, measured(node, axis, self))
    if (id == null) {
        scope.Box(modifier.background(role(Role.TRACK)))
        return
    }
    refreshEvery(Metrics.GRAPH_REFRESH_SEC)
    val line = node.props["color"]?.let { paint(it) } ?: fixed(Pastel.BLUE.argb)
    val w = (self.w - node.style.padX).coerceAtLeast(1f)
    val h = (self.h - node.style.padY).coerceAtLeast(1f)
    scope.Box(modifier) {
        val doc = this@graphNode
        doc.graph(doc.data.history[id].orEmpty(), w, h, line, doc.role(Role.INK_SOFT))
    }
}

/** An on and off switch: the track lights up in the entity's colour and the knob moves over while it is on. */
private fun DocContext.switchNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val track = if (id != null) {
        bindEntity(id)
        bindTileColors(node.id, id, accent(node.props["color"], Pastel.forDomain(id.substringBefore('.'))))
        color(Vars.tile(node.id, Vars.KEY_BG))
    } else {
        role(Role.KEY)
    }
    scope.Box(styled(node, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@switchNode
        Box(Modifier.size(doc.units.px(Metrics.SWITCH_NATURAL_W), doc.units.px(Metrics.SWITCH_NATURAL_H)).clip(doc.rounded(Metrics.SWITCH_CORNER_DP)).background(track)) {
            fun knob(end: Boolean, shown: String?) {
                var modifier = Modifier.fillMaxSize().padding(doc.units.px(Metrics.SWITCH_KNOB_PAD_DP))
                if (shown != null) modifier = modifier.visibility(doc.int(shown))
                Box(modifier, horizontal = if (end) RcHorizontalPositioning.End else RcHorizontalPositioning.Start, vertical = RcVerticalPositioning.Center) {
                    Box(Modifier.size(doc.units.px(Metrics.SWITCH_KNOB_DP), doc.units.px(Metrics.SWITCH_KNOB_DP)).clip(doc.rounded(Metrics.SWITCH_KNOB_CORNER_DP)).background(doc.role(Role.INK)))
                }
            }
            if (id == null) {
                knob(false, null)
            } else {
                knob(false, Vars.entity(id, Vars.KEY_OFF))
                knob(true, Vars.entity(id, Vars.KEY_ON))
            }
        }
    }
}

/** What a stepper's keys send for an entity, and by how much a tap moves it. */
private fun DocContext.stepOf(node: Node, entityState: EntityState?, domain: String?): Pair<String, Double>? {
    val pair = when (domain) {
        "climate" -> "temperature" to (entityState?.number("target_temp_step") ?: if (homeAssistantConfig.temperatureUnit.contains("F")) Metrics.STEP_TEMP_F else Metrics.STEP_TEMP_C)
        "number", "input_number" -> "value" to (entityState?.number("step") ?: 1.0)
        "light" -> "brightness" to Metrics.STEP_BRIGHTNESS
        "fan" -> "percentage" to Metrics.STEP_PERCENTAGE
        "cover" -> "position" to Metrics.STEP_POSITION
        "media_player" -> "volume" to Metrics.STEP_VOLUME
        else -> return null
    }
    val step = node.props["step"]?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: pair.second
    return pair.first to step
}

/** Minus, the value, plus: a thermostat's target, a number, a light's brightness. */
private fun DocContext.stepperNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val domain = id?.substringBefore('.')
    val ink = role(Role.INK)
    val how = id?.let { stepOf(node, states[it], domain) }
    if (id != null) bindEntity(id)
    val step = how?.second?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.ROOT, it) }
    val down = how?.let { ActionRef(node.id, it.first, "-$step") }
    val up = how?.let { ActionRef(node.id, it.first, "+$step") }
    val isPredictable = how != null && domain?.let { Catalogue.of(it) }?.predict?.containsKey(how.first) == true
    if (id != null && isPredictable) {
        down?.let { predict(it, id) }
        up?.let { predict(it, id) }
    }
    scope.Row(styled(node, axis, pages), horizontal = RcRowHorizontalPositioning.SpaceBetween, vertical = RcVerticalPositioning.Center) {
        val doc = this@stepperNode
        fun key(ref: ActionRef?, icon: String) {
            if (ref != null) doc.key(ref, icon = icon, heightDp = Metrics.STEPPER_KEY_DP, widthDp = Metrics.STEPPER_KEY_DP, round = true, ink = ink)
            else Box(Modifier.size(doc.units.px(Metrics.STEPPER_KEY_DP), doc.units.px(Metrics.STEPPER_KEY_DP)).clip(doc.rounded(Metrics.STEPPER_KEY_CORNER_DP)).background(doc.role(Role.KEY)), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                doc.icon(icon, Metrics.STEPPER_KEY_ICON_DP, doc.role(Role.INK_DIM))
            }
        }
        key(down, "minus")
        val middle = if (node.size.width == "wrap") Modifier.width(doc.units.px(Metrics.STEPPER_MIDDLE_W)) else Modifier.horizontalWeight(1f)
        Box(middle, horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
            if (id != null) doc.label(doc.text(Vars.entity(id, Vars.KEY_VALUE)), Type.HEADLINE, ink, Weight.MEDIUM, align = RcTextAlign.Center)
            else doc.label("–", Type.HEADLINE, doc.role(Role.INK_DIM), Weight.MEDIUM, align = RcTextAlign.Center)
        }
        key(up, "plus")
    }
}

/** What a row of choices offers for an entity: the verb, the choices, how each reads, and which is chosen. */
private class ChoiceSet(val verb: String, val attribute: String, val choices: List<String>, val word: (String) -> String, val chosen: (EntityState) -> String?, val icons: Map<String, String> = emptyMap())

private fun DocContext.choicesOf(entityState: EntityState?, domain: String?): ChoiceSet? = when (domain) {
    "select", "input_select" -> ChoiceSet("option", "options", entityState?.list("options").orEmpty(), { it }, { it.state })
    "climate" -> ChoiceSet("mode", "hvac_modes", entityState?.list("hvac_modes").orEmpty(), { i18n.state("climate", it) }, { it.state }, MODE_ICONS)
    "light" -> ChoiceSet("effect", "effect_list", entityState?.list("effect_list").orEmpty(), { it }, { it.attr("effect") })
    else -> null
}

/** A row of keys, one per option: a select's options, a thermostat's modes, a light's effects. */
private fun DocContext.choicesNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val modifier = styled(node, axis, pages, measured(node, axis, self))
    val set = id?.let { choicesOf(states[it], it.substringBefore('.')) }
    if (id == null || set == null || set.choices.isEmpty()) {
        scope.Box(modifier.clip(rounded(Metrics.CHOICES_EMPTY_CORNER_DP)).background(role(Role.TRACK)))
        return
    }
    structural(id) { it?.list(set.attribute)?.joinToString() ?: "" }
    val shown = set.choices.take(node.props["max"]?.toIntOrNull()?.coerceIn(Metrics.CHOICES_MIN, Metrics.CHOICES_MAX) ?: Metrics.CHOICES_DEFAULT_MAX)
    bindChoices(node.id, "c", id, shown, set.chosen)
    val h = (self.h - node.style.padY).coerceAtLeast(Metrics.CHOICES_MIN_H)
    scope.Box(modifier, vertical = RcVerticalPositioning.Center) {
        this@choicesNode.choiceRow(node.id, "c", shown.map { it to set.word(it) }, { ActionRef(node.id, set.verb, it) }, h, set.icons)
    }
}

/** A ring three quarters round that fills with the entity's level, its value in the middle. */
private fun DocContext.gaugeNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(Metrics.GAUGE_MIN_DP, Metrics.GAUGE_MAX_DP) ?: Metrics.GAUGE_DEFAULT_DP
    val px = units.px(sizeDp)
    val stroke = px * Metrics.GAUGE_STROKE_RATIO
    val inset = stroke / 2f
    val fill = node.props["color"]?.let { paint(it) } ?: fixed(Pastel.forDomain(id?.substringBefore('.')).argb)
    val track = role(Role.TRACK)
    if (id != null) {
        bindEntity(id)
        bindLevel(node, id)
    }
    // In the gap under the ring: the entity's name for `@name`, else the words given.
    val caption = node.props["caption"]?.takeIf { it.isNotBlank() }?.let {
        if (it == "@name") id?.let { entityId -> text(Vars.entity(entityId, Vars.KEY_NAME)) } else literal(it)
    }
    scope.Box(styled(node, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@gaugeNode
        Box(Modifier.size(px, px), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
            Canvas(Modifier.size(px, px)) {
                paint { color(track); style(RcPaintStyle.Stroke); strokeWidth(stroke); strokeCap(RcStrokeCap.Round) }
                drawArc(inset, inset, px - inset, px - inset, Metrics.GAUGE_ARC_START_DEG, Metrics.GAUGE_ARC_SWEEP_DEG)
                if (id != null) {
                    paint { color(fill); style(RcPaintStyle.Stroke); strokeWidth(stroke); strokeCap(RcStrokeCap.Round) }
                    drawArc(inset.rf, inset.rf, (px - inset).rf, (px - inset).rf, Metrics.GAUGE_ARC_START_DEG.rf, doc.float(nodeLevel(node.id)) * Metrics.GAUGE_ARC_SWEEP_DEG)
                }
                if (caption != null) {
                    paint { color(doc.role(Role.INK_SOFT)); style(RcPaintStyle.Fill); textSize(px * Metrics.GAUGE_CAPTION_SIZE_RATIO) }
                    drawTextAnchored(caption, px / 2f, px - px * Metrics.GAUGE_CAPTION_Y_RATIO, 0f, 0f)
                }
            }
            if (id != null) doc.label(doc.text(Vars.entity(id, Vars.KEY_VALUE)), maxOf(sizeDp * Metrics.GAUGE_VALUE_SIZE_RATIO, Type.CAPTION), doc.role(Role.INK), Weight.MEDIUM, align = RcTextAlign.Center)
        }
    }
}

/** A clock face whose hands the player turns by its own clock, so it keeps time without the add-on. */
private fun DocContext.analogNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(Metrics.ANALOG_MIN_DP, Metrics.ANALOG_MAX_DP) ?: Metrics.ANALOG_DEFAULT_DP
    val px = units.px(sizeDp)
    val mid = px / 2f
    val face = node.props["color"]?.let { paint(it) } ?: role(Role.CARD)
    val ink = role(Role.INK)
    val dim = role(Role.INK_DIM)
    val accent = role(Role.ACCENT)
    val seconds = node.props["seconds"] == "true"
    scope.Box(styled(node, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@analogNode
        Canvas(Modifier.size(px, px)) {
            paint { color(face); style(RcPaintStyle.Fill) }
            drawCircle(mid, mid, mid)
            paint { color(dim); style(RcPaintStyle.Stroke); strokeWidth(px * Metrics.ANALOG_TICK_STROKE_RATIO); strokeCap(RcStrokeCap.Round) }
            for (i in 0 until Metrics.ANALOG_HOUR_MARKS) {
                val angle = i * 2 * PI / Metrics.ANALOG_HOUR_MARKS
                val inner = if (i % Metrics.ANALOG_QUARTER_STEP == 0) Metrics.ANALOG_TICK_INNER_MAJOR else Metrics.ANALOG_TICK_INNER_MINOR
                drawLine(
                    mid + (sin(angle) * mid * inner).toFloat(),
                    mid - (cos(angle) * mid * inner).toFloat(),
                    mid + (sin(angle) * mid * Metrics.ANALOG_TICK_OUTER).toFloat(),
                    mid - (cos(angle) * mid * Metrics.ANALOG_TICK_OUTER).toFloat(),
                )
            }
            val hours = when (node.props["numbers"]) {
                "all" -> 1..Metrics.ANALOG_HOUR_MARKS
                "quarters" -> listOf(
                    Metrics.ANALOG_QUARTER_3,
                    Metrics.ANALOG_QUARTER_6,
                    Metrics.ANALOG_QUARTER_9,
                    Metrics.ANALOG_QUARTER_12,
                )
                else -> emptyList()
            }
            if (hours.any()) {
                val size = px * Metrics.ANALOG_NUMBER_SIZE_RATIO
                paint { color(ink); style(RcPaintStyle.Fill); textSize(size) }
                for (hour in hours) {
                    val angle = hour * 2 * PI / Metrics.ANALOG_HOUR_MARKS
                    drawTextAnchored(
                        doc.literal(hour.toString()),
                        mid + (sin(angle) * mid * Metrics.ANALOG_NUMBER_RADIUS).toFloat(),
                        mid - (cos(angle) * mid * Metrics.ANALOG_NUMBER_RADIUS).toFloat(),
                        0f,
                        0f,
                    )
                }
            }
            // minutes() is the minute of the day and seconds() the second of the hour.
            fun hand(angle: RcFloat, length: Float, width: Float, colour: RcColor) {
                save {
                    rotate(angle, mid.rf, mid.rf)
                    paint { color(colour); style(RcPaintStyle.Stroke); strokeWidth(width); strokeCap(RcStrokeCap.Round) }
                    drawLine(mid, mid, mid, mid - mid * length)
                }
            }
            hand(minutes() * Metrics.ANALOG_HOUR_DEG_PER_MIN, Metrics.ANALOG_HOUR_HAND_LEN, px * Metrics.ANALOG_HOUR_HAND_WIDTH, ink)
            hand(seconds() * Metrics.ANALOG_MINUTE_DEG_PER_SEC, Metrics.ANALOG_MINUTE_HAND_LEN, px * Metrics.ANALOG_MINUTE_HAND_WIDTH, ink)
            if (seconds) hand(continuousSeconds() * Metrics.ANALOG_SECOND_DEG, Metrics.ANALOG_SECOND_HAND_LEN, px * Metrics.ANALOG_SECOND_HAND_WIDTH, accent)
            paint { color(ink); style(RcPaintStyle.Fill) }
            drawCircle(mid, mid, px * Metrics.ANALOG_CENTER_DOT_RATIO)
        }
    }
}

/** A round key with an icon that lights up in the entity's colour while it is on. */
private fun DocContext.iconButtonNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(Metrics.ICON_BUTTON_MIN_DP, Metrics.ICON_BUTTON_MAX_DP) ?: Metrics.ICON_BUTTON_DEFAULT_DP
    val id = node.bind?.entityId
    val domain = id?.substringBefore('.')
    val (bg, ink) = if (id != null) {
        bindEntity(id)
        bindTileColors(node.id, id, accent(node.props["color"], Pastel.forDomain(domain)))
        color(Vars.tile(node.id, Vars.KEY_BG)) to color(Vars.tile(node.id, Vars.KEY_BG_INK))
    } else {
        (node.props["color"]?.let { paint(it) } ?: role(Role.KEY)) to role(Role.INK)
    }
    val name = node.props["icon"]?.takeIf { it.isNotEmpty() } ?: id?.let { states[it]?.attr("icon") } ?: domain?.let { Catalogue.of(it)?.icon } ?: "gesture-tap"
    var base = sized(node, axis)
    if (node.size.width == "wrap" && node.size.height == "wrap") base = base.size(units.px(sizeDp), units.px(sizeDp))
    base = base.clip(rounded(if (node.style.corner > 0f) node.style.corner else sizeDp / 2f))
    if (node.style.background == null) base = base.background(bg)
    scope.Box(styled(node, axis, pages, base), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@iconButtonNode
        val on = node.props["iconOn"]
        if (id != null && on != null) doc.toggleIcon(id, name, on, sizeDp * Metrics.ICON_BUTTON_ICON_RATIO, ink) else doc.icon(name, sizeDp * Metrics.ICON_BUTTON_ICON_RATIO, ink)
    }
}

/** The keys an entity's remote has: a player's transport, a cover's open, stop and close, a vacuum's, a lock's. */
private fun DocContext.keysOf(entityState: EntityState?, domain: String?): List<Pair<String, String>> = when (domain) {
    "media_player" -> listOf("previous" to "skip-previous", "play_pause" to "play-pause", "next" to "skip-next")
    "cover" -> listOf("open" to "arrow-up", "stop" to "stop", "close" to "arrow-down")
    "vacuum" -> listOf("start" to "play", "pause" to "pause", "dock" to "home-import-outline", "locate" to "map-marker-outline")
    "lock" -> listOf("lock" to "lock", "unlock" to "lock-open-variant")
    "alarm_control_panel" -> (entityState?.features() ?: 0).let { features ->
        listOfNotNull(
            ("arm_home" to "shield-home").takeIf { features and Metrics.ALARM_FEAT_ARM_HOME != 0 },
            ("arm_away" to "shield-lock").takeIf { features and Metrics.ALARM_FEAT_ARM_AWAY != 0 },
            ("arm_night" to "shield-moon").takeIf { features and Metrics.ALARM_FEAT_ARM_NIGHT != 0 },
        ) + ("disarm" to "shield-off")
    }
    "light" -> listOf("off" to "lightbulb-off-outline", "on" to "lightbulb-on")
    else -> emptyList()
}

/** A row of the entity's remote keys; a lock asks for a second tap before it opens, as on its tile. */
private fun DocContext.keysNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val domain = id?.substringBefore('.')
    val keys = id?.let { keysOf(states[it], domain) }.orEmpty()
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(Metrics.KEYS_MIN_DP, Metrics.KEYS_MAX_DP) ?: Metrics.KEYS_DEFAULT_DP
    val modifier = styled(node, axis, pages)
    if (id == null || keys.isEmpty()) {
        scope.Box(modifier.size(units.px(sizeDp * Metrics.KEYS_COLS + Metrics.KEYS_GAP_TOTAL_DP), units.px(sizeDp)).clip(rounded(Metrics.KEYS_EMPTY_CORNER_DP)).background(role(Role.TRACK)))
        return
    }
    bindEntity(id)
    val spec = Catalogue.of(id.substringBefore('.'))
    val stretch = node.size.width != "wrap"
    scope.Column(modifier, horizontal = RcHorizontalPositioning.Center) {
        val doc = this@keysNode
        Row(if (stretch) Modifier.fillMaxWidth() else Modifier, horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
            keys.forEachIndexed { i, (verb, icon) ->
                if (i > 0) Spacer(Modifier.width(doc.units.px(Metrics.KEYS_GAP_DP)))
                val ref = ActionRef(node.id, verb)
                if (spec?.predict?.containsKey(verb) == true) doc.predict(ref, id)
                doc.key(ref, icon = icon, heightDp = sizeDp, widthDp = if (stretch) null else sizeDp, round = !stretch, modifier = if (stretch) Modifier.horizontalWeight(1f) else Modifier)
            }
        }
        if (domain == "lock") doc.label(doc.text(Vars.tile(node.id, Vars.KEY_CONFIRM)), Type.CAPTION, doc.role(Role.WARN), Weight.SEMI_BOLD)
    }
}

/** Round swatches that set a light's colour, or its white from warm to cool. */
private fun DocContext.swatchesNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(Metrics.SWATCH_MIN_DP, Metrics.SWATCH_MAX_DP) ?: Metrics.SWATCH_DEFAULT_DP
    val white = node.props["show"] == "white"
    val colours = if (white) CardRenderer.KELVINS.map { CardRenderer.kelvinColor(it) to ActionRef(node.id, "kelvin", it.toString()) }
    else CardRenderer.HUES.map { hue -> java.awt.Color.HSBtoRGB(hue / Metrics.HUE_DEGREES, Metrics.HUE_SATURATION, 1f) to ActionRef(node.id, "hue", hue.toString()) }
    val stretch = node.size.width != "wrap"
    scope.Row(styled(node, axis, pages), horizontal = if (stretch) RcRowHorizontalPositioning.SpaceBetween else RcRowHorizontalPositioning.Start, vertical = RcVerticalPositioning.Center) {
        val doc = this@swatchesNode
        colours.forEachIndexed { i, (argb, ref) ->
            if (i > 0 && !stretch) Spacer(Modifier.width(doc.units.px(Metrics.SWATCH_GAP_DP)))
            Box(doc.tap(Modifier.size(doc.units.px(sizeDp), doc.units.px(sizeDp)).clip(doc.rounded(sizeDp / 2f)).background(doc.fixed(argb or Metrics.OPAQUE_ARGB)), if (node.bind != null) ref else null))
        }
    }
}

/** The digits of an alarm's or a lock's code, kept by the add-on and sent with the next arm or disarm. */
private fun DocContext.keypadNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(Metrics.KEYPAD_MIN_DP, Metrics.KEYPAD_MAX_DP) ?: Metrics.KEYPAD_DEFAULT_DP
    val keyW = sizeDp * Metrics.KEYPAD_KEY_WIDTH_RATIO
    val codeVar = Vars.tile("e:${id ?: node.id}", "code")
    if (id != null) bind(id) { listOf(VarValue.string(codeVar, "")) }
    scope.Column(styled(node, axis, pages), horizontal = RcHorizontalPositioning.Center) {
        val doc = this@keypadNode
        Box(Modifier.height(doc.units.px(Metrics.KEYPAD_DISPLAY_H)), vertical = RcVerticalPositioning.Center) {
            if (id != null) doc.label(doc.text(codeVar), Type.VALUE, doc.role(Role.INK), Weight.MEDIUM, align = RcTextAlign.Center)
        }
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("clear", "0", "back"))) {
            Row(Modifier.padding(0f, 0f, 0f, doc.units.px(Metrics.KEYPAD_ROW_GAP_DP)), horizontal = RcRowHorizontalPositioning.Center) {
                row.forEachIndexed { i, digit ->
                    if (i > 0) Spacer(Modifier.width(doc.units.px(Metrics.KEYPAD_KEY_GAP_DP)))
                    val ref = when (digit) {
                        "clear" -> ActionRef(node.id, "code_clear")
                        "back" -> ActionRef(node.id, "code_back")
                        else -> ActionRef(node.id, "code_digit", digit)
                    }
                    val icon = when (digit) { "clear" -> "close"; "back" -> "backspace-outline"; else -> null }
                    doc.key(ref, text = if (icon == null) digit else null, icon = icon, heightDp = sizeDp, widthDp = keyW)
                }
            }
        }
    }
}
