package dev.remotescreens.server

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

/**
 * The words of the screens. Our own texts come from `translations/<lang>.json` (English when a key is missing);
 * the words for a state ("Open", "Heat", "Armed away") come from Home Assistant itself, so they match its own
 * dashboards in every language it speaks.
 */
class I18n(val language: String, private val own: Map<String, String>, private val ha: Map<String, String>, val clock24h: Boolean) {
    val locale: Locale = Locale.forLanguageTag(language)

    fun t(key: String, vararg args: Pair<String, Any>): String {
        var s = own[key] ?: ENGLISH[key] ?: key
        for ((k, v) in args) s = s.replace("{$k}", v.toString())
        return s
    }

    /** Home Assistant's word for [state] of an entity of [domain], with its device class when it has one. */
    fun state(domain: String, state: String, deviceClass: String? = null): String {
        if (deviceClass != null) ha["component.$domain.entity_component.$deviceClass.state.$state"]?.let { return it }
        ha["component.$domain.entity_component._.state.$state"]?.let { return it }
        own["state.$state"]?.let { return it }
        ENGLISH["state.$state"]?.let { return it }
        return state.replace('_', ' ').replaceFirstChar { it.titlecase(locale) }
    }

    fun number(v: Double, decimals: Int): String {
        val f = NumberFormat.getNumberInstance(locale)
        f.minimumFractionDigits = decimals
        f.maximumFractionDigits = decimals
        return f.format(v)
    }

    /** "75 %" in German and French, "75%" in English: the locale's own percent pattern. */
    fun percent(v: Int): String = NumberFormat.getPercentInstance(locale).format(v / 100.0)

    fun time(t: ZonedDateTime): String =
        t.format(DateTimeFormatter.ofPattern(if (clock24h) "HH:mm" else "h:mm a", locale))

    fun date(t: ZonedDateTime): String =
        t.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).replaceFirstChar { it.titlecase(locale) }

    /** "24 MAY 2024". */
    fun day(t: ZonedDateTime): String = t.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)).replace(".", "").uppercase(locale)

    fun weekday(t: ZonedDateTime): String = t.format(DateTimeFormatter.ofPattern("EEE", locale))

    fun fullWeekday(t: ZonedDateTime): String = t.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) }

    fun shortDate(t: ZonedDateTime): String = t.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))

    companion object {
        val SUPPORTED = listOf("en", "nl", "de", "fr", "es", "it", "pl", "pt")

        val ENGLISH: Map<String, String> by lazy { load("en") }

        fun load(lang: String): Map<String, String> {
            val stream = I18n::class.java.getResourceAsStream("/translations/$lang.json") ?: return emptyMap()
            val obj = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
            return obj.mapValues { (it.value as JsonPrimitive).content }
        }

        /** 24 hours unless the language's own time format has AM/PM. */
        fun default24h(language: String): Boolean {
            val pattern = (DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.forLanguageTag(language)))
                .format(ZonedDateTime.of(2026, 1, 1, 15, 0, 0, 0, ZoneId.of("UTC")))
            return !pattern.contains("PM", ignoreCase = true) && !pattern.contains("3")
        }

        /** Flattens Home Assistant's `frontend/get_translations` result into its keys. */
        fun haResources(result: JsonObject?): Map<String, String> =
            (result?.get("resources") as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }?.toMap() ?: emptyMap()
    }
}
