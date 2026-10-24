@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.lights

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.theme.Rpi
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.VarValue

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcCanvasScope
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.max
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.LightsMetrics as M

private const val KEY_ROW_ON = "lightsOn"
private const val KEY_ROW_OFF = "lightsOff"
private const val KEY_ROW_LEVEL = "lightsLevel"
private const val KEY_ROW_TINT = "lightsTint"

internal fun rowOnVar(entityId: String) = Vars.entity(entityId, KEY_ROW_ON)

internal fun rowOffVar(entityId: String) = Vars.entity(entityId, KEY_ROW_OFF)

/** A device's switch, slider and icon tint follow its state through variables, so taps need no rebuild. */
private fun DocContext.bindRow(entityId: String) {
    bind(entityId) { states ->
        val entityState = states[entityId]
        val isOn = entityState.isRowOn()
        val level = when (entityState?.domain) {
            "cover" -> (entityState.number("current_tilt_position") ?: 0.0) / M.PERCENT
            else -> (entityState?.number("brightness") ?: 0.0) / M.BRIGHTNESS_MAX
        }
        val tint = if (entityState?.domain == "light") entityState.selectedColor() else Rpi.White
        listOf(
            VarValue.int(rowOnVar(entityId), if (isOn) Vars.VISIBLE else Vars.GONE),
            VarValue.int(rowOffVar(entityId), if (isOn) Vars.GONE else Vars.VISIBLE),
            VarValue.float(Vars.entity(entityId, KEY_ROW_LEVEL), level.toFloat().coerceIn(0f, 1f)),
            VarValue.color(Vars.entity(entityId, KEY_ROW_TINT), tint),
        )
    }
}

/**
 * RPI's `LightsDeviceItem` for a light or switch and `BlindDeviceItem` for a cover: the icon and name, a slider
 * (brightness 0–255, or tilt in 20 steps), and a Material switch. A light that can do more opens its dialog from
 * the name.
 */
internal fun DocContext.deviceRow(tileId: String, entityId: String, layout: LightsLayout) {
    val entityState = states[entityId] ?: return
    val domain = entityState.domain
    structural(entityId) { current ->
        listOf(
            current?.name,
            current?.rowIcon(),
            current?.hasLightSlider(),
            current?.supportsLightDetail(),
        ).joinToString("|")
    }
    bindRow(entityId)
    val hasSlider = domain == "cover" || (domain == "light" && entityState.hasLightSlider())
    val detailRef = if (domain == "light" && entityState.supportsLightDetail()) {
        ActionRef(tileId, "lights_detail", entityId)
    } else {
        null
    }
    val padH = units.px(layout.dp(M.ROW_PAD_H_DP))
    scope.Column(Modifier.fillMaxWidth().padding(0f, 0f, 0f, units.px(layout.dp(M.ROW_BOTTOM_DP)))) {
        val doc = this@deviceRow
        Row(Modifier.padding(padH, doc.units.px(layout.dp(M.ROW_HEADER_TOP_DP)), padH, 0f)) {
            Row(doc.tap(Modifier, detailRef), vertical = RcVerticalPositioning.Center) {
                doc.icon(
                    entityState.rowIcon(),
                    layout.dp(M.ROW_ICON_DP),
                    doc.color(Vars.entity(entityId, KEY_ROW_TINT), Rpi.White),
                )
                Spacer(Modifier.width(doc.units.px(layout.dp(M.ROW_ICON_GAP_DP))))
                doc.label(entityState.name, layout.sp(M.ROW_TITLE_SP), doc.role(Role.INK))
            }
        }
        if (hasSlider) {
            val sliderW = layout.devicesW - layout.dp(M.ROW_PAD_H_DP + M.SLIDER_END_DP)
            Box(
                Modifier.padding(
                    padH,
                    doc.units.px(layout.dp(M.SLIDER_TOP_DP)),
                    doc.units.px(layout.dp(M.SLIDER_END_DP)),
                    0f,
                ),
            ) {
                doc.rowSlider(entityId, sliderW, layout)
            }
        }
        Box(Modifier.padding(padH, doc.units.px(layout.dp(M.SWITCH_TOP_DP)), padH, 0f)) {
            doc.rowSwitch(entityState, layout)
        }
    }
}

/**
 * RPI's Material 3 `Slider`: a 16dp track, teal up to a 4dp white thumb and black after it. Remote Compose has no
 * drag that reports a value, so the slider is a row of steps, as [levelBar] is.
 */
private fun DocContext.rowSlider(entityId: String, widthDp: Float, layout: LightsLayout) {
    val isCover = entityId.substringBefore('.') == "cover"
    val steps = if (isCover) M.TILT_STEPS else M.BRIGHTNESS_STEPS
    val verb = if (isCover) "tilt" else "brightness"
    val w = units.px(widthDp)
    val h = units.px(layout.dp(M.SLIDER_H_DP))
    val level = float(Vars.entity(entityId, KEY_ROW_LEVEL))
    scope.Box(Modifier.size(w, h)) {
        val doc = this@rowSlider
        Canvas(Modifier.size(w, h)) {
            val track = doc.units.px(layout.dp(M.SLIDER_TRACK_DP))
            val thumbW = doc.units.px(layout.dp(M.SLIDER_THUMB_W_DP))
            val gap = doc.units.px(layout.dp(M.SLIDER_THUMB_GAP_DP))
            val inner = doc.units.px(layout.dp(M.SLIDER_INNER_CORNER_DP))
            val top = (h - track) / 2f
            val bottom = top + track
            val thumbX = level * (w - thumbW) + thumbW / 2f
            val activeEnd = max(thumbX - (gap + thumbW / 2f), 0f)
            val inactiveStart = thumbX + (gap + thumbW / 2f)
            sliderSegment(0f.rf, activeEnd, top, bottom, track / 2f, inner, doc.fixed(Rpi.LightsOn))
            sliderSegment(inactiveStart, w.rf, top, bottom, inner, track / 2f, doc.role(Role.TRACK))
            paint { color(doc.fixed(Rpi.LightsOn)) }
            val stop = doc.units.px(layout.dp(M.SLIDER_STOP_DP))
            drawCircle(w - track / 2f, h / 2f, stop / 2f)
            paint { color(doc.fixed(Rpi.White)) }
            drawRoundRect(thumbX - thumbW / 2f, 0f.rf, thumbX + thumbW / 2f, h.rf, (thumbW / 2f).rf, (thumbW / 2f).rf)
        }
        Row(Modifier.size(w, h)) {
            for (step in 1..steps) {
                val value = step * M.PERCENT / steps
                Box(doc.tap(Modifier.horizontalWeight(1f).fillMaxHeight(), ActionRef("e:$entityId", verb, "$value")))
            }
        }
    }
}

/** A track segment with [startCorner] at its left end and [endCorner] at its right, as Material rounds them. */
@Suppress("LongParameterList")
private fun RcCanvasScope.sliderSegment(
    left: RcFloat,
    right: RcFloat,
    top: Float,
    bottom: Float,
    startCorner: Float,
    endCorner: Float,
    fill: RcColor,
) {
    paint {
        clearShader()
        color(fill)
        alpha(1f)
        style(RcPaintStyle.Fill)
    }
    val corner = maxOf(startCorner, endCorner)
    drawRoundRect(left, top.rf, right, bottom.rf, corner.rf, corner.rf)
    val small = minOf(startCorner, endCorner)
    val squareLeft = if (startCorner < endCorner) left else max(left, right - corner)
    val squareRight = if (startCorner < endCorner) (left + corner).min(right) else right
    drawRoundRect(squareLeft, top.rf, squareRight, bottom.rf, small.rf, small.rf)
}

/**
 * RPI's Material 3 `Switch`: a black 52 by 32 track; on, a 24dp teal thumb at the end, off, a 16dp white thumb at
 * the start inside a grey edge. Two stacked tap targets send on or off, whichever the state calls for.
 */
private fun DocContext.rowSwitch(entityState: EntityState, layout: LightsLayout) {
    val entityId = entityState.entityId
    val (onVerb, offVerb) = if (entityState.domain == "cover") "open_tilt" to "close_tilt" else "on" to "off"
    val onRef = ActionRef("e:$entityId", onVerb)
    val offRef = ActionRef("e:$entityId", offVerb)
    Catalogue.of(entityState.domain)?.predict?.let { predictions ->
        if (onVerb in predictions) predict(onRef, entityId)
        if (offVerb in predictions) predict(offRef, entityId)
    }
    val w = units.px(layout.dp(M.SWITCH_W_DP))
    val trackH = layout.dp(M.SWITCH_H_DP)
    val touchH = units.px(layout.dp(M.SWITCH_TOUCH_H_DP))
    scope.Box(Modifier.size(w, touchH), vertical = RcVerticalPositioning.Center) {
        val doc = this@rowSwitch
        doc.switchTrack(entityId, isOn = true, trackH, layout, offRef)
        doc.switchTrack(entityId, isOn = false, trackH, layout, onRef)
    }
}

private fun DocContext.switchTrack(
    entityId: String,
    isOn: Boolean,
    trackH: Float,
    layout: LightsLayout,
    ref: ActionRef,
) {
    val visible = int(if (isOn) rowOnVar(entityId) else rowOffVar(entityId), Vars.GONE)
    val border = if (isOn) 0f else layout.dp(M.SWITCH_BORDER_DP)
    val thumb = layout.dp(if (isOn) M.SWITCH_THUMB_ON_DP else M.SWITCH_THUMB_OFF_DP)
    val thumbPad = units.px((trackH - 2 * border - thumb) / 2f)
    scope.Box(
        tap(
            Modifier.visibility(visible).size(units.px(layout.dp(M.SWITCH_W_DP)), units.px(trackH))
                .clip(rounded(trackH / 2f))
                .background(role(Role.INK_DIM))
                .padding(units.px(border)),
            ref,
        ),
    ) {
        val doc = this@switchTrack
        Box(
            Modifier.fillMaxSize().clip(doc.rounded(trackH / 2f - border)).background(doc.role(Role.TRACK))
                .padding(thumbPad),
            horizontal = if (isOn) RcHorizontalPositioning.End else RcHorizontalPositioning.Start,
            vertical = RcVerticalPositioning.Center,
        ) {
            Box(
                Modifier.size(doc.units.px(thumb), doc.units.px(thumb)).clip(doc.rounded(thumb / 2f))
                    .background(doc.fixed(if (isOn) Rpi.LightsOn else Rpi.White)),
            )
        }
    }
}
