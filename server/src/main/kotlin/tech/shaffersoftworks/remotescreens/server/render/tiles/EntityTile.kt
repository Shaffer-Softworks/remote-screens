@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.HaRenderFlags
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.levelBar

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcText
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ControlVerb
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.LightColorMode
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TypeSpec
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C

/** The tile most entities get: an icon, the name and a line about the state, with a control where there is room. */
internal fun DocContext.entityTile(
    tile: Tile,
    box: TileBox,
    spec: TypeSpec?,
    entityState: EntityState?,
    named: Boolean = true,
) {
    val entityId = tile.entityId
    val name = if (entityId != null && named) text(Vars.entity(entityId, Vars.KEY_NAME)) else null
    val state = if (entityId != null) text(Vars.entity(entityId, Vars.KEY_STATE)) else null
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val pad = if (box.w < C.NARROW_WIDTH_DP) C.PAD_MD_DP else C.PAD_XL_DP
    val short = box.h < C.SHORT_HEIGHT_DP
    val wide = (box.w >= 2 * box.h * C.WIDE_ASPECT && box.h < C.TALL_HEIGHT_DP) || short

    val hasLevel = entityHasLevel(tile = tile, box = box, entityState = entityState)

    val bigValue = tile.domain in setOf(
        Domain.SENSOR.value,
        Domain.NUMBER.value,
        Domain.INPUT_NUMBER.value,
        Domain.COUNTER.value,
        Domain.TODO.value,
        Domain.TIMER.value,
    )

    val face = TileFace(
        tile = tile,
        box = box,
        spec = spec,
        entityState = entityState,
        name = name,
        state = state,
    )

    val tone = TileTone(
        ink = ink,
        ink2 = ink2,
        pad = pad,
        bigValue = bigValue,
        hasLevel = hasLevel,
        named = named,
    )

    when {
        box.full -> fullEntityTile(face, tone)
        wide && !hasLevel -> wideEntityTile(face, tone)
        else -> stackedEntityTile(face, tone)
    }
}

private data class TileFace(
    val tile: Tile,
    val box: TileBox,
    val spec: TypeSpec?,
    val entityState: EntityState?,
    val name: RcText?,
    val state: RcText?,
)

private data class TileTone(
    val ink: RcColor,
    val ink2: RcColor,
    val pad: Float,
    val bigValue: Boolean,
    val hasLevel: Boolean,
    val named: Boolean,
)

private fun entityHasLevel(tile: Tile, box: TileBox, entityState: EntityState?): Boolean {
    val short = box.h < C.SHORT_HEIGHT_DP
    val wide = (box.w >= 2 * box.h * C.WIDE_ASPECT && box.h < C.TALL_HEIGHT_DP) || short
    val fits = tile.entityId != null && entityState != null && levelFits(box = box, wide = wide, short = short)

    return fits && entityState != null && domainHasLevel(tile, entityState)
}

private fun levelFits(box: TileBox, wide: Boolean, short: Boolean): Boolean =
    box.w >= C.TALL_HEIGHT_DP && (box.h >= C.TALL_HEIGHT_DP || (wide && !short))

private fun domainHasLevel(tile: Tile, entityState: EntityState): Boolean = when (tile.domain) {
    Domain.LIGHT.value -> entityState.light.supportedColorModes.any { it != LightColorMode.ONOFF }
    Domain.FAN.value -> entityState.supports(C.FAN_PERCENTAGE_FLAG)
    Domain.COVER.value, Domain.VALVE.value -> coverHasLevel(entityState)
    Domain.HUMIDIFIER.value, Domain.WATER_HEATER.value -> true
    Domain.NUMBER.value, Domain.INPUT_NUMBER.value, Domain.MEDIA_PLAYER.value -> true
    else -> false
}

private fun coverHasLevel(entityState: EntityState): Boolean =
    entityState.supports(C.COVER_POSITION_FLAG) ||
        entityState.cover.currentPosition != null ||
        entityState.supports(HaRenderFlags.COVER_TILT_POSITION_FLAG) ||
        entityState.cover.currentTiltPosition != null

private fun DocContext.fullEntityTile(face: TileFace, tone: TileTone) {
    val tile = face.tile
    val spec = face.spec
    val entityState = face.entityState
    val name = face.name
    val state = face.state
    val ink = tone.ink
    val ink2 = tone.ink2

    rcScope.Column(
        modifier = Modifier.fillMaxSize().padding(units.px(C.PAD_XXL_DP)),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        this@fullEntityTile.tileIcon(
            tile = tile,
            spec = spec,
            entityState = entityState,
            sizeDp = C.SHORT_HEIGHT_DP,
        )

        this@fullEntityTile.spacer(C.PAD_XL_DP)

        if (name != null) {
            this@fullEntityTile.label(
                text = name,
                sp = Type.DISPLAY * C.FULL_NAME_SCALE,
                color = ink,
                weight = Weight.SEMI_BOLD,
                align = RcTextAlign.Center,
            )
        }

        if (state != null) {
            this@fullEntityTile.label(
                text = state,
                sp = Type.HEADLINE,
                color = ink2,
                align = RcTextAlign.Center,
            )
        }
    }
}

private fun DocContext.wideEntityTile(face: TileFace, tone: TileTone) {
    val tile = face.tile
    val spec = face.spec
    val entityState = face.entityState
    val name = face.name
    val state = face.state
    val ink = tone.ink
    val ink2 = tone.ink2
    val pad = tone.pad
    val bigValue = tone.bigValue

    rcScope.Row(
        Modifier.fillMaxSize().padding(units.px(pad)),
        vertical = RcVerticalPositioning.Center,
    ) {
        this@wideEntityTile.tileIcon(tile = tile, spec = spec, entityState = entityState, sizeDp = C.HEADER_SHORT_DP)
        Spacer(Modifier.width(this@wideEntityTile.units.px(C.PAD_LG_DP)))

        // Preserve the name; secondary state text yields first when the tile is too short.
        CollapsibleColumn(Modifier.horizontalWeight(1f)) {
            if (name != null) {
                this@wideEntityTile.label(
                    text = name,
                    sp = Type.TITLE,
                    color = ink,
                    weight = Weight.SEMI_BOLD,
                )
            }

            if (state != null) {
                this@wideEntityTile.label(
                    text = state,
                    sp = if (bigValue) Type.HEADLINE else Type.BODY,
                    color = ink2,
                    weight = if (bigValue) Weight.MEDIUM else Weight.NORMAL,
                )
            }
        }
    }
}

private fun DocContext.stackedWords(face: TileFace, tone: TileTone) {
    val box = face.box
    val name = face.name
    val state = face.state
    val tall = box.h >= C.TALL_HEIGHT_DP

    if (tone.bigValue && state != null) {
        label(
            text = state,
            sp = if (tall) Type.VALUE else Type.HEADLINE,
            color = tone.ink,
            weight = Weight.MEDIUM,
        )
    }

    if (name != null) {
        label(
            text = name,
            sp = Type.BODY + if (tall) 2f else 0f,
            color = tone.ink,
            weight = Weight.SEMI_BOLD,
            lines = if (state == null || tone.bigValue) 2 else 1,
        )
    }

    if (state != null && !tone.bigValue) {
        label(
            text = state,
            sp = if (tone.named) Type.CAPTION + 1f else Type.TITLE,
            color = tone.ink2,
        )
    }
}

private fun DocContext.stackedEntityTile(face: TileFace, tone: TileTone) {
    val tile = face.tile
    val box = face.box
    val spec = face.spec
    val entityState = face.entityState
    val entityId = tile.entityId
    val pad = tone.pad
    val hasLevel = tone.hasLevel

    rcScope.Column(Modifier.fillMaxSize().padding(units.px(pad))) {
        val doc = this@stackedEntityTile

        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Top) {
            doc.tileIcon(
                tile = tile,
                spec = spec,
                entityState = entityState,
                sizeDp = if (box.h >= C.TALL_HEIGHT_DP) C.COL_ICON_TALL_DP else C.COL_ICON_SHORT_DP,
            )

            Spacer(Modifier.horizontalWeight(1f))

            if (tile.domain == "lock") doc.lockConfirmBadge(tile)
        }

        Spacer(Modifier.verticalWeight(1f))
        doc.stackedWords(face, tone)

        if (hasLevel) {
            doc.entityTileLevelBar(
                tile = tile,
                entityId = requireNotNull(entityId),
                entityState = requireNotNull(entityState),
                widthDp = box.w - 2 * pad,
            )
        }
    }
}

internal fun DocContext.entityTileLevelBar(tile: Tile, entityId: String, entityState: EntityState, widthDp: Float) {
    spacer(C.GAP_10_DP)

    val verb = when (tile.domain) {
        Domain.LIGHT.value -> ControlVerb.BRIGHTNESS
        Domain.FAN.value -> ControlVerb.PERCENTAGE
        Domain.COVER.value -> if (
            entityState.supports(C.COVER_POSITION_FLAG) || entityState.cover.currentPosition != null
        ) {
            ControlVerb.POSITION
        } else {
            ControlVerb.TILT
        }
        Domain.MEDIA_PLAYER.value -> ControlVerb.VOLUME
        else -> ControlVerb.VALUE
    }

    val (minValue, maxValue) = if (tile.domain in setOf(Domain.NUMBER.value, Domain.INPUT_NUMBER.value)) {
        (entityState.input.min ?: 0.0) to (entityState.input.max ?: C.LEVEL_DEFAULT_MAX)
    } else {
        0.0 to C.LEVEL_DEFAULT_MAX
    }

    levelBar(
        level = float(Vars.entity(entityId, Vars.KEY_LEVEL)),
        widthDp = widthDp,
        heightDp = C.LEVEL_BAR_HEIGHT_DP,
        fill = color(Vars.tile(tile.id, Vars.KEY_ICON)),
        track = role(Role.TRACK),
        steps = C.LEVEL_STEPS,
    ) { step ->
        val value = minValue + (maxValue - minValue) * step / C.LEVEL_STEPS.toDouble()

        val action = ActionRef(
            tile = tile.id,
            verb = verb,
            arg = if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                "%.1f".format(
                    java.util.Locale.ROOT,
                    value,
                )
            },
        )

        predict(action, entityId)
        action
    }
}

/** "Tap again to unlock" on a lock tile, shown by the add-on between the two taps. */
internal fun DocContext.lockConfirmBadge(tile: Tile) {
    val confirmText = text(Vars.tile(tile.id, Vars.KEY_CONFIRM))
    label(text = confirmText, sp = Type.CAPTION, color = role(Role.WARN), weight = Weight.SEMI_BOLD)
}
