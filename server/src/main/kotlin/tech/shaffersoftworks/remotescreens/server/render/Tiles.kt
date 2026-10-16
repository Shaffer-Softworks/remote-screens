@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.TapType
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TypeSpec
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.time.ZoneId
import kotlin.math.roundToInt


/** Where a tile sits, in dp. An [edge] tile fills the page from edge to edge, without the grid's margins. */
data class TileBox(val x: Float, val y: Float, val w: Float, val h: Float, val cols: Int, val rows: Int, val full: Boolean, val edge: Boolean = false)

/** Whether the tile is a page of its own (the home panel, a full weather or climate tile), drawn without a card from edge to edge. */
fun Tile.edgeToEdge(): Boolean = full && effectiveKind() in setOf("home", "weather", "climate")

/** What a tap (or a hold) on a tile sends. */
fun tapRef(tile: Tile, action: TapAction, spec: TypeSpec?, pageIndex: (String) -> Int?, hold: Boolean): ActionRef? {
    val hasEntityFor = tile.entityId != null || spec?.needsEntity == false
    fun card() = if (spec != null && spec.card != "none" && hasEntityFor) ActionRef(tile.id, "card") else null
    return when (action.type) {
        TapType.NONE -> null
        TapType.TOGGLE -> ActionRef(tile.id, "toggle")
        TapType.CARD -> card()
        TapType.ACTION -> ActionRef(tile.id, "run")
        TapType.PAGE -> action.page?.let(pageIndex)?.let { ActionRef("_", "page", it.toString()) }
        TapType.DEFAULT -> when {
            hold -> card()
            tile.effectiveKind() == "nav" -> tile.options["page"]?.let(pageIndex)?.let { ActionRef("_", "page", it.toString()) }
            tile.effectiveKind() == "favourite" -> ActionRef(tile.id, "favourite")
            spec == null -> null
            spec.tap == "toggle" -> ActionRef(tile.id, "toggle")
            spec.tap == "action" -> spec.defaultVerb?.let { ActionRef(tile.id, it) }
            else -> card()
        }
    }
}

private val ALWAYS_LIT = setOf("scene", "script", "button", "input_button", "nav", "favourite")

/** Kinds whose card has no header strip: they show their own words, or none. */
private val NO_HEADER = setOf("clock", "bedside", "energy", "person", "device_tracker")

private const val CARD_EDGE = 2f
private const val HEADER_LINE = 3f
private const val CARD_CORNER_DP = 10f
private const val BODY_CORNER_DP = 8f
private const val HEADER_PAD_H_DP = 14f
private const val HEADER_MIN_DP = 96f
private const val HEADER_TALL_MIN_DP = 200f
private const val HEADER_TALL_DP = 44f
private const val HEADER_SHORT_DP = 36f
private const val PAD_SM_DP = 8f
private const val PAD_MD_DP = 12f
private const val PAD_LG_DP = 14f
private const val PAD_XL_DP = 16f
private const val PAD_XXL_DP = 24f
private const val PAD_NARROW_DP = 12f
private const val PAD_WIDE_DP = 16f
private const val NARROW_WIDTH_DP = 130f
private const val SHORT_HEIGHT_DP = 120f
private const val TALL_HEIGHT_DP = 150f
private const val WIDE_ASPECT = 0.9f
private const val LEVEL_MIN_WIDTH_DP = 150f
private const val LEVEL_BAR_HEIGHT_DP = 28f
private const val LEVEL_STEPS = 10
private const val LEVEL_DEFAULT_MAX = 100.0
private const val COVER_POSITION_FLAG = 4
private const val FAN_PERCENTAGE_FLAG = 1
private const val FULL_ICON_DP = 120f
private const val FULL_NAME_SCALE = 0.8f
private const val ROW_ICON_DP = 36f
private const val COL_ICON_TALL_DP = 40f
private const val COL_ICON_SHORT_DP = 30f
private const val CLOCK_H_SHARE = 0.38f
private const val CLOCK_W_SHARE = 0.22f
private const val BEDSIDE_TIME_W = 0.24f
private const val BEDSIDE_TIME_H = 0.4f
private const val BEDSIDE_KEY_DP = 76f
private const val BEDSIDE_KEY_ICON_DP = 34f
private const val BEDSIDE_KEY_GAP_DP = 28f
private const val BEDSIDE_WEIGHT = 200f
private const val NAV_ICON_NAMED_DP = 32f
private const val NAV_ICON_MAX_DP = 56f
private const val NAV_ICON_SHARE = 0.45f
private const val PERSON_SHORT_MAX_DP = 140f
private const val PERSON_PHOTO_MAX_DP = 56f
private const val PERSON_PHOTO_MIN_DP = 28f
private const val PERSON_PHOTO_W_SHARE = 0.35f
private const val PERSON_PHOTO_PAD_DP = 24f
private const val PERSON_TALL_MAX_DP = 70f
private const val PERSON_TALL_H_SHARE = 0.45f
private const val PERSON_TALL_W_SHARE = 0.4f
private const val PERSON_TALL_MIN_DP = 32f
private const val WEATHER_REFRESH_S = 1800
private const val WEATHER_WIDE_DP = 300f
private const val WEATHER_MID_DP = 200f
private const val WEATHER_DAYS_WIDE = 5
private const val WEATHER_DAYS_MID = 3
private const val WEATHER_ICON_TALL_DP = 52f
private const val WEATHER_ICON_SHORT_DP = 36f
private const val FORECAST_ICON_DP = 26f
private const val CLIMATE_STEPPER_W_DP = 200f
private const val CLIMATE_KEY_SM_DP = 44f
private const val CLIMATE_KEY_LG_DP = 48f
private const val CLIMATE_VALUE_BUMP = 4f
private const val CLIMATE_MODES_MIN_H_DP = 260f
private const val CLIMATE_MODE_H_DP = 40f
private const val CLIMATE_MODES_TAKE = 4
private const val MEDIA_MIN_W_DP = 200f
private const val MEDIA_ART_ALPHA = 0.35f
private const val MEDIA_ICON_DP = 28f
private const val MEDIA_CTRL_DP = 44f
private const val MEDIA_PLAY_DP = 52f
private const val MEDIA_PLAY_ICON_DP = 28f
private const val MEDIA_TITLE_BUMP = 2f
private const val CAMERA_LIVE_MIN_S = 5
private const val CAMERA_LIVE_MAX_S = 30
private const val CAMERA_LIVE_DEFAULT_S = 60
private const val GAP_10_DP = 10f
private const val FAV_ART_MIN_W_DP = 120f
private const val FAV_ART_ALPHA = 0.45f
private const val FAV_ICON_DP = 28f
private const val HISTORY_REFRESH_S = 300
private const val HISTORY_GAP_DP = 6f
private const val GRAPH_PAD_H_DP = 28f
private const val GRAPH_NAMED_RESERVE_DP = 90f
private const val GRAPH_UNNAMED_RESERVE_DP = 70f
private const val GRAPH_MIN_H_DP = 30f
private const val GRAPH_STROKE_DP = 2.5f
private const val GRAPH_FLAT_EPS = 0.001
private const val MAP_LABEL_PAD_DP = 8f
private const val ENERGY_NODE_COMPACT_DP = 30f
private const val ENERGY_NODE_DP = 42f
private const val ENERGY_CY_BUMP_DP = 6f
private const val ENERGY_SOLAR_Y_PAD_DP = 16f
private const val ENERGY_SIDE_PAD_DP = 14f
private const val ENERGY_HOME_Y_PAD_DP = 26f
private const val ENERGY_LINE_DP = 3f
private const val ENERGY_LABEL_EXTRA_DP = 22f
private const val ENERGY_ICON_COMPACT_DP = 18f
private const val ENERGY_ICON_DP = 24f
private const val ENERGY_DEVICE_X_DP = 12f
private const val SPACE_20_DP = 20f
private const val ENERGY_DEVICE_ROW_DP = 22f
private const val ENERGY_DEVICE_MAX = 4
private const val ENERGY_DEVICE_GAP_DP = 6f
private const val ENERGY_COMPACT_MAX_W_DP = 400f
private const val CAPTION_BUMP = 1f
private const val BODY_BUMP = 1f
private const val BODY_BUMP_2 = 2f

/** The tile's own border colour, else the theme's card edge. */
private fun DocContext.edgeOf(tile: Tile) = tile.borderColor?.let { paint(it) } ?: role(Role.CARD_EDGE)

/**
 * A tile is an RPI card: a black edge, a grey body and, where there is room, a darker header strip with its name.
 * The strip's bottom line takes the tile's accent while the entity is on.
 */
fun DocContext.tile(tile: Tile, box: TileBox, pageIndex: (String) -> Int?) {
    val kind = tile.effectiveKind()
    val spec = Catalogue.of(kind)
    val entityId = tile.entityId
    val entityState = entityId?.let { states[it] }
    if (entityId != null) bindEntity(entityId, tile.name, tile.icon)
    val lit = accent(tile.color, Pastel.forDomain(tile.domain ?: kind))
    bindTileColors(tile.id, entityId, lit, alwaysOn = kind in ALWAYS_LIT)

    val tapRef = tapRef(tile, tile.tap, spec, pageIndex, hold = false)
    val holdRef = tapRef(tile, tile.hold, spec, pageIndex, hold = true)
    if (tapRef != null && entityId != null && spec?.predict?.containsKey(tapRef.verb) == true) predict(tapRef, entityId)

    if (kind == "home" || box.edge) {
        at(box.x, box.y) { scope.Box(tap(Modifier.size(units.px(box.w), units.px(box.h)), tapRef, holdRef)) {
            when (kind) {
                "home" -> this@tile.homeTile(tile, box)
                "climate" -> this@tile.thermostatPanel(tile, box)
                else -> this@tile.weatherPanel(tile, box)
            }
        } }
        return
    }

    val inner = box.copy(w = box.w - 2 * CARD_EDGE, h = box.h - 2 * CARD_EDGE)
    val asMap = kind == "map" || (kind in setOf("person", "device_tracker") && tile.options["map"] == "true")
    val hasHeader = (asMap || kind !in NO_HEADER) && inner.w >= HEADER_MIN_DP && inner.h >= HEADER_MIN_DP &&
        !(kind == "camera" && tile.options["name"] == "false")
    val headerH = if (!hasHeader) 0f else if (inner.h >= HEADER_TALL_MIN_DP) HEADER_TALL_DP else HEADER_SHORT_DP
    val body = inner.copy(h = inner.h - if (hasHeader) headerH + HEADER_LINE else 0f)
    val named = !hasHeader

    val modifier = Modifier.size(units.px(box.w), units.px(box.h)).clip(rounded(CARD_CORNER_DP)).background(edgeOf(tile)).padding(units.px(CARD_EDGE))
    at(box.x, box.y) { scope.Box(tap(modifier, tapRef, holdRef)) {
        val docContext = this@tile
        Column(Modifier.fillMaxSize().clip(docContext.rounded(PAD_SM_DP)).background(docContext.role(Role.CARD))) {
            if (hasHeader) {
                Box(Modifier.fillMaxWidth().height(docContext.units.px(headerH)).background(docContext.role(Role.CARD_HEADER)).padding(docContext.units.px(PAD_LG_DP), 0f, docContext.units.px(PAD_LG_DP), 0f),
                    vertical = RcVerticalPositioning.Center) {
                    docContext.headerTitle(tile, kind, if (headerH >= HEADER_TALL_DP) Type.TITLE else Type.BODY + 1f)
                }
                Box(Modifier.fillMaxWidth().height(docContext.units.px(HEADER_LINE)).background(docContext.color(Vars.tile(tile.id, Vars.KEY_LINE))))
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
                    "energy" -> docContext.energyFlow(body.w, body.h, compact = !body.full && body.w < 400f)
                    "map" -> docContext.mapTile(tile, body, named)
                    "person", "device_tracker" -> if (asMap) docContext.mapTile(tile, body, named) else docContext.personTile(tile, body, entityState)
                    "favourite" -> docContext.favouriteTile(tile, body, named)
                    "sensor" -> if (tile.options["graph"] == "true" && body.h >= SHORT_HEIGHT_DP) docContext.historyTile(tile, body, named) else docContext.entityTile(tile, body, spec, entityState, named)
                    else -> docContext.entityTile(tile, body, spec, entityState, named)
                }
            }
        }
    } }
}

/** The words of a card's header strip: the entity's name, or the tile's own for tiles without one. */
private fun DocContext.headerTitle(tile: Tile, kind: String, sp: Float) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val entityId = tile.entityId
    when {
        kind == "map" && entityId == null -> {
            val who = mapPeople(tile)
            label(tile.name ?: i18n.t("ui.people_home", "count" to who.count { states[it]?.state == "home" }), sp, ink)
        }
        entityId != null && kind != "favourite" -> label(text(Vars.entity(entityId, Vars.KEY_NAME)), sp, ink)
        else -> label(tile.name ?: tile.options["title"] ?: Catalogue.of(kind)?.name ?: "", sp, ink)
    }
}

private fun DocContext.tileIcon(tile: Tile, spec: TypeSpec?, entityState: EntityState?, sizeDp: Float, colorKey: String = Vars.KEY_ICON) {
    val entityId = tile.entityId
    val ink = color(Vars.tile(tile.id, colorKey))
    val override = tile.icon ?: entityState?.attr("icon")
    when {
        override != null -> icon(override, sizeDp, ink)
        entityId != null && entityState != null && spec != null && spec.toggle && (spec.domain != "cover" && spec.domain != "lock") ->
            toggleIcon(entityId, spec.icon, spec.iconOn, sizeDp, ink)
        entityState != null -> {
            structural(entityState.entityId) { s -> s?.let { Present.view(it, i18n).icon } ?: "" }
            icon(Present.view(entityState, i18n).icon, sizeDp, ink)
        }
        else -> icon(spec?.icon, sizeDp, ink)
    }
}

/** The tile most entities get: an icon, the name and a line about the state, with a control where there is room. */
private fun DocContext.entityTile(tile: Tile, box: TileBox, spec: TypeSpec?, entityState: EntityState?, named: Boolean = true) {
    val entityId = tile.entityId
    val name = if (entityId != null && named) text(Vars.entity(entityId, Vars.KEY_NAME)) else null
    val state = if (entityId != null) text(Vars.entity(entityId, Vars.KEY_STATE)) else null
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val pad = if (box.w < NARROW_WIDTH_DP) PAD_MD_DP else PAD_XL_DP
    val short = box.h < SHORT_HEIGHT_DP
    val wide = (box.w >= 2 * box.h * WIDE_ASPECT && box.h < TALL_HEIGHT_DP) || short
    val hasLevel = entityId != null && box.w >= TALL_HEIGHT_DP && entityState != null && when (tile.domain) {
        "light" -> entityState.list("supported_color_modes").any { it != "onoff" }
        "fan" -> entityState.supports(FAN_PERCENTAGE_FLAG)
        "cover" -> entityState.supports(COVER_POSITION_FLAG)
        "number", "input_number", "media_player" -> true
        else -> false
    } && (box.h >= TALL_HEIGHT_DP || (wide && !short))
    val bigValue = tile.domain in setOf("sensor", "number", "input_number")

    if (box.full) {
        scope.Column(Modifier.fillMaxSize().padding(units.px(PAD_XXL_DP)), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
            this@entityTile.tileIcon(tile, spec, entityState, SHORT_HEIGHT_DP)
            this@entityTile.gap(PAD_XL_DP)
            if (name != null) this@entityTile.label(name, Type.DISPLAY * FULL_NAME_SCALE, ink, Weight.SEMI_BOLD, align = RcTextAlign.Center)
            if (state != null) this@entityTile.label(state, Type.HEADLINE, ink2, align = RcTextAlign.Center)
        }
        return
    }
    if (wide && !hasLevel) {
        scope.Row(Modifier.fillMaxSize().padding(units.px(pad)), vertical = RcVerticalPositioning.Center) {
            this@entityTile.tileIcon(tile, spec, entityState, HEADER_SHORT_DP)
            Spacer(Modifier.width(this@entityTile.units.px(PAD_LG_DP)))
            Column(Modifier.horizontalWeight(1f)) {
                if (name != null) this@entityTile.label(name, Type.TITLE, ink, Weight.SEMI_BOLD)
                if (state != null) this@entityTile.label(state, if (bigValue) Type.HEADLINE else Type.BODY, ink2, if (bigValue) Weight.MEDIUM else Weight.NORMAL)
            }
        }
        return
    }
    scope.Column(Modifier.fillMaxSize().padding(units.px(pad))) {
        val doc = this@entityTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Top) {
            doc.tileIcon(tile, spec, entityState, if (box.h >= TALL_HEIGHT_DP) COL_ICON_TALL_DP else COL_ICON_SHORT_DP)
            Spacer(Modifier.horizontalWeight(1f))
            if (tile.domain == "lock") doc.lockConfirmBadge(tile)
        }
        Spacer(Modifier.verticalWeight(1f))
        if (bigValue && state != null) doc.label(state, if (box.h >= TALL_HEIGHT_DP) Type.VALUE else Type.HEADLINE, ink, Weight.MEDIUM)
        if (name != null) doc.label(name, Type.BODY + if (box.h >= TALL_HEIGHT_DP) 2f else 0f, ink, Weight.SEMI_BOLD, lines = if (state == null || bigValue) 2 else 1)
        if (state != null && !bigValue) doc.label(state, if (named) Type.CAPTION + 1f else Type.TITLE, ink2)
        if (hasLevel) doc.entityTileLevelBar(tile, entityId!!, entityState!!, box.w - 2 * pad)
    }
}

private fun DocContext.entityTileLevelBar(tile: Tile, entityId: String, entityState: EntityState, widthDp: Float) {
    gap(GAP_10_DP)
    val verb = when (tile.domain) {
        "light" -> "brightness"
        "fan" -> "percentage"
        "cover" -> "position"
        "media_player" -> "volume"
        else -> "value"
    }
    val (minValue, maxValue) = if (tile.domain in setOf("number", "input_number")) {
        (entityState.number("min") ?: 0.0) to (entityState.number("max") ?: LEVEL_DEFAULT_MAX)
    } else {
        0.0 to LEVEL_DEFAULT_MAX
    }
    levelBar(
        float(Vars.entity(entityId, Vars.KEY_LEVEL)),
        widthDp,
        LEVEL_BAR_HEIGHT_DP,
        color(Vars.tile(tile.id, Vars.KEY_ICON)),
        role(Role.TRACK),
        LEVEL_STEPS,
    ) { step ->
        val value = minValue + (maxValue - minValue) * step / LEVEL_STEPS.toDouble()
        ActionRef(
            tile.id,
            verb,
            if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, value),
        )
    }
}

/** "Tap again to unlock" on a lock tile, shown by the add-on between the two taps. */
private fun DocContext.lockConfirmBadge(tile: Tile) {
    val confirmText = text(Vars.tile(tile.id, Vars.KEY_CONFIRM))
    label(confirmText, Type.CAPTION, role(Role.WARN), Weight.SEMI_BOLD)
}

private fun DocContext.clockTile(tile: Tile, box: TileBox) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val big = minOf(box.h * CLOCK_H_SHARE, box.w * CLOCK_W_SHARE).coerceAtLeast(Type.HEADLINE)
    scope.Column(Modifier.fillMaxSize().padding(units.px(PAD_LG_DP)), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
        this@clockTile.label(this@clockTile.text(Vars.TIME), big, ink, Weight.LIGHT, align = RcTextAlign.Center)
        this@clockTile.label(this@clockTile.text(Vars.DATE), Type.BODY, ink2, align = RcTextAlign.Center, lines = 2)
    }
}

/** The bedside clock: the time as large as the glass allows and up to three round keys for the night. */
private fun DocContext.bedsideTile(tile: Tile, box: TileBox) {
    val ink = role(Role.INK_SOFT)
    val keys = tile.options["keys"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
    scope.Column(Modifier.fillMaxSize().padding(units.px(PAD_XXL_DP)), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
        val doc = this@bedsideTile
        doc.label(doc.text(Vars.TIME), minOf(box.w * BEDSIDE_TIME_W, box.h * BEDSIDE_TIME_H), ink, HEADER_TALL_MIN_DP, align = RcTextAlign.Center)
        doc.gap(PAD_XXL_DP)
        Row(horizontal = RcRowHorizontalPositioning.Center) {
            keys.forEachIndexed { i, entityId ->
                if (i > 0) Spacer(Modifier.width(doc.units.px(LEVEL_BAR_HEIGHT_DP)))
                val spec = Catalogue.of(entityId.substringBefore('.'))
                val keyTile = Tile(id = "${tile.id}.k$i", entityId = entityId)
                doc.bindEntity(entityId)
                doc.bindTileColors(keyTile.id, entityId, Pastel.forDomain(spec?.domain))
                val ref = tapRef(keyTile, TapAction(), spec, { null }, hold = false)
                if (ref != null && spec?.predict?.containsKey(ref.verb) == true) doc.predict(ref, entityId)
                Box(doc.tap(Modifier.size(doc.units.px(BEDSIDE_KEY_DP), doc.units.px(BEDSIDE_KEY_DP)).clip(doc.rounded((BEDSIDE_KEY_DP) / 2f)).background(doc.color(Vars.tile(keyTile.id, Vars.KEY_BG))), ref),
                    horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                    doc.tileIcon(keyTile, spec, doc.states[entityId], BEDSIDE_KEY_ICON_DP, colorKey = Vars.KEY_BG_INK)
                }
            }
        }
    }
}

private fun DocContext.navTile(tile: Tile, box: TileBox, named: Boolean) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    scope.Column(Modifier.fillMaxSize().padding(units.px(PAD_XL_DP)), vertical = if (named) RcColumnVerticalPositioning.Top else RcColumnVerticalPositioning.Center,
        horizontal = if (named) RcHorizontalPositioning.Start else RcHorizontalPositioning.Center) {
        this@navTile.icon(tile.icon ?: "arrow-right-circle", if (named) NAV_ICON_NAMED_DP else minOf(NAV_ICON_MAX_DP, box.h * NAV_ICON_SHARE), color(Vars.tile(tile.id, Vars.KEY_ICON)))
        if (named) {
            Spacer(Modifier.verticalWeight(1f))
            this@navTile.label(tile.name ?: "", Type.TITLE, ink, Weight.SEMI_BOLD, lines = 2)
        }
    }
}

/** A person as the RPI home strip shows one: a round photo, the name, and HOME or AWAY in teal. */
private fun DocContext.personTile(tile: Tile, box: TileBox, entityState: EntityState?) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    bindWhere(Vars.tile(tile.id, "where"), entityId)
    if (box.h < PERSON_SHORT_MAX_DP) {
        scope.Row(Modifier.fillMaxSize().padding(units.px(PAD_MD_DP)), horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
            val doc = this@personTile
            doc.photo(entityState, entityId, "pic.$entityId", minOf(NAV_ICON_MAX_DP, box.h - PAD_XXL_DP, box.w * PERSON_PHOTO_W_SHARE).coerceAtLeast(LEVEL_BAR_HEIGHT_DP))
            Spacer(Modifier.width(doc.units.px(PAD_MD_DP)))
            Column {
                doc.label(doc.text(Vars.entity(entityId, Vars.KEY_NAME)), Type.TITLE, ink, Weight.MEDIUM)
                doc.label(doc.text(Vars.tile(tile.id, "where")), Type.BODY, doc.role(Role.ACCENT_SOFT), Weight.SEMI_BOLD)
            }
        }
        return
    }
    val photo = minOf(PERSON_TALL_MAX_DP, box.h * NAV_ICON_SHARE, box.w * BEDSIDE_TIME_H).coerceAtLeast(NAV_ICON_NAMED_DP)
    scope.Column(Modifier.fillMaxSize().padding(units.px(PAD_LG_DP)), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
        val doc = this@personTile
        doc.photo(entityState, entityId, "pic.$entityId", photo)
        doc.gap(PAD_SM_DP)
        doc.label(doc.text(Vars.entity(entityId, Vars.KEY_NAME)), Type.TITLE, ink, Weight.MEDIUM, align = RcTextAlign.Center)
        doc.label(doc.text(Vars.tile(tile.id, "where")), Type.BODY, doc.role(Role.ACCENT_SOFT), Weight.SEMI_BOLD, align = RcTextAlign.Center)
    }
}

/** A person's photo in a circle, or their initials when Home Assistant has no picture. */
fun DocContext.photo(entityState: EntityState?, entityId: String, slot: String, sizeDp: Float) {
    val pic = entityState?.attr("entity_picture")
    val px = units.px(sizeDp).toInt()
    if (pic != null) {
        picture(PictureSlot(slot, pic, px, px, 0, round = true))
        scope.Image(image(slot, px, px), Modifier.size(units.px(sizeDp), units.px(sizeDp)).clip(rounded(sizeDp / 2f)), contentScale = RcContentScale.Crop)
    } else {
        initials(entityState?.name ?: entityId, sizeDp, role(Role.KEY), role(Role.INK))
    }
}

/** HOME, or AWAY (or the zone's name), in capitals as the RPI panel writes it. */
fun DocContext.bindWhere(varName: String, personId: String) {
    bind(personId) { states ->
        val s = states[personId]?.state ?: "unknown"
        val word = if (s == "home" || s == "not_home") i18n.state("person", s) else s.replace('_', ' ')
        listOf(VarValue.string(varName, word.uppercase(i18n.locale)))
    }
}

private fun DocContext.weatherTile(tile: Tile, box: TileBox, entityState: EntityState?, named: Boolean) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    structural(entityId) { it?.state ?: "" }
    refreshEvery(WEATHER_REFRESH_S)
    val days = data.forecasts[entityId].orEmpty()
    val zone = runCatching { ZoneId.of(homeAssistantConfig.timeZone) }.getOrDefault(ZoneId.of("UTC"))
    val dayCount = when {
        box.w >= WEATHER_WIDE_DP -> WEATHER_DAYS_WIDE
        box.w >= HEADER_TALL_MIN_DP -> 3
        else -> 0
    }.coerceAtMost(days.size)
    scope.Column(Modifier.fillMaxSize().padding(units.px(PAD_LG_DP))) {
        val doc = this@weatherTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            doc.icon(Present.weatherIcon(entityState?.state ?: ""), if (box.h >= TALL_HEIGHT_DP) WEATHER_ICON_TALL_DP else HEADER_SHORT_DP, ink)
            Spacer(Modifier.width(doc.units.px(CARD_CORNER_DP)))
            Column(Modifier.horizontalWeight(1f)) {
                doc.label(doc.text(Vars.entity(entityId, Vars.KEY_VALUE)), Type.HEADLINE, ink, Weight.MEDIUM)
                doc.label(doc.i18n.state("weather", entityState?.state ?: "unknown"), Type.CAPTION + 1f, ink2)
            }
        }
        Spacer(Modifier.verticalWeight(1f))
        if (dayCount > 0) {
            Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.SpaceBetween) {
                for (d in days.take(dayCount)) {
                    Column(horizontal = RcHorizontalPositioning.Center) {
                        doc.label(doc.i18n.weekday(d.time.withZoneSameInstant(zone)), Type.CAPTION, ink2, align = RcTextAlign.Center)
                        doc.icon(Present.weatherIcon(d.condition), FORECAST_ICON_DP, ink)
                        doc.label(listOfNotNull(d.high?.let { "${it.roundToInt()}°" }, d.low?.let { "${it.roundToInt()}°" }).joinToString(" "), Type.CAPTION, ink, align = RcTextAlign.Center)
                    }
                }
            }
        } else if (named) {
            doc.label(doc.text(Vars.entity(entityId, Vars.KEY_NAME)), Type.BODY, ink, Weight.SEMI_BOLD)
        }
    }
}

private fun DocContext.climateTile(tile: Tile, box: TileBox, entityState: EntityState?, named: Boolean) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val step = entityState?.number("target_temp_step") ?: if (homeAssistantConfig.temperatureUnit.contains("F")) 1.0 else 0.5
    val stepper = box.w >= HEADER_TALL_MIN_DP || box.h >= TALL_HEIGHT_DP
    val modes = entityState?.list("hvac_modes").orEmpty()
    structural(entityId) { it?.list("hvac_modes")?.joinToString() ?: "" }
    predict(ActionRef(tile.id, "temperature", "+$step"), entityId)
    predict(ActionRef(tile.id, "temperature", "-$step"), entityId)
    scope.Column(Modifier.fillMaxSize().padding(units.px(PAD_LG_DP))) {
        val doc = this@climateTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            doc.tileIcon(tile, Catalogue.of("climate"), entityState, COL_ICON_SHORT_DP)
            Spacer(Modifier.width(doc.units.px(CARD_CORNER_DP)))
            Column(Modifier.horizontalWeight(1f)) {
                if (named) doc.label(doc.text(Vars.entity(entityId, Vars.KEY_NAME)), Type.BODY, ink, Weight.SEMI_BOLD)
                doc.label(doc.text(Vars.entity(entityId, Vars.KEY_STATE)), if (named) Type.CAPTION + 1f else Type.BODY, ink2)
            }
        }
        Spacer(Modifier.verticalWeight(1f))
        if (stepper && box.w < HEADER_TALL_MIN_DP) {
            doc.label(doc.text(Vars.entity(entityId, Vars.KEY_VALUE)), Type.VALUE, ink, Weight.MEDIUM)
            doc.gap(PAD_SM_DP)
            Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.SpaceBetween, vertical = RcVerticalPositioning.Center) {
                doc.key(ActionRef(tile.id, "temperature", "-$step"), icon = "minus", heightDp = HEADER_TALL_DP, widthDp = HEADER_TALL_DP, round = true, bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)), ink = ink)
                doc.key(ActionRef(tile.id, "temperature", "+$step"), icon = "plus", heightDp = HEADER_TALL_DP, widthDp = HEADER_TALL_DP, round = true, bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)), ink = ink)
            }
        } else if (stepper) {
            Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
                doc.key(ActionRef(tile.id, "temperature", "-$step"), icon = "minus", heightDp = CLIMATE_KEY_LG_DP, widthDp = CLIMATE_KEY_LG_DP, round = true, bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)), ink = ink)
                Box(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
                    doc.label(doc.text(Vars.entity(entityId, Vars.KEY_VALUE)), Type.VALUE + CLIMATE_VALUE_BUMP, ink, Weight.MEDIUM, align = RcTextAlign.Center)
                }
                doc.key(ActionRef(tile.id, "temperature", "+$step"), icon = "plus", heightDp = CLIMATE_KEY_LG_DP, widthDp = CLIMATE_KEY_LG_DP, round = true, bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)), ink = ink)
            }
            if (box.h >= CLIMATE_MODES_MIN_H_DP && modes.isNotEmpty()) {
                doc.gap(CARD_CORNER_DP)
                doc.bindChoices(tile.id, "mode", entityId, modes) { it.state }
                doc.choiceRow(tile.id, "mode", modes.take(CLIMATE_MODES_TAKE).map { it to doc.i18n.state("climate", it) }, { ActionRef(tile.id, "mode", it) }, COL_ICON_TALL_DP, MODE_ICONS)
            }
        } else {
            doc.label(doc.text(Vars.entity(entityId, Vars.KEY_VALUE)), Type.HEADLINE, ink, Weight.MEDIUM)
        }
    }
}

val MODE_ICONS = mapOf(
    "off" to "power", "heat" to "fire", "cool" to "snowflake", "heat_cool" to "sun-snowflake-variant",
    "auto" to "thermostat-auto", "dry" to "water-percent", "fan_only" to "fan",
)

private fun DocContext.mediaTile(tile: Tile, box: TileBox, entityState: EntityState?) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val big = box.w >= HEADER_TALL_MIN_DP && box.h >= TALL_HEIGHT_DP
    bind(entityId) { states ->
        val s = states[entityId]
        listOf(
            VarValue.string(Vars.entity(entityId, "title"), s?.attr("media_title") ?: s?.name ?: ""),
            VarValue.string(Vars.entity(entityId, "artist"), listOfNotNull(s?.attr("media_artist"), s?.attr("media_album_name")).joinToString(" · ").ifEmpty { i18n.state("media_player", s?.state ?: "off") }),
            VarValue.int(Vars.entity(entityId, "playing"), if (s?.state == "playing") Vars.VISIBLE else Vars.GONE),
            VarValue.int(Vars.entity(entityId, "notplaying"), if (s?.state == "playing") Vars.GONE else Vars.VISIBLE),
        )
    }
    predict(ActionRef(tile.id, "play_pause"), entityId)
    scope.Box(Modifier.fillMaxSize()) {
        val doc = this@mediaTile
        if (big) {
            val w = doc.units.px(box.w).toInt(); val h = doc.units.px(box.h).toInt()
            doc.picture(PictureSlot("art.$entityId", "attr:$entityId:entity_picture", w, h, 0))
            Image(doc.image("art.$entityId", w, h), Modifier.fillMaxSize(), contentScale = RcContentScale.Crop, alpha = PERSON_PHOTO_W_SHARE)
        }
        Column(Modifier.fillMaxSize().padding(doc.units.px(PAD_LG_DP))) {
            doc.tileIcon(tile, Catalogue.of("media_player"), entityState, LEVEL_BAR_HEIGHT_DP)
            Spacer(Modifier.verticalWeight(1f))
            doc.label(doc.text(Vars.entity(entityId, "title")), if (big) Type.TITLE + MEDIA_TITLE_BUMP else Type.BODY, ink, Weight.SEMI_BOLD, lines = if (big) 2 else 1)
            doc.label(doc.text(Vars.entity(entityId, "artist")), Type.CAPTION + 1f, ink2)
            if (box.w >= HEADER_TALL_MIN_DP || box.h >= TALL_HEIGHT_DP) {
                doc.gap(PAD_SM_DP)
                Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.SpaceBetween, vertical = RcVerticalPositioning.Center) {
                    doc.key(ActionRef(tile.id, "previous"), icon = "skip-previous", heightDp = HEADER_TALL_DP, widthDp = HEADER_TALL_DP, round = true, bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)), ink = ink)
                    Box(doc.tap(Modifier.size(doc.units.px(WEATHER_ICON_TALL_DP), doc.units.px(WEATHER_ICON_TALL_DP)).clip(doc.rounded((WEATHER_ICON_TALL_DP) / 2f)).background(ink), ActionRef(tile.id, "play_pause")),
                        horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                        doc.icon("pause", LEVEL_BAR_HEIGHT_DP, doc.color(Vars.tile(tile.id, Vars.KEY_BG)), doc.int(Vars.entity(entityId, "playing")))
                        doc.icon("play", LEVEL_BAR_HEIGHT_DP, doc.color(Vars.tile(tile.id, Vars.KEY_BG)), doc.int(Vars.entity(entityId, "notplaying")))
                    }
                    doc.key(ActionRef(tile.id, "next"), icon = "skip-next", heightDp = HEADER_TALL_DP, widthDp = HEADER_TALL_DP, round = true, bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)), ink = ink)
                }
            }
        }
    }
}

private fun DocContext.cameraTile(tile: Tile, box: TileBox, named: Boolean) {
    val entityId = tile.entityId ?: return
    val w = units.px(box.w).toInt()
    val h = units.px(box.h).toInt()
    val every = tile.options["live"]?.toIntOrNull()?.coerceIn(5, 30) ?: 60
    picture(PictureSlot("cam.$entityId.${tile.id}", "/api/camera_proxy/$entityId", w, h, every, crop = tile.options["fit"] != "whole"))
    scope.Box(Modifier.fillMaxSize().background(role(Role.SCRIM)), vertical = RcVerticalPositioning.Bottom) {
        val doc = this@cameraTile
        Image(doc.image("cam.$entityId.${tile.id}", w, h), Modifier.fillMaxSize(), contentScale = if (tile.options["fit"] == "whole") RcContentScale.Fit else RcContentScale.Crop)
        if (named && tile.options["name"] != "false") {
            Box(Modifier.fillMaxWidth().background(doc.role(Role.SCRIM)).padding(doc.units.px(CARD_CORNER_DP))) {
                doc.label(doc.text(Vars.entity(entityId, Vars.KEY_NAME)), Type.BODY, doc.role(Role.INK), Weight.SEMI_BOLD)
            }
        }
    }
}

private fun DocContext.favouriteTile(tile: Tile, box: TileBox, named: Boolean) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val art = tile.options["image"]
    scope.Box(Modifier.fillMaxSize()) {
        val doc = this@favouriteTile
        if (art != null && box.w >= SHORT_HEIGHT_DP) {
            val w = doc.units.px(box.w).toInt(); val h = doc.units.px(box.h).toInt()
            doc.picture(PictureSlot("fav.${tile.id}", art, w, h, 0))
            Image(doc.image("fav.${tile.id}", w, h), Modifier.fillMaxSize(), contentScale = RcContentScale.Crop, alpha = NAV_ICON_SHARE)
        }
        Column(Modifier.fillMaxSize().padding(doc.units.px(PAD_LG_DP))) {
            doc.icon(tile.icon ?: "playlist-music", LEVEL_BAR_HEIGHT_DP, doc.color(Vars.tile(tile.id, Vars.KEY_ICON)))
            Spacer(Modifier.verticalWeight(1f))
            if (named) doc.label(tile.name ?: tile.options["title"] ?: "", Type.BODY + 1f, ink, Weight.SEMI_BOLD, lines = 2)
        }
    }
}

/** A history graph drawn from the recorder, with its highest and lowest value. */
fun DocContext.historyTile(tile: Tile, box: TileBox, named: Boolean = true) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    refreshEvery(300)
    val points = data.history[entityId].orEmpty()
    scope.Column(Modifier.fillMaxSize().padding(units.px(PAD_LG_DP))) {
        val doc = this@historyTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            Column(Modifier.horizontalWeight(1f)) {
                if (named) doc.label(doc.text(Vars.entity(entityId, Vars.KEY_NAME)), Type.BODY, ink, Weight.SEMI_BOLD)
                doc.label(doc.text(Vars.entity(entityId, Vars.KEY_STATE)), Type.HEADLINE, ink, Weight.MEDIUM)
            }
        }
        doc.gap(HISTORY_GAP_DP)
        doc.graph(points, box.w - LEVEL_BAR_HEIGHT_DP, (box.h - if (named) GRAPH_NAMED_RESERVE_DP else PERSON_TALL_MAX_DP).coerceAtLeast(COL_ICON_SHORT_DP), doc.color(Vars.tile(tile.id, Vars.KEY_ICON)), ink2)
    }
}

fun DocContext.graph(points: List<Pair<Long, Double>>, wDp: Float, hDp: Float, line: androidx.compose.remote.creation.dsl.RcColor, soft: androidx.compose.remote.creation.dsl.RcColor) {
    val w = units.px(wDp)
    val h = units.px(hDp)
    if (points.size < 2) {
        scope.Box(Modifier.size(w, h), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
            this@graph.label(this@graph.i18n.t("ui.no_history"), Type.CAPTION, soft)
        }
        return
    }
    val t0 = points.first().first.toFloat()
    val t1 = points.last().first.toFloat().coerceAtLeast(t0 + 1)
    val lo = points.minOf { it.second }
    val hi = points.maxOf { it.second }.let { if (it - lo < 0.001) lo + 1 else it }
    val stroke = units.px(2.5f)
    fun px(p: Pair<Long, Double>) = ((p.first - t0) / (t1 - t0) * w) to (h - stroke - ((p.second - lo) / (hi - lo)).toFloat() * (h - 2 * stroke))
    scope.Canvas(Modifier.size(w, h)) {
        paint { color(line); strokeWidth(stroke); style(RcPaintStyle.Stroke) }
        var prev = px(points.first())
        for (p in points.drop(1)) {
            val next = px(p)
            drawLine(prev.first, prev.second, next.first, next.second)
            prev = next
        }
    }
}

private fun DocContext.mapPeople(tile: Tile): List<String> =
    (listOfNotNull(tile.entityId) + tile.options["people"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() })
        .ifEmpty { states.keys.filter { it.startsWith("person.") } }

private fun DocContext.mapTile(tile: Tile, box: TileBox, named: Boolean) {
    val w = units.px(box.w).toInt()
    val h = units.px(box.h).toInt()
    val who = mapPeople(tile)
    for (p in who) structural(p) { s -> "${s?.number("latitude")},${s?.number("longitude")}" }
    picture(PictureSlot("map.${tile.id}", "map:${mapStyle()}:${who.joinToString(",")}", w, h, 0))
    scope.Box(Modifier.fillMaxSize(), vertical = RcVerticalPositioning.Bottom) {
        val doc = this@mapTile
        Image(doc.image("map.${tile.id}", w, h), Modifier.fillMaxSize(), contentScale = RcContentScale.Crop)
        if (named) {
            Box(Modifier.fillMaxWidth().background(doc.role(Role.CARD_HEADER)).padding(doc.units.px(PAD_SM_DP))) {
                doc.label(tile.name ?: doc.i18n.t("ui.people_home", "count" to who.count { doc.states[it]?.state == "home" }), Type.CAPTION + 1f, doc.role(Role.INK), Weight.SEMI_BOLD)
            }
        }
    }
}

/** Solar, the grid and the battery around the house, with the power of each, as Home Assistant's energy settings name them. */
fun DocContext.energyFlow(wDp: Float, hDp: Float, compact: Boolean) {
    val prefs = data.energy ?: return
    val nodes = listOfNotNull(
        prefs.solar?.let { Triple("solar", it, "solar-power-variant") },
        prefs.grid?.let { Triple("grid", it, "transmission-tower") },
        prefs.battery?.let { Triple("battery", it, "home-battery") },
    )
    for ((_, entity, _) in nodes) bindEntity(entity)
    val r = if (compact) COL_ICON_SHORT_DP else 42f
    val cx = wDp / 2f
    val cy = hDp / 2f + HISTORY_GAP_DP
    val spots = mapOf("solar" to (cx to r + PAD_XL_DP), "grid" to (r + PAD_LG_DP to cy), "battery" to (wDp - r - PAD_LG_DP to cy), "home" to (cx to hDp - r - FORECAST_ICON_DP))
    val ink = role(Role.INK)
    val ink2 = role(Role.INK_SOFT)
    scope.Box(Modifier.fillMaxSize()) {
        val doc = this@energyFlow
        Canvas(Modifier.fillMaxSize()) {
            paint { color(doc.role(Role.DIVIDER)); strokeWidth(doc.units.px(ENERGY_LINE_DP)); style(RcPaintStyle.Stroke) }
            val home = spots.getValue("home")
            for ((key, _, _) in nodes) {
                val p = spots.getValue(key)
                drawLine(doc.units.px(p.first), doc.units.px(p.second), doc.units.px(home.first), doc.units.px(home.second))
            }
        }
        for ((key, entity, icon) in nodes + Triple("home", "", "home")) {
            val (x, y) = spots.getValue(key)
            val tint = when (key) { "solar" -> Pastel.YELLOW; "grid" -> Pastel.BLUE; "battery" -> Pastel.GREEN; else -> Pastel.GREY }
            doc.at(x - r, y - r) { doc.scope.Column(
                Modifier.size(doc.units.px(2 * r), doc.units.px(2 * r + ENERGY_LABEL_EXTRA_DP)),
                horizontal = RcHorizontalPositioning.Center,
            ) {
                val hold = if (entity.isNotEmpty()) ActionRef("e:$entity", "card", "history") else null
                Box(doc.tap(Modifier.size(doc.units.px(2 * r), doc.units.px(2 * r)).clip(doc.rounded((2 * r) / 2f)).background(doc.fixed(tint.argb)), hold),
                    horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                    Column(horizontal = RcHorizontalPositioning.Center) {
                        doc.icon(icon, if (compact) ENERGY_ICON_COMPACT_DP else PAD_XXL_DP, doc.role(Role.KEY_ON_INK))
                        if (entity.isNotEmpty()) doc.label(doc.text(Vars.entity(entity, Vars.KEY_STATE)), if (compact) Type.CAPTION - 1f else Type.CAPTION, doc.role(Role.KEY_ON_INK), Weight.SEMI_BOLD, align = RcTextAlign.Center)
                    }
                }
                doc.label(doc.i18n.t(if (key == "battery") "ui.battery_source" else if (key == "home") "ui.house" else "ui.$key"), Type.CAPTION, ink2, align = RcTextAlign.Center)
            } }
        }
        if (!compact && prefs.devices.isNotEmpty()) {
            doc.at(PAD_MD_DP, hDp - SPACE_20_DP - ENERGY_LABEL_EXTRA_DP * prefs.devices.size.coerceAtMost(4)) { doc.scope.Column {
                for ((name, entity) in prefs.devices.take(4)) {
                    doc.bindEntity(entity)
                    Row(vertical = RcVerticalPositioning.Center) {
                        doc.label(name, Type.CAPTION, ink2)
                        Spacer(Modifier.width(doc.units.px(HISTORY_GAP_DP)))
                        doc.label(doc.text(Vars.entity(entity, Vars.KEY_STATE)), Type.CAPTION, ink, Weight.SEMI_BOLD)
                    }
                }
            } }
        }
    }
}
