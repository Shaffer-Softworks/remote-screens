@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

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
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.time.ZoneId
import kotlin.math.roundToInt


private const val TEMP_WEIGHT = 100f
private const val CONDITION_ICON_DP = 110f
private const val STRIP_PAD_DP = 10f
private const val MIN_LABEL_SP = 12f
private const val LOW_TEMP_SP = 13f
private const val CONDITION_SP = 14f
private const val WEEKDAY_SP = 16f
private const val HIGH_TEMP_SP = 18f
private const val DETAIL_GAP_BASE_DP = 20f
private const val TEMP_ICON_GAP_DP = 24f
private const val CONDITION_LABEL_SP = 26f
private const val TITLE_SP = 36f
private const val DETAILS_GAP_DP = 40f
private const val DETAIL_COL_GAP_DP = 50f
private const val FORECAST_ICON_DP = 60f
private const val TEMP_ROW_GAP_DP = 4f
private const val FORECAST_GAP_DP = 6f

/** The size the RPI weather page was drawn for; the page scales from it. */
private const val BASE_W = 1280f
private const val BASE_H = 650f

/** Small labels stop shrinking here so they stay readable on 7" panels. */
private fun small(base: Float, layoutScale: Float) = maxOf(base * layoutScale, MIN_LABEL_SP)

/** The current conditions under the temperature, in the order the RPI page writes them. */
private val DETAILS = listOf("wind_speed", "humidity", "pressure")

/**
 * The RPI weather page: the entity's name in orange at the top, the condition and temperature large in the middle
 * with the wind, humidity and pressure under them, and the coming days on a darker strip along the bottom.
 */
fun DocContext.weatherPanel(tile: Tile, box: TileBox) {
    val id = tile.entityId ?: return
    val entityState = states[id]
    structural(id) { s -> (listOf(s?.state ?: "") + DETAILS.filter { s?.number(it) != null }).joinToString() }
    refreshEvery(1800)
    val details = DETAILS.filter { entityState?.number(it) != null }
    bindWeatherDetails(tile.id, id, details)

    val layoutScale = minOf(box.w / BASE_W, box.h / BASE_H).coerceIn(0.4f, 1.5f)
    val ink = role(Role.INK)
    val soft = role(Role.INK_SOFT)
    val zone = runCatching { ZoneId.of(homeAssistantConfig.timeZone) }.getOrDefault(ZoneId.of("UTC"))
    val dayW = 120f * layoutScale
    val days = data.forecasts[id].orEmpty().take(((box.w - DETAIL_GAP_BASE_DP) / dayW).toInt().coerceIn(0, 7))

    scope.Column(Modifier.fillMaxSize().background(role(Role.PAGE)), horizontal = RcHorizontalPositioning.Center) {
        val doc = this@weatherPanel
        Spacer(Modifier.height(doc.units.px(WEEKDAY_SP * layoutScale)))
        doc.label(doc.text(Vars.entity(id, Vars.KEY_NAME)), TITLE_SP * layoutScale, doc.role(Role.WEATHER_TITLE), align = RcTextAlign.Center)
        Spacer(Modifier.verticalWeight(1f))
        Row(vertical = RcVerticalPositioning.Center) {
            doc.icon(Present.weatherIcon(entityState?.state ?: ""), CONDITION_ICON_DP * layoutScale, ink)
            Spacer(Modifier.width(doc.units.px(TEMP_ICON_GAP_DP * layoutScale)))
            doc.label(doc.text(Vars.entity(id, Vars.KEY_VALUE)), CONDITION_ICON_DP * layoutScale, ink, TEMP_WEIGHT)
        }
        doc.label(doc.i18n.state("weather", entityState?.state ?: "unknown"), small(CONDITION_LABEL_SP, layoutScale), ink, align = RcTextAlign.Center)
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(doc.units.px(DETAILS_GAP_DP * layoutScale)))
            Row(vertical = RcVerticalPositioning.Center) {
                details.forEachIndexed { i, key ->
                    if (i > 0) Spacer(Modifier.width(doc.units.px(DETAIL_COL_GAP_DP * layoutScale)))
                    doc.label(doc.text(Vars.tile(tile.id, key)), small(DETAIL_GAP_BASE_DP, layoutScale), ink)
                }
            }
        }
        Spacer(Modifier.verticalWeight(1f))
        if (days.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(doc.role(Role.STRIP)).padding(doc.units.px(STRIP_PAD_DP)), horizontal = RcRowHorizontalPositioning.SpaceEvenly) {
                for (d in days) {
                    Column(Modifier.width(doc.units.px(dayW)).padding(0f, doc.units.px(STRIP_PAD_DP * layoutScale), 0f, doc.units.px(STRIP_PAD_DP * layoutScale)), horizontal = RcHorizontalPositioning.Center) {
                        doc.label(doc.i18n.fullWeekday(d.time.withZoneSameInstant(zone)), small(WEEKDAY_SP, layoutScale), ink, align = RcTextAlign.Center)
                        Spacer(Modifier.height(doc.units.px(STRIP_PAD_DP * layoutScale)))
                        doc.icon(Present.weatherIcon(d.condition), FORECAST_ICON_DP * layoutScale, ink)
                        Spacer(Modifier.height(doc.units.px(FORECAST_GAP_DP * layoutScale)))
                        doc.label(doc.i18n.state("weather", d.condition), small(CONDITION_SP, layoutScale), soft, align = RcTextAlign.Center)
                        Spacer(Modifier.height(doc.units.px(TEMP_ROW_GAP_DP * layoutScale)))
                        Row(vertical = RcVerticalPositioning.Bottom) {
                            doc.label(d.high?.let { "${it.roundToInt()}°" } ?: "–", small(HIGH_TEMP_SP, layoutScale), ink, Weight.MEDIUM)
                            doc.label(d.low?.let { " / ${it.roundToInt()}°" } ?: "", small(LOW_TEMP_SP, layoutScale), soft)
                        }
                    }
                }
            }
        }
    }
}

/** "Wind 12 km/h", "Humidity 71%" and "Pressure 1013 hPa", in the units the weather entity reports. */
private fun DocContext.bindWeatherDetails(tileId: String, entityId: String, details: List<String>) {
    fun measure(entityState: EntityState, key: String, unitKey: String, fallback: String): String? {
        val v = entityState.number(key) ?: return null
        val unit = entityState.attr(unitKey) ?: fallback
        return "${i18n.number(v, if (unit == "inHg") 2 else 0)} $unit"
    }
    bind(entityId) { states ->
        val entityState = states[entityId]
        details.map { key ->
            val word = entityState?.let {
                when (key) {
                    "wind_speed" -> measure(it, key, "wind_speed_unit", "km/h")?.let { v -> i18n.t("ui.wind", "value" to v) }
                    "humidity" -> it.number(key)?.let { v -> i18n.t("ui.humidity", "value" to i18n.percent(v.roundToInt())) }
                    else -> measure(it, key, "pressure_unit", "hPa")?.let { v -> i18n.t("ui.pressure", "value" to v) }
                }
            }
            VarValue.string(Vars.tile(tileId, key), word ?: "")
        }
    }
}
