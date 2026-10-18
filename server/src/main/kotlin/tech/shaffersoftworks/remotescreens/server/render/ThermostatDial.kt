@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcBorderShape
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcStrokeCap
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


/**
 * The dial: single target (one tinted arc) or dual Heat/Cool (orange low arc + blue high arc with a white gap),
 * knobs at each end, action and target(s) in the middle.
 */
@Suppress("LongMethod")
internal fun DocContext.thermostatDial(tileId: String, entityId: String, diameterDp: Float, dual: Boolean) {
    val strokeDp = diameterDp * ThermostatMetrics.RING_THICKNESS_SHARE
    val knobDp = diameterDp / 2f * ThermostatMetrics.KNOB_RADIUS_SHARE
    val knobRingDp = diameterDp / 2f * ThermostatMetrics.KNOB_RING_SHARE
    val tickGapDp = diameterDp * ThermostatMetrics.TICK_GAP_SHARE
    val sizeDp = diameterDp + 2 * (strokeDp + tickGapDp + knobDp)
    val px = units.px(sizeDp)
    val cx = px / 2f
    val r = units.px(diameterDp) / 2f
    val stroke = units.px(strokeDp)
    val inner = color(Vars.tile(tileId, "dialInner"))
    val outer = color(Vars.tile(tileId, "dialOuter"))
    val tint = color(Vars.tile(tileId, "dial"))
    val heat = role(Role.WARN)
    val cool = role(Role.BLUE)
    val sweep = float(Vars.tile(tileId, "sweep"))
    val sweepLow = float(Vars.tile(tileId, "sweepLow"))
    val sweepHigh = float(Vars.tile(tileId, "sweepHigh"))
    val coolStart = float(Vars.tile(tileId, "coolStart"))
    val coolSweep = float(Vars.tile(tileId, "coolSweep"))
    val ink = role(Role.INK)
    val layoutScale = (diameterDp / ThermostatMetrics.DIAL_BASE_DP).coerceIn(0.4f, 1.5f)
    scope.Box(
        Modifier.size(px, px),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@thermostatDial
        Canvas(Modifier.size(px, px)) {
            paint {
                style(RcPaintStyle.Fill)
                radialGradient(
                    cx,
                    cx,
                    r,
                    intArrayOf(inner.id, outer.id),
                    colorMask = ThermostatMetrics.DIAL_GRADIENT_COLOR_MASK,
                )
            }
            drawCircle(cx, cx, r)
            paint { color(ink); alpha(1f); strokeWidth(stroke); style(RcPaintStyle.Stroke) }
            drawCircle(cx, cx, r)
            paint {
                color(
                    tint,
                ); alpha(
                    ThermostatMetrics.DIAL_TICK_ALPHA,
                ); strokeWidth(doc.units.px(1f)); style(RcPaintStyle.Stroke)
            }
            val tickInner = r + stroke / 2f + doc.units.px(tickGapDp)
            val tickOuter = tickInner + stroke
            for (i in 0 until ThermostatMetrics.TICKS) {
                val a = i * 2 * PI / ThermostatMetrics.TICKS + PI / 2
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                drawLine(
                    cx + tickInner * ca,
                    cx + tickInner * sa,
                    cx + tickOuter * ca,
                    cx + tickOuter * sa,
                )
            }
            val left = (cx - r).rf
            val top = (cx - r).rf
            val right = (cx + r).rf
            val bottom = (cx + r).rf
            if (dual) {
                paint {
                    color(heat)
                    alpha(1f)
                    strokeWidth(stroke)
                    strokeCap(RcStrokeCap.Round)
                    style(RcPaintStyle.Stroke)
                }
                drawArc(left, top, right, bottom, ThermostatMetrics.ARC_START_DEG.rf, sweepLow)
                paint {
                    color(cool)
                    alpha(1f)
                    strokeWidth(stroke)
                    strokeCap(RcStrokeCap.Round)
                    style(RcPaintStyle.Stroke)
                }
                drawArc(left, top, right, bottom, coolStart, coolSweep)
                val knob = doc.units.px(knobDp)
                save {
                    rotate(sweepLow, cx.rf, cx.rf)
                    paint { color(heat); style(RcPaintStyle.Fill) }
                    drawCircle(cx, cx + r, knob)
                    paint { color(
                        ink,
                    ); strokeWidth(doc.units.px(knobRingDp)); style(RcPaintStyle.Stroke) }
                    drawCircle(cx, cx + r, knob)
                }
                save {
                    rotate(sweepHigh, cx.rf, cx.rf)
                    paint { color(cool); style(RcPaintStyle.Fill) }
                    drawCircle(cx, cx + r, knob)
                    paint { color(
                        ink,
                    ); strokeWidth(doc.units.px(knobRingDp)); style(RcPaintStyle.Stroke) }
                    drawCircle(cx, cx + r, knob)
                }
            } else {
                paint {
                    color(tint)
                    alpha(1f)
                    strokeWidth(stroke)
                    strokeCap(RcStrokeCap.Round)
                    style(RcPaintStyle.Stroke)
                }
                drawArc(left, top, right, bottom, ThermostatMetrics.ARC_START_DEG.rf, sweep)
                save {
                    rotate(sweep, cx.rf, cx.rf)
                    val knob = doc.units.px(knobDp)
                    paint { color(tint); style(RcPaintStyle.Fill) }
                    drawCircle(cx, cx + r, knob)
                    paint { color(
                        ink,
                    ); strokeWidth(doc.units.px(knobRingDp)); style(RcPaintStyle.Stroke) }
                    drawCircle(cx, cx + r, knob)
                }
            }
        }
        Column(
            Modifier.size(px, px),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcColumnVerticalPositioning.Center,
        ) {
            doc.label(
                doc.text(Vars.tile(tileId, "action")),
                ThermostatMetrics.ACTION_LABEL_SP * layoutScale,
                ink,
                align = RcTextAlign.Center,
            )
            Spacer(Modifier.height(doc.units.px(ThermostatMetrics.HEADER_GAP_DP * layoutScale)))
            if (dual) {
                Row(vertical = RcVerticalPositioning.Center) {
                    doc.label(
                        doc.text(Vars.tile(tileId, "low")),
                        ThermostatMetrics.TARGET_LABEL_SP * layoutScale,
                        heat,
                        Weight.LIGHT,
                        align = RcTextAlign.Center,
                    )
                    Spacer(
                        Modifier.width(
                            doc.units.px(ThermostatMetrics.DUAL_TEMP_GAP_DP * layoutScale),
                        ),
                    )
                    doc.label(
                        doc.text(Vars.tile(tileId, "high")),
                        ThermostatMetrics.TARGET_LABEL_SP * layoutScale,
                        cool,
                        Weight.LIGHT,
                        align = RcTextAlign.Center,
                    )
                }
            } else {
                doc.label(
                    doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                    ThermostatMetrics.TARGET_LABEL_SP * layoutScale,
                    ink,
                    Weight.LIGHT,
                    align = RcTextAlign.Center,
                )
            }
        }
    }
}

