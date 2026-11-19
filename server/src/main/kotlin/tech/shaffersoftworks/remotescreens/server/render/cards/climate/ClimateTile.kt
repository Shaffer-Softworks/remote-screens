@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.climate

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HvacMode
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.bindChoices
import tech.shaffersoftworks.remotescreens.server.render.panels.thermostat.ThermostatMetrics
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.tileIcon
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.choiceRow
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Tile

internal fun DocContext.climateTile(tile: Tile, box: TileBox, entityState: EntityState?, named: Boolean) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val step = climateStep(entityState)
    val showsStepper = box.w >= C.HEADER_TALL_MIN_DP || box.h >= C.TALL_HEIGHT_DP
    val modes = entityState?.climate?.hvacModes.orEmpty()
    structural(entityId) { it?.climate?.hvacModes?.joinToString().orEmpty() }
    predict(ActionRef(tile = tile.id, verb = "temperature", arg = "+$step"), entityId)
    predict(ActionRef(tile = tile.id, verb = "temperature", arg = "-$step"), entityId)

    rcScope.Column(Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP))) {
        climateHeading(
            tile = tile,
            entityId = entityId,
            entityState = entityState,
            named = named,
            ink = ink,
            ink2 = ink2
        )

        Spacer(Modifier.verticalWeight(1f))

        when {
            showsStepper && box.w < C.HEADER_TALL_MIN_DP -> stackedTemperature(
                tile = tile,
                entityId = entityId,
                step = step,
                ink = ink
            )
            showsStepper -> inlineTemperature(
                tile = tile,
                entityId = entityId,
                step = step,
                ink = ink,
                heightDp = box.h,
                modes = modes
            )
            else -> temperatureReadout(entityId = entityId, ink = ink, sp = Type.HEADLINE)
        }
    }
}

private fun DocContext.climateStep(entityState: EntityState?): Double =
    entityState?.climate?.targetTempStep
        ?: if (homeAssistantConfig.temperatureUnit.contains("F")) {
            ThermostatMetrics.TEMP_STEP_F
        } else {
            ThermostatMetrics.TEMP_STEP_C
        }

private fun DocContext.climateHeading(
    tile: Tile,
    entityId: String,
    entityState: EntityState?,
    named: Boolean,
    ink: RcColor,
    ink2: RcColor,
) {
    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@climateHeading

        doc.tileIcon(
            tile = tile,
            spec = Catalogue.of(Domain.CLIMATE.value),
            entityState = entityState,
            sizeDp = C.COL_ICON_SHORT_DP,
        )

        Spacer(Modifier.width(doc.units.px(C.CARD_CORNER_DP)))

        Column(Modifier.horizontalWeight(1f)) {
            if (named) {
                doc.label(
                    text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
                    sp = Type.BODY,
                    color = ink,
                    weight = Weight.SEMI_BOLD,
                )
            }

            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_STATE)),
                sp = if (named) Type.CAPTION + 1f else Type.BODY,
                color = ink2,
            )
        }
    }
}

private fun DocContext.stackedTemperature(tile: Tile, entityId: String, step: Double, ink: RcColor) {
    temperatureReadout(entityId = entityId, ink = ink, sp = Type.VALUE)
    spacer(C.PAD_SM_DP)

    rcScope.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontal = RcRowHorizontalPositioning.SpaceBetween,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@stackedTemperature
        doc.temperatureKey(tile = tile, amount = "-$step", iconName = "minus", ink = ink, sizeDp = C.HEADER_TALL_DP)
        doc.temperatureKey(tile = tile, amount = "+$step", iconName = "plus", ink = ink, sizeDp = C.HEADER_TALL_DP)
    }
}

private fun DocContext.inlineTemperature(
    tile: Tile,
    entityId: String,
    step: Double,
    ink: RcColor,
    heightDp: Float,
    modes: List<String>,
) {
    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@inlineTemperature

        doc.temperatureKey(
            tile = tile,
            amount = "-$step",
            iconName = "minus",
            ink = ink,
            sizeDp = C.CLIMATE_KEY_LG_DP,
        )

        Box(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                sp = Type.VALUE + C.CLIMATE_VALUE_BUMP,
                color = ink,
                weight = Weight.MEDIUM,
                align = RcTextAlign.Center,
            )
        }

        doc.temperatureKey(
            tile = tile,
            amount = "+$step",
            iconName = "plus",
            ink = ink,
            sizeDp = C.CLIMATE_KEY_LG_DP,
        )
    }

    if (heightDp >= C.CLIMATE_MODES_MIN_H_DP && modes.isNotEmpty()) {
        spacer(C.CARD_CORNER_DP)
        bindChoices(scopeId = tile.id, group = "mode", entityId = entityId, choices = modes) { it.state }

        choiceRow(
            scopeId = tile.id,
            group = "mode",
            choices = modes.take(C.CLIMATE_MODES_TAKE).map { it to i18n.state(Domain.CLIMATE.value, it) },
            refFor = { ActionRef(tile = tile.id, verb = "mode", arg = it) },
            heightDp = C.COL_ICON_TALL_DP,
            icons = MODE_ICONS,
        )
    }
}

private fun DocContext.temperatureReadout(entityId: String, ink: RcColor, sp: Float) {
    label(
        text = text(Vars.entity(entityId, Vars.KEY_VALUE)),
        sp = sp,
        color = ink,
        weight = Weight.MEDIUM,
    )
}

private fun DocContext.temperatureKey(tile: Tile, amount: String, iconName: String, ink: RcColor, sizeDp: Float) {
    key(
        ref = ActionRef(tile = tile.id, verb = "temperature", arg = amount),
        icon = iconName,
        heightDp = sizeDp,
        widthDp = sizeDp,
        round = true,
        bg = color(Vars.tile(tile.id, Vars.KEY_FILL)),
        ink = ink,
    )
}

val MODE_ICONS = mapOf(
    HvacMode.OFF.wire to "power",
    HvacMode.HEAT.wire to "fire",
    HvacMode.COOL.wire to "snowflake",
    HvacMode.HEAT_COOL.wire to "sun-snowflake-variant",
    HvacMode.AUTO.wire to "thermostat-auto",
    HvacMode.DRY.wire to "water-percent",
    HvacMode.FAN_ONLY.wire to "fan",
)
