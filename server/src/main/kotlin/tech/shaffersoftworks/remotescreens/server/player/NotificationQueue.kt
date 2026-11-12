package tech.shaffersoftworks.remotescreens.server.player

import tech.shaffersoftworks.remotescreens.shared.NOTIFICATION_MAX_DURATION
import tech.shaffersoftworks.remotescreens.shared.NotificationRequest
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

private const val MAX_ITEMS_PER_DEVICE = 5
private const val MS_PER_SECOND = 1000L
private const val UNTIL_ACK_TTL_MS = NOTIFICATION_MAX_DURATION * MS_PER_SECOND
private const val WAKE_WINDOW_MS = 5L * 60L * MS_PER_SECOND

/** A notification waiting for a paired tablet to reconnect. */
data class QueuedNotification(
    val request: NotificationRequest,
    val enqueuedAtMs: Long,
)

/**
 * In-memory offline queue for [PlayerHub.notify]. One banner and one alert per device (newest wins);
 * optional [NotificationRequest.tag] replaces an earlier item with the same tag. Expired items are
 * dropped on enqueue and flush.
 */
class NotificationQueue {
    private val byDevice = ConcurrentHashMap<String, ArrayDeque<QueuedNotification>>()

    fun enqueue(
        deviceId: String,
        request: NotificationRequest,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val queue = byDevice.getOrPut(deviceId) { ArrayDeque() }
        synchronized(queue) {
            dropExpiredLocked(queue, nowMs)
            val tag = request.tag
            if (!tag.isNullOrBlank()) {
                queue.removeAll { it.request.tag == tag }
            }
            queue.removeAll { it.request.presentation == request.presentation }
            while (queue.size >= MAX_ITEMS_PER_DEVICE) {
                queue.removeFirst()
            }
            queue.addLast(QueuedNotification(request = request, enqueuedAtMs = nowMs))
        }
    }

    /** Non-expired items for [deviceId], removed from the queue, oldest first. */
    fun drain(
        deviceId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): List<QueuedNotification> {
        val queue = byDevice[deviceId] ?: return emptyList()
        return synchronized(queue) {
            dropExpiredLocked(queue, nowMs)
            val items = queue.toList()
            queue.clear()
            byDevice.remove(deviceId, queue)
            items
        }
    }

    /** Request to deliver, with wake cleared when the item is older than [WAKE_WINDOW_MS]. */
    fun deliveryRequest(item: QueuedNotification, nowMs: Long = System.currentTimeMillis()): NotificationRequest {
        val keepWake = item.request.wake && nowMs - item.enqueuedAtMs <= WAKE_WINDOW_MS
        return if (keepWake == item.request.wake) item.request else item.request.copy(wake = false)
    }

    fun ttlMs(request: NotificationRequest): Long {
        if (request.presentation == "alert" && request.duration == 0) return UNTIL_ACK_TTL_MS
        return request.duration * MS_PER_SECOND
    }

    private fun dropExpiredLocked(queue: ArrayDeque<QueuedNotification>, nowMs: Long) {
        queue.removeAll { nowMs - it.enqueuedAtMs > ttlMs(it.request) }
    }
}
