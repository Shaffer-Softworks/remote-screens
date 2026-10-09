@file:Suppress("RestrictedApi")

package dev.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import dev.remotescreens.server.ha.EntityState
import dev.remotescreens.shared.Tile
import dev.remotescreens.shared.VarValue
import java.time.ZoneId
import kotlin.math.roundToInt

/** The size the RPI weather page was drawn for; the page scales from it. */
private const val BASE_W = 1280f
private const val BASE_H = 650f

/** Small labels stop shrinking here so they stay readable on 7" panels. */
private fun small(base: Float, k: Float) = maxOf(base * k, 12f)

/** The current conditions under the temperature, in the order the RPI page writes them. */
private val DETAILS = listOf("wind_speed", "humidity", "pressure")

/**
 * The RPI weather page: the entity's name in orange at the top, the condition and temperature large in the middle
 * with the wind, humidity and pressure under them, and the coming days on a darker strip along the bottom.
 */
fun DocContext.weatherPanel(tile: Tile, box: TileBox) {
    val id = tile.entityId ?: return
    val e = states[id]
    structural(id) { s -> (listOf(s?.state ?: "") + DETAILS.filter { s?.number(it) != null }).joinToString() }
    refreshEvery(1800)
    val details = DETAILS.filter { e?.number(it) != null }
    bindWeatherDetails(tile.id, id, details)

    val k = minOf(box.w / BASE_W, box.h / BASE_H).coerceIn(0.4f, 1.5f)
    val ink = role(Role.INK)
    val soft = role(Role.INK_SOFT)
    val zone = runCatching { ZoneId.of(ha.timeZone) }.getOrDefault(ZoneId.of("UTC"))
    val dayW = 120f * k
    val days = data.forecasts[id].orEmpty().take(((box.w - 20f) / dayW).toInt().coerceIn(0, 7))

    scope.Column(Modifier.fillMaxSize().background(role(Role.PAGE)), horizontal = RcHorizontalPositioning.Center) {
        val c = this@weatherPanel
        Spacer(Modifier.height(c.u.px(16f * k)))
        c.label(c.text(V.e(id, "name")), 36f * k, c.role(Role.WEATHER_TITLE), align = RcTextAlign.Center)
        Spacer(Modifier.verticalWeight(1f))
        Row(vertical = RcVerticalPositioning.Center) {
            c.icon(Present.weatherIcon(e?.state ?: ""), 110f * k, ink)
            Spacer(Modifier.width(c.u.px(24f * k)))
            c.label(c.text(V.e(id, "value")), 110f * k, ink, 100f)
        }
        c.label(c.i18n.state("weather", e?.state ?: "unknown"), small(26f, k), ink, align = RcTextAlign.Center)
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(c.u.px(40f * k)))
            Row(vertical = RcVerticalPositioning.Center) {
                details.forEachIndexed { i, key ->
                    if (i > 0) Spacer(Modifier.width(c.u.px(50f * k)))
                    c.label(c.text(V.t(tile.id, key)), small(20f, k), ink)
                }
            }
        }
        Spacer(Modifier.verticalWeight(1f))
        if (days.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(c.role(Role.STRIP)).padding(c.u.px(10f)), horizontal = RcRowHorizontalPositioning.SpaceEvenly) {
                for (d in days) {
                    Column(Modifier.width(c.u.px(dayW)).padding(0f, c.u.px(10f * k), 0f, c.u.px(10f * k)), horizontal = RcHorizontalPositioning.Center) {
                        c.label(c.i18n.fullWeekday(d.time.withZoneSameInstant(zone)), small(16f, k), ink, align = RcTextAlign.Center)
                        Spacer(Modifier.height(c.u.px(10f * k)))
                        c.icon(Present.weatherIcon(d.condition), 60f * k, ink)
                        Spacer(Modifier.height(c.u.px(6f * k)))
                        c.label(c.i18n.state("weather", d.condition), small(14f, k), soft, align = RcTextAlign.Center)
                        Spacer(Modifier.height(c.u.px(4f * k)))
                        Row(vertical = RcVerticalPositioning.Bottom) {
                            c.label(d.high?.let { "${it.roundToInt()}°" } ?: "–", small(18f, k), ink, 500f)
                            c.label(d.low?.let { " / ${it.roundToInt()}°" } ?: "", small(13f, k), soft)
                        }
                    }
                }
            }
        }
    }
}

/** "Wind 12 km/h", "Humidity 71%" and "Pressure 1013 hPa", in the units the weather entity reports. */
private fun DocContext.bindWeatherDetails(tileId: String, entityId: String, details: List<String>) {
    fun measure(e: EntityState, key: String, unitKey: String, fallback: String): String? {
        val v = e.number(key) ?: return null
        val unit = e.attr(unitKey) ?: fallback
        return "${i18n.number(v, if (unit == "inHg") 2 else 0)} $unit"
    }
    bind(entityId) { states ->
        val e = states[entityId]
        details.map { key ->
            val word = e?.let {
                when (key) {
                    "wind_speed" -> measure(it, key, "wind_speed_unit", "km/h")?.let { v -> i18n.t("ui.wind", "value" to v) }
                    "humidity" -> it.number(key)?.let { v -> i18n.t("ui.humidity", "value" to i18n.percent(v.roundToInt())) }
                    else -> measure(it, key, "pressure_unit", "hPa")?.let { v -> i18n.t("ui.pressure", "value" to v) }
                }
            }
            VarValue.string(V.t(tileId, key), word ?: "")
        }
    }
}
