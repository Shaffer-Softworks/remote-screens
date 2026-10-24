@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.HaRenderFlags
import tech.shaffersoftworks.remotescreens.server.render.Present
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.paint
import tech.shaffersoftworks.remotescreens.server.render.widgets.toggleIcon

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TypeSpec

internal object TileChrome {
    val ALWAYS_LIT = setOf("scene", "script", "button", "input_button", "nav", "favourite")

    /** Kinds whose card has no header strip: they show their own words, or none. */
    val NO_HEADER = setOf("clock", "bedside", "energy", "person", "device_tracker")

    const val CARD_EDGE = 2f
    const val HEADER_LINE = 3f
    const val CARD_CORNER_DP = 10f
    const val BODY_CORNER_DP = 8f
    const val HEADER_PAD_H_DP = 14f
    const val HEADER_MIN_DP = 96f
    const val HEADER_TALL_MIN_DP = 200f
    const val HEADER_TALL_DP = 44f
    const val HEADER_SHORT_DP = 36f
    const val PAD_SM_DP = 8f
    const val PAD_MD_DP = 12f
    const val PAD_LG_DP = 14f
    const val PAD_XL_DP = 16f
    const val PAD_XXL_DP = 24f
    const val PAD_NARROW_DP = 12f
    const val PAD_WIDE_DP = 16f
    const val NARROW_WIDTH_DP = 130f
    const val SHORT_HEIGHT_DP = 120f
    const val TALL_HEIGHT_DP = 150f
    const val WIDE_ASPECT = 0.9f
    const val LEVEL_MIN_WIDTH_DP = 150f
    const val LEVEL_BAR_HEIGHT_DP = 28f
    /** Domain-mockup segmented fill under entity tiles (humidifier / hot water). */
    const val TILE_SEG_BAR_H_DP = 14f
    const val LEVEL_STEPS = HaRenderFlags.TILE_LEVEL_STEPS
    const val LEVEL_DEFAULT_MAX = 100.0
    const val COVER_POSITION_FLAG = HaRenderFlags.COVER_POSITION_FLAG
    const val FAN_PERCENTAGE_FLAG = HaRenderFlags.FAN_PERCENTAGE_FLAG
    const val FULL_ICON_DP = 120f
    const val FULL_NAME_SCALE = 0.8f
    const val ROW_ICON_DP = 36f
    const val COL_ICON_TALL_DP = 40f
    const val COL_ICON_SHORT_DP = 30f
    const val CLOCK_H_SHARE = 0.38f
    const val CLOCK_W_SHARE = 0.22f
    const val BEDSIDE_TIME_W = 0.24f
    const val BEDSIDE_TIME_H = 0.4f
    const val BEDSIDE_KEY_DP = 76f
    const val BEDSIDE_KEY_ICON_DP = 34f
    const val BEDSIDE_KEY_GAP_DP = 28f
    const val BEDSIDE_WEIGHT = 200f
    const val NAV_ICON_NAMED_DP = 32f
    const val NAV_ICON_MAX_DP = 56f
    const val NAV_ICON_SHARE = 0.45f
    const val PERSON_SHORT_MAX_DP = 140f
    const val PERSON_PHOTO_MAX_DP = 56f
    const val PERSON_PHOTO_MIN_DP = 28f
    const val PERSON_PHOTO_W_SHARE = 0.35f
    const val PERSON_PHOTO_PAD_DP = 24f
    const val PERSON_TALL_MAX_DP = 70f
    const val PERSON_TALL_H_SHARE = 0.45f
    const val PERSON_TALL_W_SHARE = 0.4f
    const val PERSON_TALL_MIN_DP = 32f
    const val WEATHER_REFRESH_S = 1800
    const val WEATHER_WIDE_DP = 300f
    const val WEATHER_MID_DP = 200f
    const val WEATHER_DAYS_WIDE = 5
    const val WEATHER_DAYS_MID = 3
    const val BEDSIDE_KEYS_MAX = 3
    const val WEATHER_ICON_TALL_DP = 52f
    const val WEATHER_ICON_SHORT_DP = 36f
    const val FORECAST_ICON_DP = 26f
    const val CLIMATE_STEPPER_W_DP = 200f
    const val CLIMATE_KEY_SM_DP = 44f
    const val CLIMATE_KEY_LG_DP = 48f
    const val CLIMATE_VALUE_BUMP = 4f
    const val CLIMATE_MODES_MIN_H_DP = 260f
    const val CLIMATE_MODE_H_DP = 40f
    const val CLIMATE_MODES_TAKE = 4
    const val MEDIA_MIN_W_DP = 200f
    const val MEDIA_ART_ALPHA = 0.35f
    const val MEDIA_ICON_DP = 28f
    const val MEDIA_CTRL_DP = 44f
    const val MEDIA_PLAY_DP = 52f
    const val MEDIA_PLAY_ICON_DP = 28f
    const val MEDIA_TITLE_BUMP = 2f
    const val CAMERA_LIVE_MIN_S = 5
    const val CAMERA_LIVE_MAX_S = 30
    const val CAMERA_LIVE_DEFAULT_S = 60
    const val GAP_10_DP = 10f
    const val FAV_ART_MIN_W_DP = 120f
    const val FAV_ART_ALPHA = 0.45f
    const val FAV_ICON_DP = 28f
    const val HISTORY_REFRESH_S = 300
    const val HISTORY_GAP_DP = 6f
    const val GRAPH_PAD_H_DP = 28f
    const val GRAPH_NAMED_RESERVE_DP = 90f
    const val GRAPH_UNNAMED_RESERVE_DP = 70f
    const val GRAPH_MIN_H_DP = 30f
    const val GRAPH_STROKE_DP = 2.5f
    const val GRAPH_FLAT_EPS = 0.001
    const val MAP_LABEL_PAD_DP = 8f
    const val ENERGY_NODE_COMPACT_DP = 30f
    const val ENERGY_NODE_DP = 42f
    const val ENERGY_CY_BUMP_DP = 6f
    const val ENERGY_SOLAR_Y_PAD_DP = 16f
    const val ENERGY_SIDE_PAD_DP = 14f
    const val ENERGY_HOME_Y_PAD_DP = 26f
    const val ENERGY_LINE_DP = 3f
    const val ENERGY_LABEL_EXTRA_DP = 22f
    const val ENERGY_ICON_COMPACT_DP = 18f
    const val ENERGY_ICON_DP = 24f
    const val ENERGY_DEVICE_X_DP = 12f
    const val SPACE_20_DP = 20f
    const val ENERGY_DEVICE_ROW_DP = 22f
    const val ENERGY_DEVICE_MAX = 4
    const val ENERGY_DEVICE_GAP_DP = 6f
    const val ENERGY_COMPACT_MAX_W_DP = 400f
    const val CAPTION_BUMP = 1f
    const val BODY_BUMP = 1f
    const val BODY_BUMP_2 = 2f
}

/** The tile's own border colour, else the theme's card edge. */
internal fun DocContext.edgeOf(tile: Tile) = tile.borderColor?.let { paint(
    it,
) } ?: role(Role.CARD_EDGE)

/** The words of a card's header strip: the entity's name, or the tile's own for tiles without one. */
internal fun DocContext.headerTitle(tile: Tile, kind: String, sp: Float) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val entityId = tile.entityId
    when {
        kind == "map" && entityId == null -> {
            val who = mapPeople(tile)
            label(
                tile.name ?: i18n.t(
                    "ui.people_home",
                    "count" to who.count { states[it]?.state == "home" },
                ),
                sp,
                ink,
            )
        }
        entityId != null && kind != "favourite" -> label(
            text(Vars.entity(entityId, Vars.KEY_NAME)),
            sp,
            ink,
        )
        else -> label(tile.name ?: tile.options["title"] ?: Catalogue.of(kind)?.name ?: "", sp, ink)
    }
}

internal fun DocContext.tileIcon(tile: Tile, spec: TypeSpec?, entityState: EntityState?, sizeDp: Float, colorKey: String = Vars.KEY_ICON) {
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
