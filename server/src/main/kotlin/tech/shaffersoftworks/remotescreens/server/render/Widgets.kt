@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcInteger
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
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

fun DocContext.rounded(dp: Float) = u.px(dp).let { RoundedRectShape(it, it, it, it) }

/**
 * Places [content] at ([xDp], [yDp]) of the parent. `Modifier.offset` only moves the drawing; the player finds what
 * was tapped from the layout, so a placed tap target must be moved by padding instead.
 */
fun DocContext.at(xDp: Float, yDp: Float, content: () -> Unit) {
    scope.Box(Modifier.fillMaxSize().padding(u.px(xDp), u.px(yDp), 0f, 0f)) { content() }
}

fun DocContext.tap(m: Modifier, ref: ActionRef?, hold: ActionRef? = null): Modifier {
    var out = m
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
    val px = u.px(sizeDp)
    val p = path(name)
    var m = Modifier.size(px, px)
    if (visible != null) m = m.visibility(visible)
    scope.Canvas(m) {
        save {
            scale(px / ICON_VIEWPORT, px / ICON_VIEWPORT)
            paint { color(color); style(RcPaintStyle.Fill) }
            drawPath(p)
        }
    }
}

/** An icon that shows [on] when the entity is on and [off] otherwise, both drawn and one hidden. */
fun DocContext.toggleIcon(entityId: String, off: String, on: String?, sizeDp: Float, color: RcColor) {
    if (on == null || on == off) {
        icon(off, sizeDp, color)
        return
    }
    scope.Box(Modifier.size(u.px(sizeDp), u.px(sizeDp))) {
        this@toggleIcon.icon(off, sizeDp, color, int(Vars.e(entityId, "off")))
        this@toggleIcon.icon(on, sizeDp, color, int(Vars.e(entityId, "on")))
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
        fontSize = u.sp(sp).rsp,
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
        fontSize = u.sp(sp).rsp,
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
    var m = modifier.height(u.px(heightDp))
    m = if (widthDp != null) m.width(u.px(widthDp)) else m
    m = m.clip(rounded(if (round) heightDp / 2f else heightDp / KEY_CORNER_DIVISOR)).background(bg)
    m = tap(m, ref)
    val padded = m.padding(u.px(KEY_PADDING_DP), 0f, u.px(KEY_PADDING_DP), 0f)
    scope.Row(padded, horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        if (icon != null) this@key.icon(icon, if (text == null) heightDp * KEY_ICON_ONLY_RATIO else KEY_ICON_DP, ink)
        if (icon != null && text != null) Spacer(Modifier.width(this@key.u.px(KEY_ICON_GAP_DP)))
        if (text != null) this@key.label(text, Type.BODY, ink, weight = Weight.MEDIUM)
    }
}

/** A circle with a word in it, standing in for a person's photo. */
fun DocContext.initials(name: String, sizeDp: Float, bg: RcColor, ink: RcColor) {
    val letters = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    val circle = Modifier.size(u.px(sizeDp), u.px(sizeDp)).clip(rounded(sizeDp / 2f)).background(bg)
    scope.Box(circle, horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        this@initials.label(letters, sizeDp * INITIALS_TEXT_RATIO, ink, weight = Weight.SEMI_BOLD)
    }
}
