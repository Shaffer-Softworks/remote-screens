@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.calendar

import java.time.ZoneId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.panels.calendar.calendarEvent
import tech.shaffersoftworks.remotescreens.server.render.panels.calendar.isToday
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer

internal fun DocContext.calendarCard(
    @Suppress("UNUSED_PARAMETER") entityId: String,
    entityState: EntityState?,
) {
    val zone = runCatching {
        ZoneId.of(homeAssistantConfig.timeZone)
    }.getOrDefault(ZoneId.of("UTC"))

    val event = entityState?.let {
        calendarEvent(entityState = it, zone = zone)
    }

    spacer(CardChrome.PANEL_CORNER_DP)

    label(
        text = event?.title ?: i18n.t("ui.no_events"),
        sp = Type.DISPLAY,
        color = role(Role.INK),
        weight = Weight.SEMI_BOLD,
    )

    spacer(CardChrome.FLOW_PAD_DP)

    val whenText = when {
        event == null -> "—"
        event.allDay && isToday(event, zone) -> i18n.t("ui.today") + " · " + i18n.t("ui.all_day")
        event.allDay -> i18n.fullWeekday(event.start) + " · " + i18n.t("ui.all_day")
        isToday(event, zone) -> i18n.t("ui.today") + " · " + i18n.time(event.start)
        else -> i18n.fullWeekday(event.start) + " · " + i18n.time(event.start)
    }

    label(text = whenText, sp = Type.HEADLINE, color = role(Role.INK_SOFT))

    event?.location?.takeIf {
        it.isNotBlank()
    }?.let {
        spacer(CardChrome.FLOW_PAD_DP)
        label(text = it, sp = Type.BODY, color = role(Role.INK_SOFT))
    }
}
