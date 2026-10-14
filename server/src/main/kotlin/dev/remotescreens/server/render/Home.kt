@file:Suppress("RestrictedApi")

package dev.remotescreens.server.render

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
import dev.remotescreens.server.data.homeassistant.EntityState
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.Tile
import dev.remotescreens.shared.VarValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** The size the RPI home page was drawn for; the page scales from it. */
private const val BASE_W = 1280f
private const val BASE_H = 360f

/** Small labels stop shrinking here so they stay readable on 7" panels. */
private fun small(base: Float, k: Float) = maxOf(base * k, 12f)

/**
 * The RPI home page: the weather and the next calendar event on teal, and who is home on a white strip below.
 * The weather is the tile's entity (or the first weather entity); `calendars` and `people` options list entity ids,
 * all calendars and all people when left out.
 */
fun DocContext.homeTile(tile: Tile, box: TileBox) {
    fun listed(key: String, domain: String) = tile.options[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: states.keys.filter { it.startsWith("$domain.") }.sorted()
    val weather = tile.entityId ?: states.keys.sorted().firstOrNull { it.startsWith("weather.") }
    val calendars = listed("calendars", "calendar")
    val people = listed("people", "person")

    val stripH = if (people.isEmpty()) 0f else minOf(120f, box.h * 0.24f)
    val upperH = box.h - stripH
    val k = minOf(box.w / BASE_W, upperH / BASE_H).coerceIn(0.4f, 1.25f)

    bindHomeWeather(tile.id, weather)
    bindNextEvent(tile.id, calendars)

    scope.Column(Modifier.fillMaxSize().background(role(Role.HOME))) {
        val c = this@homeTile
        Row(Modifier.size(c.u.px(box.w), c.u.px(upperH))) {
            c.homeWeather(tile.id, weather, box.w * 0.5f, k)
            Spacer(Modifier.width(c.u.px(box.w * 0.08f)))
            c.homeEvent(tile.id, box.w * 0.42f, k)
        }
        if (stripH > 0f) c.homePeople(tile.id, people, box.w, stripH)
    }
}

private fun DocContext.homeWeather(tileId: String, weather: String?, wDp: Float, k: Float) {
    val e = weather?.let { states[it] }
    if (weather != null) structural(weather) { it?.state ?: "" }
    val ref = weather?.let { ActionRef("e:$it", "card") }
    scope.Column(tap(Modifier.width(u.px(wDp)).fillMaxHeight().padding(u.px(50f * k), u.px(30f * k), 0f, 0f), ref)) {
        val c = this@homeWeather
        Box(Modifier.size(c.u.px(150f * k), c.u.px(10f * k)).background(c.role(Role.ACCENT_DARK)))
        Spacer(Modifier.height(c.u.px(20f * k)))
        c.label(c.i18n.t("ui.outside_now").uppercase(c.i18n.locale), small(20f, k), c.role(Role.INK))
        Row(vertical = RcVerticalPositioning.Center) {
            c.icon(Present.weatherIcon(e?.state ?: ""), 110f * k, c.role(Role.INK))
            Spacer(Modifier.width(c.u.px(20f * k)))
            c.label(c.text(V.t(tileId, "temp")), 150f * k, c.role(Role.INK), 100f)
        }
    }
}

private fun DocContext.homeEvent(tileId: String, wDp: Float, k: Float) {
    scope.Column(Modifier.width(u.px(wDp)).fillMaxHeight().padding(u.px(48f * k), u.px(30f * k), u.px(32f * k), u.px(16f * k))) {
        val c = this@homeEvent
        Box(Modifier.size(c.u.px(150f * k), c.u.px(8f * k)).background(c.role(Role.ACCENT_DARK)))
        Spacer(Modifier.height(c.u.px(16f * k)))
        c.label(c.i18n.t("ui.next_event").uppercase(c.i18n.locale), small(18f, k), c.role(Role.INK), 500f)
        Spacer(Modifier.height(c.u.px(28f * k)))
        c.label(c.text(V.t(tileId, "ev.day")), 32f * k, c.role(Role.ACCENT_DARK), 300f)
        c.label(c.text(V.t(tileId, "ev.time")), 72f * k, c.role(Role.INK), 100f)
        Spacer(Modifier.height(c.u.px(12f * k)))
        c.label(c.text(V.t(tileId, "ev.title")), small(22f, k), c.role(Role.INK), lines = 2)
        Spacer(Modifier.height(c.u.px(6f * k)))
        c.label(c.text(V.t(tileId, "ev.meta")), small(16f, k), c.role(Role.INK_SOFT))
    }
}

private fun DocContext.homePeople(tileId: String, people: List<String>, wDp: Float, hDp: Float) {
    val photo = (hDp * 0.58f).coerceAtLeast(28f)
    scope.Row(
        Modifier.size(u.px(wDp), u.px(hDp)).background(role(Role.HOME_STRIP)),
        horizontal = RcRowHorizontalPositioning.SpaceEvenly, vertical = RcVerticalPositioning.Center,
    ) {
        val c = this@homePeople
        for (id in people) {
            c.bindEntity(id)
            c.bindWhere(V.t(tileId, "where.$id"), id)
            Row(c.tap(Modifier.padding(c.u.px(8f)), ActionRef("e:$id", "card")), vertical = RcVerticalPositioning.Center) {
                c.photo(c.states[id], id, "home.$id", photo)
                Spacer(Modifier.width(c.u.px(20f)))
                Column(vertical = RcColumnVerticalPositioning.Center) {
                    c.label(c.text(V.e(id, "name")), Type.TITLE, c.role(Role.INK_DARK))
                    c.label(c.text(V.t(tileId, "where.$id")), Type.TITLE, c.role(Role.ACCENT_SOFT))
                }
            }
        }
    }
}

/** The outdoor temperature in whole degrees, from a weather entity's attribute or a sensor's state. */
private fun DocContext.bindHomeWeather(tileId: String, weather: String?) {
    bind(weather) { states ->
        val e = weather?.let { states[it] }
        val t = when {
            e == null -> null
            e.domain == "weather" -> e.number("temperature")
            else -> e.numericState()
        }
        listOf(VarValue.string(V.t(tileId, "temp"), t?.let { i18n.number(it, 0) + "°" } ?: "–"))
    }
}

/**
 * The next event of [calendars], from the event each calendar entity carries in its attributes (the one under way,
 * or else the next one), so it follows Home Assistant without a fetch.
 */
private fun DocContext.bindNextEvent(tileId: String, calendars: List<String>) {
    for (c in calendars) bind(c) { emptyList() }
    val zone = runCatching { ZoneId.of(ha.timeZone) }.getOrDefault(ZoneId.of("UTC"))
    bind(null) { states ->
        val now = ZonedDateTime.now(zone)
        val next = calendars.mapNotNull { id -> states[id]?.let { calendarEvent(it, zone) } }
            .filter { it.end == null || !it.end.isBefore(now) }
            .minByOrNull { it.start }
        val day = when {
            next == null -> "—"
            next.start.toLocalDate() == LocalDate.now(zone) -> i18n.t("ui.today")
            else -> i18n.fullWeekday(next.start)
        }
        val time = when {
            next == null -> "—"
            next.allDay -> i18n.t("ui.all_day")
            else -> i18n.time(next.start)
        }
        listOf(
            VarValue.string(V.t(tileId, "ev.day"), day),
            VarValue.string(V.t(tileId, "ev.time"), time),
            VarValue.string(V.t(tileId, "ev.title"), next?.title ?: i18n.t("ui.no_events")),
            VarValue.string(V.t(tileId, "ev.meta"), listOfNotNull(next?.calendar, next?.location).filter { it.isNotBlank() }.joinToString(" · ")),
        )
    }
}

private data class CalendarEvent(val title: String, val start: ZonedDateTime, val end: ZonedDateTime?, val allDay: Boolean, val calendar: String, val location: String?)

private val HA_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun calendarEvent(e: EntityState, zone: ZoneId): CalendarEvent? {
    fun time(key: String) = e.attr(key)?.let { s -> runCatching { LocalDateTime.parse(s, HA_LOCAL).atZone(zone) }.getOrNull() }
    val title = e.attr("message") ?: return null
    val start = time("start_time") ?: return null
    return CalendarEvent(title, start, time("end_time"), e.attr("all_day") == "true", e.name, e.attr("location"))
}
