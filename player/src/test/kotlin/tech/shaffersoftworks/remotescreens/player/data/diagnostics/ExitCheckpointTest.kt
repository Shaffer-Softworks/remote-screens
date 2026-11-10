package tech.shaffersoftworks.remotescreens.player.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class ExitCheckpointTest {
    @Test
    fun aFullySentBatchAdvancesToItsNewestExit() {
        assertEquals(30L, exitCheckpoint(timestamps = listOf(10L, 20L, 30L), firstUnsent = 3, previous = 5L))
    }

    @Test
    fun anEmptyBatchKeepsThePreviousCheckpoint() {
        assertEquals(5L, exitCheckpoint(timestamps = emptyList(), firstUnsent = 0, previous = 5L))
    }

    @Test
    fun aRejectionAdvancesOnlyPastTheExitsAlreadySent() {
        assertEquals(20L, exitCheckpoint(timestamps = listOf(10L, 20L, 30L), firstUnsent = 2, previous = 5L))
        assertEquals(5L, exitCheckpoint(timestamps = listOf(10L, 20L, 30L), firstUnsent = 0, previous = 5L))
    }

    @Test
    fun aRejectedExitSharingATimestampStaysPending() {
        assertEquals(10L, exitCheckpoint(timestamps = listOf(10L, 20L, 20L), firstUnsent = 2, previous = 5L))
    }
}
