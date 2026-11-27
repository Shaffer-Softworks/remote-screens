package tech.shaffersoftworks.remotescreens.server.data.screensaver

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MediaSourceNode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SEARCH_SIZE = 5

class ScreensaverCatalogTest {
    @Test
    fun homeAssistantImagesAndOneFolderLevel() {
        assertTrue(
            ScreensaverCatalog.isImage(
                MediaSourceNode(mediaClass = "image", mediaContentType = "image/jpeg"),
            ),
        )

        assertFalse(
            ScreensaverCatalog.isImage(
                MediaSourceNode(mediaClass = "video", mediaContentType = "video/mp4"),
            ),
        )

        assertTrue(
            ScreensaverCatalog.isFolder(
                MediaSourceNode(
                    mediaContentId = "media-source://media_source/local/Photos",
                    mediaClass = "directory",
                    canExpand = true,
                ),
            ),
        )

        assertFalse(
            ScreensaverCatalog.isFolder(
                MediaSourceNode(mediaClass = "directory", canExpand = true),
            ),
        )
    }

    @Test
    fun immichAlbumSearchAndAlbumBodies() {
        val albums = """[{"id":"abc","albumName":"Trips","extra":1},{"id":"def","albumName":"Home"}]"""

        assertEquals("abc", ScreensaverCatalog.albumId(albumsBody = albums, albumName = "trips"))
        assertNull(ScreensaverCatalog.albumId(albumsBody = albums, albumName = "missing"))

        val search = """{"assets":{"items":[{"id":"1","type":"IMAGE"},{"id":"2","type":"VIDEO"}]}}"""
        val album = """{"id":"abc","assets":[{"id":"9","type":"IMAGE"},{"id":"8"}]}"""

        assertEquals(listOf("1"), ScreensaverCatalog.imageIds(search))
        assertEquals(listOf("9", "8"), ScreensaverCatalog.imageIds(album))
    }

    @Test
    fun namedAlbumSearchAsksForThatAlbum() {
        val body = Json.decodeFromString(
            ImmichMetadataSearch.serializer(),
            ScreensaverCatalog.metadataSearch(size = SEARCH_SIZE, albumId = "abc"),
        )

        val library = ScreensaverCatalog.metadataSearch(size = SEARCH_SIZE)

        assertEquals("IMAGE", body.type)
        assertEquals(false, body.isArchived)
        assertEquals(SEARCH_SIZE, body.size)
        assertEquals(listOf("abc"), body.albumIds)
        assertFalse(library.contains("albumIds"))
    }
}
