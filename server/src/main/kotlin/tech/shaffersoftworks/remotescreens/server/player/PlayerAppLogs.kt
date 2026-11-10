package tech.shaffersoftworks.remotescreens.server.player

import org.slf4j.LoggerFactory
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage

private const val MAX_DEVICE_ID = 100
private const val MAX_TAG = 100
private const val MAX_EVENT_ID = 100
private const val MAX_MESSAGE = 4_000
private const val REMEMBERED_EVENTS = 4_096
private val LEVELS = setOf("error", "warning")
private val CONTROL_CHARACTERS = Regex("[\\r\\n\\p{Cntrl}]")

/**
 * Writes tablets' warnings and errors to the add-on log. Event IDs are remembered across reconnects so a retried
 * entry is acknowledged without being written twice; the memory is bounded and lasts until the add-on restarts.
 */
class PlayerAppLogs {
    enum class Outcome { WRITTEN, DUPLICATE, REJECTED }

    private val logger = LoggerFactory.getLogger(PlayerSession::class.java)
    private val written = LinkedHashMap<Pair<String, String>, Unit>()

    /** [canWriteNew] is false while the sending session is over its rate; duplicates are still acknowledged. */
    fun write(deviceId: String, entry: PlayerMessage.AppLog, canWriteNew: Boolean): Outcome {
        val eventId = entry.eventId
        val hasValidId = eventId == null || eventId.isNotBlank() && eventId.length <= MAX_EVENT_ID
        if (entry.level !in LEVELS || !hasValidId) return Outcome.REJECTED
        val key = eventId?.let { deviceId to it }
        return synchronized(written) {
            when {
                key != null && key in written -> Outcome.DUPLICATE
                !canWriteNew -> Outcome.REJECTED
                else -> {
                    log(deviceId, entry)
                    key?.let(::remember)
                    Outcome.WRITTEN
                }
            }
        }
    }

    private fun log(deviceId: String, entry: PlayerMessage.AppLog) {
        val text = "Tablet ${clean(deviceId, MAX_DEVICE_ID)} [${clean(entry.tag, MAX_TAG)}] " +
            "at ${entry.timestampMs}: ${clean(entry.message, MAX_MESSAGE)}"
        if (entry.level == "error") logger.error("{}", text) else logger.warn("{}", text)
    }

    /** Called under the [written] lock. */
    private fun remember(key: Pair<String, String>) {
        written[key] = Unit
        if (written.size > REMEMBERED_EVENTS) written.remove(written.keys.first())
    }

    /** Keeps each event on one line and bounds client-supplied fields. */
    private fun clean(value: String, limit: Int) = value.take(limit).replace(CONTROL_CHARACTERS, " ")
}
