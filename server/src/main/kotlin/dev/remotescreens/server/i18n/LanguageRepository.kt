package dev.remotescreens.server.i18n

import dev.remotescreens.server.data.homeassistant.HomeAssistant
import dev.remotescreens.server.data.layout.LayoutRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/** The language of the screens: the add-on's preference, else Home Assistant's, with Home Assistant's own words. */
class LanguageRepository(
    private val homeAssistant: HomeAssistant,
    private val layoutRepository: LayoutRepository,
) {
    @Volatile
    private var cached: I18n? = null
    private var cacheKey = ""
    private val homeAssistantWords = ConcurrentHashMap<String, Map<String, String>>()

    fun current(): I18n {
        val preferences = layoutRepository.layout.value.preferences
        val language = (preferences.language ?: homeAssistant.config.value.language).ifBlank { "en" }
        val is24Hour = preferences.clock24h ?: I18n.default24h(language)
        val key = "$language|$is24Hour"
        cached?.takeIf { cacheKey == key }?.let { return it }
        val built = I18n(
            language,
            I18n.load(language.substringBefore('-')),
            homeAssistantWords[language].orEmpty(),
            is24Hour,
        )
        cached = built
        cacheKey = key
        return built
    }

    /** Loads Home Assistant's words for states in the current language. */
    suspend fun loadHomeAssistantWords() {
        val language = current().language
        if (homeAssistantWords.containsKey(language)) return
        val translations = runCatching {
            val request = buildJsonObject {
                put("language", language)
                put("category", "entity_component")
            }
            homeAssistant.command("frontend/get_translations", request) as? JsonObject
        }.getOrNull()
        homeAssistantWords[language] = I18n.haResources(translations)
        cached = null
    }
}
