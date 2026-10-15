package tech.shaffersoftworks.remotescreens.server.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    private val placeholder = Regex("\\{\\w+}")

    @Test
    fun everyLanguageHasEveryUiTextWithTheSamePlaceholders() {
        val english = I18n.ENGLISH
        val ui = english.keys.filter { it.startsWith("ui.") }.toSet()
        for (lang in I18n.SUPPORTED - "en") {
            val own = I18n.load(lang)
            assertTrue(own.isNotEmpty(), "translations/$lang.json is missing")
            assertEquals(emptySet(), ui - own.keys, "$lang lacks texts")
            assertEquals(emptySet(), own.keys - english.keys, "$lang has keys English does not")
            for ((k, v) in own) {
                val expected = placeholder.findAll(english.getValue(k)).map { it.value }.toSet()
                assertEquals(expected, placeholder.findAll(v).map { it.value }.toSet(), "$lang $k")
            }
        }
    }

    @Test
    fun missingTextsAndStatesFallBack() {
        val homeAssistantWords = mapOf("component.light.entity_component._.state.on" to "På")
        val sv = I18n("sv", I18n.load("sv"), homeAssistantWords, is24Hour = true)
        assertEquals("Back", sv.t("ui.back"))
        assertEquals("På", sv.state("light", "on"))
        assertEquals("Off", sv.state("light", "off"))
        val nl = I18n("nl", I18n.load("nl"), emptyMap(), is24Hour = true)
        assertEquals("Dit is Hal", nl.t("ui.identify", "name" to "Hal"))
    }
}
