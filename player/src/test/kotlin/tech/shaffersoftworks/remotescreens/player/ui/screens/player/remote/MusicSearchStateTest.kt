package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import tech.shaffersoftworks.remotescreens.shared.MusicSearchRow
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MusicSearchStateTest {
    @Test
    fun searchClearsWhenDocumentIsReplaced() {
        val withSearch = RemoteScreenState()
            .withDocument(
                ServerMessage.Document(slot = Slot.MAIN, rev = 1, data = ""),
                byteArrayOf(1),
            )
            .withSearch(
                Slot.MAIN,
                SlotSearch(1, 0x5E_0000, "ma1", "media_player.living_room"),
            )
        assertEquals("ma1", withSearch.playingSearch?.second?.tileId)

        val replaced = withSearch.withDocument(
            ServerMessage.Document(slot = Slot.MAIN, rev = 2, data = ""),
            byteArrayOf(2),
        )
        assertNull(replaced.playingSearch)
    }

    @Test
    fun searchResultsAttachToOpenSearch() {
        val state = RemoteScreenState()
            .withDocument(
                ServerMessage.Document(slot = Slot.MAIN, rev = 1, data = ""),
                byteArrayOf(1),
            )
            .withSearch(
                Slot.MAIN,
                SlotSearch(1, 0x5E_0000, "ma1", "media_player.living_room"),
            )
            .withSearchResults(
                "ma1",
                listOf(MusicSearchRow("Night Drive", "Various", "demo://1")),
            )
        assertEquals(1, state.playingSearch?.second?.rows?.size)
        assertEquals("Night Drive", state.playingSearch?.second?.rows?.first()?.title)
    }

    @Test
    fun searchHidesWhenTheTabletLeavesItsPage() {
        val onMusic = RemoteScreenState()
            .withDocument(
                ServerMessage.Document(slot = Slot.MAIN, rev = 1, data = "", pageCount = 4),
                byteArrayOf(1),
            )
            .withPage(3)
            .withSearch(
                Slot.MAIN,
                SlotSearch(1, 0x5E_0000, "ma1", "media_player.living_room", page = 3),
            )
        assertNotNull(onMusic.playingSearch)
        assertNull(onMusic.withPage(0).playingSearch)
        assertNotNull(onMusic.withPage(3).playingSearch)
    }
}
