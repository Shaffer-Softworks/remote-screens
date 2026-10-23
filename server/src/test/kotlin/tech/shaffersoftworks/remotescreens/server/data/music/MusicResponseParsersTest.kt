package tech.shaffersoftworks.remotescreens.server.data.music

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicResponseParsersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun libraryParsesHaWebsocketResponseWrapper() {
        val body = buildJsonObject {
            putJsonObject("context") { put("id", "ctx") }
            putJsonObject("response") {
                putJsonArray("items") {
                    add(
                        buildJsonObject {
                            put("uri", "library://track/1")
                            put("name", "Ænema")
                            putJsonArray("artists") {
                                add(buildJsonObject { put("name", "TOOL") })
                            }
                            put("image", "https://example.com/art.jpg")
                        },
                    )
                }
            }
        }
        val rows = MusicResponseParsers.parseLibrary(body)
        assertEquals(1, rows.size)
        assertEquals("Library · Ænema", rows[0].title)
        assertEquals("TOOL", rows[0].subtitle)
        assertEquals("library://track/1", rows[0].uri)
        assertEquals("https://example.com/art.jpg", rows[0].imagePathOrUrl)
    }

    @Test
    fun queueParsesHaResponseKeyedByEntity() {
        val entityId = "media_player.office"
        val body = buildJsonObject {
            putJsonObject("response") {
                putJsonObject(entityId) {
                    put("queue_id", "q1")
                    put("items", 5)
                    putJsonObject("current_item") {
                        put("queue_item_id", "c1")
                        put("name", "Now")
                        putJsonObject("media_item") {
                            put("uri", "demo://1")
                            put("name", "Now")
                        }
                    }
                    putJsonObject("next_item") {
                        put("queue_item_id", "n1")
                        put("name", "Next")
                        putJsonObject("media_item") {
                            put("uri", "demo://2")
                            put("name", "Next")
                        }
                    }
                }
            }
        }
        val queue = MusicResponseParsers.parseQueue(body, entityId)
        assertEquals(2, queue.rows.size)
        assertTrue(queue.rows[0].isCurrentPlayback)
        assertEquals(3, queue.moreCount)
    }

    @Test
    fun searchParsesServiceResponseDemoShape() {
        val body = json.parseToJsonElement(
            """
            {"service_response":{"tracks":[{"uri":"demo://t","name":"Night","artist":"Demo"}]}}
            """.trimIndent(),
        )
        val rows = MusicResponseParsers.parseSearch(body)
        assertEquals(1, rows.size)
        assertEquals("Track · Night", rows[0].title)
    }
}
