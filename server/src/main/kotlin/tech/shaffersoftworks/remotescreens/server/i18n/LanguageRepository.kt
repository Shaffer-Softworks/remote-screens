package tech.shaffersoftworks.remotescreens.server.i18n

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.layout.LayoutRepository
import java.util.concurrent.ConcurrentHashMap
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.GetTranslations

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
            language = language,
            ownWords = I18n.load(language.substringBefore('-')),
            homeAssistantWords = homeAssistantWords[language].orEmpty(),
            is24Hour = is24Hour,
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
            homeAssistant.send(GetTranslations(language = language, category = ENTITY_COMPONENT))
        }.getOrNull()

        homeAssistantWords[language] = translations?.texts.orEmpty()
        cached = null
    }

    private companion object {
        /** Home Assistant's words for entity states, such as `component.light.entity_component._.state.on`. */
        const val ENTITY_COMPONENT = "entity_component"
    }
}
