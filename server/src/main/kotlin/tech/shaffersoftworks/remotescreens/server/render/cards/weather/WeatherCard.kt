@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.weather

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import java.time.ZoneId
import kotlin.math.roundToInt
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.Forecast
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.weatherStateIcon

internal const val WEATHER_REFRESH_S = 1800
private const val FORECAST_DAYS = 7
private const val WEATHER_ICON_ROW_DP = 28f
private const val RANGE_FLOOR = 1.0

internal fun DocContext.weatherCard(entityId: String, entityState: EntityState?, width: Float) {
    structural(entityId) { it?.state.orEmpty() }
    refreshEvery(WEATHER_REFRESH_S)
    currentWeather(entityId, entityState)
    spacer(CardChrome.CARD_GAP_DP)
    val days = data.forecasts[entityId].orEmpty()
    val lows = days.mapNotNull { it.low }
    val highs = days.mapNotNull { it.high }
    val low = lows.minOrNull() ?: 0.0
    val high = highs.maxOrNull() ?: RANGE_FLOOR
    val zone = homeZone()

    for (day in days.take(FORECAST_DAYS)) {
        forecastDay(day = day, zone = zone, width = width, low = low, high = high)
    }
}

internal fun DocContext.homeZone(): ZoneId =
    runCatching { ZoneId.of(homeAssistantConfig.timeZone) }.getOrDefault(ZoneId.of("UTC"))

private fun DocContext.currentWeather(entityId: String, entityState: EntityState?) {
    val ink = role(Role.INK)
    val soft = role(Role.INK_SOFT)

    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@currentWeather

        doc.weatherStateIcon(
            condition = entityState?.state.orEmpty(),
            sizeDp = CardChrome.STEPPER_KEY_DP,
            fallbackColor = ink
        )

        Spacer(Modifier.width(doc.units.px(CardChrome.CARD_GAP_DP)))

        Column {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                sp = Type.DISPLAY,
                color = ink,
                weight = Weight.NORMAL,
            )

            doc.label(text = doc.weatherDetails(entityState), sp = Type.BODY, color = soft)
        }
    }
}

private fun DocContext.weatherDetails(entityState: EntityState?): String {
    val weather = entityState?.weather ?: return ""

    return listOfNotNull(
        weather.humidity?.let { i18n.t("ui.humidity", "value" to i18n.percent(it.roundToInt())) },
        weather.windSpeed?.let {
            i18n.t("ui.wind", "value" to "${it.roundToInt()} ${weather.windSpeedUnit ?: "km/h"}")
        },
    ).joinToString(" · ")
}

private fun DocContext.forecastDay(day: Forecast, zone: ZoneId, width: Float, low: Double, high: Double) {
    val ink = role(Role.INK)
    val soft = role(Role.INK_SOFT)

    rcScope.Row(
        Modifier.fillMaxWidth().padding(
            start = 0f,
            top = units.px(CardChrome.ROW_GAP_DP),
            end = 0f,
            bottom = units.px(CardChrome.ROW_GAP_DP),
        ),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@forecastDay

        doc.label(
            text = doc.i18n.weekday(day.time.withZoneSameInstant(zone)),
            sp = Type.TITLE,
            color = ink,
            weight = Weight.MEDIUM,
            modifier = Modifier.width(doc.units.px(CardChrome.MEDIA_CTRL_DP)),
        )

        doc.weatherStateIcon(condition = day.condition, sizeDp = WEATHER_ICON_ROW_DP, fallbackColor = ink)
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))

        doc.label(
            text = day.rain?.let { "$it%" }.orEmpty(),
            sp = Type.BODY,
            color = doc.role(Role.ACCENT),
            modifier = Modifier.width(doc.units.px(CardChrome.FLOW_KEY_H_DP)),
        )

        doc.degreeLabel(temperature = day.low, color = soft, weight = Weight.NORMAL)
        doc.temperatureRange(day = day, width = width, low = low, high = high)
        doc.degreeLabel(temperature = day.high, color = ink, weight = Weight.SEMI_BOLD)
    }
}

private fun DocContext.degreeLabel(temperature: Double?, color: RcColor, weight: Float) {
    label(
        text = temperature?.let { "${it.roundToInt()}°" }.orEmpty(),
        sp = Type.BODY,
        color = color,
        weight = weight,
        modifier = Modifier.width(units.px(CardChrome.CLOSE_KEY_DP)),
    )
}

private fun DocContext.temperatureRange(day: Forecast, width: Float, low: Double, high: Double) {
    val span = (high - low).coerceAtLeast(RANGE_FLOOR)
    val start = (((day.low ?: low) - low) / span).toFloat().coerceIn(0f, 1f)
    val end = (((day.high ?: high) - low) / span).toFloat().coerceIn(start, 1f)

    val reserved = CardChrome.MEDIA_CTRL_DP + WEATHER_ICON_ROW_DP + CardChrome.PANEL_CORNER_DP +
        CardChrome.FLOW_KEY_H_DP + CardChrome.CLOSE_KEY_DP + CardChrome.CLOSE_KEY_DP +
        CardChrome.SECTION_GAP_DP

    val barW = units.px((width - reserved).coerceAtLeast(CardChrome.CLOSE_KEY_DP))
    val barH = units.px(CardChrome.ROW_GAP_DP)
    rcScope.Spacer(Modifier.width(units.px(CardChrome.ROW_GAP_DP)))

    rcScope.Canvas(Modifier.size(barW, barH)) {
        paint { color(role(Role.DIVIDER)); style(RcPaintStyle.Fill) }
        drawRoundRect(left = 0f, top = 0f, right = barW, bottom = barH, radiusX = barH / 2f, radiusY = barH / 2f)
        paint { color(role(Role.WARN)) }
        val barLeft = start * barW

        drawRoundRect(
            left = barLeft,
            top = 0f,
            right = maxOf(end * barW, barLeft + barH),
            bottom = barH,
            radiusX = barH / 2f,
            radiusY = barH / 2f,
        )
    }

    rcScope.Spacer(Modifier.width(units.px(CardChrome.ROW_GAP_DP)))
}
