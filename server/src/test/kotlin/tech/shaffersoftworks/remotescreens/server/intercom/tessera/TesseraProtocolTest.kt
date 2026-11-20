package tech.shaffersoftworks.remotescreens.server.intercom.tessera

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TesseraProtocolTest {
    @Test
    fun aLiveEventKeepsTheListenAndTalkLinks() {
        val call = assertIs<TesseraInbound.Call>(TesseraProtocol.parse(event(LIVE)))

        assertEquals("station-tablet", call.node)
        assertEquals("kitchen", call.peer)
        assertEquals("ab12", call.key)
        assertEquals(TesseraProtocol.STATE_LIVE, call.state)
        assertEquals("Kitchen", call.name)
        assertEquals("http://screens/listen", call.listenUrl)
        assertEquals("http://screens/talk", call.talkUrl)
    }

    @Test
    fun aContactsEventReadsEachRow() {
        val contacts = assertIs<TesseraInbound.Contacts>(TesseraProtocol.parse(event(CONTACTS)))
        val row = contacts.rows.single()

        assertEquals("kitchen", row.id)
        assertEquals("Kitchen", row.name)
        assertTrue(row.canSound)
        assertEquals("free", row.availability)
        assertFalse(contacts.more)
    }

    @Test
    fun anUnknownOperationIsIgnored() {
        assertNull(TesseraProtocol.parse(event("""{"op":"picture"}""")))
    }

    private fun event(body: String): JsonObject = Json.parseToJsonElement(body.trimIndent()).jsonObject

    private companion object {
        const val LIVE = """
            {
              "node": "station-tablet",
              "peer": "kitchen",
              "op": "call",
              "k": "ab12",
              "s": "live",
              "n": "Kitchen",
              "l": "http://screens/listen",
              "tk": "http://screens/talk"
            }
        """
        const val CONTACTS = """
            {
              "op": "contacts",
              "r": [{"id": "kitchen", "n": "Kitchen", "a": 1, "s": "free"}],
              "more": 0
            }
        """
    }
}
