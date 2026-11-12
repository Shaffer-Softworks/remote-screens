package tech.shaffersoftworks.remotescreens.server.player

import org.junit.Test
import tech.shaffersoftworks.remotescreens.shared.NotificationRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationQueueTest {
    private val queue = NotificationQueue()

    @Test
    fun drainReturnsItemsOldestFirstAndClearsTheDevice() {
        queue.enqueue("one", NotificationRequest("First", targets = listOf("one")), nowMs = 1_000L)
        queue.enqueue(
            "one",
            NotificationRequest("Second", targets = listOf("one"), presentation = "alert"),
            nowMs = 2_000L,
        )
        val items = queue.drain("one", nowMs = 3_000L)
        assertEquals(listOf("First", "Second"), items.map { it.request.title })
        assertTrue(queue.drain("one").isEmpty())
    }

    @Test
    fun samePresentationAndTagReplaceEarlierItems() {
        queue.enqueue("one", NotificationRequest("Old banner", targets = listOf("one"), tag = "laundry"), nowMs = 1L)
        queue.enqueue("one", NotificationRequest("New banner", targets = listOf("one"), tag = "laundry"), nowMs = 2L)
        queue.enqueue(
            "one",
            NotificationRequest("Alert", targets = listOf("one"), presentation = "alert", tag = "garage"),
            nowMs = 3L,
        )
        queue.enqueue(
            "one",
            NotificationRequest("Newer alert", targets = listOf("one"), presentation = "alert"),
            nowMs = 4L,
        )
        val items = queue.drain("one", nowMs = 5L)
        assertEquals(listOf("New banner", "Newer alert"), items.map { it.request.title })
    }

    @Test
    fun expiredItemsAreDroppedOnDrain() {
        queue.enqueue(
            "one",
            NotificationRequest("Gone", targets = listOf("one"), timeout = 10),
            nowMs = 1_000L,
        )
        assertTrue(queue.drain("one", nowMs = 1_000L + 11_000L).isEmpty())
    }

    @Test
    fun wakeIsClearedAfterTheWakeWindow() {
        val request = NotificationRequest("Wake me", targets = listOf("one"), wake = true)
        val item = QueuedNotification(request, enqueuedAtMs = 1_000L)
        assertTrue(queue.deliveryRequest(item, nowMs = 1_000L + 60_000L).wake)
        assertFalse(queue.deliveryRequest(item, nowMs = 1_000L + 6 * 60_000L).wake)
    }
}
