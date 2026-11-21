@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.freeform.accent
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.alarmPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.assist.assistPanel
import tech.shaffersoftworks.remotescreens.server.render.widgets.at
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.panels.calendar.calendarPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.home.homeTile
import tech.shaffersoftworks.remotescreens.server.render.panels.cameras.camerasPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.energy.energyPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.energydashboard.energyDashboardPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.intercom.intercomPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.blinds.blindsPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.lightsPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.security.securityPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.vacuum.vacuumPanel
import tech.shaffersoftworks.remotescreens.server.render.cards.calendar.calendarTile
import tech.shaffersoftworks.remotescreens.server.render.cards.camera.cameraTile
import tech.shaffersoftworks.remotescreens.server.render.cards.climate.climateTile
import tech.shaffersoftworks.remotescreens.server.render.cards.history.historyTile
import tech.shaffersoftworks.remotescreens.server.render.cards.map.mapTile
import tech.shaffersoftworks.remotescreens.server.render.cards.media.mediaTile
import tech.shaffersoftworks.remotescreens.server.render.cards.weather.weatherTile
import tech.shaffersoftworks.remotescreens.server.render.panels.energy.energyFlow
import tech.shaffersoftworks.remotescreens.server.render.tiles.bedside.bedsideTile
import tech.shaffersoftworks.remotescreens.server.render.tiles.clock.clockTile
import tech.shaffersoftworks.remotescreens.server.render.tiles.favourite.favouriteTile
import tech.shaffersoftworks.remotescreens.server.render.tiles.nav.navTile
import tech.shaffersoftworks.remotescreens.server.render.tiles.person.personTile
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.server.render.panels.music.musicPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.thermostat.thermostatPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.weather.weatherPanel

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TileKind
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C

/**
 * A tile is an RPI card: a black edge, a grey body and, where there is room, a darker header strip with its name.
 * The strip's bottom line takes the tile's accent while the entity is on.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun DocContext.tile(tile: Tile, box: TileBox, pageIndex: (String) -> Int?) {
    val kindWire = tile.effectiveKind()
    val kind = tile.tileKind()
    val spec = Catalogue.of(kindWire)
    val entityId = tile.entityId
    val entityState = entityId?.let { states[it] }

    if (entityId != null) bindEntity(entityId = entityId, name = tile.name, icon = tile.icon)

    val lit = accent(tile.color, Pastel.forDomain(tile.domain ?: kindWire))
    bindTileColors(tileId = tile.id, entityId = entityId, accent = lit, alwaysOn = kind in C.ALWAYS_LIT)

    val tapRef = tapRef(tile = tile, action = tile.tap, spec = spec, pageIndex = pageIndex, hold = false)
    val holdRef = tapRef(tile = tile, action = tile.hold, spec = spec, pageIndex = pageIndex, hold = true)

    if (tapRef != null && entityId != null && spec?.predict?.containsKey(
            tapRef.verb,
        ) == true) {
        predict(tapRef, entityId)
    }

    if (kind == TileKind.HOME || box.edge) {
        val hasOwnTaps = kind in setOf(
            TileKind.CALENDAR,
            TileKind.ALARM_PANEL,
            TileKind.LIGHTS,
            TileKind.BLINDS,
            TileKind.VACUUM,
            TileKind.SECURITY,
            TileKind.CAMERAS,
            TileKind.INTERCOM,
            TileKind.ENERGY,
            TileKind.ENERGY_DASHBOARD,
            TileKind.ASSIST,
        )

        val pageTap = if (hasOwnTaps) null else tapRef
        val pageHold = if (hasOwnTaps) null else holdRef

        at(
            box.x,
            box.y,
        ) { rcScope.Box(
            tap(modifier = Modifier.size(units.px(box.w), units.px(box.h)), ref = pageTap, hold = pageHold)
        ) {
            when (kind) {
                TileKind.HOME -> this@tile.homeTile(tile, box)
                TileKind.CLIMATE -> this@tile.thermostatPanel(tile, box)
                TileKind.MUSIC -> this@tile.musicPanel(tile, box)
                TileKind.CALENDAR -> this@tile.calendarPanel(tile, box)
                TileKind.ALARM_PANEL -> this@tile.alarmPanel(tile, box)
                TileKind.LIGHTS -> this@tile.lightsPanel(tile, box)
                TileKind.BLINDS -> this@tile.blindsPanel(tile, box)
                TileKind.VACUUM -> this@tile.vacuumPanel(tile, box)
                TileKind.SECURITY -> this@tile.securityPanel(tile, box)
                TileKind.CAMERAS -> this@tile.camerasPanel(tile, box)
                TileKind.INTERCOM -> this@tile.intercomPanel(tile, box)
                TileKind.ENERGY -> this@tile.energyPanel(tile, box)
                TileKind.ENERGY_DASHBOARD -> this@tile.energyDashboardPanel(tile, box)
                TileKind.ASSIST -> this@tile.assistPanel(tile, box)
                else -> this@tile.weatherPanel(tile, box)
            }
        } }

        return
    }

    val inner = box.copy(w = box.w - 2 * C.CARD_EDGE, h = box.h - 2 * C.CARD_EDGE)

    val asMap = kind == TileKind.MAP || (kind in setOf(
        TileKind.PERSON,
        TileKind.DEVICE_TRACKER,
    ) && tile.options[Tile.OPTION_MAP] == Tile.OPTION_TRUE)

    val hasHeader = (asMap || kind !in C.NO_HEADER) && inner.w >= C.HEADER_MIN_DP && inner.h >= C.HEADER_MIN_DP &&
        !(kind == TileKind.CAMERA && tile.options[Tile.OPTION_NAME] == Tile.OPTION_FALSE)

    val headerH = if (!hasHeader) {
        0f
    } else if (inner.h >= C.HEADER_TALL_MIN_DP) {
        C.HEADER_TALL_DP
    } else {
        C.HEADER_SHORT_DP
    }

    val body = inner.copy(h = inner.h - if (hasHeader) headerH + C.HEADER_LINE else 0f)
    val named = !hasHeader

    val modifier = Modifier.size(
        units.px(box.w),
        units.px(box.h),
    ).clip(rounded(C.CARD_CORNER_DP)).background(edgeOf(tile)).padding(units.px(C.CARD_EDGE))

    at(box.x, box.y) { rcScope.Box(tap(modifier = modifier, ref = tapRef, hold = holdRef)) {
        val docContext = this@tile

        Column(
            Modifier.fillMaxSize().clip(
                docContext.rounded(C.PAD_SM_DP),
            ).background(docContext.role(Role.CARD)),
        ) {
            if (hasHeader) {
                Box(
                    Modifier.fillMaxWidth().height(
                        docContext.units.px(headerH),
                    ).background(
                        docContext.role(Role.CARD_HEADER),
                    ).padding(
                        start = docContext.units.px(C.PAD_LG_DP),
                        top = 0f,
                        end = docContext.units.px(C.PAD_LG_DP),
                        bottom = 0f,
                    ),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    docContext.headerTitle(
                        tile = tile,
                        kind = kind,
                        sp = if (headerH >= C.HEADER_TALL_DP) Type.TITLE else Type.BODY + 1f,
                    )
                }

                Box(
                    Modifier.fillMaxWidth().height(
                        docContext.units.px(C.HEADER_LINE),
                    ).background(docContext.color(Vars.tile(tile.id, Vars.KEY_LINE))),
                )
            }

            // A weight here lays the header title out at the top of the screen, so the name paints
            // in the bar. The body is the height left under the header.
            Box(Modifier.fillMaxWidth().height(docContext.units.px(body.h))) {
                when (kind) {
                    TileKind.CLOCK -> docContext.clockTile(tile, body)
                    TileKind.BEDSIDE -> docContext.bedsideTile(tile, body)
                    TileKind.NAV -> docContext.navTile(tile = tile, box = body, named = named)
                    TileKind.WEATHER -> docContext.weatherTile(
                        tile = tile,
                        box = body,
                        entityState = entityState,
                        named = named,
                    )
                    TileKind.CLIMATE -> docContext.climateTile(
                        tile = tile,
                        box = body,
                        entityState = entityState,
                        named = named,
                    )
                    TileKind.MEDIA_PLAYER -> docContext.mediaTile(tile = tile, box = body, entityState = entityState)
                    TileKind.CAMERA -> docContext.cameraTile(tile = tile, box = body, named = named)
                    TileKind.HISTORY -> docContext.historyTile(tile = tile, box = body, named = named)
                    TileKind.ENERGY -> docContext.energyFlow(
                        wDp = body.w,
                        hDp = body.h,
                        compact = !body.full && body.w < 400f,
                    )
                    TileKind.MAP -> docContext.mapTile(tile = tile, box = body, named = named)
                    TileKind.PERSON, TileKind.DEVICE_TRACKER -> if (asMap) {
                        docContext.mapTile(
                            tile = tile,
                            box = body,
                            named = named,
                        )
                    } else {
                        docContext.personTile(tile = tile, box = body, entityState = entityState)
                    }
                    TileKind.FAVOURITE -> docContext.favouriteTile(tile = tile, box = body, named = named)
                    TileKind.CALENDAR -> docContext.calendarTile(
                        tile = tile,
                        box = body,
                        entityState = entityState,
                        named = named,
                    )
                    TileKind.SENSOR -> if (
                        tile.options[Tile.OPTION_GRAPH] == Tile.OPTION_TRUE &&
                        body.h >= C.SHORT_HEIGHT_DP
                    ) {
                        docContext.historyTile(
                            tile = tile,
                            box = body,
                            named = named,
                        )
                    } else {
                        docContext.entityTile(
                            tile = tile,
                            box = body,
                            spec = spec,
                            entityState = entityState,
                            named = named,
                        )
                    }
                    else -> docContext.entityTile(
                        tile = tile,
                        box = body,
                        spec = spec,
                        entityState = entityState,
                        named = named,
                    )
                }
            }
        }
    } }
}
