package tech.shaffersoftworks.remotescreens.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class MusicSearchProtocolTest {
    @Test
    fun aLiveSearchReadsBackAsSent() {
        val search = ServerMessage.LiveSearch(
            slot = Slot.MAIN,
            rev = 3,
            componentId = 0x5E_0000,
            tileId = "ma1",
            playerEntityId = "media_player.living_room",
            x = 40,
            y = 120,
            width = 480,
            height = 360,
            page = 3,
        )
        assertEquals(search, Protocol.decodeServer(Protocol.encode(search)))
        val legacy = """{"type":"live_search","slot":"main","rev":3,"componentId":6160384,""" +
            """"tileId":"ma1","playerEntityId":"media_player.living_room"}"""
        val decoded = Protocol.decodeServer(legacy) as ServerMessage.LiveSearch
        assertEquals(-1, decoded.page)
    }

    @Test
    fun aSearchQueryAndResultsRoundTrip() {
        val query = PlayerMessage.SearchQuery(tileId = "ma1", query = "night")
        assertEquals(query, Protocol.decodePlayer(Protocol.encode(query)))

        val results = ServerMessage.SearchResults(
            tileId = "ma1",
            rows = listOf(
                MusicSearchRow("Track · Night Drive", "Various", "demo://1", null),
            ),
        )
        assertEquals(results, Protocol.decodeServer(Protocol.encode(results)))
    }

    @Test
    fun anActionCarriesThePageForRebuilds() {
        val action = PlayerMessage.Action(
            slot = Slot.MAIN,
            rev = 4,
            metadata = "ma1|browse_type|album",
            page = 6,
        )
        assertEquals(action, Protocol.decodePlayer(Protocol.encode(action)))
        val legacy = """{"type":"action","slot":"main","rev":4,"metadata":"ma1|browse_type|album"}"""
        val decoded = Protocol.decodePlayer(legacy) as PlayerMessage.Action
        assertEquals(null, decoded.page)
    }
}
