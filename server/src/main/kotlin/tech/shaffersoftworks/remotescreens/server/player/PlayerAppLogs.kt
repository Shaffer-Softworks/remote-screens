package tech.shaffersoftworks.remotescreens.server.player

import org.slf4j.LoggerFactory
import tech.shaffersoftworks.remotescreens.shared.AppLogEntry
import tech.shaffersoftworks.remotescreens.shared.AppLogLevel
import tech.shaffersoftworks.remotescreens.shared.Protocol

private const val MAX_DEVICE_ID = 100
private const val MAX_TAG = 100
private const val MAX_EVENT_ID = 100
private const val MAX_MESSAGE = 4_000
private const val REMEMBERED_EVENTS = 4_096
private val CONTROL_CHARACTERS = Regex("[\\r\\n\\p{Cntrl}]")

/**
 * Writes tablets' warnings and errors to the add-on log. Event IDs are remembered across reconnects so a retried
 * entry is acknowledged without being written twice; the memory is bounded and lasts until the add-on restarts.
 */
class PlayerAppLogs {
    private val logger = LoggerFactory.getLogger(PlayerSession::class.java)
    private val written = LinkedHashMap<Pair<String, String>, Unit>()

    /** Returns the event IDs to acknowledge: entries written now or earlier. A batch over the limit gets none. */
    fun write(deviceId: String, entries: List<AppLogEntry>): List<String> {
        if (entries.size > Protocol.MAX_APP_LOG_BATCH) return emptyList()

        return synchronized(written) {
            entries.filter { entry -> entry.eventId.isNotBlank() && entry.eventId.length <= MAX_EVENT_ID }
                .onEach { entry ->
                    val key = deviceId to entry.eventId
                    if (key !in written) {
                        log(deviceId, entry)
                        remember(key)
                    }
                }
                .map { it.eventId }
                .distinct()
        }
    }

    private fun log(deviceId: String, entry: AppLogEntry) {
        val text = "Tablet ${clean(deviceId, MAX_DEVICE_ID)} [${clean(entry.tag, MAX_TAG)}] " +
            "at ${entry.timestampMs}: ${clean(entry.message, MAX_MESSAGE)}"
        when (entry.level) {
            AppLogLevel.ERROR -> logger.error("{}", text)
            AppLogLevel.WARNING -> logger.warn("{}", text)
        }
    }

    /** Called under the [written] lock. */
    private fun remember(key: Pair<String, String>) {
        written[key] = Unit
        if (written.size > REMEMBERED_EVENTS) written.remove(written.keys.first())
    }

    /** Keeps each event on one line and bounds client-supplied fields. */
    private fun clean(value: String, limit: Int) = value.take(limit).replace(CONTROL_CHARACTERS, " ")
}
