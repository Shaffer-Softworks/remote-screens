@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.RenderInput
import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.widgets.gap
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.server.render.tiles.energyFlow
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import tech.shaffersoftworks.remotescreens.server.render.keptVerticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue

/**
 * The card a long press opens: the whole of one entity, on a panel over the page. A card is its own document in the
 * overlay slot; its taps name the entity as `e:<entity_id>`, so they work whichever tile opened it.
 */
object CardRenderer {
    @Suppress("LongMethod", "LongParameterList")
    fun render(
        entityId: String,
        kind: String?,
        device: Device,
        input: RenderInput,
        topInsetDp: Float = 0f,
        bottomInsetDp: Float = 0f,
    ): RenderedDoc {
        val entityState = input.states[entityId]
        val spec = Catalogue.of(kind ?: entityId.substringBefore('.'))
        val card = when (kind) {
            "history" -> "history"
            "energy" -> "energy"
            else -> spec?.card ?: "toggle"
        }

        val panelW = minOf(
            device.widthDp - CardChrome.PANEL_MARGIN_DP,
            CardChrome.PANEL_MAX_WIDTH_DP,
        )

        // Same vertical inset as the lights detail dialog, measured in the page area between the bars.
        val panelH = device.heightDp - topInsetDp - bottomInsetDp - 2 * CardChrome.PANEL_MARGIN_V_DP
        val (bytes, docContext) = DocContext.build(
            widthPx = device.widthPx,
            heightPx = device.heightPx,
            description = entityState?.name ?: entityId,
            newContext = { input.context(device) },
        ) { docContext ->
            if (card != "energy") docContext.bindEntity(entityId)

            docContext.bindTileColors(
                tileId = TILE,
                entityId = entityId,
                pastel = Pastel.forDomain(entityState?.domain),
                alwaysOn = card in setOf("camera", "map", "energy", "history"),
            )

            Column(
                docContext.tap(
                    Modifier.fillMaxSize().background(docContext.role(Role.SCRIM)),
                    ActionRef("_", "close"),
                ),
                horizontal = RcHorizontalPositioning.Center,
            ) {
                // Centre the card in the page area; the scrim still covers the bars.
                Spacer(Modifier.height(docContext.units.px(topInsetDp)))
                Spacer(Modifier.verticalWeight(1f))
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

                    Column(Modifier.fillMaxWidth().verticalWeight(1f).keptVerticalScroll("card.$entityId")) {
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
                Spacer(Modifier.verticalWeight(1f))
                Spacer(Modifier.height(docContext.units.px(bottomInsetDp)))
            }
        }
        return RenderedDoc(bytes, docContext)
    }

    /** Colours a card reads, under a tile id of its own. */
    const val TILE = "card"
}
