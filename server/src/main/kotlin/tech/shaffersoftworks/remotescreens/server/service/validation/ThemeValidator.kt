package tech.shaffersoftworks.remotescreens.server.service.validation

import tech.shaffersoftworks.remotescreens.server.render.Pastel
import tech.shaffersoftworks.remotescreens.server.render.Role
import tech.shaffersoftworks.remotescreens.server.render.Themes
import tech.shaffersoftworks.remotescreens.shared.Theme
import io.ktor.server.plugins.BadRequestException

/** A custom theme the editor may store: its own id, a built-in base, colours in hex, and room for it. */
object ThemeValidator {
    fun validate(theme: Theme, existing: List<Theme>): Theme {
        val isNew = existing.none { it.id == theme.id }
        val invalidColour = (theme.roles.values + theme.pastels.values).firstOrNull { !Theme.HEX.matches(it) }
        ensureValid(Theme.ID.matches(theme.id)) { "Theme id: lowercase letters, digits, - and _" }
        ensureValid(!Themes.isBuiltin(theme.id)) { "${theme.id} is a built-in theme" }
        ensureValid(Themes.isBuiltin(theme.base)) { "Unknown base theme ${theme.base}" }
        ensureValid(!isNew || existing.size < Theme.MAX_THEMES) { "At most ${Theme.MAX_THEMES} themes" }
        ensureValid(invalidColour == null) { "Not a colour: $invalidColour" }
        return theme.validated().copy(
            roles = theme.roles
                .filterKeys { Role.of(it) != null }
                .mapKeys { it.key.lowercase() }
                .mapValues { it.value.uppercase() },
            pastels = theme.pastels
                .filterKeys { Pastel.of(it) != null }
                .mapValues { it.value.uppercase() },
        )
    }

    private inline fun ensureValid(isValid: Boolean, message: () -> String) {
        if (!isValid) throw BadRequestException(message())
    }
}
