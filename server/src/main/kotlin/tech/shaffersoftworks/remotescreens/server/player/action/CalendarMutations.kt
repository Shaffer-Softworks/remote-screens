package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.CalendarCreateEvent
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.CalendarEventDelete
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.call
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaCopy
import tech.shaffersoftworks.remotescreens.server.data.renderdata.CalendarAgendaStore
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import kotlinx.coroutines.CancellationException

/** What a calendar create or delete did. */
sealed interface AgendaMutation {
    data class Accepted(val message: String) : AgendaMutation
    data class Rejected(val message: String) : AgendaMutation
}

/** Creates and deletes Home Assistant calendar events the way the RPI calendar screen does. */
class CalendarMutations(
    private val homeAssistant: HomeAssistant,
    private val agendas: CalendarAgendaStore,
) {
    suspend fun create(message: PlayerMessage.CalendarCreate): AgendaMutation {
        val summary = message.summary.trim()
        val calendar = agendas.latest.calendars.firstOrNull { it.entityId == message.entityId }
        val rejected = when {
            summary.isEmpty() -> AgendaMutation.Rejected(AgendaCopy.TITLE_REQUIRED)
            message.entityId.isBlank() || calendar?.canCreate != true ->
                AgendaMutation.Rejected(AgendaCopy.SELECT_CALENDAR)
            else -> null
        }
        return rejected ?: sendCreate(message, summary)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendCreate(message: PlayerMessage.CalendarCreate, summary: String): AgendaMutation {
        return try {
            homeAssistant.call(calendarCreateEvent(message, summary))
            agendas.invalidate()
            AgendaMutation.Accepted(AgendaCopy.CREATED)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AgendaMutation.Rejected(error.message ?: CREATE_FAILED)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun delete(message: PlayerMessage.CalendarDelete): AgendaMutation {
        val calendar = agendas.latest.calendars.firstOrNull { it.entityId == message.entityId }
        if (message.uid.isBlank() || calendar?.canDelete != true) {
            return AgendaMutation.Rejected(AgendaCopy.CANNOT_DELETE)
        }
        val request = CalendarEventDelete(
            entityId = message.entityId,
            uid = message.uid,
            recurrenceId = message.recurrenceId?.takeIf { it.isNotBlank() },
        )
        return try {
            homeAssistant.send(request)
            agendas.invalidate()
            AgendaMutation.Accepted(AgendaCopy.DELETED)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AgendaMutation.Rejected(error.message ?: DELETE_FAILED)
        }
    }

    private fun calendarCreateEvent(message: PlayerMessage.CalendarCreate, summary: String): CalendarCreateEvent {
        val event = CalendarCreateEvent(
            entityId = EntityId(message.entityId),
            summary = summary,
            location = message.location?.trim()?.takeIf { it.isNotEmpty() },
        )
        return if (message.allDay) {
            event.copy(startDate = message.startDate, endDate = message.endDate)
        } else {
            event.copy(
                startDateTime = localDateTime(message.startDate, message.startTime),
                endDateTime = localDateTime(message.endDate, message.endTime),
            )
        }
    }

    private fun localDateTime(date: String, time: String?): String {
        val clock = time?.takeIf { it.isNotBlank() } ?: "00:00"
        val withSeconds = if (clock.length == HOUR_MINUTE_LENGTH) "$clock:00" else clock
        return "$date $withSeconds"
    }

    private companion object {
        const val CREATE_FAILED = "Failed to create event"
        const val DELETE_FAILED = "Failed to delete event"
        const val HOUR_MINUTE_LENGTH = 5
    }
}
