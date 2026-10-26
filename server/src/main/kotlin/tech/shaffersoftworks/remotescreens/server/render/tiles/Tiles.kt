@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.freeform.accent
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.alarmPanel
import tech.shaffersoftworks.remotescreens.server.render.widgets.at
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.panels.calendar.calendarPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.home.homeTile
import tech.shaffersoftworks.remotescreens.server.render.panels.cameras.camerasPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.intercom.intercomPanel
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.lightsPanel
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
import androidx.compose.remote.creation.dsl.verticalWeight
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C

/**
 * A tile is an RPI card: a black edge, a grey body and, where there is room, a darker header strip with its name.
 * The strip's bottom line takes the tile's accent while the entity is on.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun DocContext.tile(tile: Tile, box: TileBox, pageIndex: (String) -> Int?) {
    val kind = tile.effectiveKind()
    val spec = Catalogue.of(kind)
    val entityId = tile.entityId
    val entityState = entityId?.let { states[it] }
    if (entityId != null) bindEntity(entityId, tile.name, tile.icon)
    val lit = accent(tile.color, Pastel.forDomain(tile.domain ?: kind))
    bindTileColors(tile.id, entityId, lit, alwaysOn = kind in C.ALWAYS_LIT)

    val tapRef = tapRef(tile, tile.tap, spec, pageIndex, hold = false)
    val holdRef = tapRef(tile, tile.hold, spec, pageIndex, hold = true)
    if (tapRef != null && entityId != null && spec?.predict?.containsKey(
            tapRef.verb,
        ) == true) {
        predict(tapRef, entityId)
    }

    if (kind == "home" || box.edge) {
        val hasOwnTaps = kind in setOf("calendar", "alarm_control_panel", "lights", "cameras", "intercom")
        val pageTap = if (hasOwnTaps) null else tapRef
        val pageHold = if (hasOwnTaps) null else holdRef
        at(
            box.x,
            box.y,
        ) { scope.Box(tap(Modifier.size(units.px(box.w), units.px(box.h)), pageTap, pageHold)) {
            when (kind) {
                "home" -> this@tile.homeTile(tile, box)
                "climate" -> this@tile.thermostatPanel(tile, box)
                "music" -> this@tile.musicPanel(tile, box)
                "calendar" -> this@tile.calendarPanel(tile, box)
                "alarm_control_panel" -> this@tile.alarmPanel(tile, box)
                "lights" -> this@tile.lightsPanel(tile, box)
                "cameras" -> this@tile.camerasPanel(tile, box)
                "intercom" -> this@tile.intercomPanel(tile, box)
                else -> this@tile.weatherPanel(tile, box)
            }
        } }
        return
    }

    val inner = box.copy(w = box.w - 2 * C.CARD_EDGE, h = box.h - 2 * C.CARD_EDGE)
    val asMap = kind == "map" || (kind in setOf(
        "person",
        "device_tracker",
    ) && tile.options["map"] == "true")
    val hasHeader = (asMap || kind !in C.NO_HEADER) && inner.w >= C.HEADER_MIN_DP && inner.h >= C.HEADER_MIN_DP &&
        !(kind == "camera" && tile.options["name"] == "false")
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
    at(box.x, box.y) { scope.Box(tap(modifier, tapRef, holdRef)) {
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
                        docContext.units.px(C.PAD_LG_DP),
                        0f,
                        docContext.units.px(C.PAD_LG_DP),
                        0f,
                    ),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    docContext.headerTitle(
                        tile,
                        kind,
                        if (headerH >= C.HEADER_TALL_DP) Type.TITLE else Type.BODY + 1f,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(
                        docContext.units.px(C.HEADER_LINE),
                    ).background(docContext.color(Vars.tile(tile.id, Vars.KEY_LINE))),
                )
            }
            Box(Modifier.fillMaxWidth().verticalWeight(1f)) {
                when (kind) {
                    "clock" -> docContext.clockTile(tile, body)
                    "bedside" -> docContext.bedsideTile(tile, body)
                    "nav" -> docContext.navTile(tile, body, named)
                    "weather" -> docContext.weatherTile(tile, body, entityState, named)
                    "climate" -> docContext.climateTile(tile, body, entityState, named)
                    "media_player" -> docContext.mediaTile(tile, body, entityState)
                    "camera" -> docContext.cameraTile(tile, body, named)
                    "history" -> docContext.historyTile(tile, body, named)
                    "energy" -> docContext.energyFlow(
                        body.w,
                        body.h,
                        compact = !body.full && body.w < 400f,
                    )
                    "map" -> docContext.mapTile(tile, body, named)
                    "person", "device_tracker" -> if (asMap) {
                        docContext.mapTile(
                            tile,
                            body,
                            named,
                        )
                    } else {
                        docContext.personTile(tile, body, entityState)
                    }
                    "favourite" -> docContext.favouriteTile(tile, body, named)
                    "calendar" -> docContext.calendarTile(tile, body, entityState, named)
                    "sensor" -> if (tile.options["graph"] == "true" && body.h >= C.SHORT_HEIGHT_DP) {
                        docContext.historyTile(
                            tile,
                            body,
                            named,
                        )
                    } else {
                        docContext.entityTile(tile, body, spec, entityState, named)
                    }
                    else -> docContext.entityTile(tile, body, spec, entityState, named)
                }
            }
        }
    } }
}
