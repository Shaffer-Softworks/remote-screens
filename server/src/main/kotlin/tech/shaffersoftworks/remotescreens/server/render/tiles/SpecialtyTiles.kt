@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.bindChoices
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.hasLiveVideoOnCurrentPage
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.panels.calendar.calendarEvent
import tech.shaffersoftworks.remotescreens.server.render.panels.calendar.isToday
import tech.shaffersoftworks.remotescreens.server.render.panels.thermostat.ThermostatMetrics
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.at
import tech.shaffersoftworks.remotescreens.server.render.widgets.choiceRow
import tech.shaffersoftworks.remotescreens.server.render.widgets.gap
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.initials
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.numericLabel
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.spinner
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.server.render.widgets.weatherStateIcon
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.time.ZoneId
import kotlin.math.roundToInt
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C

private const val CLOCK_GLYPHS = 8

internal fun DocContext.clockTile(tile: Tile, box: TileBox) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val big = minOf(box.h * C.CLOCK_H_SHARE, box.w * C.CLOCK_W_SHARE).coerceAtLeast(Type.HEADLINE)
    scope.Column(
        Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP)),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        this@clockTile.numericLabel(
            text = this@clockTile.text(Vars.TIME),
            sp = big,
            color = ink,
            weight = Weight.LIGHT,
            glyphs = CLOCK_GLYPHS,
        )
        this@clockTile.label(
            text = this@clockTile.text(Vars.DATE),
            sp = Type.BODY,
            color = ink2,
            align = RcTextAlign.Center,
            lines = 2,
        )
    }
}

/** The bedside clock: the time as large as the glass allows and up to three round keys for the night. */
internal fun DocContext.bedsideTile(tile: Tile, box: TileBox) {
    val ink = role(Role.INK_SOFT)
    val keys = tile.options["keys"].orEmpty().split(
        ',',
    ).map { it.trim() }.filter { it.isNotEmpty() }.take(C.BEDSIDE_KEYS_MAX)
    scope.Column(
        Modifier.fillMaxSize().padding(units.px(C.PAD_XXL_DP)),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        val doc = this@bedsideTile
        doc.numericLabel(
            text = doc.text(Vars.TIME),
            sp = minOf(box.w * C.BEDSIDE_TIME_W, box.h * C.BEDSIDE_TIME_H),
            color = ink,
            weight = C.HEADER_TALL_MIN_DP,
            glyphs = CLOCK_GLYPHS,
        )
        doc.gap(C.PAD_XXL_DP)
        Row(horizontal = RcRowHorizontalPositioning.Center) {
            keys.forEachIndexed { i, entityId ->
                if (i > 0) Spacer(Modifier.width(doc.units.px(C.LEVEL_BAR_HEIGHT_DP)))
                val spec = Catalogue.of(entityId.substringBefore('.'))
                val keyTile = Tile(id = "${tile.id}.k$i", entityId = entityId)
                doc.bindEntity(entityId)
                doc.bindTileColors(keyTile.id, entityId, Pastel.forDomain(spec?.domain))
                val ref = tapRef(tile = keyTile, action = TapAction(), spec = spec, pageIndex = { null }, hold = false)
                if (ref != null && spec?.predict?.containsKey(
                        ref.verb,
                    ) == true) {
                    doc.predict(ref, entityId)
                }
                Box(
                    doc.tap(
                        Modifier.size(
                            doc.units.px(C.BEDSIDE_KEY_DP),
                            doc.units.px(C.BEDSIDE_KEY_DP),
                        ).clip(
                            doc.rounded((C.BEDSIDE_KEY_DP) / 2f),
                        ).background(doc.color(Vars.tile(keyTile.id, Vars.KEY_BG))),
                        ref,
                    ),
                    horizontal = RcHorizontalPositioning.Center,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    doc.tileIcon(
                        tile = keyTile,
                        spec = spec,
                        entityState = doc.states[entityId],
                        sizeDp = C.BEDSIDE_KEY_ICON_DP,
                        colorKey = Vars.KEY_BG_INK,
                    )
                }
            }
        }
    }
}

internal fun DocContext.navTile(tile: Tile, box: TileBox, named: Boolean) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    scope.Column(
        Modifier.fillMaxSize().padding(units.px(C.PAD_XL_DP)),
        vertical = if (named) RcColumnVerticalPositioning.Top else RcColumnVerticalPositioning.Center,
        horizontal = if (named) RcHorizontalPositioning.Start else RcHorizontalPositioning.Center,
    ) {
        this@navTile.icon(
            tile.icon ?: "arrow-right-circle",
            if (named) C.NAV_ICON_NAMED_DP else minOf(C.NAV_ICON_MAX_DP, box.h * C.NAV_ICON_SHARE),
            color(Vars.tile(tile.id, Vars.KEY_ICON)),
        )
        if (named) {
            Spacer(Modifier.verticalWeight(1f))
            this@navTile.label(
                text = tile.name ?: "",
                sp = Type.TITLE,
                color = ink,
                weight = Weight.SEMI_BOLD,
                lines = 2,
            )
        }
    }
}

/** A person as the RPI home strip shows one: a round photo, the name, and HOME or AWAY in teal. */
internal fun DocContext.personTile(tile: Tile, box: TileBox, entityState: EntityState?) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    bindWhere(Vars.tile(tile.id, "where"), entityId)
    if (box.h < C.PERSON_SHORT_MAX_DP) {
        scope.Row(
            Modifier.fillMaxSize().padding(units.px(C.PAD_MD_DP)),
            horizontal = RcRowHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            val doc = this@personTile
            doc.photo(
                entityState = entityState,
                entityId = entityId,
                slot = "pic.$entityId",
                sizeDp = minOf(
                    C.NAV_ICON_MAX_DP,
                    box.h - C.PAD_XXL_DP,
                    box.w * C.PERSON_PHOTO_W_SHARE,
                ).coerceAtLeast(C.LEVEL_BAR_HEIGHT_DP),
            )
            Spacer(Modifier.width(doc.units.px(C.PAD_MD_DP)))
            Column {
                doc.label(
                    text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
                    sp = Type.TITLE,
                    color = ink,
                    weight = Weight.MEDIUM,
                )
                doc.label(
                    text = doc.text(Vars.tile(tile.id, "where")),
                    sp = Type.BODY,
                    color = doc.role(Role.ACCENT_SOFT),
                    weight = Weight.SEMI_BOLD,
                )
            }
        }
        return
    }
    val photo = minOf(
        C.PERSON_TALL_MAX_DP,
        box.h * C.NAV_ICON_SHARE,
        box.w * C.BEDSIDE_TIME_H,
    ).coerceAtLeast(C.NAV_ICON_NAMED_DP)
    scope.Column(
        Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP)),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        val doc = this@personTile
        doc.photo(entityState = entityState, entityId = entityId, slot = "pic.$entityId", sizeDp = photo)
        doc.gap(C.PAD_SM_DP)
        doc.label(
            text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
            sp = Type.TITLE,
            color = ink,
            weight = Weight.MEDIUM,
            align = RcTextAlign.Center,
        )
        doc.label(
            text = doc.text(Vars.tile(tile.id, "where")),
            sp = Type.BODY,
            color = doc.role(Role.ACCENT_SOFT),
            weight = Weight.SEMI_BOLD,
            align = RcTextAlign.Center,
        )
    }
}

/** Next event from a calendar entity: day/time, title, and optional location. */
internal fun DocContext.calendarTile(tile: Tile, box: TileBox, @Suppress(
    "UNUSED_PARAMETER",
) entityState: EntityState?, named: Boolean,) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val zone = runCatching { ZoneId.of(
        homeAssistantConfig.timeZone,
    ) }.getOrDefault(ZoneId.of("UTC"))
    bind(entityId) { states ->
        val event = states[entityId]?.let { calendarEvent(it, zone) }
        val day = when {
            event == null -> "—"
            isToday(event, zone) -> i18n.t("ui.today")
            else -> i18n.fullWeekday(event.start)
        }
        val time = when {
            event == null -> "—"
            event.allDay -> i18n.t("ui.all_day")
            else -> i18n.time(event.start)
        }
        listOf(
            VarValue.string(Vars.tile(tile.id, "ev.day"), day),
            VarValue.string(Vars.tile(tile.id, "ev.time"), time),
            VarValue.string(Vars.tile(tile.id, "ev.title"), event?.title ?: i18n.t("ui.no_events")),
            VarValue.string(Vars.tile(tile.id, "ev.meta"), event?.location.orEmpty()),
        )
    }
    scope.Column(Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP))) {
        val doc = this@calendarTile
        doc.label(doc.text(Vars.tile(tile.id, "ev.day")), Type.CAPTION + 1f, ink2)
        doc.label(
            text = doc.text(Vars.tile(tile.id, "ev.time")),
            sp = if (box.h >= C.TALL_HEIGHT_DP) Type.HEADLINE else Type.TITLE,
            color = ink,
            weight = Weight.MEDIUM,
        )
        Spacer(Modifier.verticalWeight(1f))
        doc.label(
            text = doc.text(Vars.tile(tile.id, "ev.title")),
            sp = Type.BODY + 1f,
            color = ink,
            weight = Weight.SEMI_BOLD,
            lines = 2,
        )
        if (named) doc.label(doc.text(Vars.entity(entityId, Vars.KEY_NAME)), Type.CAPTION, ink2)
    }
}

/** A person's photo in a circle, or their initials when Home Assistant has no picture. */
fun DocContext.photo(
    entityState: EntityState?,
    entityId: String,
    slot: String,
    sizeDp: Float,
    ink: RcColor = role(Role.INK),
) {
    val pic = entityState?.attr("entity_picture")
    val px = units.px(sizeDp).toInt()
    if (pic != null) {
        picture(PictureSlot(name = slot, source = pic, widthPx = px, heightPx = px, refreshSeconds = 0, round = true))
        scope.Box(
            Modifier
                .size(
                    units.px(sizeDp),
                    units.px(sizeDp),
                )
                .clip(rounded(sizeDp / 2f))
                .background(fixed(0xFF000000.toInt()))
                .padding(1f),
        ) {
            Image(
                image(slot, px, px),
                Modifier.fillMaxSize().clip(rounded((sizeDp / 2f - 1f / units.density).coerceAtLeast(0f))),
                contentScale = RcContentScale.Crop,
            )
        }
    } else {
        initials(name = entityState?.name ?: entityId, sizeDp = sizeDp, bg = role(Role.KEY), ink = ink)
    }
}

/** HOME, or AWAY (or the zone's name), in capitals as the RPI panel writes it. */
fun DocContext.bindWhere(varName: String, personId: String) {
    bind(personId) { states ->
        val place = states[personId]?.state ?: "unknown"
        val word = if (place == "home" || place == "not_home") {
            i18n.state(
                "person",
                place,
            )
        } else {
            place.replace('_', ' ')
        }
        listOf(VarValue.string(varName, word.uppercase(i18n.locale)))
    }
}

internal fun DocContext.weatherTile(tile: Tile, box: TileBox, entityState: EntityState?, named: Boolean) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    structural(entityId) { it?.state ?: "" }
    refreshEvery(C.WEATHER_REFRESH_S)
    val days = data.forecasts[entityId].orEmpty()
    val zone = runCatching { ZoneId.of(
        homeAssistantConfig.timeZone,
    ) }.getOrDefault(ZoneId.of("UTC"))
    val dayCount = when {
        box.w >= C.WEATHER_WIDE_DP -> C.WEATHER_DAYS_WIDE
        box.w >= C.HEADER_TALL_MIN_DP -> C.WEATHER_DAYS_MID
        else -> 0
    }.coerceAtMost(days.size)
    scope.Column(Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP))) {
        val doc = this@weatherTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            doc.weatherStateIcon(
                entityState?.state ?: "",
                if (box.h >= C.TALL_HEIGHT_DP) C.WEATHER_ICON_TALL_DP else C.HEADER_SHORT_DP,
                fallbackColor = ink,
            )
            Spacer(Modifier.width(doc.units.px(C.CARD_CORNER_DP)))
            Column(Modifier.horizontalWeight(1f)) {
                doc.label(
                    text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                    sp = Type.HEADLINE,
                    color = ink,
                    weight = Weight.MEDIUM,
                )
                doc.label(
                    doc.i18n.state("weather", entityState?.state ?: "unknown"),
                    Type.CAPTION + 1f,
                    ink2,
                )
            }
        }
        Spacer(Modifier.verticalWeight(1f))
        if (dayCount > 0) {
            Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.SpaceBetween) {
                for (day in days.take(dayCount)) {
                    Column(horizontal = RcHorizontalPositioning.Center) {
                        doc.label(
                            text = doc.i18n.weekday(day.time.withZoneSameInstant(zone)),
                            sp = Type.CAPTION,
                            color = ink2,
                            align = RcTextAlign.Center,
                        )
                        doc.weatherStateIcon(day.condition, C.FORECAST_ICON_DP, fallbackColor = ink)
                        doc.label(
                            text = listOfNotNull(
                                day.high?.let { "${it.roundToInt()}°" },
                                day.low?.let { "${it.roundToInt()}°" },
                            ).joinToString(" "),
                            sp = Type.CAPTION,
                            color = ink,
                            align = RcTextAlign.Center,
                        )
                    }
                }
            }
        } else if (named) {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
                sp = Type.BODY,
                color = ink,
                weight = Weight.SEMI_BOLD,
            )
        }
    }
}

internal fun DocContext.climateTile(tile: Tile, box: TileBox, entityState: EntityState?, named: Boolean) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val step = entityState?.number("target_temp_step")
        ?: if (homeAssistantConfig.temperatureUnit.contains("F")) {
            ThermostatMetrics.TEMP_STEP_F
        } else {
            ThermostatMetrics.TEMP_STEP_C
        }
    val stepper = box.w >= C.HEADER_TALL_MIN_DP || box.h >= C.TALL_HEIGHT_DP
    val modes = entityState?.list("hvac_modes").orEmpty()
    structural(entityId) { it?.list("hvac_modes")?.joinToString() ?: "" }
    predict(ActionRef(tile.id, "temperature", "+$step"), entityId)
    predict(ActionRef(tile.id, "temperature", "-$step"), entityId)
    scope.Column(Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP))) {
        val doc = this@climateTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            doc.tileIcon(
                tile = tile,
                spec = Catalogue.of("climate"),
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
                    doc.text(Vars.entity(entityId, Vars.KEY_STATE)),
                    if (named) Type.CAPTION + 1f else Type.BODY,
                    ink2,
                )
            }
        }
        Spacer(Modifier.verticalWeight(1f))
        if (stepper && box.w < C.HEADER_TALL_MIN_DP) {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                sp = Type.VALUE,
                color = ink,
                weight = Weight.MEDIUM,
            )
            doc.gap(C.PAD_SM_DP)
            Row(
                Modifier.fillMaxWidth(),
                horizontal = RcRowHorizontalPositioning.SpaceBetween,
                vertical = RcVerticalPositioning.Center,
            ) {
                doc.key(
                    ref = ActionRef(tile.id, "temperature", "-$step"),
                    icon = "minus",
                    heightDp = C.HEADER_TALL_DP,
                    widthDp = C.HEADER_TALL_DP,
                    round = true,
                    bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)),
                    ink = ink,
                )
                doc.key(
                    ref = ActionRef(tile.id, "temperature", "+$step"),
                    icon = "plus",
                    heightDp = C.HEADER_TALL_DP,
                    widthDp = C.HEADER_TALL_DP,
                    round = true,
                    bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)),
                    ink = ink,
                )
            }
        } else if (stepper) {
            Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
                doc.key(
                    ref = ActionRef(tile.id, "temperature", "-$step"),
                    icon = "minus",
                    heightDp = C.CLIMATE_KEY_LG_DP,
                    widthDp = C.CLIMATE_KEY_LG_DP,
                    round = true,
                    bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)),
                    ink = ink,
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
                doc.key(
                    ref = ActionRef(tile.id, "temperature", "+$step"),
                    icon = "plus",
                    heightDp = C.CLIMATE_KEY_LG_DP,
                    widthDp = C.CLIMATE_KEY_LG_DP,
                    round = true,
                    bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)),
                    ink = ink,
                )
            }
            if (box.h >= C.CLIMATE_MODES_MIN_H_DP && modes.isNotEmpty()) {
                doc.gap(C.CARD_CORNER_DP)
                doc.bindChoices(scopeId = tile.id, group = "mode", entityId = entityId, choices = modes) { it.state }
                doc.choiceRow(
                    scopeId = tile.id,
                    group = "mode",
                    choices = modes.take(C.CLIMATE_MODES_TAKE).map { it to doc.i18n.state("climate", it) },
                    refFor = { ActionRef(tile.id, "mode", it) },
                    heightDp = C.COL_ICON_TALL_DP,
                    icons = MODE_ICONS,
                )
            }
        } else {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                sp = Type.HEADLINE,
                color = ink,
                weight = Weight.MEDIUM,
            )
        }
    }
}

val MODE_ICONS = mapOf(
    "off" to "power",
    "heat" to "fire",
    "cool" to "snowflake",
    "heat_cool" to "sun-snowflake-variant",
    "auto" to "thermostat-auto",
    "dry" to "water-percent",
    "fan_only" to "fan",
)

internal fun DocContext.mediaTile(tile: Tile, box: TileBox, entityState: EntityState?) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val big = box.w >= C.HEADER_TALL_MIN_DP && box.h >= C.TALL_HEIGHT_DP
    bind(entityId) { states ->
        val current = states[entityId]
        listOf(
            VarValue.string(
                Vars.entity(entityId, "title"),
                current?.attr("media_title") ?: current?.name ?: "",
            ),
            VarValue.string(
                Vars.entity(entityId, "artist"),
                listOfNotNull(
                    current?.attr("media_artist"),
                    current?.attr("media_album_name"),
                ).joinToString(" · ").ifEmpty { i18n.state("media_player", current?.state ?: "off") },
            ),
            VarValue.int(
                Vars.entity(entityId, "playing"),
                if (current?.state == "playing") Vars.VISIBLE else Vars.GONE,
            ),
            VarValue.int(
                Vars.entity(entityId, "notplaying"),
                if (current?.state == "playing") Vars.GONE else Vars.VISIBLE,
            ),
        )
    }
    predict(ActionRef(tile.id, "play_pause"), entityId)
    scope.Box(Modifier.fillMaxSize()) {
        val doc = this@mediaTile
        if (big) {
            val w = doc.units.px(box.w).toInt(); val h = doc.units.px(box.h).toInt()
            doc.picture(
                PictureSlot(
                    name = "art.$entityId",
                    source = "attr:$entityId:entity_picture",
                    widthPx = w,
                    heightPx = h,
                    refreshSeconds = 0,
                ),
            )
            Image(
                image = doc.image("art.$entityId", w, h),
                modifier = Modifier.fillMaxSize(),
                contentScale = RcContentScale.Crop,
                alpha = C.PERSON_PHOTO_W_SHARE,
            )
        }
        Column(Modifier.fillMaxSize().padding(doc.units.px(C.PAD_LG_DP))) {
            doc.tileIcon(
                tile = tile,
                spec = Catalogue.of("media_player"),
                entityState = entityState,
                sizeDp = C.LEVEL_BAR_HEIGHT_DP,
            )
            Spacer(Modifier.verticalWeight(1f))
            doc.label(
                text = doc.text(Vars.entity(entityId, "title")),
                sp = if (big) Type.TITLE + C.MEDIA_TITLE_BUMP else Type.BODY,
                color = ink,
                weight = Weight.SEMI_BOLD,
                lines = if (big) 2 else 1,
            )
            doc.label(doc.text(Vars.entity(entityId, "artist")), Type.CAPTION + 1f, ink2)
            if (box.w >= C.HEADER_TALL_MIN_DP || box.h >= C.TALL_HEIGHT_DP) {
                doc.gap(C.PAD_SM_DP)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontal = RcRowHorizontalPositioning.SpaceBetween,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    doc.key(
                        ref = ActionRef(tile.id, "previous"),
                        icon = "skip-previous",
                        heightDp = C.HEADER_TALL_DP,
                        widthDp = C.HEADER_TALL_DP,
                        round = true,
                        bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)),
                        ink = ink,
                    )
                    Box(
                        doc.tap(
                            Modifier.size(
                                doc.units.px(C.WEATHER_ICON_TALL_DP),
                                doc.units.px(C.WEATHER_ICON_TALL_DP),
                            ).clip(doc.rounded((C.WEATHER_ICON_TALL_DP) / 2f)).background(ink),
                            ActionRef(tile.id, "play_pause"),
                        ),
                        horizontal = RcHorizontalPositioning.Center,
                        vertical = RcVerticalPositioning.Center,
                    ) {
                        doc.icon(
                            name = "pause",
                            sizeDp = C.LEVEL_BAR_HEIGHT_DP,
                            color = doc.color(Vars.tile(tile.id, Vars.KEY_BG)),
                            visible = doc.int(Vars.entity(entityId, "playing")),
                        )
                        doc.icon(
                            name = "play",
                            sizeDp = C.LEVEL_BAR_HEIGHT_DP,
                            color = doc.color(Vars.tile(tile.id, Vars.KEY_BG)),
                            visible = doc.int(Vars.entity(entityId, "notplaying")),
                        )
                    }
                    doc.key(
                        ref = ActionRef(tile.id, "next"),
                        icon = "skip-next",
                        heightDp = C.HEADER_TALL_DP,
                        widthDp = C.HEADER_TALL_DP,
                        round = true,
                        bg = doc.color(Vars.tile(tile.id, Vars.KEY_FILL)),
                        ink = ink,
                    )
                }
            }
        }
    }
}

internal fun DocContext.cameraTile(tile: Tile, box: TileBox, named: Boolean) {
    val entityId = tile.entityId ?: return
    val widthPx = units.px(box.w).toInt()
    val heightPx = units.px(box.h).toInt()
    val isCropped = tile.options["fit"] != "whole"
    val contentScale = if (isCropped) RcContentScale.Crop else RcContentScale.Fit
    val canStream = tile.options["stream"] == "true" && !hasLiveVideoOnCurrentPage()
    val refreshSeconds = if (canStream) {
        0
    } else {
        tile.options["live"]?.toIntOrNull()
            ?.coerceIn(C.CAMERA_LIVE_MIN_S, C.CAMERA_LIVE_MAX_S)
            ?: C.CAMERA_LIVE_DEFAULT_S
    }
    val pictureName = "cam.$entityId.${tile.id}"
    picture(
        PictureSlot(
            name = pictureName,
            source = "/api/camera_proxy/$entityId",
            widthPx = widthPx,
            heightPx = heightPx,
            refreshSeconds = refreshSeconds,
            crop = isCropped,
        ),
    )
    if (canStream) {
        cameraStreamTile(
            entityId = entityId,
            pictureName = pictureName,
            widthPx = widthPx,
            heightPx = heightPx,
            isCropped = isCropped,
            contentScale = contentScale,
            showName = named && tile.options["name"] != "false",
        )
        return
    }
    cameraSnapshotTile(
        entityId = entityId,
        pictureName = pictureName,
        widthPx = widthPx,
        heightPx = heightPx,
        contentScale = contentScale,
        showName = named && tile.options["name"] != "false",
    )
}

private fun DocContext.cameraStreamTile(
    entityId: String,
    pictureName: String,
    widthPx: Int,
    heightPx: Int,
    isCropped: Boolean,
    contentScale: RcContentScale,
    showName: Boolean,
) {
    val video = liveVideo(entityId, crop = isCropped)
    val waiting = int(Vars.entity(entityId, Vars.KEY_CAM_WAITING), Vars.VISIBLE)
    val still = int(Vars.entity(entityId, Vars.KEY_CAM_STILL), Vars.GONE)
    scope.Box(
        Modifier.fillMaxSize().background(role(Role.TRACK)).componentId(video),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@cameraStreamTile
        Image(
            doc.image(pictureName, widthPx, heightPx),
            Modifier.fillMaxSize().visibility(still),
            contentScale = contentScale,
        )
        doc.spinner(CardChrome.CAMERA_SPINNER_DP, doc.role(Role.ACCENT), waiting)
        if (showName) {
            Box(
                Modifier.fillMaxSize(),
                vertical = RcVerticalPositioning.Bottom,
            ) {
                Box(
                    Modifier.fillMaxWidth().background(doc.role(Role.SCRIM))
                        .padding(doc.units.px(C.CARD_CORNER_DP)),
                ) {
                    doc.label(
                        text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
                        sp = Type.BODY,
                        color = doc.role(Role.INK),
                        weight = Weight.SEMI_BOLD,
                    )
                }
            }
        }
    }
}

private fun DocContext.cameraSnapshotTile(
    entityId: String,
    pictureName: String,
    widthPx: Int,
    heightPx: Int,
    contentScale: RcContentScale,
    showName: Boolean,
) {
    scope.Box(
        Modifier.fillMaxSize().background(role(Role.SCRIM)),
        vertical = RcVerticalPositioning.Bottom,
    ) {
        val doc = this@cameraSnapshotTile
        Image(
            doc.image(pictureName, widthPx, heightPx),
            Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
        if (showName) {
            Box(
                Modifier.fillMaxWidth().background(
                    doc.role(Role.SCRIM),
                ).padding(doc.units.px(C.CARD_CORNER_DP)),
            ) {
                doc.label(
                    text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
                    sp = Type.BODY,
                    color = doc.role(Role.INK),
                    weight = Weight.SEMI_BOLD,
                )
            }
        }
    }
}

internal fun DocContext.favouriteTile(tile: Tile, box: TileBox, named: Boolean) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val art = tile.options["image"]
    scope.Box(Modifier.fillMaxSize()) {
        val doc = this@favouriteTile
        if (art != null && box.w >= C.SHORT_HEIGHT_DP) {
            val w = doc.units.px(box.w).toInt(); val h = doc.units.px(box.h).toInt()
            doc.picture(
                PictureSlot(
                    name = "fav.${tile.id}",
                    source = art,
                    widthPx = w,
                    heightPx = h,
                    refreshSeconds = 0,
                ),
            )
            Image(
                image = doc.image("fav.${tile.id}", w, h),
                modifier = Modifier.fillMaxSize(),
                contentScale = RcContentScale.Crop,
                alpha = C.NAV_ICON_SHARE,
            )
        }
        Column(Modifier.fillMaxSize().padding(doc.units.px(C.PAD_LG_DP))) {
            doc.icon(
                tile.icon ?: "playlist-music",
                C.LEVEL_BAR_HEIGHT_DP,
                doc.color(Vars.tile(tile.id, Vars.KEY_ICON)),
            )
            Spacer(Modifier.verticalWeight(1f))
            if (named) {
                doc.label(
                    text = tile.name ?: tile.options["title"] ?: "",
                    sp = Type.BODY + 1f,
                    color = ink,
                    weight = Weight.SEMI_BOLD,
                    lines = 2,
                )
            }
        }
    }
}

/** A history graph drawn from the recorder, with its highest and lowest value. */
fun DocContext.historyTile(tile: Tile, box: TileBox, named: Boolean = true) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    refreshEvery(C.HISTORY_REFRESH_S)
    val points = data.history[entityId].orEmpty()
    scope.Column(Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP))) {
        val doc = this@historyTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
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
                    sp = Type.HEADLINE,
                    color = ink,
                    weight = Weight.MEDIUM,
                )
            }
        }
        doc.gap(C.HISTORY_GAP_DP)
        doc.graph(
            points = points,
            wDp = box.w - C.LEVEL_BAR_HEIGHT_DP,
            hDp = (box.h - if (named) C.GRAPH_NAMED_RESERVE_DP else C.PERSON_TALL_MAX_DP).coerceAtLeast(
                C.COL_ICON_SHORT_DP,
            ),
            line = doc.color(Vars.tile(tile.id, Vars.KEY_ICON)),
            soft = ink2,
        )
    }
}

fun DocContext.graph(points: List<Pair<Long, Double>>, wDp: Float, hDp: Float, line: androidx.compose.remote.creation.dsl.RcColor, soft: androidx.compose.remote.creation.dsl.RcColor) {
    val w = units.px(wDp)
    val h = units.px(hDp)
    if (points.size < 2) {
        scope.Box(
            Modifier.size(w, h),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            this@graph.label(this@graph.i18n.t("ui.no_history"), Type.CAPTION, soft)
        }
        return
    }
    val t0 = points.first().first.toFloat()
    val t1 = points.last().first.toFloat().coerceAtLeast(t0 + 1)
    val lo = points.minOf { it.second }
    val hi = points.maxOf { it.second }.let {
        if (it - lo < C.GRAPH_FLAT_EPS) lo + 1 else it
    }
    val stroke = units.px(C.GRAPH_STROKE_DP)
    fun px(point: Pair<Long, Double>) =
        ((point.first - t0) / (t1 - t0) * w) to
            (h - stroke - ((point.second - lo) / (hi - lo)).toFloat() * (h - 2 * stroke))
    scope.Canvas(Modifier.size(w, h)) {
        paint { color(line); strokeWidth(stroke); style(RcPaintStyle.Stroke) }
        var prev = px(points.first())
        for (point in points.drop(1)) {
            val next = px(point)
            drawLine(x1 = prev.first, y1 = prev.second, x2 = next.first, y2 = next.second)
            prev = next
        }
    }
}

internal fun DocContext.mapPeople(tile: Tile): List<String> =
    (listOfNotNull(
        tile.entityId,
    ) + tile.options["people"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() })
        .ifEmpty { states.keys.filter { it.startsWith("person.") } }

internal fun DocContext.mapTile(tile: Tile, box: TileBox, named: Boolean) {
    val w = units.px(box.w).toInt()
    val h = units.px(box.h).toInt()
    val who = mapPeople(tile)
    for (personId in who) {
        structural(personId) { person ->
            "${person?.number("latitude")},${person?.number("longitude")}"
        }
    }
    picture(
        PictureSlot(
            name = "map.${tile.id}",
            source = "map:${mapStyle()}:${who.joinToString(",")}",
            widthPx = w,
            heightPx = h,
            refreshSeconds = 0,
        ),
    )
    scope.Box(Modifier.fillMaxSize(), vertical = RcVerticalPositioning.Bottom) {
        val doc = this@mapTile
        Image(
            doc.image("map.${tile.id}", w, h),
            Modifier.fillMaxSize(),
            contentScale = RcContentScale.Crop,
        )
        if (named) {
            Box(
                Modifier.fillMaxWidth().background(
                    doc.role(Role.CARD_HEADER),
                ).padding(doc.units.px(C.PAD_SM_DP)),
            ) {
                doc.label(
                    text = tile.name ?: doc.i18n.t(
                        "ui.people_home",
                        "count" to who.count { doc.states[it]?.state == "home" },
                    ),
                    sp = Type.CAPTION + 1f,
                    color = doc.role(Role.INK),
                    weight = Weight.SEMI_BOLD,
                )
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
    val r = if (compact) C.COL_ICON_SHORT_DP else C.ENERGY_NODE_DP
    val cx = wDp / 2f
    val cy = hDp / 2f + C.HISTORY_GAP_DP
    val spots = mapOf(
        "solar" to (cx to r + C.PAD_XL_DP),
        "grid" to (r + C.PAD_LG_DP to cy),
        "battery" to (wDp - r - C.PAD_LG_DP to cy),
        "home" to (cx to hDp - r - C.FORECAST_ICON_DP),
    )
    val ink = role(Role.INK)
    val ink2 = role(Role.INK_SOFT)
    scope.Box(Modifier.fillMaxSize()) {
        val doc = this@energyFlow
        Canvas(Modifier.fillMaxSize()) {
            paint { color(
                doc.role(Role.DIVIDER),
            ); strokeWidth(doc.units.px(C.ENERGY_LINE_DP)); style(RcPaintStyle.Stroke) }
            val home = spots.getValue("home")
            for ((key, _, _) in nodes) {
                val spot = spots.getValue(key)
                drawLine(
                    x1 = doc.units.px(spot.first),
                    y1 = doc.units.px(spot.second),
                    x2 = doc.units.px(home.first),
                    y2 = doc.units.px(home.second),
                )
            }
        }
        for ((key, entity, icon) in nodes + Triple("home", "", "home")) {
            val (x, y) = spots.getValue(key)
            val tint = when (key) { "solar" -> Pastel.YELLOW; "grid" -> Pastel.BLUE; "battery" -> Pastel.GREEN; else -> Pastel.GREY }
            doc.at(x - r, y - r) { doc.scope.Column(
                Modifier.size(doc.units.px(2 * r), doc.units.px(2 * r + C.ENERGY_LABEL_EXTRA_DP)),
                horizontal = RcHorizontalPositioning.Center,
            ) {
                val hold = if (entity.isNotEmpty()) {
                    ActionRef(
                        "e:$entity",
                        "card",
                        "history",
                    )
                } else {
                    null
                }
                Box(
                    doc.tap(
                        Modifier.size(
                            doc.units.px(2 * r),
                            doc.units.px(2 * r),
                        ).clip(doc.rounded((2 * r) / 2f)).background(doc.fixed(tint.argb)),
                        hold,
                    ),
                    horizontal = RcHorizontalPositioning.Center,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    Column(horizontal = RcHorizontalPositioning.Center) {
                        doc.icon(
                            icon,
                            if (compact) C.ENERGY_ICON_COMPACT_DP else C.PAD_XXL_DP,
                            doc.role(Role.KEY_ON_INK),
                        )
                        if (entity.isNotEmpty()) {
                            doc.label(
                                text = doc.text(Vars.entity(entity, Vars.KEY_STATE)),
                                sp = if (compact) Type.CAPTION - 1f else Type.CAPTION,
                                color = doc.role(Role.KEY_ON_INK),
                                weight = Weight.SEMI_BOLD,
                                align = RcTextAlign.Center,
                            )
                        }
                    }
                }
                doc.label(
                    text = doc.i18n.t(
                        if (key == "battery") {
                            "ui.battery_source"
                        } else if (key == "home") {
                            "ui.house"
                        } else {
                            "ui.$key"
                        },
                    ),
                    sp = Type.CAPTION,
                    color = ink2,
                    align = RcTextAlign.Center,
                )
            } }
        }
        if (!compact && prefs.devices.isNotEmpty()) {
            doc.at(
                C.PAD_MD_DP,
                hDp - C.SPACE_20_DP -
                    C.ENERGY_LABEL_EXTRA_DP * prefs.devices.size.coerceAtMost(C.ENERGY_DEVICE_MAX),
            ) { doc.scope.Column {
                for ((name, entity) in prefs.devices.take(C.ENERGY_DEVICE_MAX)) {
                    doc.bindEntity(entity)
                    Row(vertical = RcVerticalPositioning.Center) {
                        doc.label(name, Type.CAPTION, ink2)
                        Spacer(Modifier.width(doc.units.px(C.HISTORY_GAP_DP)))
                        doc.label(
                            text = doc.text(Vars.entity(entity, Vars.KEY_STATE)),
                            sp = Type.CAPTION,
                            color = ink,
                            weight = Weight.SEMI_BOLD,
                        )
                    }
                }
            } }
        }
    }
}
