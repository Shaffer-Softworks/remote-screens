@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.render.tiles.bindWhere
import tech.shaffersoftworks.remotescreens.server.render.tiles.photo

import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.time.ZoneId

private const val WEATHER_EVENT_GAP_SHARE = 0.08f
private const val EVENT_WIDTH_SHARE = 0.42f
private const val WEATHER_WIDTH_SHARE = 0.5f
private const val TEMP_WEIGHT = 100f
private const val WEATHER_ICON_DP = 110f
private const val ACCENT_BAR_WIDTH_DP = 150f
private const val ACCENT_BAR_HEIGHT_DP = 10f
private const val MIN_LABEL_SP = 12f
private const val EVENT_META_SP = 16f
private const val SECTION_CAPTION_SP = 18f
private const val SECTION_GAP_DP = 20f
private const val EVENT_TITLE_SP = 22f
private const val EVENT_TIME_GAP_DP = 28f
private const val SECTION_TOP_PAD_DP = 30f
private const val EVENT_DAY_SP = 32f
private const val EVENT_SIDE_PAD_DP = 48f
private const val WEATHER_SIDE_PAD_DP = 50f
private const val EVENT_TIME_SP = 72f
private const val META_GAP_DP = 6f
private const val STRIP_ACCENT_H_DP = 8f
private const val STRIP_MAX_H_DP = 120f
private const val STRIP_H_SHARE = 0.24f
private const val PERSON_PHOTO_H_SHARE = 0.58f
private const val LAYOUT_SCALE_MIN = 0.4f
private const val LAYOUT_SCALE_MAX = 1.25f

/** The size the RPI home page was drawn for; the page scales from it. */
private const val BASE_W = 1280f
private const val BASE_H = 360f

/** Small labels stop shrinking here so they stay readable on 7" panels. */
private fun small(base: Float, layoutScale: Float) = maxOf(base * layoutScale, MIN_LABEL_SP)

/**
 * The RPI home page: the weather and the next calendar event on teal, and who is home on a white strip below.
 * The weather is the tile's entity (or the first weather entity); `calendars` and `people` options list entity ids,
 * all calendars and all people when left out.
 */
fun DocContext.homeTile(tile: Tile, box: TileBox) {
    fun listed(key: String, domain: String) = tile.options[key]?.split(
        ',',
    )?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: states.keys.filter { it.startsWith("$domain.") }.sorted()
    val weather = tile.entityId ?: states.keys.sorted().firstOrNull { it.startsWith("weather.") }
    val calendars = listed("calendars", "calendar")
    val people = listed("people", "person")

    val stripH = if (people.isEmpty()) 0f else minOf(STRIP_MAX_H_DP, box.h * STRIP_H_SHARE)
    val upperH = box.h - stripH
    val layoutScale = minOf(box.w / BASE_W, upperH / BASE_H)
        .coerceIn(LAYOUT_SCALE_MIN, LAYOUT_SCALE_MAX)

    bindHomeWeather(tile.id, weather)
    bindNextEvent(tile.id, calendars)

    scope.Column(Modifier.fillMaxSize().background(role(Role.HOME))) {
        val doc = this@homeTile
        Row(Modifier.size(doc.units.px(box.w), doc.units.px(upperH))) {
            doc.homeWeather(tile.id, weather, box.w * WEATHER_WIDTH_SHARE, layoutScale)
            Spacer(Modifier.width(doc.units.px(box.w * WEATHER_EVENT_GAP_SHARE)))
            doc.homeEvent(tile.id, box.w * EVENT_WIDTH_SHARE, layoutScale)
        }
        if (stripH > 0f) doc.homePeople(tile.id, people, box.w, stripH)
    }
}

private fun DocContext.homeWeather(tileId: String, weather: String?, wDp: Float, layoutScale: Float) {
    val entityState = weather?.let { states[it] }
    if (weather != null) structural(weather) { it?.state ?: "" }
    val ref = weather?.let { ActionRef("e:$it", "card") }
    scope.Column(
        tap(
            Modifier.width(
                units.px(wDp),
            ).fillMaxHeight().padding(
                units.px(WEATHER_SIDE_PAD_DP * layoutScale),
                units.px(SECTION_TOP_PAD_DP * layoutScale),
                0f,
                0f,
            ),
            ref,
        ),
    ) {
        val doc = this@homeWeather
        Box(
            Modifier.size(
                doc.units.px(ACCENT_BAR_WIDTH_DP * layoutScale),
                doc.units.px(ACCENT_BAR_HEIGHT_DP * layoutScale),
            ).background(doc.role(Role.ACCENT_DARK)),
        )
        Spacer(Modifier.height(doc.units.px(SECTION_GAP_DP * layoutScale)))
        doc.label(
            doc.i18n.t("ui.outside_now").uppercase(doc.i18n.locale),
            small(SECTION_GAP_DP, layoutScale),
            doc.role(Role.INK),
        )
        Row(vertical = RcVerticalPositioning.Center) {
            doc.weatherStateIcon(
                entityState?.state ?: "",
                WEATHER_ICON_DP * layoutScale,
            )
            Spacer(Modifier.width(doc.units.px(SECTION_GAP_DP * layoutScale)))
            doc.label(
                doc.text(Vars.tile(tileId, "temp")),
                ACCENT_BAR_WIDTH_DP * layoutScale,
                doc.role(Role.INK),
                TEMP_WEIGHT,
            )
        }
    }
}

private fun DocContext.homeEvent(tileId: String, wDp: Float, layoutScale: Float) {
    scope.Column(
        Modifier.width(
            units.px(wDp),
        ).fillMaxHeight().padding(
            units.px(EVENT_SIDE_PAD_DP * layoutScale),
            units.px(SECTION_TOP_PAD_DP * layoutScale),
            units.px(EVENT_DAY_SP * layoutScale),
            units.px(EVENT_META_SP * layoutScale),
        ),
    ) {
        val doc = this@homeEvent
        Box(
            Modifier.size(
                doc.units.px(ACCENT_BAR_WIDTH_DP * layoutScale),
                doc.units.px(STRIP_ACCENT_H_DP * layoutScale),
            ).background(doc.role(Role.ACCENT_DARK)),
        )
        Spacer(Modifier.height(doc.units.px(EVENT_META_SP * layoutScale)))
        doc.label(
            doc.i18n.t("ui.next_event").uppercase(doc.i18n.locale),
            small(SECTION_CAPTION_SP, layoutScale),
            doc.role(Role.INK),
            Weight.MEDIUM,
        )
        Spacer(Modifier.height(doc.units.px(EVENT_TIME_GAP_DP * layoutScale)))
        doc.label(
            doc.text(Vars.tile(tileId, "ev.day")),
            EVENT_DAY_SP * layoutScale,
            doc.role(Role.ACCENT_DARK),
            Weight.LIGHT,
        )
        doc.label(
            doc.text(Vars.tile(tileId, "ev.time")),
            EVENT_TIME_SP * layoutScale,
            doc.role(Role.INK),
            TEMP_WEIGHT,
        )
        Spacer(Modifier.height(doc.units.px(MIN_LABEL_SP * layoutScale)))
        doc.label(
            doc.text(Vars.tile(tileId, "ev.title")),
            small(EVENT_TITLE_SP, layoutScale),
            doc.role(Role.INK),
            lines = 2,
        )
        Spacer(Modifier.height(doc.units.px(META_GAP_DP * layoutScale)))
        doc.label(
            doc.text(Vars.tile(tileId, "ev.meta")),
            small(EVENT_META_SP, layoutScale),
            doc.role(Role.INK_SOFT),
        )
    }
}

private fun DocContext.homePeople(tileId: String, people: List<String>, wDp: Float, hDp: Float) {
    val photo = (hDp * PERSON_PHOTO_H_SHARE).coerceAtLeast(EVENT_TIME_GAP_DP)
    scope.Row(
        Modifier.size(units.px(wDp), units.px(hDp)).background(role(Role.HOME_STRIP)),
        horizontal = RcRowHorizontalPositioning.SpaceEvenly,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@homePeople
        for (id in people) {
            doc.bindEntity(id)
            doc.bindWhere(Vars.tile(tileId, "where.$id"), id)
            Row(
                doc.tap(
                    Modifier.padding(doc.units.px(STRIP_ACCENT_H_DP)),
                    ActionRef("e:$id", "card"),
                ),
                vertical = RcVerticalPositioning.Center,
            ) {
                doc.photo(doc.states[id], id, "home.$id", photo)
                Spacer(Modifier.width(doc.units.px(SECTION_GAP_DP)))
                Column(vertical = RcColumnVerticalPositioning.Center) {
                    doc.label(
                        doc.text(Vars.entity(id, Vars.KEY_NAME)),
                        Type.TITLE,
                        doc.role(Role.INK_DARK),
                    )
                    doc.label(
                        doc.text(Vars.tile(tileId, "where.$id")),
                        Type.TITLE,
                        doc.role(Role.ACCENT_SOFT),
                    )
                }
            }
        }
    }
}

/** The outdoor temperature in whole degrees, from a weather entity's attribute or a sensor's state. */
private fun DocContext.bindHomeWeather(tileId: String, weather: String?) {
    bind(weather) { states ->
        val entityState = weather?.let { states[it] }
        val temperature = when {
            entityState == null -> null
            entityState.domain == "weather" -> entityState.number("temperature")
            else -> entityState.numericState()
        }
        listOf(
            VarValue.string(
                Vars.tile(tileId, "temp"),
                temperature?.let { i18n.number(it, 0) + "°" } ?: "–",
            ),
        )
    }
}

/**
 * The next event of [calendars], from the event each calendar entity carries in its attributes (the one under way,
 * or else the next one), so it follows Home Assistant without a fetch.
 */
private fun DocContext.bindNextEvent(tileId: String, calendars: List<String>) {
    for (calendarId in calendars) bind(calendarId) { emptyList() }
    val zone = runCatching { ZoneId.of(
        homeAssistantConfig.timeZone,
    ) }.getOrDefault(ZoneId.of("UTC"))
    bind(null) { states ->
        val next = nextCalendarEvent(calendars, states, zone)
        val day = when {
            next == null -> "—"
            isToday(next, zone) -> i18n.t("ui.today")
            else -> i18n.fullWeekday(next.start)
        }
        val time = when {
            next == null -> "—"
            next.allDay -> i18n.t("ui.all_day")
            else -> i18n.time(next.start)
        }
        listOf(
            VarValue.string(Vars.tile(tileId, "ev.day"), day),
            VarValue.string(Vars.tile(tileId, "ev.time"), time),
            VarValue.string(Vars.tile(tileId, "ev.title"), next?.title ?: i18n.t("ui.no_events")),
            VarValue.string(
                Vars.tile(tileId, "ev.meta"),
                listOfNotNull(
                    next?.calendar,
                    next?.location,
                ).filter { it.isNotBlank() }.joinToString(" · "),
            ),
        )
    }
}
