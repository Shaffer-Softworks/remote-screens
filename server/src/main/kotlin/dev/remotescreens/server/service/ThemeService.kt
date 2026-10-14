package dev.remotescreens.server.service

import dev.remotescreens.server.data.layout.LayoutRepository
import dev.remotescreens.server.render.ResolvedTheme
import dev.remotescreens.server.render.Themes
import dev.remotescreens.server.service.validation.ThemeValidator
import dev.remotescreens.shared.Theme
import io.ktor.server.plugins.BadRequestException

/** The built-in themes and the custom ones the editor stores. */
class ThemeService(private val layoutRepository: LayoutRepository) {

    fun allThemes(): List<ResolvedTheme> = Themes.all(layoutRepository.layout.value.themes)

    fun saveTheme(themeId: String, theme: Theme): ResolvedTheme {
        val validated = ThemeValidator.validate(theme.copy(id = themeId), layoutRepository.layout.value.themes)
        layoutRepository.update { layout ->
            val themes = if (layout.themes.any { it.id == themeId }) {
                layout.themes.map { if (it.id == themeId) validated else it }
            } else {
                layout.themes + validated
            }
            layout.copy(themes = themes)
        }
        return allThemes().first { it.id == themeId }
    }

    fun deleteTheme(themeId: String) {
        if (Themes.isBuiltin(themeId)) throw BadRequestException("A built-in theme cannot be deleted")
        layoutRepository.update { layout -> layout.copy(themes = layout.themes.filterNot { it.id == themeId }) }
    }
}
