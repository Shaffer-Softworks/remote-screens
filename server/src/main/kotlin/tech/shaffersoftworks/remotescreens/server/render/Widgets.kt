@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcInteger
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcStrokeCap
import androidx.compose.remote.creation.dsl.RcText
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcTextOverflow
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.onLongClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.rsp
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import tech.shaffersoftworks.remotescreens.shared.ActionRef

/** The host action id every tap uses; what it means is in its metadata ([ActionRef]). */
const val TAP = 1

object Type {
    const val CAPTION = 12f
    const val BODY = 14f
    const val TITLE = 16f
    const val HEADLINE = 22f
    const val VALUE = 28f
    const val DISPLAY = 44f
    const val HUGE = 96f
}

/** Font weights, as CSS numbers them. */
object Weight {
    const val LIGHT = 300f
    const val NORMAL = 400f
    const val MEDIUM = 500f
    const val SEMI_BOLD = 600f
    const val BOLD = 700f
}

/** Icon paths are drawn on the Material 24 × 24 grid. */
private const val ICON_VIEWPORT = 24f
private const val KEY_CORNER_DIVISOR = 2.6f
private const val KEY_PADDING_DP = 12f
private const val KEY_ICON_DP = 20f
private const val KEY_ICON_ONLY_RATIO = 0.5f
private const val KEY_ICON_GAP_DP = 8f
private const val INITIALS_TEXT_RATIO = 0.38f
private const val SPINNER_STROKE_RATIO = 0.1f
private const val SPINNER_INSET_RATIO = 0.1f
private const val SPINNER_SWEEP_DEG = 270f
/** One turn per second: continuousSeconds runs 0–60 over a minute. */
private const val SPINNER_DEG_PER_SEC = 360f

fun DocContext.rounded(dp: Float) = units.px(dp).let { RoundedRectShape(it, it, it, it) }

/**
 * Places [content] at ([xDp], [yDp]) of the parent. `Modifier.offset` only moves the drawing; the player finds what
 * was tapped from the layout, so a placed tap target must be moved by padding instead.
 */
fun DocContext.at(xDp: Float, yDp: Float, content: () -> Unit) {
    scope.Box(Modifier.fillMaxSize().padding(units.px(xDp), units.px(yDp), 0f, 0f)) { content() }
}

fun DocContext.tap(modifier: Modifier, ref: ActionRef?, hold: ActionRef? = null): Modifier {
    var out = modifier
    if (ref != null) {
        val meta = meta(ref)
        out = out.onClick { hostAction(TAP, meta) }
    }
    if (hold != null) {
        val meta = meta(hold)
        out = out.onLongClick { hostAction(TAP, meta) }
    }
    return out
}

fun DocContext.icon(name: String?, sizeDp: Float, color: RcColor, visible: RcInteger? = null) {
    val px = units.px(sizeDp)
    val iconPath = path(name)
    var modifier = Modifier.size(px, px)
    if (visible != null) modifier = modifier.visibility(visible)
    scope.Canvas(modifier) {
        save {
            scale(px / ICON_VIEWPORT, px / ICON_VIEWPORT)
            // Paint is shared for the whole document. The dial's gradient stays installed
            // until cleared, so icons drawn later (nav, modes) fill with it and look grey.
            paint {
                clearShader()
                color(color)
                alpha(1f)
                style(RcPaintStyle.Fill)
            }
            drawPath(iconPath)
        }
    }
}

/**
 * Home Assistant's coloured weather state SVG for [condition] (17×17 viewBox).
 * Falls back to the monochrome MDI glyph when HA has no layered SVG (e.g. `exceptional`).
 */
fun DocContext.weatherStateIcon(
    condition: String,
    sizeDp: Float,
    nightTime: Boolean = false,
    fallbackColor: RcColor = role(Role.INK),
) {
    val pieces = HaWeatherIcons.pieces(condition, nightTime)
    if (pieces == null) {
        icon(Present.weatherIcon(condition), sizeDp, fallbackColor)
        return
    }
    val px = units.px(sizeDp)
    val layers = pieces.map { piece -> path(rawSvg = piece.path) to fixed(HaWeatherIcons.color(piece.layer)) }
    scope.Canvas(Modifier.size(px, px)) {
        save {
            scale(px / HaWeatherIcons.VIEWPORT, px / HaWeatherIcons.VIEWPORT)
            for ((iconPath, color) in layers) {
                paint {
                    clearShader()
                    color(color)
                    alpha(1f)
                    style(RcPaintStyle.Fill)
                }
                drawPath(iconPath)
            }
        }
    }
}

/** An icon that shows [on] when the entity is on and [off] otherwise, both drawn and one hidden. */
fun DocContext.toggleIcon(entityId: String, off: String, on: String?, sizeDp: Float, color: RcColor) {
    if (on == null || on == off) {
        icon(off, sizeDp, color)
        return
    }
    scope.Box(Modifier.size(units.px(sizeDp), units.px(sizeDp))) {
        this@toggleIcon.icon(off, sizeDp, color, int(Vars.entity(entityId, Vars.KEY_OFF)))
        this@toggleIcon.icon(on, sizeDp, color, int(Vars.entity(entityId, Vars.KEY_ON)))
    }
}

/**
 * An indeterminate ring the player turns with its clock (same continuousSeconds as an analog second hand), for
 * waiting on live video and the like.
 */
fun DocContext.spinner(sizeDp: Float, color: RcColor, visible: RcInteger? = null) {
    val px = units.px(sizeDp)
    val mid = px / 2f
    val inset = px * SPINNER_INSET_RATIO
    val stroke = px * SPINNER_STROKE_RATIO
    var modifier = Modifier.size(px, px)
    if (visible != null) modifier = modifier.visibility(visible)
    scope.Canvas(modifier) {
        save {
            rotate(continuousSeconds() * SPINNER_DEG_PER_SEC, mid.rf, mid.rf)
            paint {
                color(color)
                style(RcPaintStyle.Stroke)
                strokeWidth(stroke)
                strokeCap(RcStrokeCap.Round)
            }
            drawArc(inset, inset, px - inset, px - inset, 0f, SPINNER_SWEEP_DEG)
        }
    }
}

fun DocContext.label(
    text: RcText,
    sp: Float,
    color: RcColor,
    weight: Float = Weight.NORMAL,
    lines: Int = 1,
    align: RcTextAlign = RcTextAlign.Start,
    modifier: Modifier = Modifier,
) {
    scope.Text(
        text,
        modifier,
        color = color,
        fontSize = units.sp(sp).rsp,
        fontWeight = weight,
        textAlign = align,
        overflow = RcTextOverflow.Ellipsis,
        maxLines = lines,
    )
}

fun DocContext.label(
    text: String,
    sp: Float,
    color: RcColor,
    weight: Float = Weight.NORMAL,
    lines: Int = 1,
    align: RcTextAlign = RcTextAlign.Start,
    modifier: Modifier = Modifier,
) {
    scope.Text(
        text,
        modifier,
        color = color,
        fontSize = units.sp(sp).rsp,
        fontWeight = weight,
        textAlign = align,
        overflow = RcTextOverflow.Ellipsis,
        maxLines = lines,
    )
}

/** A key: a rounded button with an icon, a word, or both. */
fun DocContext.key(
    ref: ActionRef,
    text: String? = null,
    icon: String? = null,
    bg: RcColor = role(Role.KEY),
    ink: RcColor = role(Role.INK),
    heightDp: Float = 48f,
    widthDp: Float? = null,
    modifier: Modifier = Modifier,
    round: Boolean = false,
) {
    var box = modifier.height(units.px(heightDp))
    box = if (widthDp != null) box.width(units.px(widthDp)) else box
    box = box.clip(
        rounded(if (round) heightDp / 2f else heightDp / KEY_CORNER_DIVISOR),
    ).background(bg)
    box = tap(box, ref)
    val padded = box.padding(units.px(KEY_PADDING_DP), 0f, units.px(KEY_PADDING_DP), 0f)
    scope.Row(
        padded,
        horizontal = RcRowHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        if (icon != null) {
            this@key.icon(
                icon,
                if (text == null) heightDp * KEY_ICON_ONLY_RATIO else KEY_ICON_DP,
                ink,
            )
        }
        if (icon != null && text != null) Spacer(Modifier.width(this@key.units.px(KEY_ICON_GAP_DP)))
        if (text != null) this@key.label(text, Type.BODY, ink, weight = Weight.MEDIUM)
    }
}

/** A circle with a word in it, standing in for a person's photo. */
fun DocContext.initials(name: String, sizeDp: Float, bg: RcColor, ink: RcColor) {
    val letters = name.split(
        ' ',
    ).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    val circle = Modifier.size(
        units.px(sizeDp),
        units.px(sizeDp),
    ).clip(rounded(sizeDp / 2f)).background(bg)
    scope.Box(
        circle,
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        this@initials.label(letters, sizeDp * INITIALS_TEXT_RATIO, ink, weight = Weight.SEMI_BOLD)
    }
}
