package tech.shaffersoftworks.remotescreens.server.player

import tech.shaffersoftworks.remotescreens.shared.AppLogEntry
import tech.shaffersoftworks.remotescreens.shared.AppLogLevel
import tech.shaffersoftworks.remotescreens.shared.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerAppLogsTest {
    private fun entry(eventId: String) = AppLogEntry(
        eventId = eventId,
        timestampMs = 1L,
        level = AppLogLevel.WARNING,
        tag = "Player",
        message = "message",
    )

    @Test
    fun invalidIdsAreLeftOutOfTheAcknowledgement() {
        val acknowledged = PlayerAppLogs().write("tablet", listOf(entry("ok"), entry(" "), entry("x".repeat(101))))
        assertEquals(listOf("ok"), acknowledged)
    }

    @Test
    fun aBatchOverTheLimitIsNotAcknowledged() {
        val batch = List(Protocol.MAX_APP_LOG_BATCH + 1) { index -> entry("event-$index") }
        assertEquals(emptyList(), PlayerAppLogs().write("tablet", batch))
    }

    @Test
    fun repeatedIdsAreAcknowledgedOnceAndAnotherTabletsSameIdIsNew() {
        val appLogs = PlayerAppLogs()
        assertEquals(listOf("same"), appLogs.write("tablet", listOf(entry("same"), entry("same"))))
        assertEquals(listOf("same"), appLogs.write("tablet", listOf(entry("same"))))
        assertEquals(listOf("same"), appLogs.write("other", listOf(entry("same"))))
    }
}
