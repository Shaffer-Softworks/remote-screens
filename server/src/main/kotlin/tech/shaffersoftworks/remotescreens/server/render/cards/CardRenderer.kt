@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.vacuum.VacuumUiState
import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.panels.vacuum.vacuumRoomsDialog
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.RenderInput
import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
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
    fun render(
        entityId: String,
        kind: String?,
        device: Device,
        input: RenderInput,
        topInsetDp: Float = 0f,
        bottomInsetDp: Float = 0f,
    ): RenderedDoc {
        val entityState = input.states[entityId]
        val card = resolveCardType(kind = kind, entityId = entityId)

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
                pastel = Pastel.forDomain(entityState?.domain?.value),
                alwaysOn = card in ALWAYS_ON_CARDS,
            )

            Box(Modifier.fillMaxSize()) {
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
                    docContext.cardPanel(
                        card = card,
                        entityId = entityId,
                        entityState = entityState,
                        panelW = panelW,
                        panelH = panelH,
                    )
                    Spacer(Modifier.verticalWeight(1f))
                    Spacer(Modifier.height(docContext.units.px(bottomInsetDp)))
                }
                if (card == "vacuum") docContext.vacuumRoomsLayer(entityId, entityState, device.widthDp.toFloat())
            }
        }
        return RenderedDoc(bytes, docContext)
    }

    private fun DocContext.cardPanel(
        card: String,
        entityId: String,
        entityState: EntityState?,
        panelW: Float,
        panelH: Float,
    ) {
        rcScope.Column(
            tap(
                Modifier.size(units.px(panelW), units.px(panelH))
                    .clip(rounded(CardChrome.PANEL_CORNER_DP))
                    .background(role(Role.CARD)),
                ActionRef("_", "noop"),
            ).padding(units.px(CardChrome.PANEL_PAD_DP)),
        ) {
            val doc = this@cardPanel
            doc.header(entityId, card)
            doc.spacer(CardChrome.SECTION_GAP_DP)
            Column(Modifier.fillMaxWidth().verticalWeight(1f).keptVerticalScroll("card.$entityId")) {
                doc.renderCardBody(
                    card = card,
                    entityId = entityId,
                    entityState = entityState,
                    bodyWidthDp = panelW - CardChrome.CLOSE_KEY_DP,
                    panelHeightDp = panelH,
                )
            }
        }
    }

    /** A vacuum card's rooms dialog, over the whole card document, while it is open. */
    private fun DocContext.vacuumRoomsLayer(entityId: String, entityState: EntityState?, screenW: Float) {
        val tileId = "e:$entityId"
        val ui = data.vacuumUi[tileId]?.takeIf { it.roomsDialogOpen }
        val vacuumRooms = data.vacuumRooms[entityId]
        if (ui != null && vacuumRooms != null) {
            vacuumRoomsDialog(
                tileId = tileId,
                vacuumName = entityState?.name?.ifBlank { null } ?: entityId,
                vacuumRooms = vacuumRooms,
                ui = ui,
                screenW = screenW,
            )
        }
    }

    /** Colours a card reads, under a tile id of its own. */
    const val TILE = "card"

    private val ALWAYS_ON_CARDS = setOf("camera", "map", "energy", "history")

    private fun resolveCardType(kind: String?, entityId: String): String {
        val spec = Catalogue.of(kind ?: entityId.substringBefore('.'))
        return when (kind) {
            "history" -> "history"
            "energy" -> "energy"
            else -> spec?.card ?: "toggle"
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun DocContext.renderCardBody(
        card: String,
        entityId: String,
        entityState: EntityState?,
        bodyWidthDp: Float,
        panelHeightDp: Float,
    ) {
        when (card) {
            "light" -> lightCard(entityId, entityState, bodyWidthDp)
            "fan" -> fanCard(entityId, entityState, bodyWidthDp)
            "humidifier" -> humidifierCard(entityId, entityState, bodyWidthDp)
            "cover" -> coverCard(entityId, entityState, bodyWidthDp)
            "lock" -> lockCard(entityId, entityState)
            "climate" -> climateCard(entityId, entityState)
            "media" -> mediaCard(entityId, entityState, bodyWidthDp)
            "weather" -> weatherCard(entityId, entityState, bodyWidthDp)
            "history" -> historyCard(
                entityId,
                bodyWidthDp,
                panelHeightDp - CardChrome.HISTORY_RESERVE_DP,
            )
            "camera" -> cameraCard(
                entityId,
                bodyWidthDp,
                panelHeightDp - CardChrome.CAMERA_RESERVE_DP,
            )
            "alarm" -> alarmCard(entityId, entityState, bodyWidthDp)
            "select" -> selectCard(entityId, entityState)
            "number" -> numberCard(entityId, entityState, bodyWidthDp)
            "vacuum" -> vacuumCard(
                entityId = entityId,
                entityState = entityState,
                uiTileId = "e:$entityId",
                ui = data.vacuumUi["e:$entityId"] ?: VacuumUiState(),
            )
            "lawn_mower" -> lawnMowerCard(entityId)
            "calendar" -> calendarCard(entityId, entityState)
            "todo" -> todoCard(entityId)
            "timer" -> timerCard(entityId, entityState)
            "counter" -> counterCard(entityId, entityState)
            "water_heater" -> waterHeaterCard(
                entityId,
                entityState,
                bodyWidthDp,
            )
            "valve" -> valveCard(entityId, entityState, bodyWidthDp)
            "siren" -> sirenCard(entityId, entityState)
            "input_text" -> inputTextCard(entityId, entityState)
            "input_datetime" -> inputDatetimeCard(entityId, entityState)
            "map" -> mapCard(
                entityId,
                bodyWidthDp,
                panelHeightDp - CardChrome.CAMERA_RESERVE_DP,
            )
            "energy" -> energyFlow(
                bodyWidthDp,
                panelHeightDp - CardChrome.CAMERA_RESERVE_DP,
                compact = false,
            )
            else -> toggleCard(entityId, entityState)
        }
    }
}
