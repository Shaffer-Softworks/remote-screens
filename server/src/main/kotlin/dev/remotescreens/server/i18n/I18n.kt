package dev.remotescreens.server.i18n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.text.NumberFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val PERCENT = 100.0
private const val PROBE_YEAR = 2026
private const val PROBE_AFTERNOON_HOUR = 15

/**
 * The words of the screens. Our own texts come from `translations/<lang>.json` (English when a key is missing);
 * the words for a state ("Open", "Heat", "Armed away") come from Home Assistant itself, so they match its own
 * dashboards in every language it speaks.
 */
class I18n(
    val language: String,
    private val ownWords: Map<String, String>,
    private val homeAssistantWords: Map<String, String>,
    val is24Hour: Boolean,
) {
    val locale: Locale = Locale.forLanguageTag(language)

    fun t(key: String, vararg args: Pair<String, Any>): String {
        var text = ownWords[key] ?: ENGLISH[key] ?: key
        for ((name, value) in args) text = text.replace("{$name}", value.toString())
        return text
    }

    /** Home Assistant's word for [state] of an entity of [domain], with its device class when it has one. */
    fun state(domain: String, state: String, deviceClass: String? = null): String {
        val deviceClassKey = deviceClass?.let { "component.$domain.entity_component.$it.state.$state" }
        return deviceClassKey?.let(homeAssistantWords::get)
            ?: homeAssistantWords["component.$domain.entity_component._.state.$state"]
            ?: ownWords["state.$state"]
            ?: ENGLISH["state.$state"]
            ?: state.replace('_', ' ').replaceFirstChar { it.titlecase(locale) }
    }

    fun number(value: Double, decimals: Int): String {
        val format = NumberFormat.getNumberInstance(locale)
        format.minimumFractionDigits = decimals
        format.maximumFractionDigits = decimals
        return format.format(value)
    }

    /** "75 %" in German and French, "75%" in English: the locale's own percent pattern. */
    fun percent(value: Int): String = NumberFormat.getPercentInstance(locale).format(value / PERCENT)

    fun time(time: ZonedDateTime): String = time.format(pattern(if (is24Hour) "HH:mm" else "h:mm a", locale))

    fun date(time: ZonedDateTime): String = time.format(pattern("EEEE d MMMM", locale)).capitalized(locale)

    /** "24 MAY 2024". */
    fun day(time: ZonedDateTime): String =
        time.format(pattern("d MMM yyyy", locale)).replace(".", "").uppercase(locale)

    fun weekday(time: ZonedDateTime): String = time.format(pattern("EEE", locale))

    fun fullWeekday(time: ZonedDateTime): String = time.format(pattern("EEEE", locale)).capitalized(locale)

    fun shortDate(time: ZonedDateTime): String =
        time.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))

    companion object {
        val SUPPORTED = listOf("en", "nl", "de", "fr", "es", "it", "pl", "pt")

        val ENGLISH: Map<String, String> by lazy { load("en") }

        fun load(language: String): Map<String, String> {
            val stream = I18n::class.java.getResourceAsStream("/translations/$language.json") ?: return emptyMap()
            val translations = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
            return translations.mapValues { (it.value as JsonPrimitive).content }
        }

        /** 24 hours unless the language's own time format has AM/PM. */
        fun default24h(language: String): Boolean {
            val afternoon = ZonedDateTime.of(PROBE_YEAR, 1, 1, PROBE_AFTERNOON_HOUR, 0, 0, 0, ZoneId.of("UTC"))
            val formatted = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag(language))
                .format(afternoon)
            return !formatted.contains("PM", ignoreCase = true) && !formatted.contains("3")
        }

        /** Flattens Home Assistant's `frontend/get_translations` result into its keys. */
        fun haResources(result: JsonObject?): Map<String, String> =
            (result?.get("resources") as? JsonObject)
                ?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.content?.let { key to it } }
                ?.toMap()
                .orEmpty()
    }
}

private fun pattern(pattern: String, locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, locale)

private fun String.capitalized(locale: Locale): String = replaceFirstChar { it.titlecase(locale) }
