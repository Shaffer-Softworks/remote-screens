package tech.shaffersoftworks.remotescreens.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationDrawerProtocolTest {
    @Test
    fun aNativeDrawerReadsBackWithItsPagesAndSettings() {
        val message = ServerMessage.Document(
            slot = Slot.MAIN,
            rev = 2,
            data = "",
            navigationDrawer = NavigationDrawer(
                title = "Kitchen",
                items = listOf(
                    NavigationDrawerItem(title = "Home", iconPath = "M0,0L24,24", page = 0),
                    NavigationDrawerItem(title = "Settings"),
                ),
                side = "end",
                width = 320,
            ),
        )
        assertEquals(message, Protocol.decodeServer(Protocol.encode(message)))
    }

    @Test
    fun olderDocumentsHaveNoNativeDrawer() {
        val message = Protocol.decodeServer(
            """{"type":"document","slot":"main","rev":1,"data":""}""",
        ) as ServerMessage.Document
        assertNull(message.navigationDrawer)
    }
}
