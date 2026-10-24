@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.lights

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.theme.Rpi
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.VarValue

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.visibility
import java.awt.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.LightsDialogMetrics as D

private const val KEY_DETAIL_LEVEL = "lightsDetailLevel"
private const val KEY_DETAIL_FILLED = "lightsDetailFilled"
private const val CHANNEL_MAX = 255f
private const val HALF = 0.5f

internal fun detailLevelVar(tileId: String) = Vars.tile(tileId, KEY_DETAIL_LEVEL)

internal fun detailFilledVar(tileId: String) = Vars.tile(tileId, KEY_DETAIL_FILLED)

/** The dialog's brightness fill follows Home Assistant through variables; a tap needs no rebuild. */
internal fun DocContext.bindDetailLevel(tileId: String, entityId: String) {
    bind(entityId) { states ->
        val percent = states[entityId].brightnessPercent()
        listOf(
            VarValue.float(detailLevelVar(tileId), percent / LightsMetrics.PERCENT.toFloat()),
            VarValue.int(detailFilledVar(tileId), if (percent > 0) Vars.VISIBLE else Vars.GONE),
        )
    }
}

/** A tall rounded slider of [stepCount] taps from top to bottom, sending what [refAt] gives each (top first). */
private fun DocContext.verticalTaps(widthPx: Float, heightPx: Float, refAt: (Int) -> ActionRef?) {
    scope.Column(Modifier.size(widthPx, heightPx)) {
        val doc = this@verticalTaps
        for (index in 0 until D.SLIDER_STEPS) {
            Box(doc.tap(Modifier.fillMaxWidth().verticalWeight(1f), refAt(index)))
        }
    }
}

/**
 * RPI's `VerticalBrightnessSlider`: a 72dp wide pill on the warm dark track, filled from the bottom in the light's
 * colour, with a white line on top of the fill.
 */
internal fun DocContext.verticalBrightnessSlider(tileId: String, entityState: EntityState, layout: LightsLayout) {
    val w = units.px(layout.dp(D.SLIDER_W_DP))
    val h = units.px(layout.dp(D.CONTROL_H_DP))
    val line = units.px(layout.dp(D.SLIDER_LINE_DP))
    val level = float(detailLevelVar(tileId))
    val fill = liftedForTrack(entityState.selectedColor())
    val canSet = entityState.supportsBrightness()
    val track = Modifier.size(w, h).clip(rounded(layout.dp(D.SLIDER_CORNER_DP))).background(fixed(Rpi.LightDetailTrack))
    scope.Box(track) {
        val doc = this@verticalBrightnessSlider
        Canvas(Modifier.size(w, h).visibility(doc.int(detailFilledVar(tileId), Vars.GONE))) {
            val fillTop = level * -h + h
            paint {
                clearShader()
                color(doc.fixed(fill))
                alpha(1f)
                style(RcPaintStyle.Fill)
            }
            drawRect(0f.rf, fillTop, w.rf, h.rf)
            paint { color(doc.fixed(Rpi.White)) }
            drawRect(0f.rf, fillTop - line, w.rf, fillTop)
        }
        doc.verticalTaps(w, h) { index ->
            val percent = (D.SLIDER_STEPS - index) * (LightsMetrics.PERCENT / D.SLIDER_STEPS)
            if (canSet) ActionRef("e:${entityState.entityId}", "brightness", "$percent") else null
        }
    }
}

/** RPI keeps a near-black light's fill visible on the dark track by lifting each channel a quarter. */
private fun liftedForTrack(argb: Int): Int {
    val red = argb.red() / CHANNEL_MAX
    val green = argb.green() / CHANNEL_MAX
    val blue = argb.blue() / CHANNEL_MAX
    val luma = D.LUMA_R * red + D.LUMA_G * green + D.LUMA_B * blue
    if (luma >= D.DARK_LUMA) return argb
    fun lift(channel: Float) = ((channel + D.DARK_LIFT).coerceAtMost(1f) * CHANNEL_MAX).roundToInt()
    return rgb(lift(red), lift(green), lift(blue))
}

/** RPI's `VerticalColorTemperatureSlider`: warmest at the top, coolest at the bottom, a white line at the kelvin. */
internal fun DocContext.verticalTemperatureSlider(entityState: EntityState, layout: LightsLayout) {
    val w = units.px(layout.dp(D.SLIDER_W_DP))
    val h = units.px(layout.dp(D.CONTROL_H_DP))
    val line = units.px(layout.dp(D.SLIDER_LINE_DP))
    val range = entityState.kelvinRange()
    val span = (range.last - range.first).coerceAtLeast(1)
    val lineY = (entityState.kelvin() - range.first).toFloat() / span * h
    val canSet = entityState.supportsColorTemp()
    scope.Box(Modifier.size(w, h).clip(rounded(layout.dp(D.SLIDER_CORNER_DP)))) {
        val doc = this@verticalTemperatureSlider
        Canvas(Modifier.size(w, h)) {
            paint {
                style(RcPaintStyle.Fill)
                linearGradient(
                    0f,
                    0f,
                    0f,
                    h,
                    intArrayOf(blackbodyColor(range.first), blackbodyColor(range.last)),
                )
            }
            drawRect(0f, 0f, w, h)
            paint {
                clearShader()
                color(doc.fixed(Rpi.White))
                alpha(1f)
            }
            drawRect(0f, lineY - line / 2f, w, lineY + line / 2f)
        }
        doc.verticalTaps(w, h) { index ->
            val kelvin = (range.first + (index + HALF) / D.SLIDER_STEPS * span).roundToInt()
            if (canSet) ActionRef("e:${entityState.entityId}", "kelvin", "$kelvin") else null
        }
    }
}

/**
 * RPI's `LightDetailColorWheel`: the HSV disc filling the control's height less 8dp each side, a white ring where the
 * light's colour sits, and the eyedropper RPI draws but does not wire. A tap picks the hue and saturation under it.
 */
internal fun DocContext.colorWheel(entityState: EntityState, layout: LightsLayout) {
    val h = units.px(layout.dp(D.CONTROL_H_DP))
    val padH = units.px(layout.dp(D.WHEEL_PAD_H_DP))
    val w = h - 2 * padH
    scope.Box(
        Modifier.fillMaxWidth().height(h),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@colorWheel
        Box(Modifier.size(w, h)) {
            doc.wheelDisc(entityState, w, h, layout)
            doc.wheelTaps(entityState, w, h)
            Box(Modifier.size(w, h), horizontal = RcHorizontalPositioning.End) {
                val buttonPx = doc.units.px(layout.dp(D.EYEDROPPER_DP))
                Box(
                    Modifier.size(buttonPx, buttonPx),
                    horizontal = RcHorizontalPositioning.Center,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    doc.icon("eyedropper", layout.dp(D.EYEDROPPER_ICON_DP), doc.fixed(Rpi.White))
                }
            }
        }
    }
}

private fun DocContext.wheelDisc(entityState: EntityState, w: Float, h: Float, layout: LightsLayout) {
    val cx = w / 2f
    val cy = h / 2f
    val radius = minOf(cx, cy)
    val ring = units.px(layout.dp(D.WHEEL_RING_DP))
    val hsb = entityState.rgbColor()?.let { Color.RGBtoHSB(it.red(), it.green(), it.blue(), null) }
    scope.Canvas(Modifier.size(w, h)) {
        paint {
            style(RcPaintStyle.Fill)
            sweepGradient(cx, cy, Rpi.LightDetailWheelHues)
        }
        drawOval(0f, 0f, w, h)
        paint { radialGradient(cx, cy, radius, Rpi.LightDetailWheelCentre) }
        drawOval(0f, 0f, w, h)
        if (hsb != null) {
            val angle = hsb[0] * 2 * PI
            val x = (cx + hsb[1] * radius * cos(angle)).toFloat()
            val y = (cy + hsb[1] * radius * sin(angle)).toFloat()
            paint {
                clearShader()
                color(Rpi.White)
                alpha(1f)
                strokeWidth(this@wheelDisc.units.px(layout.dp(D.WHEEL_RING_STROKE_DP)))
                style(RcPaintStyle.Stroke)
            }
            drawCircle(x, y, ring)
            paint {
                color(Rpi.Black)
                alpha(D.WHEEL_RING_EDGE_ALPHA / CHANNEL_MAX)
                strokeWidth(this@wheelDisc.units.px(layout.dp(D.WHEEL_RING_EDGE_DP)))
            }
            drawCircle(x, y, ring)
            paint { alpha(1f) }
        }
    }
}

/** A grid of cells over the disc; each inside RPI's tap circle sends the colour at its centre. */
private fun DocContext.wheelTaps(entityState: EntityState, w: Float, h: Float) {
    val canSet = entityState.supportsRgbColor()
    val radius = minOf(w, h) / 2f
    val cellW = w / D.WHEEL_CELLS
    val cellH = h / D.WHEEL_CELLS
    scope.Column(Modifier.size(w, h)) {
        val doc = this@wheelTaps
        for (row in 0 until D.WHEEL_CELLS) {
            Row(Modifier.size(w, cellH)) {
                for (column in 0 until D.WHEEL_CELLS) {
                    val dx = (column + HALF) * cellW - w / 2f
                    val dy = (row + HALF) * cellH - h / 2f
                    val distance = hypot(dx, dy)
                    val ref = if (canSet && distance <= radius) {
                        val hue = ((atan2(dy, dx) / (2 * PI)).toFloat() + 1f) % 1f
                        val argb = Color.HSBtoRGB(hue, (distance / radius).coerceIn(0f, 1f), 1f)
                        ActionRef("e:${entityState.entityId}", "rgb", argb.rgbArg())
                    } else {
                        null
                    }
                    Box(doc.tap(Modifier.size(cellW, cellH), ref))
                }
            }
        }
    }
}
