@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.energy

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EnergySource
import tech.shaffersoftworks.remotescreens.server.data.renderdata.EnergyPrefs
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.cards.CardType
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.at
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef

/**
 * Solar, the grid and the battery around the house, with the power of each, as Home Assistant's energy
 * settings name them.
 */
fun DocContext.energyFlow(wDp: Float, hDp: Float, compact: Boolean) {
    val prefs = data.energy ?: return
    val sources = energySources(prefs)

    for (source in sources) bindEntity(source.entityId)

    val radius = if (compact) C.COL_ICON_SHORT_DP else C.ENERGY_NODE_DP
    val spots = energySpots(wDp = wDp, hDp = hDp, radius = radius)
    val ink = role(Role.INK)
    val ink2 = role(Role.INK_SOFT)

    rcScope.Box(Modifier.fillMaxSize()) {
        energyLinks(sources, spots)

        for (source in sources) {
            energyNode(
                source = source,
                spot = spots.getValue(source.key),
                radius = radius,
                compact = compact,
                ink2 = ink2
            )
        }

        energyNode(
            source = FlowNode(key = EnergySource.HOME, entityId = "", icon = "home"),
            spot = spots.getValue(EnergySource.HOME),
            radius = radius,
            compact = compact,
            ink2 = ink2,
        )

        if (!compact && prefs.devices.isNotEmpty()) {
            energyDevices(prefs = prefs, hDp = hDp, ink = ink, ink2 = ink2)
        }
    }
}

private data class FlowNode(val key: String, val entityId: String, val icon: String)

private fun energySources(prefs: EnergyPrefs): List<FlowNode> = listOfNotNull(
    prefs.solar?.let { FlowNode(key = EnergySource.SOLAR, entityId = it, icon = "solar-power-variant") },
    prefs.grid?.let { FlowNode(key = EnergySource.GRID, entityId = it, icon = "transmission-tower") },
    prefs.battery?.let { FlowNode(key = EnergySource.BATTERY, entityId = it, icon = "home-battery") },
)

private fun energySpots(wDp: Float, hDp: Float, radius: Float): Map<String, Pair<Float, Float>> {
    val centerX = wDp / 2f
    val centerY = hDp / 2f + C.HISTORY_GAP_DP

    return mapOf(
        EnergySource.SOLAR to (centerX to radius + C.PAD_XL_DP),
        EnergySource.GRID to (radius + C.PAD_LG_DP to centerY),
        EnergySource.BATTERY to (wDp - radius - C.PAD_LG_DP to centerY),
        EnergySource.HOME to (centerX to hDp - radius - C.FORECAST_ICON_DP),
    )
}

private fun DocContext.energyLinks(sources: List<FlowNode>, spots: Map<String, Pair<Float, Float>>) {
    val home = spots.getValue(EnergySource.HOME)

    rcScope.Canvas(Modifier.fillMaxSize()) {
        paint {
            color(role(Role.DIVIDER))
            strokeWidth(units.px(C.ENERGY_LINE_DP))
            style(RcPaintStyle.Stroke)
        }

        for (source in sources) {
            val spot = spots.getValue(source.key)

            drawLine(
                x1 = units.px(spot.first),
                y1 = units.px(spot.second),
                x2 = units.px(home.first),
                y2 = units.px(home.second),
            )
        }
    }
}

private fun DocContext.energyNode(
    source: FlowNode,
    spot: Pair<Float, Float>,
    radius: Float,
    compact: Boolean,
    ink2: RcColor,
) {
    val (x, y) = spot
    val diameter = 2 * radius

    at(x - radius, y - radius) {
        rcScope.Column(
            Modifier.size(units.px(diameter), units.px(diameter + C.ENERGY_LABEL_EXTRA_DP)),
            horizontal = RcHorizontalPositioning.Center,
        ) {
            val doc = this@energyNode

            val hold = if (source.entityId.isNotEmpty()) {
                ActionRef(tile = "e:${source.entityId}", verb = "card", arg = CardType.HISTORY.wire)
            } else {
                null
            }

            Box(
                modifier = doc.tap(
                    Modifier.size(doc.units.px(diameter), doc.units.px(diameter))
                        .clip(doc.rounded(diameter / 2f))
                        .background(doc.fixed(doc.energyTint(source.key))),
                    hold,
                ),
                horizontal = RcHorizontalPositioning.Center,
                vertical = RcVerticalPositioning.Center,
            ) {
                Column(horizontal = RcHorizontalPositioning.Center) {
                    doc.icon(
                        name = source.icon,
                        sizeDp = if (compact) C.ENERGY_ICON_COMPACT_DP else C.PAD_XXL_DP,
                        color = doc.role(Role.KEY_ON_INK),
                    )

                    if (source.entityId.isNotEmpty()) {
                        doc.label(
                            text = doc.text(Vars.entity(source.entityId, Vars.KEY_STATE)),
                            sp = if (compact) Type.CAPTION - 1f else Type.CAPTION,
                            color = doc.role(Role.KEY_ON_INK),
                            weight = Weight.SEMI_BOLD,
                            align = RcTextAlign.Center,
                        )
                    }
                }
            }

            doc.label(
                text = doc.i18n.t(energyLabelKey(source.key)),
                sp = Type.CAPTION,
                color = ink2,
                align = RcTextAlign.Center,
            )
        }
    }
}

private fun DocContext.energyDevices(prefs: EnergyPrefs, hDp: Float, ink: RcColor, ink2: RcColor) {
    val shown = prefs.devices.take(C.ENERGY_DEVICE_MAX)

    at(C.PAD_MD_DP, hDp - C.SPACE_20_DP - C.ENERGY_LABEL_EXTRA_DP * shown.size) {
        rcScope.Column {
            val doc = this@energyDevices

            for ((name, entityId) in shown) {
                doc.bindEntity(entityId)

                Row(vertical = RcVerticalPositioning.Center) {
                    doc.label(text = name, sp = Type.CAPTION, color = ink2)
                    Spacer(Modifier.width(doc.units.px(C.HISTORY_GAP_DP)))

                    doc.label(
                        text = doc.text(Vars.entity(entityId, Vars.KEY_STATE)),
                        sp = Type.CAPTION,
                        color = ink,
                        weight = Weight.SEMI_BOLD,
                    )
                }
            }
        }
    }
}

private fun DocContext.energyTint(key: String): Int = when (key) {
    EnergySource.SOLAR -> Pastel.YELLOW.argb
    EnergySource.GRID -> Pastel.BLUE.argb
    EnergySource.BATTERY -> Pastel.GREEN.argb
    else -> Pastel.GREY.argb
}

private fun energyLabelKey(key: String): String = when (key) {
    EnergySource.BATTERY -> "ui.battery_source"
    EnergySource.HOME -> "ui.house"
    else -> "ui.$key"
}
