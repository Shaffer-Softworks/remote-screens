@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.weather

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.requiredHeightIn
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.RectShape
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.Forecast
import tech.shaffersoftworks.remotescreens.server.render.keptVerticalScroll
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.weatherStateIcon
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
private const val LAYOUT_SCALE_MIN = 0.4f
private const val LAYOUT_SCALE_MAX = 1.5f
private const val FORECAST_DAY_W_DP = 120f
private const val FORECAST_DAYS_MAX = 7

/** Sizes for the tall page. A wider portrait grows the hero; a narrow one shrinks it. */
private class PortraitFit(widthDp: Float) {
    val titleSp: Float = if (widthDp >= WIDE_DP) TITLE_WIDE_SP else TITLE_SP
    val iconDp: Float = when {
        widthDp < NARROW_DP -> ICON_NARROW_DP
        widthDp >= WIDE_DP -> ICON_WIDE_DP
        else -> ICON_DP
    }
    val tempSp: Float = when {
        widthDp < NARROW_DP -> TEMP_NARROW_SP
        widthDp >= WIDE_DP -> TEMP_WIDE_SP
        else -> TEMP_SP
    }
    val weekdayW: Float = if (widthDp < NARROW_DP) WEEKDAY_NARROW_W_DP else WEEKDAY_W_DP

    /** Tall enough for the icon and for the thin temperature beside it. */
    val nowRowDp: Float = maxOf(iconDp, tempSp * TEMP_LINE)

    /** The hero block, including the gap of page colour above the forecast plate. */
    fun heroDp(hasDetails: Boolean): Float {
        val details = if (hasDetails) {
            AFTER_CONDITION_DP + LABEL_SP * LINE + LABEL_GAP_DP + VALUE_SP * LINE
        } else {
            0f
        }
        return TOP_PAD_DP + titleSp * LINE + AFTER_TITLE_DP + nowRowDp + AFTER_TEMP_DP +
            NOW_CONDITION_SP * LINE + details + BEFORE_STRIP_DP
    }

    companion object {
        const val WIDE_DP = 520f
        const val NARROW_DP = 420f
        const val LINE = 1.4f
        const val TOP_PAD_DP = 20f
        const val TITLE_SP = 28f
        const val TITLE_WIDE_SP = 32f
        const val AFTER_TITLE_DP = 12f
        const val ICON_DP = 96f
        const val ICON_WIDE_DP = 110f
        const val ICON_NARROW_DP = 72f
        const val TEMP_SP = 84f
        const val TEMP_WIDE_SP = 96f
        const val TEMP_NARROW_SP = 64f
        const val TEMP_LINE = 1.2f
        const val TEMP_GAP_DP = 16f
        const val AFTER_TEMP_DP = 4f
        const val NOW_CONDITION_SP = 22f
        const val AFTER_CONDITION_DP = 20f
        const val LABEL_SP = 13f
        const val VALUE_SP = 18f
        const val LABEL_GAP_DP = 4f
        const val BEFORE_STRIP_DP = 16f
        const val STRIP_PAD_H_DP = 16f
        const val STRIP_PAD_V_DP = 8f
        const val ROW_H_DP = 56f
        const val ROW_ICON_DP = 40f
        const val WEEKDAY_W_DP = 112f
        const val WEEKDAY_NARROW_W_DP = 92f
        const val ROW_GAP_DP = 10f
    }
}

@Suppress("LongParameterList")
private class PortraitPage(
    val tileId: String,
    val entityId: String,
    val entityState: EntityState?,
    val details: List<String>,
    val zone: ZoneId,
    val days: List<Forecast>,
    val fit: PortraitFit,
    val ink: RcColor,
    val soft: RcColor,
    val heightDp: Float,
) {
    val hero: Float = fit.heroDp(details.isNotEmpty())
    val scrolls: Boolean =
        days.isNotEmpty() && hero + days.size * PortraitFit.ROW_H_DP + PortraitFit.STRIP_PAD_V_DP * 2 > heightDp
}

/** Small labels stop shrinking here so they stay readable on 7" panels. */
private fun small(base: Float, layoutScale: Float) = maxOf(base * layoutScale, MIN_LABEL_SP)

/** The current conditions under the temperature, in the order the RPI page writes them. */
private val DETAILS = listOf("wind_speed", "humidity", "pressure")

/**
 * The RPI weather page. Landscape keeps the wide layout: the name in orange, the condition and temperature
 * in the middle, and the coming days in columns on a darker strip. A taller page stacks that same hero and
 * lists each day on its own row, scrolling the list only when the page is too short to hold it.
 */
fun DocContext.weatherPanel(tile: Tile, box: TileBox) {
    val id = tile.entityId ?: return
    val entityState = states[id]
    structural(
        id,
    ) { s -> (listOf(s?.state ?: "") + DETAILS.filter { s?.number(it) != null }).joinToString() }
    refreshEvery(CardChrome.WEATHER_REFRESH_S)
    val details = DETAILS.filter { entityState?.number(it) != null }
    val zone = runCatching { ZoneId.of(homeAssistantConfig.timeZone) }.getOrDefault(ZoneId.of("UTC"))
    if (box.h > box.w) {
        bindWeatherReadings(tile.id, id, details)
        val forecast = data.forecasts[id].orEmpty().take(FORECAST_DAYS_MAX)
        portraitWeather(
            PortraitPage(
                tile.id,
                id,
                entityState,
                details,
                zone,
                forecast,
                PortraitFit(box.w),
                role(Role.INK),
                role(Role.INK_SOFT),
                box.h,
            ),
        )
        return
    }
    bindWeatherDetails(tile.id, id, details)

    val layoutScale = minOf(box.w / BASE_W, box.h / BASE_H)
        .coerceIn(LAYOUT_SCALE_MIN, LAYOUT_SCALE_MAX)
    val ink = role(Role.INK)
    val soft = role(Role.INK_SOFT)
    val dayW = FORECAST_DAY_W_DP * layoutScale
    val days = data.forecasts[id].orEmpty().take(
        ((box.w - DETAIL_GAP_BASE_DP) / dayW).toInt().coerceIn(0, FORECAST_DAYS_MAX),
    )

    scope.Column(
        Modifier.fillMaxSize().background(role(Role.PAGE)),
        horizontal = RcHorizontalPositioning.Center,
    ) {
        val doc = this@weatherPanel
        Spacer(Modifier.height(doc.units.px(WEEKDAY_SP * layoutScale)))
        doc.label(
            doc.text(Vars.entity(id, Vars.KEY_NAME)),
            TITLE_SP * layoutScale,
            doc.role(Role.WEATHER_TITLE),
            align = RcTextAlign.Center,
        )
        Spacer(Modifier.verticalWeight(1f))
        Row(vertical = RcVerticalPositioning.Center) {
            doc.weatherStateIcon(
                entityState?.state ?: "",
                CONDITION_ICON_DP * layoutScale,
                fallbackColor = ink,
            )
            Spacer(Modifier.width(doc.units.px(TEMP_ICON_GAP_DP * layoutScale)))
            doc.label(
                doc.text(Vars.entity(id, Vars.KEY_VALUE)),
                CONDITION_ICON_DP * layoutScale,
                ink,
                TEMP_WEIGHT,
            )
        }
        doc.label(
            doc.i18n.state("weather", entityState?.state ?: "unknown"),
            small(CONDITION_LABEL_SP, layoutScale),
            ink,
            align = RcTextAlign.Center,
        )
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(doc.units.px(DETAILS_GAP_DP * layoutScale)))
            Row(vertical = RcVerticalPositioning.Center) {
                details.forEachIndexed { i, key ->
                    if (i > 0) Spacer(Modifier.width(doc.units.px(DETAIL_COL_GAP_DP * layoutScale)))
                    doc.label(
                        doc.text(Vars.tile(tile.id, key)),
                        small(DETAIL_GAP_BASE_DP, layoutScale),
                        ink,
                    )
                }
            }
        }
        Spacer(Modifier.verticalWeight(1f))
        if (days.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(
                    doc.role(Role.STRIP),
                ).padding(doc.units.px(STRIP_PAD_DP)),
                horizontal = RcRowHorizontalPositioning.SpaceEvenly,
            ) {
                for (day in days) {
                    Column(
                        Modifier.width(
                            doc.units.px(dayW),
                        ).padding(
                            0f,
                            doc.units.px(STRIP_PAD_DP * layoutScale),
                            0f,
                            doc.units.px(STRIP_PAD_DP * layoutScale),
                        ),
                        horizontal = RcHorizontalPositioning.Center,
                    ) {
                        doc.label(
                            doc.i18n.fullWeekday(day.time.withZoneSameInstant(zone)),
                            small(WEEKDAY_SP, layoutScale),
                            ink,
                            align = RcTextAlign.Center,
                        )
                        Spacer(Modifier.height(doc.units.px(STRIP_PAD_DP * layoutScale)))
                        doc.weatherStateIcon(
                            day.condition,
                            FORECAST_ICON_DP * layoutScale,
                            fallbackColor = ink,
                        )
                        Spacer(Modifier.height(doc.units.px(FORECAST_GAP_DP * layoutScale)))
                        doc.label(
                            doc.i18n.state("weather", day.condition),
                            small(CONDITION_SP, layoutScale),
                            soft,
                            align = RcTextAlign.Center,
                        )
                        Spacer(Modifier.height(doc.units.px(TEMP_ROW_GAP_DP * layoutScale)))
                        Row(vertical = RcVerticalPositioning.Bottom) {
                            doc.label(
                                day.high?.let { "${it.roundToInt()}°" } ?: "–",
                                small(HIGH_TEMP_SP, layoutScale),
                                ink,
                                Weight.MEDIUM,
                            )
                            doc.label(
                                day.low?.let { " / ${it.roundToInt()}°" } ?: "",
                                small(LOW_TEMP_SP, layoutScale),
                                soft,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DocContext.portraitWeather(page: PortraitPage) {
    if (page.scrolls) {
        portraitScroll(page)
    } else {
        portraitStack(page)
    }
}

private fun DocContext.portraitStack(page: PortraitPage) {
    scope.Column(
        Modifier.fillMaxSize().background(role(Role.PAGE)),
        horizontal = RcHorizontalPositioning.Center,
    ) {
        val doc = this@portraitStack
        doc.portraitHero(page)
        if (page.days.isNotEmpty()) {
            doc.portraitDays(
                page,
                Modifier.fillMaxWidth().verticalWeight(1f),
                RcColumnVerticalPositioning.SpaceEvenly,
            )
        }
    }
}

/** The list fills the page and the hero is painted over it, so a scrolled row cannot cover the temperature. */
private fun DocContext.portraitScroll(page: PortraitPage) {
    val viewport = units.px(page.heightDp)
    scope.Box(Modifier.fillMaxSize().background(role(Role.PAGE))) {
        val doc = this@portraitScroll
        scope.Column(
            Modifier.fillMaxWidth()
                .height(viewport)
                .requiredHeightIn(max = viewport)
                .clip(RectShape(0f, 0f, 0f, 0f))
                .background(doc.role(Role.STRIP))
                .keptVerticalScroll("weather.${page.tileId}"),
        ) {
            Spacer(Modifier.height(doc.units.px(page.hero)))
            Spacer(Modifier.height(doc.units.px(PortraitFit.STRIP_PAD_V_DP)))
            for (day in page.days) doc.portraitDay(page, day)
            Spacer(Modifier.height(doc.units.px(PortraitFit.STRIP_PAD_V_DP)))
        }
        doc.portraitHero(page)
    }
}

private fun DocContext.portraitHero(page: PortraitPage) {
    scope.Column(
        Modifier.fillMaxWidth().height(units.px(page.hero)).background(role(Role.PAGE)),
        horizontal = RcHorizontalPositioning.Center,
    ) {
        val doc = this@portraitHero
        Spacer(Modifier.height(doc.units.px(PortraitFit.TOP_PAD_DP)))
        doc.label(
            doc.text(Vars.entity(page.entityId, Vars.KEY_NAME)),
            page.fit.titleSp,
            doc.role(Role.WEATHER_TITLE),
            align = RcTextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(doc.units.px(page.fit.titleSp * PortraitFit.LINE)),
        )
        Spacer(Modifier.height(doc.units.px(PortraitFit.AFTER_TITLE_DP)))
        Row(Modifier.height(doc.units.px(page.fit.nowRowDp)), vertical = RcVerticalPositioning.Center) {
            doc.weatherStateIcon(page.entityState?.state ?: "", page.fit.iconDp, fallbackColor = page.ink)
            Spacer(Modifier.width(doc.units.px(PortraitFit.TEMP_GAP_DP)))
            doc.label(
                doc.text(Vars.entity(page.entityId, Vars.KEY_VALUE)),
                page.fit.tempSp,
                page.ink,
                TEMP_WEIGHT,
            )
        }
        Spacer(Modifier.height(doc.units.px(PortraitFit.AFTER_TEMP_DP)))
        doc.label(
            doc.i18n.state("weather", page.entityState?.state ?: "unknown"),
            PortraitFit.NOW_CONDITION_SP,
            page.ink,
            align = RcTextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(doc.units.px(PortraitFit.NOW_CONDITION_SP * PortraitFit.LINE)),
        )
        if (page.details.isNotEmpty()) doc.portraitDetails(page)
        Spacer(Modifier.height(doc.units.px(PortraitFit.BEFORE_STRIP_DP)))
    }
}

private fun DocContext.portraitDetails(page: PortraitPage) {
    val block = PortraitFit.LABEL_SP * PortraitFit.LINE +
        PortraitFit.LABEL_GAP_DP +
        PortraitFit.VALUE_SP * PortraitFit.LINE
    scope.Column(Modifier.fillMaxWidth().height(units.px(PortraitFit.AFTER_CONDITION_DP + block))) {
        val doc = this@portraitDetails
        Spacer(Modifier.height(doc.units.px(PortraitFit.AFTER_CONDITION_DP)))
        Row(
            Modifier.fillMaxWidth().padding(
                doc.units.px(PortraitFit.STRIP_PAD_H_DP),
                0f,
                doc.units.px(PortraitFit.STRIP_PAD_H_DP),
                0f,
            ),
            vertical = RcVerticalPositioning.Center,
        ) {
            for (key in page.details) {
                Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
                    doc.label(
                        doc.detailLabel(key),
                        PortraitFit.LABEL_SP,
                        page.soft,
                        align = RcTextAlign.Center,
                        modifier = Modifier.height(doc.units.px(PortraitFit.LABEL_SP * PortraitFit.LINE)),
                    )
                    Spacer(Modifier.height(doc.units.px(PortraitFit.LABEL_GAP_DP)))
                    doc.label(
                        doc.text(Vars.tile(page.tileId, key)),
                        PortraitFit.VALUE_SP,
                        page.ink,
                        align = RcTextAlign.Center,
                        modifier = Modifier.height(doc.units.px(PortraitFit.VALUE_SP * PortraitFit.LINE)),
                    )
                }
            }
        }
    }
}

private fun DocContext.portraitDays(
    page: PortraitPage,
    modifier: Modifier,
    vertical: RcColumnVerticalPositioning,
) {
    scope.Column(
        modifier.background(role(Role.STRIP)).padding(
            0f,
            units.px(PortraitFit.STRIP_PAD_V_DP),
            0f,
            units.px(PortraitFit.STRIP_PAD_V_DP),
        ),
        vertical = vertical,
    ) {
        val doc = this@portraitDays
        for (day in page.days) doc.portraitDay(page, day)
    }
}

private fun DocContext.portraitDay(page: PortraitPage, day: Forecast) {
    scope.Row(
        Modifier.fillMaxWidth().height(units.px(PortraitFit.ROW_H_DP)).padding(
            units.px(PortraitFit.STRIP_PAD_H_DP),
            0f,
            units.px(PortraitFit.STRIP_PAD_H_DP),
            0f,
        ),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@portraitDay
        doc.label(
            doc.i18n.fullWeekday(day.time.withZoneSameInstant(page.zone)),
            WEEKDAY_SP,
            page.ink,
            modifier = Modifier.width(doc.units.px(page.fit.weekdayW)),
        )
        Spacer(Modifier.width(doc.units.px(PortraitFit.ROW_GAP_DP)))
        doc.weatherStateIcon(day.condition, PortraitFit.ROW_ICON_DP, fallbackColor = page.ink)
        Spacer(Modifier.width(doc.units.px(PortraitFit.ROW_GAP_DP)))
        doc.label(
            doc.i18n.state("weather", day.condition),
            CONDITION_SP,
            page.soft,
            modifier = Modifier.horizontalWeight(1f),
        )
        doc.label(day.high?.let { "${it.roundToInt()}°" } ?: "–", HIGH_TEMP_SP, page.ink, Weight.MEDIUM)
        doc.label(day.low?.let { " / ${it.roundToInt()}°" } ?: "", LOW_TEMP_SP, page.soft)
    }
}

/** "Wind 12 km/h", "Humidity 71%" and "Pressure 1013 hPa", in the units the weather entity reports. */
private fun DocContext.bindWeatherDetails(tileId: String, entityId: String, details: List<String>) {
    bind(entityId) { states ->
        val entityState = states[entityId]
        details.map { key ->
            VarValue.string(Vars.tile(tileId, key), entityState?.let { detailPhrase(it, key) } ?: "")
        }
    }
}

/** The number alone, so a narrow page can put the word above it. */
private fun DocContext.bindWeatherReadings(tileId: String, entityId: String, details: List<String>) {
    bind(entityId) { states ->
        val entityState = states[entityId]
        details.map { key ->
            VarValue.string(Vars.tile(tileId, key), entityState?.let { reading(it, key) } ?: "")
        }
    }
}

private fun DocContext.detailPhrase(entityState: EntityState, key: String): String? {
    val value = reading(entityState, key) ?: return null
    return i18n.t(detailWord(key), "value" to value)
}

private fun DocContext.detailLabel(key: String): String = i18n.t(detailWord(key), "value" to "").trim()

private fun detailWord(key: String): String = when (key) {
    "wind_speed" -> "ui.wind"
    "humidity" -> "ui.humidity"
    else -> "ui.pressure"
}

private fun DocContext.reading(entityState: EntityState, key: String): String? = when (key) {
    "wind_speed" -> measure(entityState, key, "wind_speed_unit", "km/h")
    "humidity" -> entityState.number(key)?.let { i18n.percent(it.roundToInt()) }
    else -> measure(entityState, key, "pressure_unit", "hPa")
}

private fun DocContext.measure(entityState: EntityState, key: String, unitKey: String, fallback: String): String? {
    val value = entityState.number(key) ?: return null
    val unit = entityState.attr(unitKey) ?: fallback
    return "${i18n.number(value, if (unit == "inHg") 2 else 0)} $unit"
}
