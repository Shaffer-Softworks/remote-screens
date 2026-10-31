package tech.shaffersoftworks.remotescreens.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class NotificationTest {
    @Test
    fun requiresExplicitRecipients() {
        assertThrows(IllegalArgumentException::class.java) { NotificationRequest("Hi").validate() }
        assertThrows(IllegalArgumentException::class.java) {
            NotificationRequest("Hi", all = true, targets = listOf("tablet")).validate()
        }
        NotificationRequest("Hi", all = true).validate()
        NotificationRequest("Hi", targets = listOf("tablet")).validate()
    }

    @Test
    fun validatesPresentationSpecificOptions() {
        assertThrows(IllegalArgumentException::class.java) {
            NotificationRequest("Hi", all = true, timeout = 0).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            NotificationRequest("Hi", all = true, camera = "camera.door").validate()
        }
        NotificationRequest("Hi", all = true, presentation = "alert", timeout = 0, camera = "camera.door").validate()
        assertEquals(10, NotificationRequest("Hi").duration)
        assertEquals(30, NotificationRequest("Hi", presentation = "alert").duration)
        assertFalse(NotificationRequest("Hi").wake)
    }

    @Test
    fun validatesActionsAndText() {
        assertThrows(IllegalArgumentException::class.java) {
            NotificationRequest(" ", all = true).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            NotificationRequest("Hi", all = true, buttons = listOf(NotificationButton("Open", "invalid"))).validate()
        }
        NotificationRequest("Hi", all = true, buttons = listOf(NotificationButton("Open", "lock.open"))).validate()
    }

    @Test
    fun bannerAndActionRoundTripAndLegacyDocumentsWakeByDefault() {
        val banner = ServerMessage.NotificationBanner("id", "Laundry", buttons = listOf("Done"))
        assertEquals(banner, Protocol.decodeServer(Protocol.encode(banner)))
        val action = PlayerMessage.NotificationAction("id", 0)
        assertEquals(action, Protocol.decodePlayer(Protocol.encode(action)))
        val document = Protocol.decodeServer("""{"type":"document","slot":"alert","rev":1,"data":""}""")
            as ServerMessage.Document
        assertEquals(true, document.wakeOnShow)
        val silent = document.copy(wakeOnShow = false)
        assertEquals(silent, Protocol.decodeServer(Protocol.encode(silent)))
    }
}
