package tech.shaffersoftworks.remotescreens.player.data.diagnostics

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LogDatabaseTest {
    private fun entry(id: String) = StoredLog(
        eventId = id,
        timestampMs = 123L,
        level = "error",
        tag = "Player",
        message = "failure",
    )

    private fun inMemoryDatabase() =
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LogDatabase::class.java).build()

    @Test
    fun pendingLogsSurviveReopeningAndAcknowledgementDeletesOnlyItsEntry() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "log-persistence-test.db"
        context.deleteDatabase(name)
        var database = Room.databaseBuilder(context, LogDatabase::class.java, name).build()
        try {
            database.logs().enqueue(entry("one"))
            database.logs().enqueue(entry("two"))
            database.close()
            database = Room.databaseBuilder(context, LogDatabase::class.java, name).build()
            assertEquals("one", database.logs().oldest().first()?.eventId)
            database.logs().acknowledge("one")
            assertEquals("two", database.logs().oldest().first()?.eventId)
            database.logs().acknowledge("two")
            assertNull(database.logs().oldest().first())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun queueIsBoundedAndDuplicateEventIdsAreIgnored() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val logs = database.logs()
            logs.enqueue(entry("same"))
            logs.enqueue(entry("same"))
            assertEquals(1, logs.count())
            for (index in 0..MAX_STORED_LOGS) logs.enqueue(entry("event-$index"))
            assertEquals(MAX_STORED_LOGS, logs.count())
            assertEquals("event-1", logs.oldest().first()?.eventId)
        } finally {
            database.close()
        }
    }

    @Test
    fun aFullQueueDiscardsOrdinaryEntriesBeforeProcessExits() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val logs = database.logs()
            val exitId = "${PROCESS_EXIT_EVENT_PREFIX}123:42"
            logs.enqueue(entry(exitId))
            for (index in 0..MAX_STORED_LOGS) logs.enqueue(entry("event-$index"))
            assertEquals(MAX_STORED_LOGS, logs.count())
            assertEquals(exitId, logs.oldest().first()?.eventId)
            logs.acknowledge(exitId)
            assertEquals("event-2", logs.oldest().first()?.eventId)
        } finally {
            database.close()
        }
    }
}
