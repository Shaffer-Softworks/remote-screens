package dev.remotescreens.server.render

import dev.remotescreens.server.ha.EntityState
import dev.remotescreens.shared.VarValue

/**
 * The names of the named variables. An entity's values are shared by every tile and card that shows it; a tile's
 * colours are its own, because two tiles of one lamp may wear different pastels.
 */
object V {
    /** Values for `Modifier.visibility`: the player's `Component.Visibility`. */
    const val VISIBLE = 1
    const val GONE = 0

    fun e(entityId: String, key: String) = "e.$entityId.$key"
    fun t(tileId: String, key: String) = "t.$tileId.$key"
    fun o(scope: String, group: String, index: Int, key: String) = "o.$scope.$group.$index.$key"

    const val PAGE = "page"
    const val TIME = "sys.time"
    const val DATE = "sys.date"
    const val TEMP = "sys.temp"
    const val PEOPLE = "sys.people"
    const val CONNECTED = "sys.connected"
}

/** Binds the values every tile and card of an entity reads. Once per entity per document. */
fun DocContext.bindEntity(entityId: String, name: String? = null, icon: String? = null) {
    if (bindings.any { it.entityId == entityId && it.compute is EntityBinding }) return
    bind(entityId, EntityBinding(entityId, this, name, icon))
}

private class EntityBinding(val id: String, val ctx: DocContext, val name: String?, val icon: String?) : (Map<String, EntityState>) -> List<VarValue> {
    override fun invoke(states: Map<String, EntityState>): List<VarValue> {
        val v = Present.view(states[id], ctx.i18n, name, icon)
        return listOf(
            VarValue.string(V.e(id, "name"), v.name),
            VarValue.string(V.e(id, "state"), v.stateText),
            VarValue.string(V.e(id, "value"), v.value),
            VarValue.int(V.e(id, "on"), if (v.on) V.VISIBLE else V.GONE),
            VarValue.int(V.e(id, "off"), if (v.on) V.GONE else V.VISIBLE),
            VarValue.float(V.e(id, "level"), v.level ?: 0f),
            VarValue.float(V.e(id, "level_rest"), 1f - (v.level ?: 0f)),
        )
    }
}

/** A tile's colours follow whether its entity is on. */
fun DocContext.bindTileColors(tileId: String, entityId: String?, pastel: Pastel, alwaysOn: Boolean = false) {
    val dark = dark
    bind(entityId) { states ->
        val e = entityId?.let { states[it] }
        val view = Present.view(e, i18n)
        val on = alwaysOn || view.on
        val avail = entityId == null || view.available
        listOf(
            VarValue.color(V.t(tileId, "bg"), if (on && avail) pastel.bg(dark) else Role.CARD_OFF.argb(dark)),
            VarValue.color(V.t(tileId, "ink"), if (!avail) Role.INK_FAINT.argb(dark) else if (on) pastel.ink(dark) else Role.INK.argb(dark)),
            VarValue.color(V.t(tileId, "ink2"), if (!avail) Role.INK_FAINT.argb(dark) else if (on) pastel.ink(dark) else Role.INK_SOFT.argb(dark)),
            VarValue.color(V.t(tileId, "icon"), if (!avail) Role.INK_FAINT.argb(dark) else if (on) pastel.ink(dark) else Role.INK_SOFT.argb(dark)),
            VarValue.color(V.t(tileId, "fill"), if (on) pastel.ink(dark) and 0x00FFFFFF or 0x33000000 else Role.DIVIDER.argb(dark)),
        )
    }
}

/** A row of choices (modes, options, speeds): the chosen one is dark on light, the rest plain. */
fun DocContext.bindChoices(scopeId: String, group: String, entityId: String, choices: List<String>, chosen: (EntityState) -> String?) {
    val dark = dark
    bind(entityId) { states ->
        val current = states[entityId]?.let(chosen)
        choices.flatMapIndexed { i, c ->
            val on = c == current
            listOf(
                VarValue.color(V.o(scopeId, group, i, "bg"), (if (on) Role.KEY_ON else Role.KEY).argb(dark)),
                VarValue.color(V.o(scopeId, group, i, "ink"), (if (on) Role.KEY_ON_INK else Role.INK).argb(dark)),
            )
        }
    }
}
