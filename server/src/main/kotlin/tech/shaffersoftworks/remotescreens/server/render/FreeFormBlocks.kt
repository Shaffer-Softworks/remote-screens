@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.time.ZoneId
import kotlin.math.roundToInt

private object BlockMetrics {
    const val TIMER_KEY_H_DP = 52f
    const val TIMER_GAP_DP = 10f
    const val TIMER_KEY_CORNER_DIVISOR = 4f
    const val TIMER_ICON_SHARE = 0.4f
    const val TIMER_NATURAL_W = 320f
    const val TIMER_NATURAL_H = 56f
    const val TODO_ROW_H_DP = 44f
    const val TODO_GAP_DP = 6f
    const val TODO_DEFAULT_MAX = 8
    const val TODO_MAX = 20
    const val TODO_NATURAL_W = 280f
    const val TODO_ROW_NATURAL_H = 50f
    const val TODO_REFRESH_S = 60
    const val EVENT_ROW_H_DP = 56f
    const val EVENT_GAP_DP = 8f
    const val EVENT_DEFAULT_MAX = 3
    const val EVENT_MAX = 8
    const val EVENT_NATURAL_W = 280f
    const val MEDIA_H_DP = 72f
    const val MEDIA_ART_DP = 56f
    const val MEDIA_ART_CORNER_DP = 8f
    const val MEDIA_KEY_DP = 44f
    const val MEDIA_KEY_ICON_SHARE = 0.45f
    const val MEDIA_NATURAL_W = 360f
    const val FORECAST_DAY_W_DP = 56f
    const val FORECAST_ICON_DP = 28f
    const val FORECAST_DEFAULT_DAYS = 5
    const val FORECAST_MAX_DAYS = 7
    const val FORECAST_NATURAL_H = 72f
    const val FORECAST_REFRESH_S = 1800
    const val PRESENCE_PHOTO_DP = 48f
    const val PRESENCE_GAP_DP = 12f
    const val PRESENCE_PAD_DP = 4f
    const val PRESENCE_NATURAL_H = 72f
    const val PRESENCE_NATURAL_W = 320f
    const val LED_DEFAULT_DP = 16f
    const val LED_MIN_DP = 8f
    const val LED_MAX_DP = 64f
    const val LED_CORNER_DIVISOR = 2f
    /** The size the RPI thermostat page was drawn for (ThermostatPanel BASE_W / BASE_H). */
    const val THERMOSTAT_NATURAL_W = 1280f
    const val THERMOSTAT_NATURAL_H = 650f
}

/** Natural size of a composite free-form block, for layout measurement. */
@Suppress("CyclomaticComplexMethod")
internal fun blockNatural(node: Node): Dims? = when (node.type) {
    "timerkeys" -> Dims(BlockMetrics.TIMER_NATURAL_W, BlockMetrics.TIMER_NATURAL_H)
    "todolist" -> {
        val max = node.props["max"]?.toIntOrNull()?.coerceIn(1, BlockMetrics.TODO_MAX)
            ?: BlockMetrics.TODO_DEFAULT_MAX
        Dims(BlockMetrics.TODO_NATURAL_W, max * BlockMetrics.TODO_ROW_NATURAL_H)
    }
    "events" -> {
        val max = node.props["max"]?.toIntOrNull()?.coerceIn(1, BlockMetrics.EVENT_MAX)
            ?: BlockMetrics.EVENT_DEFAULT_MAX
        Dims(
            BlockMetrics.EVENT_NATURAL_W,
            max * (BlockMetrics.EVENT_ROW_H_DP + BlockMetrics.EVENT_GAP_DP),
        )
    }
    "mediabar" -> Dims(BlockMetrics.MEDIA_NATURAL_W, BlockMetrics.MEDIA_H_DP)
    "forecast" -> {
        val days = node.props["days"]?.toIntOrNull()?.coerceIn(1, BlockMetrics.FORECAST_MAX_DAYS)
            ?: BlockMetrics.FORECAST_DEFAULT_DAYS
        Dims(days * BlockMetrics.FORECAST_DAY_W_DP, BlockMetrics.FORECAST_NATURAL_H)
    }
    "presence" -> Dims(BlockMetrics.PRESENCE_NATURAL_W, BlockMetrics.PRESENCE_NATURAL_H)
    "led" -> (node.props["size"]?.toFloatOrNull() ?: BlockMetrics.LED_DEFAULT_DP).let { Dims(
        it,
        it,
    ) }
    "thermostat" -> Dims(BlockMetrics.THERMOSTAT_NATURAL_W, BlockMetrics.THERMOSTAT_NATURAL_H)
    else -> null
}

internal fun DocContext.blockNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    when (node.type) {
        "timerkeys" -> timerKeysNode(node, axis, pages)
        "todolist" -> todoListNode(node, self, axis, pages)
        "events" -> eventsNode(node, self, axis, pages)
        "mediabar" -> mediaBarNode(node, self, axis, pages)
        "forecast" -> forecastNode(node, self, axis, pages)
        "presence" -> presenceNode(node, self, axis, pages)
        "led" -> ledNode(node, axis, pages)
        "thermostat" -> thermostatNode(node, self, axis, pages)
    }
}

/** Start / Pause / Cancel for a bound timer. */
private fun DocContext.timerKeysNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    if (id != null) bindEntity(id)
    val keyH = BlockMetrics.TIMER_KEY_H_DP
    scope.Row(styled(node, axis, pages), vertical = RcVerticalPositioning.Center) {
        val doc = this@timerKeysNode
        fun key(verb: String, word: String, icon: String) {
            val ref = id?.let { ActionRef(node.id, verb) }
            if (ref != null) {
                doc.key(
                    ref,
                    text = word,
                    icon = icon,
                    heightDp = keyH,
                    modifier = Modifier.horizontalWeight(1f),
                )
            } else {
                Box(
                    Modifier.horizontalWeight(1f).size(doc.units.px(keyH), doc.units.px(keyH))
                        .clip(doc.rounded(keyH / BlockMetrics.TIMER_KEY_CORNER_DIVISOR))
                        .background(doc.role(Role.KEY)),
                    horizontal = RcHorizontalPositioning.Center,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    doc.icon(icon, keyH * BlockMetrics.TIMER_ICON_SHARE, doc.role(Role.INK_DIM))
                }
            }
        }
        key("start", doc.i18n.t("ui.start"), "play")
        Spacer(Modifier.width(doc.units.px(BlockMetrics.TIMER_GAP_DP)))
        key("pause", doc.i18n.t("ui.pause"), "pause")
        Spacer(Modifier.width(doc.units.px(BlockMetrics.TIMER_GAP_DP)))
        key("cancel", doc.i18n.t("ui.cancel"), "close")
    }
}

/** Open to-do items; a tap marks one complete. */
private fun DocContext.todoListNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val max = node.props["max"]?.toIntOrNull()?.coerceIn(1, BlockMetrics.TODO_MAX)
        ?: BlockMetrics.TODO_DEFAULT_MAX
    if (id != null) {
        bindEntity(id)
        structural(id) { data.todos[id].orEmpty().joinToString { it.uid } }
        refreshEvery(BlockMetrics.TODO_REFRESH_S)
    }
    val items = id?.let { data.todos[it].orEmpty().take(max) }.orEmpty()
    scope.Column(styled(node, axis, pages, measured(node, axis, self))) {
        val doc = this@todoListNode
        if (id == null || items.isEmpty()) {
            doc.label(doc.i18n.t("ui.no_todos"), Type.BODY, doc.role(Role.INK_SOFT))
        } else {
            items.forEachIndexed { index, item ->
                if (index > 0) doc.gap(BlockMetrics.TODO_GAP_DP)
                doc.key(
                    ActionRef(node.id, "complete", item.uid),
                    text = item.summary,
                    icon = "checkbox-blank-outline",
                    heightDp = BlockMetrics.TODO_ROW_H_DP,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Upcoming events from the bound calendar and/or a comma-separated `calendars` prop. */
private fun DocContext.eventsNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val max = node.props["max"]?.toIntOrNull()?.coerceIn(1, BlockMetrics.EVENT_MAX)
        ?: BlockMetrics.EVENT_DEFAULT_MAX
    val calendars = listedEntities(node, "calendars", "calendar")
    val zone = runCatching { ZoneId.of(
        homeAssistantConfig.timeZone,
    ) }.getOrDefault(ZoneId.of("UTC"))
    for (calendarId in calendars) {
        bindEntity(calendarId)
        structural(calendarId) { s ->
            listOf(s?.attr("message"), s?.attr("start_time"), s?.attr("all_day")).joinToString("|")
        }
    }
    val events = calendars.mapNotNull { calendarId ->
        states[calendarId]?.let { entityState -> calendarEvent(
            entityState,
            zone,
        )?.let { calendarId to it } }
    }.sortedBy { it.second.start }.take(max)
    scope.Column(styled(node, axis, pages, measured(node, axis, self))) {
        val doc = this@eventsNode
        if (events.isEmpty()) {
            doc.label(doc.i18n.t("ui.no_events"), Type.BODY, doc.role(Role.INK_SOFT))
        } else {
            events.forEachIndexed { index, (calendarId, event) ->
                if (index > 0) doc.gap(BlockMetrics.EVENT_GAP_DP)
                val whenText = when {
                    event.allDay && isToday(event, zone) ->
                        doc.i18n.t("ui.today") + " · " + doc.i18n.t("ui.all_day")
                    event.allDay -> doc.i18n.fullWeekday(
                        event.start,
                    ) + " · " + doc.i18n.t("ui.all_day")
                    isToday(
                        event,
                        zone,
                    ) -> doc.i18n.t("ui.today") + " · " + doc.i18n.time(event.start)
                    else -> doc.i18n.fullWeekday(event.start) + " · " + doc.i18n.time(event.start)
                }
                Column(doc.tap(Modifier.fillMaxWidth(), ActionRef("e:$calendarId", "card"))) {
                    doc.label(
                        event.title,
                        Type.BODY,
                        doc.role(Role.INK),
                        Weight.SEMI_BOLD,
                        lines = 1,
                    )
                    doc.label(whenText, Type.CAPTION, doc.role(Role.INK_SOFT))
                }
            }
        }
    }
}

/** Album art, title/artist, and previous / play-pause / next for a media player. */
private fun DocContext.mediaBarNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    if (id != null) bindMediaBar(node, id)
    val art = BlockMetrics.MEDIA_ART_DP
    val key = BlockMetrics.MEDIA_KEY_DP
    scope.Row(
        styled(node, axis, pages, measured(node, axis, self)),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@mediaBarNode
        if (id == null) {
            doc.label("—", Type.BODY, doc.role(Role.INK_DIM))
            return@Row
        }
        val px = doc.units.px(art).toInt()
        doc.picture(PictureSlot("art.$id.${node.id}", "attr:$id:entity_picture", px, px, 0))
        Image(
            doc.image("art.$id.${node.id}", px, px),
            Modifier.size(doc.units.px(art), doc.units.px(art))
                .clip(doc.rounded(BlockMetrics.MEDIA_ART_CORNER_DP)),
            contentScale = RcContentScale.Crop,
        )
        Spacer(Modifier.width(doc.units.px(BlockMetrics.PRESENCE_GAP_DP)))
        Column(Modifier.horizontalWeight(1f)) {
            doc.label(
                doc.text(Vars.entity(id, "title")),
                Type.BODY,
                doc.role(Role.INK),
                Weight.SEMI_BOLD,
                lines = 1,
            )
            doc.label(
                doc.text(Vars.entity(id, "artist")),
                Type.CAPTION,
                doc.role(Role.INK_SOFT),
                lines = 1,
            )
        }
        doc.key(
            ActionRef(node.id, "previous"),
            icon = "skip-previous",
            heightDp = key,
            widthDp = key,
            round = true,
        )
        Box(
            doc.tap(
                Modifier.size(doc.units.px(key), doc.units.px(key))
                    .clip(doc.rounded(key / 2f)).background(doc.role(Role.KEY_ON)),
                ActionRef(node.id, "play_pause"),
            ),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            val iconDp = key * BlockMetrics.MEDIA_KEY_ICON_SHARE
            doc.icon(
                "pause",
                iconDp,
                doc.role(Role.KEY_ON_INK),
                doc.int(Vars.entity(id, "playing")),
            )
            doc.icon(
                "play",
                iconDp,
                doc.role(Role.KEY_ON_INK),
                doc.int(Vars.entity(id, "notplaying")),
            )
        }
        doc.key(
            ActionRef(node.id, "next"),
            icon = "skip-next",
            heightDp = key,
            widthDp = key,
            round = true,
        )
    }
}

private fun DocContext.bindMediaBar(node: Node, id: String) {
    bindEntity(id)
    bind(id) { states ->
        val entityState = states[id]
        val artist = listOfNotNull(
            entityState?.attr("media_artist"),
            entityState?.attr("media_album_name"),
        ).joinToString(" · ").ifEmpty { i18n.state("media_player", entityState?.state ?: "off") }
        val playing = entityState?.state == "playing"
        listOf(
            VarValue.string(
                Vars.entity(id, "title"),
                entityState?.attr("media_title") ?: entityState?.name ?: "",
            ),
            VarValue.string(Vars.entity(id, "artist"), artist),
            VarValue.int(Vars.entity(id, "playing"), if (playing) Vars.VISIBLE else Vars.GONE),
            VarValue.int(Vars.entity(id, "notplaying"), if (playing) Vars.GONE else Vars.VISIBLE),
        )
    }
    predict(ActionRef(node.id, "play_pause"), id)
}

/** Daily forecast chips for a weather entity. */
private fun DocContext.forecastNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val days = node.props["days"]?.toIntOrNull()?.coerceIn(1, BlockMetrics.FORECAST_MAX_DAYS)
        ?: BlockMetrics.FORECAST_DEFAULT_DAYS
    if (id != null) {
        bindEntity(id)
        structural(id) { it?.state ?: "" }
        refreshEvery(BlockMetrics.FORECAST_REFRESH_S)
    }
    val forecast = id?.let { data.forecasts[it].orEmpty().take(days) }.orEmpty()
    val zone = runCatching { ZoneId.of(
        homeAssistantConfig.timeZone,
    ) }.getOrDefault(ZoneId.of("UTC"))
    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val soft = role(Role.INK_SOFT)
    scope.Row(
        styled(node, axis, pages, measured(node, axis, self)),
        horizontal = RcRowHorizontalPositioning.SpaceBetween,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@forecastNode
        if (forecast.isEmpty()) {
            doc.label("—", Type.BODY, soft)
        } else {
            for (day in forecast) {
                Column(
                    Modifier.width(doc.units.px(BlockMetrics.FORECAST_DAY_W_DP)),
                    horizontal = RcHorizontalPositioning.Center,
                ) {
                    doc.label(
                        doc.i18n.weekday(day.time.withZoneSameInstant(zone)),
                        Type.CAPTION,
                        soft,
                        align = RcTextAlign.Center,
                    )
                    doc.icon(Present.weatherIcon(day.condition), BlockMetrics.FORECAST_ICON_DP, ink)
                    doc.label(
                        listOfNotNull(
                            day.high?.let { "${it.roundToInt()}°" },
                            day.low?.let { "${it.roundToInt()}°" },
                        ).joinToString(" "),
                        Type.CAPTION,
                        ink,
                        align = RcTextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Person chips: photo, name, HOME/AWAY. `people` lists entity ids; otherwise every person. */
private fun DocContext.presenceNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val people = listedEntities(node, "people", "person")
    val photo = BlockMetrics.PRESENCE_PHOTO_DP
    scope.Row(
        styled(node, axis, pages, measured(node, axis, self)),
        horizontal = RcRowHorizontalPositioning.SpaceEvenly,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@presenceNode
        if (people.isEmpty()) {
            doc.label("—", Type.BODY, doc.role(Role.INK_DIM))
        } else {
            for (personId in people) {
                doc.bindEntity(personId)
                doc.bindWhere(Vars.tile(node.id, "where.$personId"), personId)
                Row(
                    doc.tap(
                        Modifier.padding(doc.units.px(BlockMetrics.PRESENCE_PAD_DP)),
                        ActionRef("e:$personId", "card"),
                    ),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    doc.photo(
                        doc.states[personId],
                        personId,
                        "presence.${node.id}.$personId",
                        photo,
                    )
                    Spacer(Modifier.width(doc.units.px(BlockMetrics.PRESENCE_GAP_DP)))
                    Column {
                        doc.label(
                            doc.text(Vars.entity(personId, Vars.KEY_NAME)),
                            Type.BODY,
                            doc.role(Role.INK),
                            Weight.SEMI_BOLD,
                            lines = 1,
                        )
                        doc.label(
                            doc.text(Vars.tile(node.id, "where.$personId")),
                            Type.CAPTION,
                            doc.role(Role.ACCENT_SOFT),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The RPI thermostat page as a free-form leaf: current temp and humidity, the dial with +/−, Heat/Cool modes and
 * Fan mode — the same drawing as a full climate tile ([thermostatPanel]).
 */
private fun DocContext.thermostatNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val entityId = node.bind?.entityId
    val contentW = (self.w - node.style.padX).coerceAtLeast(0f)
    val contentH = (self.h - node.style.padY).coerceAtLeast(0f)
    val modifier = styled(node, axis, pages, measured(node, axis, self))
    if (entityId == null) {
        scope.Box(
            modifier.background(role(Role.PAGE)),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            this@thermostatNode.label("—", Type.BODY, this@thermostatNode.role(Role.INK_DIM))
        }
        return
    }
    scope.Box(modifier) {
        this@thermostatNode.thermostatPanel(
            Tile(id = node.id, entityId = entityId, full = true),
            TileBox(0f, 0f, contentW, contentH, 1, 1, full = true),
        )
    }
}

/** A small on/off LED for a bound entity. */
private fun DocContext.ledNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val sizeDp = node.props["size"]?.toFloatOrNull()
        ?.coerceIn(BlockMetrics.LED_MIN_DP, BlockMetrics.LED_MAX_DP)
        ?: BlockMetrics.LED_DEFAULT_DP
    val onColor = node.props["color"]?.let { accent(it, Pastel.TEAL) } ?: Pastel.TEAL.argb
    if (id != null) {
        bindEntity(id)
        bind(id) { states ->
            val on = Present.view(states[id], i18n).on
            listOf(
                VarValue.color(
                    Vars.tile(node.id, Vars.KEY_BG),
                    if (on) onColor else Role.INK_DIM.argb,
                ),
            )
        }
    }
    val bg = if (id != null) color(Vars.tile(node.id, Vars.KEY_BG)) else role(Role.INK_DIM)
    scope.Box(
        styled(node, axis, pages)
            .size(units.px(sizeDp), units.px(sizeDp))
            .clip(rounded(sizeDp / BlockMetrics.LED_CORNER_DIVISOR))
            .background(bg),
    )
}

/** Entity ids from [prop] (comma-separated), else the bound entity, else every entity of [domain]. */
private fun DocContext.listedEntities(node: Node, prop: String, domain: String): List<String> {
    val fromProp = node.props[prop]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
    val bound = node.bind?.entityId?.takeIf { it.startsWith("$domain.") }?.let { listOf(it) }
    return when {
        !fromProp.isNullOrEmpty() -> fromProp
        bound != null -> bound
        else -> states.keys.filter { it.startsWith("$domain.") }.sorted()
    }
}
