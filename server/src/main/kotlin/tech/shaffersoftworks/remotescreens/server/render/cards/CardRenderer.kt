@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.vacuum.VacuumUiState
import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.freeform.accent
import tech.shaffersoftworks.remotescreens.server.render.withTileAccent
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.RenderInput
import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.server.render.cards.alarm.alarmCard
import tech.shaffersoftworks.remotescreens.server.render.cards.calendar.calendarCard
import tech.shaffersoftworks.remotescreens.server.render.cards.camera.cameraCard
import tech.shaffersoftworks.remotescreens.server.render.cards.climate.climateCard
import tech.shaffersoftworks.remotescreens.server.render.cards.counter.counterCard
import tech.shaffersoftworks.remotescreens.server.render.cards.cover.coverCard
import tech.shaffersoftworks.remotescreens.server.render.cards.fan.fanCard
import tech.shaffersoftworks.remotescreens.server.render.cards.history.HISTORY_RESERVE_DP
import tech.shaffersoftworks.remotescreens.server.render.cards.history.historyCard
import tech.shaffersoftworks.remotescreens.server.render.cards.humidifier.humidifierCard
import tech.shaffersoftworks.remotescreens.server.render.cards.input.inputDatetimeCard
import tech.shaffersoftworks.remotescreens.server.render.cards.input.inputTextCard
import tech.shaffersoftworks.remotescreens.server.render.cards.lawnmower.lawnMowerCard
import tech.shaffersoftworks.remotescreens.server.render.cards.light.lightCard
import tech.shaffersoftworks.remotescreens.server.render.cards.lock.lockCard
import tech.shaffersoftworks.remotescreens.server.render.cards.map.mapCard
import tech.shaffersoftworks.remotescreens.server.render.cards.media.mediaCard
import tech.shaffersoftworks.remotescreens.server.render.cards.number.numberCard
import tech.shaffersoftworks.remotescreens.server.render.cards.select.selectCard
import tech.shaffersoftworks.remotescreens.server.render.cards.siren.sirenCard
import tech.shaffersoftworks.remotescreens.server.render.cards.timer.timerCard
import tech.shaffersoftworks.remotescreens.server.render.cards.todo.todoCard
import tech.shaffersoftworks.remotescreens.server.render.cards.toggle.toggleCard
import tech.shaffersoftworks.remotescreens.server.render.cards.valve.valveCard
import tech.shaffersoftworks.remotescreens.server.render.cards.waterheater.waterHeaterCard
import tech.shaffersoftworks.remotescreens.server.render.cards.weather.weatherCard
import tech.shaffersoftworks.remotescreens.server.render.panels.energy.energyFlow
import tech.shaffersoftworks.remotescreens.server.render.panels.vacuum.vacuumCard
import tech.shaffersoftworks.remotescreens.server.render.panels.vacuum.vacuumRoomsLayer
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
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
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId

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
        colorKey: String? = null,
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
            if (card != CardType.ENERGY) docContext.bindEntity(entityId)

            val fallback = Pastel.forDomain(entityState?.domain?.value)

            docContext.withTileAccent(colorKey = colorKey, fallback = fallback) {
                val chosen = colorKey?.let { accent(it, fallback) }

                bindTileColors(
                    tileId = TILE,
                    entityId = entityId,
                    accent = chosen ?: fallback.argb,
                    alwaysOn = card in ALWAYS_ON_CARDS,
                    explicitColor = colorKey != null,
                )

                Box(Modifier.fillMaxSize()) {
                    Column(
                        docContext.tap(
                            Modifier.fillMaxSize().background(docContext.role(Role.SCRIM)),
                            ActionRef("_", "close"),
                        ),
                        horizontal = RcHorizontalPositioning.Center,
                    ) {
                        // Box-center in the page area; twin weight spacers pile leftover on the first.
                        Spacer(Modifier.height(docContext.units.px(topInsetDp)))

                        Box(
                            Modifier.fillMaxWidth().verticalWeight(1f),
                            horizontal = RcHorizontalPositioning.Center,
                            vertical = RcVerticalPositioning.Center,
                        ) {
                            docContext.cardPanel(
                                card = card,
                                entityId = entityId,
                                entityState = entityState,
                                panelW = panelW,
                                panelH = panelH,
                            )
                        }

                        Spacer(Modifier.height(docContext.units.px(bottomInsetDp)))
                    }

                    if (card == CardType.VACUUM) {
                        docContext.vacuumRoomsLayer(
                            entityId = entityId,
                            entityState = entityState,
                            screenW = device.widthDp.toFloat(),
                        )
                    }
                }
            }
        }

        return RenderedDoc(bytes, docContext)
    }

    private fun DocContext.cardPanel(
        card: CardType,
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

    /** Colours a card reads, under a tile id of its own. */
    const val TILE = "card"

    private val ALWAYS_ON_CARDS = setOf(CardType.CAMERA, CardType.MAP, CardType.ENERGY, CardType.HISTORY)

    private fun resolveCardType(kind: String?, entityId: String): CardType {
        val spec = Catalogue.of(kind ?: EntityId(entityId).domain.value)

        return when (kind) {
            CardType.HISTORY.wire -> CardType.HISTORY
            CardType.ENERGY.wire -> CardType.ENERGY
            else -> CardType.fromWire(spec?.card)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun DocContext.renderCardBody(
        card: CardType,
        entityId: String,
        entityState: EntityState?,
        bodyWidthDp: Float,
        panelHeightDp: Float,
    ) {
        when (card) {
            CardType.LIGHT -> lightCard(entityId = entityId, entityState = entityState, width = bodyWidthDp)
            CardType.FAN -> fanCard(entityId = entityId, entityState = entityState, width = bodyWidthDp)
            CardType.HUMIDIFIER -> humidifierCard(entityId = entityId, entityState = entityState, width = bodyWidthDp)
            CardType.COVER -> coverCard(entityId = entityId, entityState = entityState, width = bodyWidthDp)
            CardType.LOCK -> lockCard(entityId, entityState)
            CardType.CLIMATE -> climateCard(entityId, entityState)
            CardType.MEDIA -> mediaCard(entityId = entityId, entityState = entityState, width = bodyWidthDp)
            CardType.WEATHER -> weatherCard(entityId = entityId, entityState = entityState, width = bodyWidthDp)
            CardType.HISTORY -> historyCard(
                entityId = entityId,
                width = bodyWidthDp,
                height = panelHeightDp - HISTORY_RESERVE_DP
            )
            CardType.CAMERA -> cameraCard(
                entityId = entityId,
                width = bodyWidthDp,
                height = panelHeightDp - CardChrome.CAMERA_RESERVE_DP,
            )
            CardType.ALARM -> alarmCard(entityId = entityId, entityState = entityState, width = bodyWidthDp)
            CardType.SELECT -> selectCard(entityId, entityState)
            CardType.NUMBER -> numberCard(entityId = entityId, entityState = entityState, width = bodyWidthDp)
            CardType.VACUUM -> vacuumCard(
                entityId = entityId,
                entityState = entityState,
                uiTileId = "e:$entityId",
                ui = data.vacuumUi["e:$entityId"] ?: VacuumUiState(),
            )
            CardType.LAWN_MOWER -> lawnMowerCard(entityId)
            CardType.CALENDAR -> calendarCard(entityId, entityState)
            CardType.TODO -> todoCard(entityId)
            CardType.TIMER -> timerCard(entityId, entityState)
            CardType.COUNTER -> counterCard(entityId, entityState)
            CardType.WATER_HEATER -> waterHeaterCard(
                entityId = entityId,
                entityState = entityState,
                width = bodyWidthDp
            )
            CardType.VALVE -> valveCard(entityId = entityId, entityState = entityState, width = bodyWidthDp)
            CardType.SIREN -> sirenCard(entityId, entityState)
            CardType.INPUT_TEXT -> inputTextCard(entityId, entityState)
            CardType.INPUT_DATETIME -> inputDatetimeCard(entityId, entityState)
            CardType.MAP -> mapCard(
                entityId = entityId,
                width = bodyWidthDp,
                height = panelHeightDp - CardChrome.CAMERA_RESERVE_DP,
            )
            CardType.ENERGY -> energyFlow(
                wDp = bodyWidthDp,
                hDp = panelHeightDp - CardChrome.CAMERA_RESERVE_DP,
                compact = false,
            )
            CardType.TOGGLE, CardType.NONE -> toggleCard(entityId, entityState)
        }
    }
}
