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
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
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
    fun importDropsABackgroundImage() {
        val (_, service) = opened()
        val original = screen().copy(
            pages = listOf(Page(id = "home", title = "Home", backgroundImage = "1700000000000")),
        )

        val imported = service.importScreen(file(original))

        assertNull(imported.pages.single().backgroundImage)
    }

    @Test
    fun importRejectsAFileItCannotUse() {
        val (repository, service) = opened()
        val sample = screen()
        assertFailsWith<BadRequestException> {
            service.importScreen(file(sample).copy(kind = "layout"))
        }
        assertFailsWith<BadRequestException> {
            service.importScreen(file(sample).copy(schemaVersion = SCHEMA_VERSION + 1))
        }
        assertFailsWith<BadRequestException> {
            service.importScreen(file(sample.copy(pages = emptyList())))
        }
        assertFailsWith<SerializationException> {
            ApiJson.decodeFromString(ScreenFile.serializer(), """{"kind":"${ScreenFile.KIND}"}""")
        }
        assertTrue(repository.layout.value.screens.isEmpty())
    }

    private fun opened(): Pair<LayoutRepository, ScreenService> {
        val directory = Files.createTempDirectory("screen-import").toFile()
        val repository = LayoutRepository(directory)
        return repository to ScreenService(repository, BackgroundStore(directory))
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

    private fun file(screen: Screen, themes: List<Theme> = emptyList()): ScreenFile = ScreenFile(
        kind = ScreenFile.KIND,
        schemaVersion = SCHEMA_VERSION,
        screen = screen,
        themes = themes,
    )
}
