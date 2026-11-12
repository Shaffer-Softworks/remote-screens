package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.server.data.layout.LayoutRepository
import tech.shaffersoftworks.remotescreens.server.data.pictures.BackgroundStore
import tech.shaffersoftworks.remotescreens.server.plugins.ApiJson
import tech.shaffersoftworks.remotescreens.server.service.validation.ScreenValidator
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.PlayerRecord
import tech.shaffersoftworks.remotescreens.shared.SCHEMA_VERSION
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.ScreenSettings
import tech.shaffersoftworks.remotescreens.shared.Theme
import io.ktor.server.plugins.BadRequestException
import kotlinx.serialization.SerializationException
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenServiceTest {
    @Test
    fun importMakesANewScreenAndLeavesTheOriginalTablet() {
        val (repository, service) = opened()
        val original = screen(id = "keep", name = "Kitchen")
        repository.update { layout ->
            layout.copy(
                screens = listOf(original),
                players = listOf(PlayerRecord(deviceId = "tablet", name = "Wall", screenId = original.id)),
            )
        }

        val imported = service.importScreen(file(original))

        assertNotEquals(original.id, imported.id)
        assertTrue(imported.id.startsWith("s"))
        assertEquals("Kitchen copy", imported.name)
        assertEquals(listOf("home", "more"), imported.pages.map { it.id })
        val stored = repository.layout.value
        assertEquals(listOf("keep", imported.id), stored.screens.map { it.id })
        assertEquals("keep", stored.players.single().screenId)
    }

    @Test
    fun importAddsAMissingThemeAndLeavesAnExistingOne() {
        val (repository, service) = opened()
        val kitchen = Theme("kitchen", "Kitchen", roles = mapOf("card" to "#112233"))
        val dusk = Theme("dusk", "Dusk", roles = mapOf("card" to "#445566"))
        val replacement = kitchen.copy(roles = mapOf("card" to "#AABBCC"))
        repository.update { it.copy(themes = listOf(kitchen)) }

        val imported = service.importScreen(
            file(
                screen(theme = "kitchen", darkTheme = "dusk"),
                themes = listOf(replacement, dusk, dusk),
            ),
        )

        val themes = repository.layout.value.themes
        assertEquals(listOf("kitchen", "dusk"), themes.map { it.id })
        assertEquals("#112233", themes.first { it.id == "kitchen" }.roles["card"])
        assertEquals("#445566", themes.first { it.id == "dusk" }.roles["card"])
        assertEquals("kitchen", imported.settings.theme)
        assertEquals("dusk", imported.settings.darkTheme)
    }

    @Test
    fun importSuffixesANameThatIsTaken() {
        val (_, service) = opened()
        assertEquals("Pantry", service.importScreen(file(screen(name = "Pantry"))).name)
        assertEquals("Pantry copy", service.importScreen(file(screen(name = "Pantry"))).name)
        assertEquals("Pantry copy 2", service.importScreen(file(screen(name = "Pantry"))).name)
    }

    @Test
    fun aLongNameIsShortenedToFitTheCopySuffix() {
        val (repository, service) = opened()
        val longName = "N".repeat(ScreenValidator.MAX_SCREEN_NAME_LENGTH)
        repository.update { it.copy(screens = listOf(screen(name = longName))) }

        val imported = service.importScreen(file(screen(name = longName)))

        assertEquals(ScreenValidator.MAX_SCREEN_NAME_LENGTH, imported.name.length)
        assertTrue(imported.name.endsWith(" copy"))
        assertNotEquals(longName, imported.name)
    }

    @Test
    fun importDropsABareBackgroundToken() {
        val (_, service) = opened()
        val original = screen().copy(
            pages = listOf(Page(id = "home", title = "Home", backgroundImage = "1700000000000")),
        )

        val imported = service.importScreen(file(original))

        assertNull(imported.pages.single().backgroundImage)
    }

    @Test
    fun importRestoresAnEmbeddedBackground() {
        val opened = opened()
        val jpeg = sampleJpeg()
        val original = screen().copy(
            pages = listOf(Page(id = "home", title = "Home")),
        )

        val imported = opened.service.importScreen(
            file(
                original,
                backgrounds = listOf(
                    ScreenBackgroundAsset(
                        pageId = "home",
                        mime = "image/jpeg",
                        data = Base64.getEncoder().encodeToString(jpeg),
                    ),
                ),
            ),
        )

        val token = assertNotNull(imported.pages.single().backgroundImage)
        val stored = assertNotNull(opened.store.read(imported.id, "home", token))
        assertTrue(stored.bytes.isNotEmpty())
        assertTrue(!stored.isPng)
    }

    @Test
    fun importSkipsAnInvalidEmbeddedBackground() {
        val opened = opened()
        val imported = opened.service.importScreen(
            file(
                screen().copy(pages = listOf(Page(id = "home", title = "Home"))),
                backgrounds = listOf(
                    ScreenBackgroundAsset(
                        pageId = "home",
                        mime = "image/jpeg",
                        data = Base64.getEncoder().encodeToString("not a picture".encodeToByteArray()),
                    ),
                ),
            ),
        )

        assertNull(imported.pages.single().backgroundImage)
    }

    @Test
    fun importRejectsAFileItCannotUse() {
        val opened = opened()
        val sample = screen()
        assertFailsWith<BadRequestException> {
            opened.service.importScreen(file(sample).copy(kind = "layout"))
        }
        assertFailsWith<BadRequestException> {
            opened.service.importScreen(file(sample).copy(schemaVersion = SCHEMA_VERSION + 1))
        }
        assertFailsWith<BadRequestException> {
            opened.service.importScreen(file(sample.copy(pages = emptyList())))
        }
        assertFailsWith<SerializationException> {
            ApiJson.decodeFromString(ScreenFile.serializer(), """{"kind":"${ScreenFile.KIND}"}""")
        }
        assertTrue(opened.repository.layout.value.screens.isEmpty())
    }

    private data class Opened(
        val repository: LayoutRepository,
        val service: ScreenService,
        val store: BackgroundStore,
    )

    private fun opened(): Opened {
        val directory = Files.createTempDirectory("screen-import").toFile()
        val repository = LayoutRepository(directory)
        val store = BackgroundStore(directory)
        return Opened(repository, ScreenService(repository, store), store)
    }

    private fun screen(
        id: String = "file",
        name: String = "Kitchen",
        theme: String = "rpi",
        darkTheme: String = "rpi",
    ): Screen = Screen(
        id = id,
        name = name,
        pages = listOf(Page(id = "home", title = "Home"), Page(id = "more", title = "More")),
        settings = ScreenSettings(theme = theme, darkTheme = darkTheme),
    )

    private fun file(
        screen: Screen,
        themes: List<Theme> = emptyList(),
        backgrounds: List<ScreenBackgroundAsset> = emptyList(),
    ): ScreenFile = ScreenFile(
        kind = ScreenFile.KIND,
        schemaVersion = SCHEMA_VERSION,
        screen = screen,
        themes = themes,
        backgrounds = backgrounds,
    )

    private fun sampleJpeg(): ByteArray {
        val image = BufferedImage(SAMPLE_JPEG_SIZE, SAMPLE_JPEG_SIZE, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, SAMPLE_JPEG_SIZE, SAMPLE_JPEG_SIZE)
        graphics.dispose()
        val output = ByteArrayOutputStream()
        check(ImageIO.write(image, "jpg", output))
        return output.toByteArray()
    }
}

private const val SAMPLE_JPEG_SIZE = 2
