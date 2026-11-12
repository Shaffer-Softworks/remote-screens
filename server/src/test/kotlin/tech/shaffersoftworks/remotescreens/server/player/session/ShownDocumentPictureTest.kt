package tech.shaffersoftworks.remotescreens.server.player.session

import org.junit.Test
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantConfig
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.server.render.theme.Units
import tech.shaffersoftworks.remotescreens.shared.Slot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShownDocumentPictureTest {
    private fun shown(): ShownDocument {
        val docContext = DocContext(
            Units(1f),
            Themes.RPI,
            I18n("en", emptyMap(), emptyMap(), true),
            emptyMap(),
            HomeAssistantConfig(),
            RenderData(),
        )
        return ShownDocument(
            slot = Slot.MAIN,
            renderedDoc = RenderedDoc(ByteArray(0), docContext),
            rebuild = { null },
        )
    }

    @Test
    fun aFailedFetchLeavesTheKeyUnsetSoTheNextSendRetries() {
        val document = shown()
        assertTrue(
            document.beginPictureFetch(
                name = "cam.still",
                sourceKey = "/api/camera_proxy/camera.front",
                nowMs = 1_000L,
                isForced = true,
                refreshSeconds = 0,
            ),
        )
        document.finishPictureFetch(
            name = "cam.still",
            sourceKey = "/api/camera_proxy/camera.front",
            nowMs = 1_100L,
            isSuccess = false,
        )
        assertNull(document.pictureKeys["cam.still"])
        assertNull(document.pictureInflightKeys["cam.still"])
        assertTrue(
            document.beginPictureFetch(
                name = "cam.still",
                sourceKey = "/api/camera_proxy/camera.front",
                nowMs = 2_000L,
                isForced = false,
                refreshSeconds = 0,
            ),
        )
    }

    @Test
    fun aSuccessfulFetchSkipsUntilTheKeyChangesOrTheSlotIsDue() {
        val document = shown()
        val sourceKey = "/api/camera_proxy/camera.front"
        assertTrue(
            document.beginPictureFetch(
                name = "cam.still",
                sourceKey = sourceKey,
                nowMs = 1_000L,
                isForced = true,
                refreshSeconds = 4,
            ),
        )
        document.finishPictureFetch(
            name = "cam.still",
            sourceKey = sourceKey,
            nowMs = 1_050L,
            isSuccess = true,
        )
        assertEquals(sourceKey, document.pictureKeys["cam.still"])
        assertEquals(1_050L, document.pictureSentAtMs["cam.still"])
        assertFalse(
            document.beginPictureFetch(
                name = "cam.still",
                sourceKey = sourceKey,
                nowMs = 2_000L,
                isForced = false,
                refreshSeconds = 4,
            ),
        )
        assertTrue(
            document.beginPictureFetch(
                name = "cam.still",
                sourceKey = sourceKey,
                nowMs = 6_000L,
                isForced = false,
                refreshSeconds = 4,
            ),
        )
        document.finishPictureFetch(
            name = "cam.still",
            sourceKey = sourceKey,
            nowMs = 6_100L,
            isSuccess = true,
        )
        assertTrue(
            document.beginPictureFetch(
                name = "cam.still",
                sourceKey = "/api/camera_proxy/camera.side",
                nowMs = 6_200L,
                isForced = false,
                refreshSeconds = 4,
            ),
        )
    }

    @Test
    fun aSupersededFetchDoesNotClearTheNewerInflightOrMarkTheOldKey() {
        val document = shown()
        assertTrue(
            document.beginPictureFetch(
                name = "person",
                sourceKey = "old",
                nowMs = 1L,
                isForced = true,
                refreshSeconds = 0,
            ),
        )
        assertTrue(
            document.beginPictureFetch(
                name = "person",
                sourceKey = "new",
                nowMs = 2L,
                isForced = true,
                refreshSeconds = 0,
            ),
        )
        assertEquals("new", document.pictureInflightKeys["person"])
        document.finishPictureFetch(
            name = "person",
            sourceKey = "old",
            nowMs = 3L,
            isSuccess = true,
        )
        assertEquals("new", document.pictureInflightKeys["person"])
        assertNull(document.pictureKeys["person"])
        document.finishPictureFetch(
            name = "person",
            sourceKey = "new",
            nowMs = 4L,
            isSuccess = true,
        )
        assertNull(document.pictureInflightKeys["person"])
        assertEquals("new", document.pictureKeys["person"])
        assertEquals(4L, document.pictureSentAtMs["person"])
    }

    @Test
    fun anInflightKeyIsNotStartedTwice() {
        val document = shown()
        assertTrue(
            document.beginPictureFetch(
                name = "cam.still",
                sourceKey = "same",
                nowMs = 1L,
                isForced = true,
                refreshSeconds = 0,
            ),
        )
        assertFalse(
            document.beginPictureFetch(
                name = "cam.still",
                sourceKey = "same",
                nowMs = 2L,
                isForced = true,
                refreshSeconds = 0,
            ),
        )
    }
}
