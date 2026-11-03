@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.widgets

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.VarValue
import kotlin.math.roundToInt

private const val CHOICE_GAP_DP = 8f
private const val CHOICE_HEIGHT_DP = 44f
private const val CHOICE_ICON_SHARE = 0.45f
private const val CHOICE_ICON_TEXT_GAP_DIVISOR = 2f
private const val SEG_EDGE_DP = 1f
private const val SEG_LEVEL_GAP_DP = 4f
private const val SEG_LEVEL_CORNER_DP = 3f
private const val SEG_CHOICE_CORNER_DIVISOR = 2f
/** Knob diameter as a share of the track height; smaller than the pill so it reads as a thumb, not an end-cap. */
private const val LEVEL_KNOB_SHARE = 0.9f

/**
 * A bar from 0 to 1 that follows [level], with [steps] taps across it: a tap on step i sends [refAt] (i + 1).
 * Remote Compose has no drag that reports a value, so a slider is a row of steps. The fill is painted: a width
 * bound to a variable is measured once and does not follow it. A round white knob sits on the active fill (its
 * right edge at the value) so the bar reads as a slider. [borderDp] strokes the pill when greater than 0.
 */
fun DocContext.levelBar(
    level: RcFloat,
    widthDp: Float,
    heightDp: Float,
    fill: RcColor,
    track: RcColor,
    steps: Int,
    borderDp: Float = 0f,
    border: RcColor? = null,
    refAt: (Int) -> ActionRef,
) {
    val widthPx = units.px(widthDp)
    val heightPx = units.px(heightDp)
    val knob = role(Role.INK)
    val strokeColor = border ?: role(Role.DIVIDER)
    val stroke = if (borderDp > 0f) units.px(borderDp) else 0f
    val thumb = heightPx * LEVEL_KNOB_SHARE
    val thumbPad = (heightPx - thumb) / 2f
    val barRadius = heightPx / 2f
    scope.Box(Modifier.size(widthPx, heightPx).clip(rounded(heightDp / 2f)).background(track)) {
        Canvas(Modifier.size(widthPx, heightPx)) {
            // Knob travels in-bounds; fill tip peeks past its right edge by the vertical inset so the
            // stadium end reads past the thumb (knob sits left of the tip).
            val thumbLeft = level * (widthPx - thumb - thumbPad)
            val thumbRight = thumbLeft + thumb
            val tip = thumbRight + thumbPad
            paint { color(fill); style(RcPaintStyle.Fill) }
            drawRoundRect(
                left = 0f.rf,
                top = 0f.rf,
                right = tip,
                bottom = heightPx.rf,
                radiusX = barRadius.rf,
                radiusY = barRadius.rf,
            )
            paint { color(knob); style(RcPaintStyle.Fill) }
            drawRoundRect(
                left = thumbLeft,
                top = thumbPad.rf,
                right = thumbRight,
                bottom = (heightPx - thumbPad).rf,
                radiusX = (thumb / 2f).rf,
                radiusY = (thumb / 2f).rf,
            )
            if (stroke > 0f) {
                paint {
                    color(strokeColor)
                    style(RcPaintStyle.Stroke)
                    strokeWidth(stroke)
                }
                val inset = stroke / 2f
                drawRoundRect(
                    left = inset.rf,
                    top = inset.rf,
                    right = (widthPx - inset).rf,
                    bottom = (heightPx - inset).rf,
                    radiusX = (barRadius - inset).coerceAtLeast(0f).rf,
                    radiusY = (barRadius - inset).coerceAtLeast(0f).rf,
                )
            }
        }
        Row(Modifier.size(widthPx, heightPx)) {
            for (i in 1..steps) Box(
                this@levelBar.tap(Modifier.horizontalWeight(1f).fillMaxHeight(), refAt(i)),
            )
        }
    }
}

/**
 * Discrete segments for detail cards (humidifier / fan mockups): lit segments use [fillRole], the rest [trackRole].
 * [levelOf] is read on each entity bind so predictions light the right count before Home Assistant answers.
 */
fun DocContext.segmentedLevelBar(
    entityId: String,
    levelKey: String,
    widthDp: Float,
    heightDp: Float,
    steps: Int,
    levelOf: (Map<String, EntityState>) -> Float,
    fillRole: Role = Role.INK,
    trackRole: Role = Role.KEY,
    refAt: (Int) -> ActionRef,
) {
    val group = "seg.$levelKey"
    for (index in 0 until steps) {
        color(Vars.option(scope = entityId, group = group, index = index, key = Vars.KEY_BG), trackRole.argb)
    }
    bind(entityId) { states ->
        val lit = (levelOf(states).coerceIn(0f, 1f) * steps).roundToInt().coerceIn(0, steps)
        (0 until steps).map { index ->
            VarValue.color(
                Vars.option(scope = entityId, group = group, index = index, key = Vars.KEY_BG),
                (if (index < lit) fillRole else trackRole).argb,
            )
        }
    }
    val gapPx = units.px(SEG_LEVEL_GAP_DP)
    val segW = ((widthDp - SEG_LEVEL_GAP_DP * (steps - 1)) / steps).coerceAtLeast(1f)
    val heightPx = units.px(heightDp)
    scope.Row(Modifier.size(units.px(widthDp), heightPx), vertical = RcVerticalPositioning.Center) {
        val doc = this@segmentedLevelBar
        for (index in 0 until steps) {
            if (index > 0) Spacer(Modifier.width(gapPx))
            Box(
                doc.tap(
                    Modifier.size(doc.units.px(segW), heightPx)
                        .clip(doc.rounded(SEG_LEVEL_CORNER_DP))
                        .background(
                            doc.color(Vars.option(scope = entityId, group = group, index = index, key = Vars.KEY_BG)),
                        ),
                    refAt(index + 1),
                ),
            )
        }
    }
}

/**
 * Connected segmented choices (mode / oscillate / preset): the chosen cell is teal; ink stays page colour via
 * [bindChoices].
 */
fun DocContext.choiceRow(
    scopeId: String,
    group: String,
    choices: List<Pair<String, String>>,
    refFor: (String) -> ActionRef,
    heightDp: Float = CHOICE_HEIGHT_DP,
    icons: Map<String, String> = emptyMap(),
) {
    val corner = heightDp / SEG_CHOICE_CORNER_DIVISOR
    val edge = units.px(SEG_EDGE_DP)
    scope.Row(
        Modifier.fillMaxWidth().height(units.px(heightDp))
            .clip(rounded(corner)).background(role(Role.INK_DIM)).padding(edge),
        horizontal = RcRowHorizontalPositioning.Start,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@choiceRow
        choices.forEachIndexed { index, (value, word) ->
            if (index > 0) Spacer(Modifier.width(edge))
            val cell = Modifier.horizontalWeight(1f).fillMaxHeight()
                .background(doc.color(Vars.option(scope = scopeId, group = group, index = index, key = Vars.KEY_BG)))
            Row(
                doc.tap(cell, refFor(value)),
                horizontal = RcRowHorizontalPositioning.Center,
                vertical = RcVerticalPositioning.Center,
            ) {
                val ink = doc.color(Vars.option(scope = scopeId, group = group, index = index, key = Vars.KEY_INK))
                val icon = icons[value]
                if (icon != null) {
                    doc.icon(icon, heightDp * CHOICE_ICON_SHARE, ink)
                    if (word.isNotEmpty()) {
                        Spacer(
                            Modifier.width(
                                doc.units.px(CHOICE_GAP_DP / CHOICE_ICON_TEXT_GAP_DIVISOR),
                            ),
                        )
                    }
                }
                if (icon == null || word.isNotEmpty()) {
                    doc.label(
                        text = word,
                        sp = Type.BODY,
                        color = ink,
                        weight = Weight.MEDIUM,
                        align = RcTextAlign.Center,
                    )
                }
            }
        }
    }
}

fun DocContext.gap(dp: Float) = scope.Spacer(Modifier.size(units.px(dp), units.px(dp)))
