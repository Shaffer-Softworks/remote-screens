@file:Suppress("RestrictedApi")

package dev.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcInteger
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcText
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcTextOverflow
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.onLongClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.rsp
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.CircleShape
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import dev.remotescreens.shared.ActionRef

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

fun DocContext.rounded(dp: Float) = u.px(dp).let { RoundedRectShape(it, it, it, it) }

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
            scale(px / 24f, px / 24f)
            paint { color(color) }
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
        this@toggleIcon.icon(off, sizeDp, color, int(V.e(entityId, "off")))
        this@toggleIcon.icon(on, sizeDp, color, int(V.e(entityId, "on")))
    }
}

fun DocContext.label(
    text: RcText,
    sp: Float,
    color: RcColor,
    weight: Float = 400f,
    lines: Int = 1,
    align: RcTextAlign = RcTextAlign.Start,
    modifier: Modifier = Modifier,
) {
    scope.Text(text, modifier, color = color, fontSize = u.sp(sp).rsp, fontWeight = weight, textAlign = align, overflow = RcTextOverflow.Ellipsis, maxLines = lines)
}

fun DocContext.label(
    text: String,
    sp: Float,
    color: RcColor,
    weight: Float = 400f,
    lines: Int = 1,
    align: RcTextAlign = RcTextAlign.Start,
    modifier: Modifier = Modifier,
) {
    scope.Text(text, modifier, color = color, fontSize = u.sp(sp).rsp, fontWeight = weight, textAlign = align, overflow = RcTextOverflow.Ellipsis, maxLines = lines)
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
    m = m.clip(if (round) CircleShape() else rounded(heightDp / 2.6f)).background(bg)
    m = tap(m, ref)
    scope.Row(m.padding(u.px(12f), 0f, u.px(12f), 0f), horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        if (icon != null) this@key.icon(icon, if (text == null) heightDp * 0.5f else 20f, ink)
        if (icon != null && text != null) Spacer(Modifier.width(this@key.u.px(8f)))
        if (text != null) this@key.label(text, Type.BODY, ink, weight = 500f)
    }
}

/**
 * A bar from 0 to 1 that follows [level], with [steps] taps across it: a tap on step i sends [refAt] (i + 1).
 * Remote Compose has no drag that reports a value, so a slider is a row of steps.
 */
fun DocContext.levelBar(
    level: RcFloat,
    widthDp: Float,
    heightDp: Float,
    fill: RcColor,
    track: RcColor,
    steps: Int,
    refAt: (Int) -> ActionRef,
) {
    val w = u.px(widthDp)
    val h = u.px(heightDp)
    scope.Box(Modifier.size(w, h).clip(rounded(heightDp / 2f)).background(track)) {
        Box(Modifier.width(level * w).fillMaxHeight().background(fill))
        Row(Modifier.size(w, h)) {
            for (i in 1..steps) Box(this@levelBar.tap(Modifier.horizontalWeight(1f).fillMaxHeight(), refAt(i)))
        }
    }
}

/** A row of choices, the chosen one lit by [bindChoices]. */
fun DocContext.choiceRow(scopeId: String, group: String, choices: List<Pair<String, String>>, refFor: (String) -> ActionRef, heightDp: Float = 44f, icons: Map<String, String> = emptyMap()) {
    scope.Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.Start, vertical = RcVerticalPositioning.Center) {
        choices.forEachIndexed { i, (value, word) ->
            if (i > 0) Spacer(Modifier.width(this@choiceRow.u.px(8f)))
            this@choiceRow.key(
                ref = refFor(value),
                text = if (icons[value] == null) word else null,
                icon = icons[value],
                bg = this@choiceRow.color(V.o(scopeId, group, i, "bg")),
                ink = this@choiceRow.color(V.o(scopeId, group, i, "ink")),
                heightDp = heightDp,
                modifier = Modifier.horizontalWeight(1f),
            )
        }
    }
}

fun DocContext.gap(dp: Float) = scope.Spacer(Modifier.size(u.px(dp), u.px(dp)))

/** A circle with a word in it, standing in for a person's photo. */
fun DocContext.initials(name: String, sizeDp: Float, bg: RcColor, ink: RcColor) {
    val letters = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    scope.Box(Modifier.size(u.px(sizeDp), u.px(sizeDp)).clip(CircleShape()).background(bg), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        this@initials.label(letters, sizeDp * 0.38f, ink, weight = 600f)
    }
}
