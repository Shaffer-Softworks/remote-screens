package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.server.data.layout.LayoutRepository
import tech.shaffersoftworks.remotescreens.server.data.pictures.BackgroundStore
import tech.shaffersoftworks.remotescreens.server.service.validation.ScreenValidator
import tech.shaffersoftworks.remotescreens.server.service.validation.ThemeValidator
import tech.shaffersoftworks.remotescreens.shared.DeviceSize
import tech.shaffersoftworks.remotescreens.shared.Grid
import tech.shaffersoftworks.remotescreens.shared.GridSize
import tech.shaffersoftworks.remotescreens.shared.LayoutData
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Preferences
import tech.shaffersoftworks.remotescreens.shared.SCHEMA_VERSION
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.ScreenSettings
import tech.shaffersoftworks.remotescreens.shared.Theme
import io.ktor.server.plugins.BadRequestException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

private const val SCREEN_ID_PREFIX = "s"
private const val SCREEN_ID_LENGTH = 10
private const val COPY_NAME_SUFFIX = " copy"
private const val MAX_COPY_NAME_ATTEMPTS = 1000

/** The screens, their settings and the editor's preferences, as the editor and the integration change them. */
class ScreenService(
    private val layoutRepository: LayoutRepository,
    private val backgroundStore: BackgroundStore,
) {

    /** The layout without token hashes; the editor only needs to know whether a player has one. */
    fun redactedLayout(): LayoutData {
        val layout = layoutRepository.layout.value
        return layout.copy(
            players = layout.players.map { player ->
                player.copy(tokenHash = if (player.tokenHash != null) "set" else null)
            },
        )
    }

    fun screens(): List<Screen> = layoutRepository.layout.value.screens

    fun exists(screenId: String): Boolean = screens().any { it.id == screenId }

    fun preferences(): Preferences = layoutRepository.layout.value.preferences

    fun updatePreferences(preferences: Preferences): Preferences =
        layoutRepository.update { it.copy(preferences = preferences) }.preferences

    fun createScreen(request: CreateScreenRequest): Screen {
        val designSize = if (request.widthDp != null && request.heightDp != null) {
            ScreenValidator.coerceDesignSize(DeviceSize(request.widthDp, request.heightDp))
        } else {
            null
        }
        val grid = request.grid
            ?: designSize?.let { Grid.suggest(it.widthDp.toFloat(), it.heightDp.toFloat()) }
            ?: GridSize.DEFAULT
        val screen = Screen(
            id = newScreenId(),
            name = ScreenValidator.normalizeName(request.name),
            grid = grid,
            pages = listOf(Page("home", "Home")),
            designSize = designSize,
            settings = ScreenSettings(orientation = ScreenValidator.orientationOf(designSize)),
        )
        layoutRepository.update { it.copy(screens = it.screens + screen) }
        return screen
    }

    /**
     * Stores [file] as a new screen. The file's screen id is dropped. A name already in use gains a " copy" suffix.
     * A custom theme is added only when that id is not already stored.
     */
    fun importScreen(file: ScreenFile): Screen {
        if (file.kind != ScreenFile.KIND) throw BadRequestException("Not a screen file")
        if (file.schemaVersion > SCHEMA_VERSION) {
            throw BadRequestException("This screen file needs a newer Remote Screens")
        }
        lateinit var imported: Screen
        layoutRepository.update { layout ->
            val screen = ScreenValidator.validate(
                file.screen.copy(
                    id = newScreenId(),
                    name = uniqueName(file.screen.name, layout.screens.map { it.name }.toSet()),
                    pages = file.screen.pages.map { page -> page.copy(backgroundImage = null) },
                ),
            )
            val themes = themesToStore(layout.themes, file.themes)
            imported = screen
            layout.copy(screens = layout.screens + screen, themes = themes)
        }
        return imported
    }

    /** Replaces the screen [screenId]; null when there is no such screen. */
    fun updateScreen(screenId: String, screen: Screen): Screen? {
        val validated = ScreenValidator.validate(screen.copy(id = screenId))
        if (!exists(screenId)) return null
        layoutRepository.update { layout ->
            layout.copy(screens = layout.screens.map { if (it.id == screenId) validated else it })
        }
        backgroundStore.deleteUnreferenced(screenId, validated.pages)
        return validated
    }

    /** Removes the screen [screenId] and unassigns the players that showed it. */
    fun deleteScreen(screenId: String) {
        layoutRepository.update { layout ->
            layout.copy(
                screens = layout.screens.filterNot { it.id == screenId },
                players = layout.players.map { if (it.screenId == screenId) {
                    it.copy(
                        screenId = null,
                    )
                } else {
                    it
                } },
            )
        }
        backgroundStore.deleteScreen(screenId)
    }

    /** Applies settings from a player or from Home Assistant (the integration's entities) to a screen. */
    fun updateSettings(screenId: String, change: (ScreenSettings) -> ScreenSettings) {
        layoutRepository.update { layout ->
            layout.copy(
                screens = layout.screens.map { screen ->
                    if (screen.id == screenId) {
                        screen.copy(
                            settings = change(screen.settings).validated(),
                        )
                    } else {
                        screen
                    }
                },
            )
        }
    }

    /** Merges the fields in [patch] into the settings of [screenId]; false when there is no such screen. */
    fun patchSettings(screenId: String, patch: JsonObject): Boolean {
        if (!exists(screenId)) return false
        updateSettings(screenId) { current ->
            val json = LayoutRepository.JSON
            val merged = JsonObject(
                json.encodeToJsonElement(ScreenSettings.serializer(), current).jsonObject + patch,
            )
            json.decodeFromJsonElement(ScreenSettings.serializer(), merged)
        }
        return true
    }

    private fun newScreenId(): String =
        SCREEN_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").take(SCREEN_ID_LENGTH)

    /** [name], or [name] plus " copy" / " copy 2" when that name is already used, within the name length. */
    private fun uniqueName(name: String, taken: Set<String>): String {
        val base = ScreenValidator.normalizeName(name)
        if (base !in taken) return base
        for (attempt in 1..MAX_COPY_NAME_ATTEMPTS) {
            val suffix = if (attempt == 1) COPY_NAME_SUFFIX else "$COPY_NAME_SUFFIX $attempt"
            val room = (ScreenValidator.MAX_SCREEN_NAME_LENGTH - suffix.length).coerceAtLeast(1)
            val candidate = ScreenValidator.normalizeName(base.take(room) + suffix)
            if (candidate !in taken) return candidate
        }
        throw BadRequestException("Could not name the copy")
    }

    /** [incoming] themes whose ids are not already in [stored]. An existing theme is left as it is. */
    private fun themesToStore(stored: List<Theme>, incoming: List<Theme>): List<Theme> {
        val seen = stored.map { it.id }.toMutableSet()
        var themes = stored
        for (theme in incoming) {
            if (!seen.add(theme.id)) continue
            themes = themes + ThemeValidator.validate(theme, themes)
        }
        return themes
    }
}
