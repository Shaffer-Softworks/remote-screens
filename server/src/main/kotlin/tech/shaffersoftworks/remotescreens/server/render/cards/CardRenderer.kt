@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Pastel
import tech.shaffersoftworks.remotescreens.server.render.RenderInput
import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.server.render.Role
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.gap
import tech.shaffersoftworks.remotescreens.server.render.rounded
import tech.shaffersoftworks.remotescreens.server.render.tap
import tech.shaffersoftworks.remotescreens.server.render.tiles.energyFlow

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue

/**
 * The card a long press opens: the whole of one entity, on a panel over the page. A card is its own document in the
 * overlay slot; its taps name the entity as `e:<entity_id>`, so they work whichever tile opened it.
 */
object CardRenderer {
    fun render(entityId: String, kind: String?, device: Device, input: RenderInput): RenderedDoc {
        val entityState = input.states[entityId]
        val spec = Catalogue.of(kind ?: entityId.substringBefore('.'))
        val card = when {
            kind == "history" -> "history"
            kind == "energy" -> "energy"
            else -> spec?.card ?: "toggle"
        }
        val panelW = minOf(
            device.widthDp - CardChrome.PANEL_MARGIN_DP,
            CardChrome.PANEL_MAX_WIDTH_DP,
        )
        val panelH = device.heightDp - CardChrome.CLOSE_KEY_DP
        val (bytes, docContext) = DocContext.build(
            device.widthPx,
            device.heightPx,
            entityState?.name ?: entityId,
            { input.context(device) },
        ) { docContext ->
            if (card != "energy") docContext.bindEntity(entityId)
            docContext.bindTileColors(
                TILE,
                entityId,
                Pastel.forDomain(entityState?.domain),
                alwaysOn = card in setOf("camera", "map", "energy", "history"),
            )
            Box(
                docContext.tap(
                    Modifier.fillMaxSize().background(docContext.role(Role.SCRIM)),
                    ActionRef("_", "close"),
                ),
                horizontal = RcHorizontalPositioning.Center,
                vertical = RcVerticalPositioning.Center,
            ) {
                Column(
                    docContext.tap(
                        Modifier.size(
                            docContext.units.px(panelW),
                            docContext.units.px(panelH),
                        ).clip(
                            docContext.rounded(CardChrome.PANEL_CORNER_DP),
                        ).background(docContext.role(Role.CARD)),
                        ActionRef("_", "noop"),
                    )
                        .padding(docContext.units.px(CardChrome.PANEL_PAD_DP)),
                ) {
                    docContext.header(entityId, card)
                    docContext.gap(CardChrome.SECTION_GAP_DP)
                    Column(Modifier.fillMaxWidth().verticalWeight(1f).verticalScroll()) {
                        val body = panelW - CardChrome.CLOSE_KEY_DP
                        when (card) {
                            "light" -> docContext.lightCard(entityId, entityState, body)
                            "fan" -> docContext.fanCard(entityId, entityState, body)
                            "humidifier" -> docContext.humidifierCard(entityId, entityState, body)
                            "cover" -> docContext.coverCard(entityId, entityState, body)
                            "lock" -> docContext.lockCard(entityId, entityState)
                            "climate" -> docContext.climateCard(entityId, entityState)
                            "media" -> docContext.mediaCard(entityId, entityState, body)
                            "weather" -> docContext.weatherCard(entityId, entityState, body)
                            "history" -> docContext.historyCard(
                                entityId,
                                body,
                                panelH - CardChrome.HISTORY_RESERVE_DP,
                            )
                            "camera" -> docContext.cameraCard(
                                entityId,
                                body,
                                panelH - CardChrome.CAMERA_RESERVE_DP,
                            )
                            "alarm" -> docContext.alarmCard(entityId, entityState, body)
                            "select" -> docContext.selectCard(entityId, entityState)
                            "number" -> docContext.numberCard(entityId, entityState, body)
                            "vacuum" -> docContext.vacuumCard(entityId, entityState)
                            "lawn_mower" -> docContext.lawnMowerCard(entityId)
                            "calendar" -> docContext.calendarCard(entityId, entityState)
                            "todo" -> docContext.todoCard(entityId)
                            "timer" -> docContext.timerCard(entityId, entityState)
                            "counter" -> docContext.counterCard(entityId, entityState)
                            "water_heater" -> docContext.waterHeaterCard(
                                entityId,
                                entityState,
                                body,
                            )
                            "valve" -> docContext.valveCard(entityId, entityState, body)
                            "siren" -> docContext.sirenCard(entityId, entityState)
                            "input_text" -> docContext.inputTextCard(entityId, entityState)
                            "input_datetime" -> docContext.inputDatetimeCard(entityId, entityState)
                            "map" -> docContext.mapCard(
                                entityId,
                                body,
                                panelH - CardChrome.CAMERA_RESERVE_DP,
                            )
                            "energy" -> docContext.energyFlow(
                                body,
                                panelH - CardChrome.CAMERA_RESERVE_DP,
                                compact = false,
                            )
                            else -> docContext.toggleCard(entityId, entityState)
                        }
                    }
                }
            }
        }
        return RenderedDoc(bytes, docContext)
    }

    /** Colours a card reads, under a tile id of its own. */
    const val TILE = "card"
}
