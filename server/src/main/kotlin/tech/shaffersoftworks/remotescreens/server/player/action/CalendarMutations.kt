package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaCopy
import tech.shaffersoftworks.remotescreens.server.data.renderdata.CalendarAgendaStore
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
        val rejected = when {
            summary.isEmpty() -> AgendaMutation.Rejected(AgendaCopy.TITLE_REQUIRED)
            message.entityId.isBlank() -> AgendaMutation.Rejected(AgendaCopy.SELECT_CALENDAR)
            else -> null
        }
        return rejected ?: sendCreate(message, summary)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendCreate(message: PlayerMessage.CalendarCreate, summary: String): AgendaMutation {
        val data = calendarCreateData(message, summary)
        return try {
            homeAssistant.callService("calendar", "create_event", data)
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
        if (message.uid.isBlank() || message.entityId.isBlank()) {
            return AgendaMutation.Rejected(AgendaCopy.CANNOT_DELETE)
        }
        val data = buildJsonObject {
            put("entity_id", message.entityId)
            put("uid", message.uid)
            message.recurrenceId?.takeIf { it.isNotBlank() }?.let { put("recurrence_id", it) }
        }
        return try {
            homeAssistant.command("calendar/event/delete", data)
            agendas.invalidate()
            AgendaMutation.Accepted(AgendaCopy.DELETED)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AgendaMutation.Rejected(error.message ?: DELETE_FAILED)
        }
    }

    private fun calendarCreateData(message: PlayerMessage.CalendarCreate, summary: String): JsonObject =
        buildJsonObject {
            put("entity_id", message.entityId)
            put("summary", summary)
            message.location?.trim()?.takeIf { it.isNotEmpty() }?.let { put("location", it) }
            if (message.allDay) {
                put("start_date", message.startDate)
                put("end_date", message.endDate)
            } else {
                put("start_date_time", localDateTime(message.startDate, message.startTime))
                put("end_date_time", localDateTime(message.endDate, message.endTime))
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
