@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.calendar

import tech.shaffersoftworks.remotescreens.server.data.renderdata.AGENDA_FIELD_SEPARATOR
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaCopy
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaEvent
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaSnapshot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.spinner
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val AGENDA_REFRESH_SECONDS = 60

private object AgendaLayout {
    const val PAD_H = 24f
    const val PAD_V = 16f
    const val TITLE_GAP = 12f
    const val AFTER_RULE = 16f
    const val RULE = 2f
    const val LIST_GAP = 8f
    const val LIST_BOTTOM = 88f
    const val HEADER_TOP = 8f
    const val HEADER_BOTTOM = 4f
    const val ROW_H = 72f
    const val ROW_PAD_H = 16f
    const val ROW_PAD_V = 10f
    const val TIME_W = 110f
    const val META_GAP = 2f
    const val TITLE_SP = 28f
    const val SECTION_SP = 20f
    const val ROW_TITLE_SP = 22f
    const val TIME_SP = 18f
    const val META_SP = 16f
    const val SPINNER = 40f
    const val SPINNER_GAP = 16f
    const val FAB = 56f
    const val FAB_RADIUS = 28f
    const val FAB_INSET = 24f
    const val FAB_ICON = 24f
}

/** RPI calendar agenda colours. Fixed, so a light document theme still draws this page. */
private object AgendaColor {
    const val BACKGROUND = 0xFF333333.toInt()
    const val SECTION = 0xFF1DA48C.toInt()
    const val ROW = 0xFF3B3B3B.toInt()
    const val SECONDARY = 0xFFCACACA.toInt()
    const val DIVIDER = 0xFF4A4A4A.toInt()
    const val TEAL = 0xFF03DAC5.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
}

private val AGENDA_DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val AGENDA_TIME = DateTimeFormatter.ofPattern("h:mma", Locale.US)

/** The RPI calendar page: upcoming events, a teal add button, and hold-to-delete on a row. */
fun DocContext.calendarPanel(tile: Tile, box: TileBox) {
    val snapshot = data.agenda ?: AgendaSnapshot(isLoading = true)
    val zone = agendaZone()
    val calendarIds = (snapshot.calendars.map { it.entityId } + snapshot.events.map { it.entityId }).distinct()
    for (entityId in calendarIds) {
        structural(entityId) { entity ->
            listOf(
                entity?.attr("message"),
                entity?.attr("start_time"),
                entity?.features()?.toString(),
            ).joinToString("|")
        }
    }
    refreshEvery(AGENDA_REFRESH_SECONDS)
    val background = fixed(AgendaColor.BACKGROUND)
    val showAdd = supportsCalendarDialogs && snapshot.calendars.any { it.canCreate }
    scope.Box(Modifier.size(units.px(box.w), units.px(box.h)).background(background)) {
        this@calendarPanel.agendaColumn(tile.id, snapshot, zone)
        if (showAdd) this@calendarPanel.agendaAddButton(tile.id)
    }
}

private fun DocContext.agendaColumn(tileId: String, snapshot: AgendaSnapshot, zone: ZoneId) {
    val teal = fixed(AgendaColor.TEAL)
    val white = fixed(AgendaColor.WHITE)
    scope.Column(
        Modifier.fillMaxSize().padding(
            units.px(AgendaLayout.PAD_H),
            units.px(AgendaLayout.PAD_V),
            units.px(AgendaLayout.PAD_H),
            units.px(AgendaLayout.PAD_V),
        ),
    ) {
        val doc = this@agendaColumn
        doc.label(AgendaCopy.TITLE, AgendaLayout.TITLE_SP, white, Weight.LIGHT)
        Spacer(Modifier.height(doc.units.px(AgendaLayout.TITLE_GAP)))
        Box(Modifier.fillMaxWidth().height(doc.units.px(AgendaLayout.RULE)).background(teal))
        val notice = doc.data.agendaNotices[tileId]
        if (!notice.isNullOrBlank()) {
            Spacer(Modifier.height(doc.units.px(AgendaLayout.TITLE_GAP)))
            doc.label(notice, AgendaLayout.META_SP, teal)
        }
        Spacer(Modifier.height(doc.units.px(AgendaLayout.AFTER_RULE)))
        doc.agendaBody(tileId, snapshot, zone)
    }
}

private fun DocContext.agendaBody(tileId: String, snapshot: AgendaSnapshot, zone: ZoneId) {
    val message = snapshot.errorMessage
    when {
        snapshot.isLoading && snapshot.events.isEmpty() -> agendaStatus(AgendaCopy.LOADING, showSpinner = true)
        message != null && snapshot.events.isEmpty() -> agendaStatus(
            message.ifBlank { AgendaCopy.ERROR },
            showSpinner = false,
        )
        snapshot.events.isEmpty() -> agendaStatus(AgendaCopy.EMPTY, showSpinner = false)
        else -> agendaList(tileId, snapshot.events, zone)
    }
}

private fun DocContext.agendaStatus(message: String, showSpinner: Boolean) {
    val color = fixed(if (showSpinner) AgendaColor.SECONDARY else AgendaColor.WHITE)
    scope.Box(
        Modifier.fillMaxWidth().verticalWeight(1f),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        Column {
            val doc = this@agendaStatus
            if (showSpinner) {
                doc.spinner(AgendaLayout.SPINNER, doc.fixed(AgendaColor.TEAL))
                Spacer(Modifier.height(doc.units.px(AgendaLayout.SPINNER_GAP)))
            }
            doc.label(message, AgendaLayout.ROW_TITLE_SP, color)
        }
    }
}

private fun DocContext.agendaList(tileId: String, events: List<AgendaEvent>, zone: ZoneId) {
    val grouped = linkedMapOf<String, List<AgendaEvent>>()
    for (event in events) {
        val label = agendaDay(event, zone)
        grouped[label] = grouped[label].orEmpty() + event
    }
    scope.Column(Modifier.fillMaxWidth().verticalWeight(1f).verticalScroll()) {
        val doc = this@agendaList
        var shown = 0
        for ((label, dayEvents) in grouped) {
            if (shown > 0) Spacer(Modifier.height(doc.units.px(AgendaLayout.LIST_GAP)))
            shown += 1
            doc.agendaDayHeader(label)
            for (event in dayEvents) {
                Spacer(Modifier.height(doc.units.px(AgendaLayout.LIST_GAP)))
                doc.agendaRow(tileId, event, zone)
            }
        }
        Spacer(Modifier.height(doc.units.px(AgendaLayout.LIST_BOTTOM)))
    }
}

private fun DocContext.agendaDayHeader(label: String) {
    label(
        label,
        AgendaLayout.SECTION_SP,
        fixed(AgendaColor.SECTION),
        Weight.SEMI_BOLD,
        modifier = Modifier.padding(
            0f,
            units.px(AgendaLayout.HEADER_TOP),
            0f,
            units.px(AgendaLayout.HEADER_BOTTOM),
        ),
    )
}

private fun DocContext.agendaRow(tileId: String, event: AgendaEvent, zone: ZoneId) {
    val hold = event.uid?.takeIf { supportsCalendarDialogs && event.canDelete }?.let { uid ->
        ActionRef(
            tileId,
            "calendar_delete",
            listOf(event.entityId, uid, event.recurrenceId.orEmpty()).joinToString(AGENDA_FIELD_SEPARATOR),
        )
    }
    val row = Modifier.fillMaxWidth()
        .height(units.px(AgendaLayout.ROW_H))
        .background(fixed(AgendaColor.ROW))
        .padding(
            units.px(AgendaLayout.ROW_PAD_H),
            units.px(AgendaLayout.ROW_PAD_V),
            units.px(AgendaLayout.ROW_PAD_H),
            units.px(AgendaLayout.ROW_PAD_V),
        )
    scope.Row(tap(row, null, hold), vertical = RcVerticalPositioning.Center) {
        val doc = this@agendaRow
        doc.label(
            agendaTime(event, zone),
            AgendaLayout.TIME_SP,
            doc.fixed(AgendaColor.WHITE),
            Weight.LIGHT,
            modifier = Modifier.width(doc.units.px(AgendaLayout.TIME_W)),
        )
        Box(
            Modifier.fillMaxHeight().width(doc.units.px(AgendaLayout.RULE)).background(doc.fixed(AgendaColor.DIVIDER)),
        )
        Spacer(Modifier.width(doc.units.px(AgendaLayout.ROW_PAD_H)))
        Column(Modifier.horizontalWeight(1f)) {
            doc.label(event.summary, AgendaLayout.ROW_TITLE_SP, doc.fixed(AgendaColor.WHITE))
            Spacer(Modifier.height(doc.units.px(AgendaLayout.META_GAP)))
            doc.label(agendaMeta(event), AgendaLayout.META_SP, doc.fixed(AgendaColor.SECONDARY))
        }
    }
}

private fun DocContext.agendaAddButton(tileId: String) {
    val inset = units.px(AgendaLayout.FAB_INSET)
    scope.Box(
        Modifier.fillMaxSize().padding(inset, inset, inset, inset),
        horizontal = RcHorizontalPositioning.End,
        vertical = RcVerticalPositioning.Bottom,
    ) {
        val doc = this@agendaAddButton
        Box(
            doc.tap(
                Modifier.size(doc.units.px(AgendaLayout.FAB))
                    .clip(doc.rounded(AgendaLayout.FAB_RADIUS))
                    .background(doc.fixed(AgendaColor.TEAL)),
                ActionRef(tileId, "calendar_create"),
            ),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            doc.icon("plus", AgendaLayout.FAB_ICON, doc.fixed(AgendaColor.BACKGROUND))
        }
    }
}

private fun DocContext.agendaZone(): ZoneId =
    runCatching { ZoneId.of(homeAssistantConfig.timeZone) }.getOrDefault(ZoneId.of("UTC"))

private fun agendaDay(event: AgendaEvent, zone: ZoneId): String {
    val start = event.start ?: return ""
    return start.atZone(zone).format(AGENDA_DAY)
}

private fun agendaTime(event: AgendaEvent, zone: ZoneId): String {
    val start = event.start
    return when {
        event.isAllDay -> AgendaCopy.ALL_DAY
        start == null -> ""
        else -> start.atZone(zone).format(AGENDA_TIME)
    }
}

private fun DocContext.agendaMeta(event: AgendaEvent): String {
    val hold = if (supportsCalendarDialogs && event.canDelete) AgendaCopy.HOLD_SUFFIX else ""
    return listOfNotNull(
        event.calendarName.takeIf { it.isNotBlank() },
        event.location,
    ).joinToString(AgendaCopy.META_SEPARATOR) + hold
}
