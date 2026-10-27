package tech.shaffersoftworks.remotescreens.server.data.pictures

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.layout.LayoutRepository
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.service.ScreenService
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundStoreTest {
    @Test
    fun aJpegOrPngIsKeptAndAnythingElseIsRefused() {
        val store = BackgroundStore(Files.createTempDirectory("backgrounds").toFile())
        val jpegToken = store.save("s", "home", jpeg())
        val pngToken = store.save("s", "more", png())

        val jpegFile = assertNotNull(store.read("s", "home", jpegToken))
        assertFalse(jpegFile.isPng)
        assertTrue(assertNotNull(store.read("s", "more", pngToken)).isPng)
        assertFailsWith<BackgroundRejected> {
            store.save("s", "home", "not a picture".encodeToByteArray())
        }
        assertFailsWith<BackgroundRejected> {
            store.save("s", "home", ByteArray(BackgroundStore.MAX_BYTES + 1))
        }
        assertFailsWith<BackgroundRejected> { store.save("../s", "home", jpeg()) }
    }

    @Test
    fun aPathThatLeavesTheFolderIsRefused() {
        val store = BackgroundStore(Files.createTempDirectory("backgrounds").toFile())
        val token = store.save("s", "home", jpeg())
        val source = assertNotNull(BackgroundStore.pictureSource("s", "home", token))

        assertNotNull(store.resolve(source))
        assertNull(store.resolve("file:../secret"))
        assertNull(store.resolve("file:/etc/passwd"))
        assertNull(store.resolve("file:s/../../etc/passwd"))
        assertNull(store.resolve("$source.jpg"))
        assertNull(BackgroundStore.pictureSource("../s", "home", token))
    }

    @Test
    fun theRepositoryReadsTheJpegAndNoticesWhenItChanges() = runBlocking {
        val store = BackgroundStore(Files.createTempDirectory("backgrounds").toFile())
        val token = store.save("s", "home", jpeg())
        val source = assertNotNull(BackgroundStore.pictureSource("s", "home", token))
        val slot = PictureSlot("bg.home", source, SAMPLE_SIZE, SAMPLE_SIZE, 0)
        HttpClient(CIO).use { client ->
            val repository = PictureRepository(DemoHome(), client, backgroundStore = store)
            assertNotNull(repository.load(slot, emptyMap()))
            val first = repository.sourceKey(slot, emptyMap())
            assertNotNull(store.resolve(source)).appendBytes(byteArrayOf(1))
            assertNotEquals(first, repository.sourceKey(slot, emptyMap()))
        }
    }

    @Test
    fun aSaveDropsAWallpaperThePageNoLongerNames() {
        val directory = Files.createTempDirectory("backgrounds").toFile()
        val store = BackgroundStore(directory)
        val repository = LayoutRepository(directory)
        val service = ScreenService(repository, store)
        val token = store.save("keep", "home", jpeg())
        val page = Page("home", "Home", backgroundImage = token)
        repository.update { it.copy(screens = listOf(Screen("keep", "Kitchen", pages = listOf(page)))) }

        service.updateScreen("keep", Screen("keep", "Kitchen", pages = listOf(Page("home", "Home"))))

        assertNull(store.read("keep", "home", token))
        store.save("keep", "home", jpeg())
        service.deleteScreen("keep")
        assertFalse(File(directory, "backgrounds/keep").exists())
    }

    private fun jpeg(): ByteArray = encoded("jpg", BufferedImage.TYPE_INT_RGB)

    private fun png(): ByteArray = encoded("png", BufferedImage.TYPE_INT_ARGB)

    private fun encoded(format: String, imageType: Int): ByteArray {
        val image = BufferedImage(SAMPLE_SIZE, SAMPLE_SIZE, imageType)
        val graphics = image.createGraphics()
        graphics.setColor(Color.RED)
        graphics.fillRect(0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        graphics.dispose()
        val output = ByteArrayOutputStream()
        check(ImageIO.write(image, format, output))
        return output.toByteArray()
    }
}

private const val SAMPLE_SIZE = 2
