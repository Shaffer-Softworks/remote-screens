package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DEFAULT_CLIMATE_MIN_TEMPERATURE = 5.0
private const val DEFAULT_CLIMATE_MAX_TEMPERATURE = 35.0
private const val DEFAULT_HUMIDITY_MIN = 0.0
private const val DEFAULT_HUMIDITY_MAX = 100.0
private const val DEFAULT_MIN_VALUE = 0.0
private const val DEFAULT_MAX_VALUE = 100.0
private const val MAX_WHOLE_NUMBER = 1e9
private const val ARG_PLACEHOLDER = "\$arg"
private const val CODE_PLACEHOLDER = "\$code"

/** Fills the service data of a catalogue action with the tap's argument and a typed code. */
object ServiceDataBuilder {

    /** The service data of [action], with `$arg` and `$code` filled in. */
    fun build(
        action: ActionSpec,
        verb: String,
        arg: String,
        entity: EntityState?,
        code: String? = null,
    ): Map<String, JsonElement> {
        var resolvedArg = resolveArg(action, arg, entity)
        val serviceData = LinkedHashMap<String, JsonElement>()
        if (verb == "play_media" && resolvedArg.contains('|')) {
            val (contentType, contentId) = resolvedArg.split('|', limit = 2)
            resolvedArg = contentId
            serviceData["media_content_type"] = JsonPrimitive(contentType)
        }
        for ((key, template) in action.data) {
            if (key in serviceData) continue
            val filled = fill(template, resolvedArg, code)
            if (filled != JsonNull) serviceData[key] = filled
        }
        return serviceData
    }

    /** An argument of `+0.5` or `-10` counts from the entity's current value, within its limits. */
    fun resolveArg(action: ActionSpec, arg: String, entity: EntityState?): String {
        val relativeTo = action.relative
        val delta = arg.takeIf { it.startsWith('+') || it.startsWith('-') }?.toDoubleOrNull()
        if (relativeTo == null || entity == null || delta == null) return arg
        return scaledValue(relativeTo, entity)
            ?.let { base -> formatNumber((base + delta).coerceIn(limits(entity, relativeTo))) }
            ?: arg
    }

    /** `$arg`, `$arg*2.55` or `$arg/100`, as a number where the argument is one. */
    fun argValue(expression: String, arg: String): JsonPrimitive {
        if (expression == ARG_PLACEHOLDER && (arg == "true" || arg == "false")) {
            return JsonPrimitive(arg == "true")
        }
        val (_, operator, factor) = parseExpression(expression)
        val number = arg.toDoubleOrNull() ?: return JsonPrimitive(arg)
        val value = when (operator) {
            '*' -> number * factor
            '/' -> number / factor
            else -> number
        }
        return if (operator == '*') JsonPrimitive(value.roundToInt()) else JsonPrimitive(wholeOrDecimal(value))
    }

    fun isArgExpression(text: String): Boolean = text.startsWith(ARG_PLACEHOLDER)

    /** A number as the tablet shows it: whole numbers without a decimal point, others with at most two decimals. */
    fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
        }

    private data class Expression(val key: String, val operator: Char?, val factor: Double)

    private fun parseExpression(expression: String): Expression {
        val operatorIndex = expression.indexOfFirst { it == '*' || it == '/' }
        if (operatorIndex < 0) return Expression(expression, null, 1.0)
        return Expression(
            key = expression.substring(0, operatorIndex),
            operator = expression[operatorIndex],
            factor = expression.substring(operatorIndex + 1).toDoubleOrNull() ?: 1.0,
        )
    }

    private fun scaledValue(expression: String, entity: EntityState): Double? {
        val (key, operator, factor) = parseExpression(expression)
        val raw = if (key == "state") entity.numericState() else entity.number(key)
        raw ?: return if (key == "brightness" && entity.state == "off") 0.0 else null
        return when (operator) {
            '*' -> raw * factor
            '/' -> raw / factor
            else -> raw
        }
    }

    private fun limits(entity: EntityState, relativeTo: String): ClosedFloatingPointRange<Double> = when {
        entity.domain == "climate" || entity.domain == "water_heater" ->
            (entity.number("min_temp") ?: DEFAULT_CLIMATE_MIN_TEMPERATURE)..
                (entity.number("max_temp") ?: DEFAULT_CLIMATE_MAX_TEMPERATURE)
        entity.domain == "humidifier" && relativeTo == "humidity" ->
            (entity.number("min_humidity") ?: DEFAULT_HUMIDITY_MIN)..
                (entity.number("max_humidity") ?: DEFAULT_HUMIDITY_MAX)
        relativeTo.startsWith("state") ->
            (entity.number("min") ?: DEFAULT_MIN_VALUE)..(entity.number("max") ?: DEFAULT_MAX_VALUE)
        else -> DEFAULT_MIN_VALUE..DEFAULT_MAX_VALUE
    }

    private fun fill(template: Any, arg: String, code: String?): JsonElement = when (template) {
        is String -> when {
            template == CODE_PLACEHOLDER -> code?.let { JsonPrimitive(it) } ?: JsonNull
            isArgExpression(template) -> argValue(template, arg)
            else -> JsonPrimitive(template)
        }
        is List<*> -> JsonArray(template.filterNotNull().map { fill(it, arg, code) })
        is Map<*, *> -> JsonObject(
            template.entries
                .filter { (_, value) -> value != null }
                .associate { (key, value) -> key.toString() to fill(checkNotNull(value), arg, code) },
        )
        is Boolean -> JsonPrimitive(template)
        is Number -> JsonPrimitive(template)
        else -> JsonPrimitive(template.toString())
    }

    private fun wholeOrDecimal(value: Double): Number =
        if (value % 1.0 == 0.0 && abs(value) < MAX_WHOLE_NUMBER) value.toLong() else value
}
