@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.light

import java.awt.Color
import kotlin.math.roundToInt
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ControlVerb
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.LightColorMode
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.bar
import tech.shaffersoftworks.remotescreens.server.render.cards.bigToggle
import tech.shaffersoftworks.remotescreens.server.render.cards.flowKeys
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.cards.section
import tech.shaffersoftworks.remotescreens.server.render.cards.swatches

internal fun DocContext.lightCard(
    entityId: String,
    entityState: EntityState?,
    width: Float,
) {
    val modes = entityState?.light?.supportedColorModes.orEmpty()
    bigToggle(entityId, entityState)

    if (modes.any { it != LightColorMode.ONOFF }) {
        section(i18n.t("ui.brightness"))
        bar(entityId = entityId, verb = ControlVerb.BRIGHTNESS, width = width)
    }

    if (modes.any { it in LightColorMode.RGB_CAPABLE }) {
        section(i18n.t("ui.colour"))

        swatches(
            HUES.map { h -> Color.HSBtoRGB(
                h / HUE_DEGREES,
                HUE_SATURATION,
                1f,
            ) to ref(entityId = entityId, verb = "hue", arg = h.toString()) },
        )
    }

    if (LightColorMode.COLOR_TEMP in modes) {
        section(i18n.t("ui.white"))

        swatches(
            KELVINS.map { kelvin -> kelvinColor(
                kelvin,
            ) to ref(entityId = entityId, verb = "kelvin", arg = kelvin.toString()) },
        )
    }

    val effects = entityState?.light?.effectList.orEmpty()

    if (effects.isNotEmpty()) {
        structural(entityId) { it?.light?.effectList?.joinToString().orEmpty() }
        section(i18n.t("ui.effects"))

        flowKeys(
            entityId = entityId,
            verb = "effect",
            choices = effects.take(EFFECTS_TAKE).map { it to it },
        ) { it.light.effect }
    }
}


private const val HUE_SATURATION = 0.55f
private const val HUE_DEGREES = 360f
private const val EFFECTS_TAKE = 24
private const val KELVIN_MIN = 2200
private const val KELVIN_SPAN = 4300f
private const val KELVIN_GREEN_BASE = 190
private const val KELVIN_GREEN_RANGE = 60
private const val KELVIN_BLUE_BASE = 120
private const val KELVIN_BLUE_RANGE = 135
private const val ALPHA_OPAQUE = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MAX = 255

internal val HUES = listOf(0, 30, 50, 120, 180, 220, 270, 320)
internal val KELVINS = listOf(2200, 2700, 3500, 4500, 5500, 6500)

/** The colour of white light at [k] kelvin, near enough for a swatch. */
internal fun kelvinColor(k: Int): Int {
    val t = ((k - KELVIN_MIN) / KELVIN_SPAN).coerceIn(0f, 1f)
    val r = CHANNEL_MAX
    val g = (KELVIN_GREEN_BASE + KELVIN_GREEN_RANGE * t).roundToInt()
    val b = (KELVIN_BLUE_BASE + KELVIN_BLUE_RANGE * t).roundToInt()

    return (ALPHA_OPAQUE shl ALPHA_SHIFT) or
        (r shl RED_SHIFT) or
        (g shl GREEN_SHIFT) or
        b
}
