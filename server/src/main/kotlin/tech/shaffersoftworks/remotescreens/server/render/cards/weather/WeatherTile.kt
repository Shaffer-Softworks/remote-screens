@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.weather

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import java.time.ZoneId
import kotlin.math.roundToInt
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Availability
import tech.shaffersoftworks.remotescreens.server.data.renderdata.Forecast
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.weatherStateIcon
import tech.shaffersoftworks.remotescreens.shared.Tile

internal fun DocContext.weatherTile(tile: Tile, box: TileBox, entityState: EntityState?, named: Boolean) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    structural(entityId) { it?.state.orEmpty() }
    refreshEvery(WEATHER_REFRESH_S)
    val days = data.forecasts[entityId].orEmpty()
    val zone = homeZone()

    val dayCount = when {
        box.w >= C.WEATHER_WIDE_DP -> C.WEATHER_DAYS_WIDE
        box.w >= C.HEADER_TALL_MIN_DP -> C.WEATHER_DAYS_MID
        else -> 0
    }.coerceAtMost(days.size)

    rcScope.Column(Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP))) {
        weatherNow(entityId = entityId, entityState = entityState, heightDp = box.h, ink = ink, ink2 = ink2)
        Spacer(Modifier.verticalWeight(1f))

        if (dayCount > 0) {
            forecastStrip(days = days.take(dayCount), zone = zone, ink = ink, ink2 = ink2)
        } else if (named) {
            label(
                text = text(Vars.entity(entityId, Vars.KEY_NAME)),
                sp = Type.BODY,
                color = ink,
                weight = Weight.SEMI_BOLD,
            )
        }
    }
}

private fun DocContext.weatherNow(
    entityId: String,
    entityState: EntityState?,
    heightDp: Float,
    ink: RcColor,
    ink2: RcColor,
) {
    val iconDp = if (heightDp >= C.TALL_HEIGHT_DP) C.WEATHER_ICON_TALL_DP else C.HEADER_SHORT_DP

    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@weatherNow
        doc.weatherStateIcon(condition = entityState?.state.orEmpty(), sizeDp = iconDp, fallbackColor = ink)
        Spacer(Modifier.width(doc.units.px(C.CARD_CORNER_DP)))

        Column(Modifier.horizontalWeight(1f)) {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                sp = Type.HEADLINE,
                color = ink,
                weight = Weight.MEDIUM,
            )

            doc.label(
                text = doc.i18n.state(Domain.WEATHER.value, entityState?.state ?: Availability.UNKNOWN.wire),
                sp = Type.CAPTION + 1f,
                color = ink2,
            )
        }
    }
}

private fun DocContext.forecastStrip(days: List<Forecast>, zone: ZoneId, ink: RcColor, ink2: RcColor) {
    rcScope.Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.SpaceBetween) {
        val doc = this@forecastStrip

        for (day in days) {
            Column(horizontal = RcHorizontalPositioning.Center) {
                doc.label(
                    text = doc.i18n.weekday(day.time.withZoneSameInstant(zone)),
                    sp = Type.CAPTION,
                    color = ink2,
                    align = RcTextAlign.Center,
                )

                doc.weatherStateIcon(condition = day.condition, sizeDp = C.FORECAST_ICON_DP, fallbackColor = ink)

                doc.label(
                    text = listOfNotNull(
                        day.high?.let { "${it.roundToInt()}°" },
                        day.low?.let { "${it.roundToInt()}°" },
                    ).joinToString(" "),
                    sp = Type.CAPTION,
                    color = ink,
                    align = RcTextAlign.Center,
                )
            }
        }
    }
}
