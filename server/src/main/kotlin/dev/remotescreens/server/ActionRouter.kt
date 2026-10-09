package dev.remotescreens.server

import dev.remotescreens.server.ha.EntityState
import dev.remotescreens.server.ha.HomeAssistant
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.ActionSpec
import dev.remotescreens.shared.Catalogue
import dev.remotescreens.shared.Screen
import dev.remotescreens.shared.TapType
import dev.remotescreens.shared.Tile
import dev.remotescreens.shared.TypeSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.slf4j.LoggerFactory
import java.util.Locale
import kotlin.math.roundToInt

/** What came of a tap. */
sealed interface Outcome {
    data object Done : Outcome
    data object Ignored : Outcome
    /** Open the card of [entityId] (or of a kind with no entity, such as energy). */
    data class Card(val entityId: String?, val kind: String?) : Outcome
    /** The action needs a second tap; show [text] in the variable [varName] until it comes. */
    data class Confirm(val varName: String, val text: String) : Outcome
    /** A key of a keypad was pressed; [shown] is the code so far, masked. */
    data class Code(val varName: String, val shown: String) : Outcome
    data class Failed(val message: String) : Outcome
}

/** What a tap is about: a tile of the screen, or an entity named by a card. */
data class Target(val tileId: String, val entityId: String?, val kind: String, val tile: Tile?) {
    val spec: TypeSpec? get() = Catalogue.of(kind)
}

/** The per-player memory taps need: a code being typed, an action waiting for its second tap. */
class TapMemory {
    val codes = HashMap<String, String>()
    var pendingConfirm: Pair<String, Long>? = null
}

class ActionRouter(private val ha: HomeAssistant, private val i18n: () -> I18n) {
    private val log = LoggerFactory.getLogger(ActionRouter::class.java)

    fun target(ref: ActionRef, screen: Screen?): Target? {
        if (ref.tile.startsWith("e:")) {
            val id = ref.tile.removePrefix("e:")
            return Target(ref.tile, id, id.substringBefore('.'), null)
        }
        if (ref.tile == "card") return null
        val tile = screen?.pages?.flatMap { it.allTiles() }?.firstOrNull { it.id == ref.tile } ?: return null
        return Target(tile.id, tile.entityId, tile.effectiveKind(), tile)
    }

    suspend fun handle(ref: ActionRef, screen: Screen?, memory: TapMemory, now: Long = System.currentTimeMillis()): Outcome {
        val target = target(ref, screen) ?: return Outcome.Ignored
        val entity = target.entityId?.let { ha.states.value[it] }
        val verb = resolveVerb(ref.verb, target, entity)
        return try {
            when (verb) {
                "card" -> Outcome.Card(target.entityId, if (ref.arg == "history") "history" else target.kind)
                "run" -> run(target)
                "favourite" -> favourite(target)
                "code_digit", "code_back", "code_clear" -> code(verb, ref.arg, target, memory)
                else -> service(verb, ref, target, entity, memory, now)
            }
        } catch (e: Exception) {
            log.warn("Action {} failed: {}", ref.encode(), e.message)
            Outcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun resolveVerb(verb: String, target: Target, entity: EntityState?): String = when {
        target.kind == "lock" && verb == "toggle" -> if (entity?.state == "locked") "unlock" else "lock"
        else -> verb
    }

    private suspend fun service(verb: String, ref: ActionRef, target: Target, entity: EntityState?, memory: TapMemory, now: Long): Outcome {
        val spec = target.spec ?: return Outcome.Ignored
        val action = spec.actions[verb] ?: return Outcome.Ignored
        val id = target.entityId ?: return Outcome.Ignored
        if (action.confirm) {
            val key = "${target.tileId}|$verb"
            val pending = memory.pendingConfirm
            if (pending == null || pending.first != key || now - pending.second > CONFIRM_MS) {
                memory.pendingConfirm = key to now
                val text = i18n().t(if (verb == "open") "ui.tap_again_open" else "ui.tap_again_unlock")
                return Outcome.Confirm("t.${target.tileId}.confirm", text)
            }
            memory.pendingConfirm = null
        }
        val code = memory.codes.remove(id)
        val data = build(action, verb, ref.arg, entity, code)
        val (domain, service) = action.service.split('.', limit = 2)
        ha.callService(domain, service, JsonObject(data + ("entity_id" to JsonPrimitive(id))))
        return Outcome.Done
    }

    /** The service data of [action], with `$arg` and `$code` filled in. */
    fun build(action: ActionSpec, verb: String, arg: String, entity: EntityState?, code: String? = null): Map<String, JsonElement> {
        var a = resolveArg(action, arg, entity)
        val out = LinkedHashMap<String, JsonElement>()
        if (verb == "play_media" && a.contains('|')) {
            val (type, id) = a.split('|', limit = 2)
            a = id
            out["media_content_type"] = JsonPrimitive(type)
        }
        for ((k, v) in action.data) {
            if (k in out) continue
            val filled = fill(v, a, code)
            if (filled != JsonNull) out[k] = filled
        }
        return out
    }

    /** An argument of `+0.5` or `-10` counts from the entity's current value, within its limits. */
    fun resolveArg(action: ActionSpec, arg: String, entity: EntityState?): String {
        val rel = action.relative ?: return arg
        if (entity == null || arg.isEmpty() || (arg[0] != '+' && arg[0] != '-')) return arg
        val delta = arg.toDoubleOrNull() ?: return arg
        val base = scaled(rel, entity) ?: return arg
        val (lo, hi) = limits(entity, rel)
        val v = (base + delta).coerceIn(lo, hi)
        return number(v)
    }

    private fun scaled(rel: String, e: EntityState): Double? {
        val (key, op, k) = parseExpr(rel)
        val raw = if (key == "state") e.numericState() else e.number(key)
        raw ?: return if (key == "brightness" && e.state == "off") 0.0 else null
        return when (op) {
            '*' -> raw * k
            '/' -> raw / k
            else -> raw
        }
    }

    private fun limits(e: EntityState, rel: String): Pair<Double, Double> = when {
        e.domain == "climate" -> (e.number("min_temp") ?: 5.0) to (e.number("max_temp") ?: 35.0)
        rel.startsWith("state") -> (e.number("min") ?: 0.0) to (e.number("max") ?: 100.0)
        else -> 0.0 to 100.0
    }

    private fun fill(v: Any, arg: String, code: String?): JsonElement = when (v) {
        is String -> when {
            v == "\$code" -> code?.let { JsonPrimitive(it) } ?: JsonNull
            v.startsWith("\$arg") -> argValue(v, arg)
            else -> JsonPrimitive(v)
        }
        is List<*> -> JsonArray(v.filterNotNull().map { fill(it, arg, code) })
        is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to fill(x!!, arg, code) })
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    private suspend fun run(target: Target): Outcome {
        val tap = target.tile?.let { t -> listOf(t.tap, t.hold).firstOrNull { it.type == TapType.ACTION } } ?: return Outcome.Ignored
        val service = tap.service ?: return Outcome.Ignored
        val parts = service.split('.', limit = 2)
        if (parts.size != 2) return Outcome.Failed("Bad service $service")
        val data = (tap.data ?: JsonObject(emptyMap())).toMutableMap()
        if ("entity_id" !in data && target.entityId != null) data["entity_id"] = JsonPrimitive(target.entityId)
        ha.callService(parts[0], parts[1], JsonObject(data))
        return Outcome.Done
    }

    private suspend fun favourite(target: Target): Outcome {
        val o = target.tile?.options ?: return Outcome.Ignored
        val player = target.entityId ?: return Outcome.Ignored
        val content = o["content_id"] ?: return Outcome.Ignored
        ha.callService("media_player", "play_media", JsonObject(mapOf(
            "entity_id" to JsonPrimitive(player),
            "media_content_id" to JsonPrimitive(content),
            "media_content_type" to JsonPrimitive(o["content_type"] ?: "music"),
        )))
        return Outcome.Done
    }

    private fun code(verb: String, arg: String, target: Target, memory: TapMemory): Outcome {
        val id = target.entityId ?: return Outcome.Ignored
        val now = memory.codes[id].orEmpty()
        val next = when (verb) {
            "code_digit" -> (now + arg.filter(Char::isDigit)).take(12)
            "code_back" -> now.dropLast(1)
            else -> ""
        }
        memory.codes[id] = next
        return Outcome.Code("t.e:$id.code", "•".repeat(next.length))
    }

    /** The state Home Assistant will most likely report once [ref] is done, or null when it cannot be told. */
    fun foresee(ref: ActionRef, target: Target?, e: EntityState): EntityState? {
        val spec = target?.spec ?: Catalogue.of(e.domain) ?: return null
        val verb = resolveVerb(ref.verb, target ?: Target("", e.entityId, e.domain, null), e)
        val p = spec.predict[verb] ?: return null
        val action = spec.actions[verb]
        val arg = if (action != null) resolveArg(action, ref.arg, e) else ref.arg
        val state = when (val s = p.state) {
            null -> e.state
            "!" -> if (e.state in spec.onStates) "off" else spec.onStates.firstOrNull() ?: "on"
            "play_pause" -> if (e.state == "playing") "paused" else "playing"
            else -> if (s.startsWith("\$arg")) argValue(s, arg).contentOrNull ?: e.state else s
        }
        val attrs = p.attributes.mapValues { (_, v) -> if (v.startsWith("\$arg")) argValue(v, arg) else JsonPrimitive(v) }
        return e.with(state, attrs)
    }

    companion object {
        const val CONFIRM_MS = 4000L

        private fun parseExpr(s: String): Triple<String, Char?, Double> {
            val i = s.indexOfFirst { it == '*' || it == '/' }
            if (i < 0) return Triple(s, null, 1.0)
            return Triple(s.substring(0, i), s[i], s.substring(i + 1).toDoubleOrNull() ?: 1.0)
        }

        /** `$arg`, `$arg*2.55` or `$arg/100`, as a number where the argument is one. */
        fun argValue(expr: String, arg: String): JsonPrimitive {
            val (_, op, k) = parseExpr(expr)
            val n = arg.toDoubleOrNull() ?: return JsonPrimitive(arg)
            val v = when (op) {
                '*' -> n * k
                '/' -> n / k
                else -> n
            }
            return if (op == '*') JsonPrimitive(v.roundToInt()) else JsonPrimitive(numberValue(v))
        }

        private fun numberValue(v: Double): Number = if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e9) v.toLong() else v

        fun number(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.')
    }
}
