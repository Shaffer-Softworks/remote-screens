package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.VarValue

/**
 * The names of the named variables. An entity's values are shared by every tile and card that shows it; a tile's
 * colours are its own, because two tiles of one lamp may wear different pastels.
 */
object Vars {
    /** Values for `Modifier.visibility`: the player's `Component.Visibility`. */
    const val VISIBLE = 1
    const val GONE = 0

    fun e(entityId: String, key: String) = "e.$entityId.$key"
    fun t(tileId: String, key: String) = "t.$tileId.$key"
    fun o(scope: String, group: String, index: Int, key: String) = "o.$scope.$group.$index.$key"

    const val PAGE = "page"
    const val TIME = "sys.time"
    const val DATE = "sys.date"
    /** The date as the header shows it: "24 MAY 2024". */
    const val DAY = "sys.day"
    const val TEMP = "sys.temp"
    const val PEOPLE = "sys.people"
    const val CONNECTED = "sys.connected"
    /** Tablet battery as a percent string, e.g. "87%". */
    const val BATTERY = "sys.battery"
    const val BATTERY_CHARGING = "sys.battery_charging"
    const val BATTERY_IDLE = "sys.battery_idle"
    /** Tablet Wi‑Fi signal level 0–4 as text, or empty when unknown. */
    const val WIFI = "sys.wifi"
}

/** Binds the values every tile and card of an entity reads. Once per entity per document. */
fun DocContext.bindEntity(entityId: String, name: String? = null, icon: String? = null) {
    if (bindings.any { it.entityId == entityId && it.compute is EntityBinding }) return
    bind(entityId, EntityBinding(entityId, this, name, icon))
}

private class EntityBinding(
    val id: String,
    val ctx: DocContext,
    val name: String?,
    val icon: String?,
) : (Map<String, EntityState>) -> List<VarValue> {
    override fun invoke(states: Map<String, EntityState>): List<VarValue> {
        val v = Present.view(states[id], ctx.i18n, name, icon)
        return listOf(
            VarValue.string(Vars.e(id, "name"), v.name),
            VarValue.string(Vars.e(id, "state"), v.stateText),
            VarValue.string(Vars.e(id, "value"), v.value),
            VarValue.int(Vars.e(id, "on"), if (v.on) Vars.VISIBLE else Vars.GONE),
            VarValue.int(Vars.e(id, "off"), if (v.on) Vars.GONE else Vars.VISIBLE),
            VarValue.float(Vars.e(id, "level"), v.level ?: 0f),
            VarValue.float(Vars.e(id, "level_rest"), 1f - (v.level ?: 0f)),
        )
    }
}

/**
 * A tile's colours follow whether its entity is on. The card itself stays grey; being on lights its icon, the strip
 * under its header (`line`) and its round badge (`bg`, with `bgink` drawn on it) in the tile's accent.
 */
fun DocContext.bindTileColors(tileId: String, entityId: String?, pastel: Pastel, alwaysOn: Boolean = false) =
    bindTileColors(tileId, entityId, pastel.argb, alwaysOn)

/** As above, lit in [accent] (ARGB). */
fun DocContext.bindTileColors(tileId: String, entityId: String?, accent: Int, alwaysOn: Boolean = false) {
    bind(entityId) { states ->
        val e = entityId?.let { states[it] }
        val view = Present.view(e, i18n)
        val on = alwaysOn || view.on
        val avail = entityId == null || view.isAvailable
        val lit = on && avail
        val iconColor = when {
            !avail -> Role.INK_FAINT.argb
            on -> accent
            else -> Role.INK_DIM.argb
        }
        listOf(
            VarValue.color(Vars.t(tileId, "bg"), if (lit) accent else Role.KEY.argb),
            VarValue.color(Vars.t(tileId, "bgink"), if (lit) Role.KEY_ON_INK.argb else Role.INK.argb),
            VarValue.color(Vars.t(tileId, "line"), if (lit) accent else Role.CARD_HEADER.argb),
            VarValue.color(Vars.t(tileId, "ink"), if (!avail) Role.INK_FAINT.argb else Role.INK.argb),
            VarValue.color(Vars.t(tileId, "ink2"), if (!avail) Role.INK_FAINT.argb else Role.INK_SOFT.argb),
            VarValue.color(Vars.t(tileId, "icon"), iconColor),
            VarValue.color(Vars.t(tileId, "fill"), Role.KEY.argb),
        )
    }
}

/** A row of choices (modes, options, speeds): the chosen one is teal, the rest plain grey keys. */
fun DocContext.bindChoices(
    scopeId: String,
    group: String,
    entityId: String,
    choices: List<String>,
    chosen: (EntityState) -> String?,
) {
    bind(entityId) { states ->
        val current = states[entityId]?.let(chosen)
        choices.flatMapIndexed { i, c ->
            val on = c == current
            listOf(
                VarValue.color(Vars.o(scopeId, group, i, "bg"), (if (on) Role.KEY_ON else Role.KEY).argb),
                VarValue.color(Vars.o(scopeId, group, i, "ink"), (if (on) Role.KEY_ON_INK else Role.INK).argb),
            )
        }
    }
}
