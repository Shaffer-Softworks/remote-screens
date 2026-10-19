@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.render.MarkdownLines
import tech.shaffersoftworks.remotescreens.server.render.Type
import tech.shaffersoftworks.remotescreens.server.render.blockNatural

import tech.shaffersoftworks.remotescreens.shared.Node

/** Natural sizes, chrome metrics and drawing ratios for free-form leaves and widgets. */
internal object Metrics {
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
    const val DIAL_DEFAULT_DP = 160f
    const val DIAL_MIN_DP = 80f
    const val DIAL_MAX_DP = 400f
    const val DIAL_STROKE_RATIO = 0.1f
    const val DIAL_VALUE_SIZE_RATIO = 0.18f
    const val COUNTDOWN_NATURAL_W = 160f
    const val COUNTDOWN_NATURAL_H = 48f
    const val QR_DEFAULT_DP = 160f
    const val QR_MIN_DP = 64f
    const val QR_MAX_DP = 400f
    const val MARKDOWN_NATURAL_W = 280f
    const val MARKDOWN_LINE_H = 22f
    const val TABS_STRIP_H = 36f
    const val TABS_UNDERLINE_H = 3f
    const val TABS_UNDERLINE_GAP_DP = 4f
    const val TABS_CHAR_W_DP = 9f
    const val TABS_UNDERLINE_MIN_W = 28f
    const val TABS_PIPE_GAP_DP = 12f
    const val TABS_NATURAL_W = 320f
    const val TABS_NATURAL_H = 200f
    const val QR_CORNER_DP = 12f
    const val QR_PAD_DP = 8f
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
internal enum class Axis { ROW, COLUMN, BOX }

/** A component's size in dp, as near as the server can tell before the player measures it. */
internal data class Dims(val w: Float, val h: Float)

/** How sizes are written in a layout: `wrap`, `fill`, `dp:N` or `weight:N`. */
internal sealed interface Spec {
    data object Wrap : Spec
    data object Fill : Spec
    data class Dp(val value: Float) : Spec
    data class Weight(val value: Float) : Spec

    companion object {
        fun of(spec: String): Spec = when {
            spec == "fill" -> Fill
            spec.startsWith(
                "dp:",
            ) -> spec.removePrefix("dp:").toFloatOrNull()?.let { Dp(it) } ?: Wrap
            spec.startsWith(
                "weight:",
            ) -> spec.removePrefix(
                "weight:",
            ).toFloatOrNull()?.takeIf { it > 0f }?.let { Weight(it) } ?: Wrap
            else -> Wrap
        }
    }
}

/** What a wrapped leaf takes when nothing else says, so a weight or a card has something to share out. */
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun Node.natural(): Dims = when (type) {
    "text" -> Dims(
        Metrics.TEXT_NATURAL_W,
        (props["fontSize"]?.toFloatOrNull() ?: Type.TITLE) * Metrics.TEXT_LINE_HEIGHT,
    )
    "icon" -> (props["size"]?.toFloatOrNull() ?: Metrics.ICON_DEFAULT_DP).let { Dims(it, it) }
    "button" -> Dims(Metrics.BUTTON_NATURAL_W, Metrics.BUTTON_NATURAL_H)
    "spacer" -> Dims(Metrics.SPACER_DEFAULT_DP, Metrics.SPACER_DEFAULT_DP)
    "divider" -> Dims(Metrics.DIVIDER_THICKNESS_DP, Metrics.DIVIDER_THICKNESS_DP)
    "slider" -> Dims(Metrics.SLIDER_NATURAL_W, Metrics.SLIDER_NATURAL_H)
    "image", "tile" -> Dims(Metrics.IMAGE_NATURAL_W, Metrics.IMAGE_NATURAL_H)
    "clock" -> (props["fontSize"]?.toFloatOrNull() ?: Type.DISPLAY).let { sp ->
        Dims(
            maxOf(Metrics.CLOCK_MIN_W, sp * Metrics.CLOCK_WIDTH_PER_SP),
            sp * Metrics.TEXT_LINE_HEIGHT + if (props["show"] == "both") Metrics.CLOCK_DATE_EXTRA_H else 0f,
        )
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
    "iconbutton" -> (props["size"]?.toFloatOrNull() ?: Metrics.ICON_BUTTON_DEFAULT_DP).let { Dims(
        it,
        it,
    ) }
    "keys" -> (props["size"]?.toFloatOrNull() ?: Metrics.KEYS_DEFAULT_DP).let { Dims(
        it * Metrics.KEYS_COLS + Metrics.KEYS_GAP_TOTAL_DP,
        it,
    ) }
    "swatches" -> (props["size"]?.toFloatOrNull() ?: Metrics.SWATCH_DEFAULT_DP).let { sizeSpec ->
        val count = if (props["show"] == "white") Metrics.SWATCH_WHITE_COUNT else Metrics.SWATCH_HUE_COUNT
        Dims(sizeSpec * count + Metrics.SWATCH_GAP_DP * (count - 1), sizeSpec)
    }
    "keypad" -> (props["size"]?.toFloatOrNull() ?: Metrics.KEYPAD_DEFAULT_DP).let {
        Dims(
            it * Metrics.KEYPAD_KEY_WIDTH_RATIO * Metrics.KEYPAD_COLS + Metrics.KEYPAD_WIDTH_PAD_DP,
            Metrics.KEYPAD_DEFAULT_DP + Metrics.KEYPAD_ROWS * (it + Metrics.KEYPAD_ROW_GAP_DP),
        )
    }
    "dial" -> (props["size"]?.toFloatOrNull() ?: Metrics.DIAL_DEFAULT_DP).let {
        Dims(it, it + Metrics.STEPPER_KEY_DP + Metrics.KEYS_GAP_DP)
    }
    "countdown" -> Dims(Metrics.COUNTDOWN_NATURAL_W, Metrics.COUNTDOWN_NATURAL_H)
    "qr" -> (props["size"]?.toFloatOrNull() ?: Metrics.QR_DEFAULT_DP).let { Dims(it, it) }
    "timerkeys", "todolist", "events", "mediabar", "forecast", "presence", "led", "thermostat",
    "weather", "nextevent",
    ->
        blockNatural(this) ?: Dims(Metrics.TEXT_NATURAL_W, Metrics.TEXT_NATURAL_W)
    "markdown" -> Dims(
        Metrics.MARKDOWN_NATURAL_W,
        Metrics.MARKDOWN_LINE_H * MarkdownLines.parse(
            props["text"].orEmpty(),
        ).size.coerceAtLeast(1),
    )
    "tabs" -> Dims(Metrics.TABS_NATURAL_W, Metrics.TABS_NATURAL_H)
    else -> Dims(0f, 0f)
}

/** About the size [node]'s content takes: a leaf's natural size, a container's children laid end to end, plus padding. */
@Suppress("CyclomaticComplexMethod")
internal fun content(node: Node): Dims {
    fun side(spec: String, natural: Float) = (Spec.of(spec) as? Spec.Dp)?.value ?: natural
    if (node.type !in setOf("column", "row", "box", "flow", "tabs")) {
        val nat = node.natural()
        val lines = if (node.type == "text") {
            (node.props["lines"]?.toFloatOrNull() ?: 1f).coerceAtLeast(
                1f,
            )
        } else {
            1f
        }
        return Dims(side(node.size.width, nat.w), side(node.size.height, nat.h * lines))
    }
    val children = node.children.filter { !it.hidden }.map(::content)
    val gap = (node.props["gap"]?.toFloatOrNull() ?: 0f) * (children.size - 1).coerceAtLeast(0)
    val inner = when (node.type) {
        "row" -> Dims(
            children.sumOf { it.w.toDouble() }.toFloat() + gap,
            children.maxOfOrNull { it.h } ?: 0f,
        )
        "column" -> Dims(
            children.maxOfOrNull { it.w } ?: 0f,
            children.sumOf { it.h.toDouble() }.toFloat() + gap,
        )
        "tabs" -> Dims(
            children.maxOfOrNull { it.w } ?: Metrics.TABS_NATURAL_W,
            (children.maxOfOrNull { it.h } ?: 0f) + Metrics.TABS_STRIP_H + gap,
        )
        else -> Dims(children.maxOfOrNull { it.w } ?: 0f, children.maxOfOrNull { it.h } ?: 0f)
    }
    return Dims(
        side(node.size.width, inner.w + node.style.padX),
        side(node.size.height, inner.h + node.style.padY),
    )
}

internal fun fills(spec: String) = spec == "fill" || spec.startsWith("weight:")
