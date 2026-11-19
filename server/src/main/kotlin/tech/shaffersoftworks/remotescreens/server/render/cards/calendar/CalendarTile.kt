@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.calendar

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalWeight
import java.time.ZoneId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.panels.calendar.calendarEvent
import tech.shaffersoftworks.remotescreens.server.render.panels.calendar.isToday
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue

/** Next event from a calendar entity: day/time, title, and optional location. */
internal fun DocContext.calendarTile(
    tile: Tile,
    box: TileBox,
    @Suppress("UNUSED_PARAMETER") entityState: EntityState?,
    named: Boolean,
) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))

    val zone = runCatching { ZoneId.of(
        homeAssistantConfig.timeZone,
    ) }.getOrDefault(ZoneId.of("UTC"))

    bind(entityId) { states ->
        val event = states[entityId]?.let { calendarEvent(it, zone) }

        val day = when {
            event == null -> "—"
            isToday(event, zone) -> i18n.t("ui.today")
            else -> i18n.fullWeekday(event.start)
        }

        val time = when {
            event == null -> "—"
            event.allDay -> i18n.t("ui.all_day")
            else -> i18n.time(event.start)
        }

        listOf(
            VarValue.string(Vars.tile(tile.id, "ev.day"), day),
            VarValue.string(Vars.tile(tile.id, "ev.time"), time),
            VarValue.string(Vars.tile(tile.id, "ev.title"), event?.title ?: i18n.t("ui.no_events")),
            VarValue.string(Vars.tile(tile.id, "ev.meta"), event?.location.orEmpty()),
        )
    }

    rcScope.Column(Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP))) {
        val doc = this@calendarTile

        doc.label(
            text = doc.text(Vars.tile(tile.id, "ev.day")),
            sp = Type.CAPTION + 1f,
            color = ink2,
        )

        doc.label(
            text = doc.text(Vars.tile(tile.id, "ev.time")),
            sp = if (box.h >= C.TALL_HEIGHT_DP) Type.HEADLINE else Type.TITLE,
            color = ink,
            weight = Weight.MEDIUM,
        )

        Spacer(Modifier.verticalWeight(1f))

        doc.label(
            text = doc.text(Vars.tile(tile.id, "ev.title")),
            sp = Type.BODY + 1f,
            color = ink,
            weight = Weight.SEMI_BOLD,
            lines = 2,
        )

        if (named) {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
                sp = Type.CAPTION,
                color = ink2,
            )
        }
    }
}
