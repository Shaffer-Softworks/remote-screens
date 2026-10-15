@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcStrokeCap
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The size the RPI thermostat page was drawn for; the page scales from it. */
private const val BASE_W = 1280f
private const val BASE_H = 650f

/** Tick marks around the dial, as many as the RPI dial has steps. */
private const val TICKS = 100

/** Small labels stop shrinking here so they stay readable on 7" panels. */
private fun small(base: Float, k: Float) = maxOf(base * k, 12f)

/** The modes in the order the RPI page lists them (Auto, Heat, Cool, Off); others follow. */
private val MODE_ORDER = listOf("auto", "heat_cool", "heat", "cool", "off")

/**
 * The RPI thermostat page: the current temperature and humidity either side of a dial that shows the target, in
 * orange while heating and blue while cooling, the fan modes at the top right and a row of modes along the bottom.
 * Remote Compose cannot report a
 * drag, so the target is set with the keys beside the dial.
 */
fun DocContext.thermostatPanel(tile: Tile, box: TileBox) {
    val id = tile.entityId ?: return
    val e = states[id]
    val step = e?.number("target_temp_step") ?: if (ha.temperatureUnit.contains("F")) 1.0 else 0.5
    val modes = e?.list("hvac_modes").orEmpty().sortedBy { MODE_ORDER.indexOf(it).let { i -> if (i < 0) MODE_ORDER.size else i } }.take(5)
    val humid = e?.number("current_humidity") != null
    val fans = e?.list("fan_modes").orEmpty().sortedBy { if (it == "auto") 0 else 1 }.take(4)
    structural(id) { s ->
        listOf(s?.list("hvac_modes")?.joinToString() ?: "", s?.list("fan_modes")?.joinToString() ?: "", (s?.number("current_humidity") != null).toString()).joinToString("|")
    }
    predict(ActionRef(tile.id, "temperature", "+$step"), id)
    predict(ActionRef(tile.id, "temperature", "-$step"), id)
    for (m in modes) predict(ActionRef(tile.id, "mode", m), id)
    for (m in fans) predict(ActionRef(tile.id, "fan", m), id)
    bindThermostat(tile.id, id, modes, fans)

    val k = minOf(box.w / BASE_W, box.h / BASE_H).coerceIn(0.4f, 1.5f)
    val ink = role(Role.INK)
    val accent = role(Role.ACCENT_DARK)

    scope.Column(Modifier.fillMaxSize().background(role(Role.PAGE))) {
        val c = this@thermostatPanel
        Row(Modifier.fillMaxWidth().padding(c.u.px(20f * k), c.u.px(28f * k), c.u.px(20f * k), 0f), vertical = RcVerticalPositioning.Top) {
            Column(Modifier.horizontalWeight(1f)) {
                Box(Modifier.size(c.u.px(100f * k), c.u.px(10f * k)).background(accent))
                Spacer(Modifier.height(c.u.px(10f * k)))
                c.label(c.i18n.t("ui.adjust_temperature").uppercase(c.i18n.locale), small(15f, k), ink)
            }
            if (fans.isNotEmpty()) c.fanModes(tile.id, fans, k)
        }
        Row(Modifier.fillMaxWidth().verticalWeight(1f), vertical = RcVerticalPositioning.Center) {
            c.reading(c.i18n.t("ui.current_temp"), Vars.t(tile.id, "current"), k, Modifier.horizontalWeight(1f))
            c.key(ActionRef(tile.id, "temperature", "-$step"), icon = "minus", heightDp = 64f * k, widthDp = 64f * k, round = true)
            Spacer(Modifier.width(c.u.px(24f * k)))
            c.dial(tile.id, id, minOf(360f * k, box.h * 0.55f))
            Spacer(Modifier.width(c.u.px(24f * k)))
            c.key(ActionRef(tile.id, "temperature", "+$step"), icon = "plus", heightDp = 64f * k, widthDp = 64f * k, round = true)
            if (humid) c.reading(c.i18n.t("ui.current_humidity"), Vars.t(tile.id, "humidity"), k, Modifier.horizontalWeight(1f))
            else Spacer(Modifier.horizontalWeight(1f))
        }
        if (modes.isNotEmpty()) {
            val segW = minOf(200f * k, (box.w - 40f) / modes.size)
            val segH = 50f * k
            Box(Modifier.fillMaxWidth().padding(0f, 0f, 0f, c.u.px(20f * k)), horizontal = RcHorizontalPositioning.Center) {
                Row(Modifier.clip(c.rounded(segH / 2f)).background(c.role(Role.INK_DIM)).padding(c.u.px(1f))) {
                    modes.forEachIndexed { i, m ->
                        if (i > 0) Spacer(Modifier.width(c.u.px(1f)))
                        val bg = c.color(Vars.o(tile.id, "seg", i, "bg"))
                        Row(c.tap(Modifier.size(c.u.px(segW), c.u.px(segH - 2f)).background(bg), ActionRef(tile.id, "mode", m)),
                            horizontal = androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                            c.icon(MODE_ICONS[m] ?: "thermostat", 22f * k, ink)
                            Spacer(Modifier.width(c.u.px(8f * k)))
                            c.label(c.i18n.state("climate", m), small(20f, k), ink)
                        }
                    }
                }
            }
        }
    }
}

/** "Fan" and a button per fan mode (Auto, On, …), the current one white with dark words as the RPI page drew it. */
private fun DocContext.fanModes(tileId: String, fans: List<String>, k: Float) {
    scope.Row(vertical = RcVerticalPositioning.Center) {
        val c = this@fanModes
        c.label(c.i18n.t("ui.fan"), small(25f, k), c.role(Role.INK))
        fans.forEachIndexed { i, m ->
            Spacer(Modifier.width(c.u.px(10f * k)))
            Box(c.tap(Modifier.size(c.u.px(90f * k), c.u.px(50f * k)).clip(c.rounded(8f * k)).background(c.color(Vars.o(tileId, "fan", i, "bg"))), ActionRef(tileId, "fan", m)),
                horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                c.label(c.fanWord(m), small(16f, k), c.color(Vars.o(tileId, "fan", i, "ink")), Weight.MEDIUM)
            }
        }
    }
}

private fun DocContext.fanWord(mode: String): String =
    i18n.t("state.$mode").takeUnless { it == "state.$mode" } ?: mode.replace('_', ' ').replaceFirstChar { it.titlecase(i18n.locale) }

/** "CURRENT TEMP" in teal over the value, large. */
private fun DocContext.reading(caption: String, varName: String, k: Float, modifier: Modifier) {
    scope.Column(modifier, horizontal = RcHorizontalPositioning.Center) {
        val c = this@reading
        c.label(caption.uppercase(c.i18n.locale), small(25f, k), c.role(Role.ACCENT_DARK), align = RcTextAlign.Center)
        Spacer(Modifier.height(c.u.px(20f * k)))
        c.label(c.text(varName), 70f * k, c.role(Role.INK), Weight.LIGHT, align = RcTextAlign.Center)
    }
}

/**
 * The RPI dial: a tinted disc in a white ring, an arc from the bottom round to the target with a knob at its end,
 * and tick marks outside; what the thermostat is doing and the target in the middle.
 */
private fun DocContext.dial(tileId: String, entityId: String, diameterDp: Float) {
    val ring = diameterDp / 25f
    val sizeDp = diameterDp + 2 * (ring + 8f)
    val px = u.px(sizeDp)
    val cx = px / 2f
    val r = u.px(diameterDp) / 2f
    val t = u.px(ring)
    val tint = color(Vars.t(tileId, "dial"))
    val sweep = float(Vars.t(tileId, "sweep"))
    val ink = role(Role.INK)
    scope.Box(Modifier.size(px, px), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val c = this@dial
        Canvas(Modifier.size(px, px)) {
            paint { color(tint); alpha(0.3f); style(RcPaintStyle.Fill) }
            drawCircle(cx, cx, r)
            paint { color(ink); alpha(1f); strokeWidth(t); style(RcPaintStyle.Stroke) }
            drawCircle(cx, cx, r)
            paint { color(tint); alpha(0.3f); strokeWidth(c.u.px(1f)) }
            val inner = r + t / 2f + c.u.px(4f)
            for (i in 0 until TICKS) {
                val a = i * 2 * PI / TICKS + PI / 2
                val (ca, sa) = cos(a).toFloat() to sin(a).toFloat()
                drawLine(cx + inner * ca, cx + inner * sa, cx + (inner + t) * ca, cx + (inner + t) * sa)
            }
            paint { color(tint); alpha(1f); strokeWidth(t); strokeCap(RcStrokeCap.Round); style(RcPaintStyle.Stroke) }
            drawArc((cx - r).rf, (cx - r).rf, (cx + r).rf, (cx + r).rf, 90f.rf, sweep)
            save {
                rotate(sweep, cx.rf, cx.rf)
                val knob = c.u.px(12f)
                paint { color(tint); style(RcPaintStyle.Fill) }
                drawCircle(cx, cx + r, knob)
                paint { color(ink); strokeWidth(c.u.px(3f)); style(RcPaintStyle.Stroke) }
                drawCircle(cx, cx + r, knob)
            }
        }
        Column(Modifier.size(px, px), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
            c.label(c.text(Vars.t(tileId, "action")), diameterDp / 18f, ink, align = RcTextAlign.Center)
            Spacer(Modifier.height(c.u.px(diameterDp / 40f)))
            c.label(c.text(Vars.e(entityId, "value")), diameterDp / 7.5f, ink, Weight.LIGHT, align = RcTextAlign.Center)
        }
    }
}

/** The current readings, the dial's colour and sweep, the action in its middle, and which mode is lit. */
private fun DocContext.bindThermostat(tileId: String, entityId: String, modes: List<String>, fans: List<String>) {
    bind(entityId) { states ->
        val e = states[entityId]
        val unit = if (ha.temperatureUnit.contains("F")) 0 else 1
        val action = e?.attr("hvac_action")
        val tint = when (action) {
            "heating", "preheating" -> Role.WARN
            "cooling" -> Role.BLUE
            else -> Role.ACCENT_DARK
        }
        val lo = e?.number("min_temp") ?: 7.0
        val hi = e?.number("max_temp") ?: 35.0
        val target = e?.number("temperature")
        val sweep = target?.let { (((it - lo) / (hi - lo).coerceAtLeast(1.0)).coerceIn(0.0, 1.0) * 360.0).toFloat() } ?: 0f
        listOf(
            VarValue.string(Vars.t(tileId, "current"), e?.number("current_temperature")?.let { i18n.number(it, unit) + "°" } ?: "–"),
            VarValue.string(Vars.t(tileId, "humidity"), e?.number("current_humidity")?.let { i18n.percent(it.toInt()) } ?: "–"),
            VarValue.string(Vars.t(tileId, "action"), action?.let { actionWord(it) } ?: i18n.state("climate", e?.state ?: "off")),
            VarValue.color(Vars.t(tileId, "dial"), tint.argb),
            VarValue.float(Vars.t(tileId, "sweep"), sweep),
        ) + modes.mapIndexed { i, m ->
            VarValue.color(Vars.o(tileId, "seg", i, "bg"), (if (e?.state == m) Role.ACCENT_DARK else Role.PAGE).argb)
        } + fans.flatMapIndexed { i, m ->
            val on = e?.attr("fan_mode") == m
            listOf(
                VarValue.color(Vars.o(tileId, "fan", i, "bg"), (if (on) Role.INK else Role.KEY).argb),
                VarValue.color(Vars.o(tileId, "fan", i, "ink"), (if (on) Role.INK_DARK else Role.INK).argb),
            )
        }
    }
}

private fun DocContext.actionWord(action: String): String =
    i18n.t("state.$action").takeUnless { it == "state.$action" } ?: action.replace('_', ' ').replaceFirstChar { it.titlecase(i18n.locale) }
