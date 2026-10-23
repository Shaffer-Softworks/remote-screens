@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Present
import tech.shaffersoftworks.remotescreens.server.render.Role
import tech.shaffersoftworks.remotescreens.server.render.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.Weight
import tech.shaffersoftworks.remotescreens.server.render.gap
import tech.shaffersoftworks.remotescreens.server.render.label
import tech.shaffersoftworks.remotescreens.server.render.segmentedLevelBar

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TypeSpec
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C

/** The tile most entities get: an icon, the name and a line about the state, with a control where there is room. */
internal fun DocContext.entityTile(tile: Tile, box: TileBox, spec: TypeSpec?, entityState: EntityState?, named: Boolean = true) {
    val entityId = tile.entityId
    val name = if (entityId != null && named) text(Vars.entity(entityId, Vars.KEY_NAME)) else null
    val state = if (entityId != null) text(Vars.entity(entityId, Vars.KEY_STATE)) else null
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val pad = if (box.w < C.NARROW_WIDTH_DP) C.PAD_MD_DP else C.PAD_XL_DP
    val short = box.h < C.SHORT_HEIGHT_DP
    val wide = (box.w >= 2 * box.h * C.WIDE_ASPECT && box.h < C.TALL_HEIGHT_DP) || short
    val hasLevel = entityId != null && box.w >= C.TALL_HEIGHT_DP && entityState != null && when (tile.domain) {
        "light" -> entityState.list("supported_color_modes").any { it != "onoff" }
        "fan" -> entityState.supports(C.FAN_PERCENTAGE_FLAG)
        "cover", "valve" -> entityState.supports(
            C.COVER_POSITION_FLAG,
        ) || entityState.number("current_position") != null
        "humidifier", "water_heater" -> true
        "number", "input_number", "media_player" -> true
        else -> false
    } && (box.h >= C.TALL_HEIGHT_DP || (wide && !short))
    val bigValue = tile.domain in setOf(
        "sensor",
        "number",
        "input_number",
        "counter",
        "todo",
        "timer",
    )

    if (box.full) {
        scope.Column(
            Modifier.fillMaxSize().padding(units.px(C.PAD_XXL_DP)),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcColumnVerticalPositioning.Center,
        ) {
            this@entityTile.tileIcon(tile, spec, entityState, C.SHORT_HEIGHT_DP)
            this@entityTile.gap(C.PAD_XL_DP)
            if (name != null) {
                this@entityTile.label(
                    name,
                    Type.DISPLAY * C.FULL_NAME_SCALE,
                    ink,
                    Weight.SEMI_BOLD,
                    align = RcTextAlign.Center,
                )
            }
            if (state != null) {
                this@entityTile.label(
                    state,
                    Type.HEADLINE,
                    ink2,
                    align = RcTextAlign.Center,
                )
            }
        }
        return
    }
    if (wide && !hasLevel) {
        scope.Row(
            Modifier.fillMaxSize().padding(units.px(pad)),
            vertical = RcVerticalPositioning.Center,
        ) {
            this@entityTile.tileIcon(tile, spec, entityState, C.HEADER_SHORT_DP)
            Spacer(Modifier.width(this@entityTile.units.px(C.PAD_LG_DP)))
            Column(Modifier.horizontalWeight(1f)) {
                if (name != null) this@entityTile.label(name, Type.TITLE, ink, Weight.SEMI_BOLD)
                if (state != null) {
                    this@entityTile.label(
                        state,
                        if (bigValue) Type.HEADLINE else Type.BODY,
                        ink2,
                        if (bigValue) Weight.MEDIUM else Weight.NORMAL,
                    )
                }
            }
        }
        return
    }
    scope.Column(Modifier.fillMaxSize().padding(units.px(pad))) {
        val doc = this@entityTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Top) {
            doc.tileIcon(
                tile,
                spec,
                entityState,
                if (box.h >= C.TALL_HEIGHT_DP) C.COL_ICON_TALL_DP else C.COL_ICON_SHORT_DP,
            )
            Spacer(Modifier.horizontalWeight(1f))
            if (tile.domain == "lock") doc.lockConfirmBadge(tile)
        }
        Spacer(Modifier.verticalWeight(1f))
        if (bigValue && state != null) {
            doc.label(
                state,
                if (box.h >= C.TALL_HEIGHT_DP) Type.VALUE else Type.HEADLINE,
                ink,
                Weight.MEDIUM,
            )
        }
        if (name != null) {
            doc.label(
                name,
                Type.BODY + if (box.h >= C.TALL_HEIGHT_DP) 2f else 0f,
                ink,
                Weight.SEMI_BOLD,
                lines = if (state == null || bigValue) 2 else 1,
            )
        }
        if (state != null && !bigValue) {
            doc.label(
                state,
                if (named) Type.CAPTION + 1f else Type.TITLE,
                ink2,
            )
        }
        if (hasLevel) doc.entityTileLevelBar(tile, entityId, entityState, box.w - 2 * pad)
    }
}

internal fun DocContext.entityTileLevelBar(tile: Tile, entityId: String, entityState: EntityState, widthDp: Float) {
    gap(C.GAP_10_DP)
    val verb = when (tile.domain) {
        "light" -> "brightness"
        "fan" -> "percentage"
        "cover" -> "position"
        "media_player" -> "volume"
        else -> "value"
    }
    val (minValue, maxValue) = if (tile.domain in setOf("number", "input_number")) {
        (entityState.number("min") ?: 0.0) to (entityState.number("max") ?: C.LEVEL_DEFAULT_MAX)
    } else {
        0.0 to C.LEVEL_DEFAULT_MAX
    }
    // Keep the continuous float declared for other readers / predictions.
    float(Vars.entity(entityId, Vars.KEY_LEVEL))
    // Domain mockup: discrete teal segments on a dark track (humidifier / hot water).
    segmentedLevelBar(
        entityId = entityId,
        levelKey = Vars.KEY_LEVEL,
        widthDp = widthDp,
        heightDp = C.TILE_SEG_BAR_H_DP,
        steps = C.LEVEL_STEPS,
        levelOf = { states -> Present.view(states[entityId], i18n).level ?: 0f },
        fillRole = Role.ACCENT,
        trackRole = Role.TRACK,
    ) { step ->
        val value = minValue + (maxValue - minValue) * step / C.LEVEL_STEPS.toDouble()
        ActionRef(
            tile.id,
            verb,
            if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                "%.1f".format(
                    java.util.Locale.ROOT,
                    value,
                )
            },
        )
    }
}

/** "Tap again to unlock" on a lock tile, shown by the add-on between the two taps. */
internal fun DocContext.lockConfirmBadge(tile: Tile) {
    val confirmText = text(Vars.tile(tile.id, Vars.KEY_CONFIRM))
    label(confirmText, Type.CAPTION, role(Role.WARN), Weight.SEMI_BOLD)
}
