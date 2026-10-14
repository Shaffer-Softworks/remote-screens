package dev.remotescreens.server.service

import dev.remotescreens.server.data.layout.LayoutRepository
import dev.remotescreens.server.routes.model.CreateScreenRequest
import dev.remotescreens.server.service.validation.ScreenValidator
import dev.remotescreens.shared.DeviceSize
import dev.remotescreens.shared.Grid
import dev.remotescreens.shared.GridSize
import dev.remotescreens.shared.LayoutData
import dev.remotescreens.shared.Page
import dev.remotescreens.shared.Preferences
import dev.remotescreens.shared.Screen
import dev.remotescreens.shared.ScreenSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

private const val SCREEN_ID_PREFIX = "s"
private const val SCREEN_ID_LENGTH = 10

/** The screens, their settings and the editor's preferences, as the editor and the integration change them. */
class ScreenService(private val layoutRepository: LayoutRepository) {

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

    /** Replaces the screen [screenId]; null when there is no such screen. */
    fun updateScreen(screenId: String, screen: Screen): Screen? {
        val validated = ScreenValidator.validate(screen.copy(id = screenId))
        if (!exists(screenId)) return null
        layoutRepository.update { layout ->
            layout.copy(screens = layout.screens.map { if (it.id == screenId) validated else it })
        }
        return validated
    }

    /** Removes the screen [screenId] and unassigns the players that showed it. */
    fun deleteScreen(screenId: String) {
        layoutRepository.update { layout ->
            layout.copy(
                screens = layout.screens.filterNot { it.id == screenId },
                players = layout.players.map { if (it.screenId == screenId) it.copy(screenId = null) else it },
            )
        }
    }

    /** Applies settings from a player or from Home Assistant (the integration's entities) to a screen. */
    fun updateSettings(screenId: String, change: (ScreenSettings) -> ScreenSettings) {
        layoutRepository.update { layout ->
            layout.copy(
                screens = layout.screens.map { screen ->
                    if (screen.id == screenId) screen.copy(settings = change(screen.settings).validated()) else screen
                },
            )
        }
    }

    /** Merges the fields in [patch] into the settings of [screenId]; false when there is no such screen. */
    fun patchSettings(screenId: String, patch: JsonObject): Boolean {
        if (!exists(screenId)) return false
        updateSettings(screenId) { current ->
            val json = LayoutRepository.JSON
            val merged = JsonObject(json.encodeToJsonElement(ScreenSettings.serializer(), current).jsonObject + patch)
            json.decodeFromJsonElement(ScreenSettings.serializer(), merged)
        }
        return true
    }

    private fun newScreenId(): String =
        SCREEN_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").take(SCREEN_ID_LENGTH)
}
