@file:Suppress("RestrictedApi")

package dev.remotescreens.server.render

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
import androidx.compose.remote.creation.dsl.offset
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.CircleShape
import dev.remotescreens.server.ha.EntityState
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.Catalogue
import dev.remotescreens.shared.TapAction
import dev.remotescreens.shared.TapType
import dev.remotescreens.shared.Tile
import dev.remotescreens.shared.TypeSpec
import java.time.ZoneId
import kotlin.math.roundToInt

/** Where a tile sits, in dp, and how the page finds other pages. */
data class TileBox(val x: Float, val y: Float, val w: Float, val h: Float, val cols: Int, val rows: Int, val full: Boolean)

/** What a tap (or a hold) on a tile sends. */
fun tapRef(tile: Tile, action: TapAction, spec: TypeSpec?, pageIndex: (String) -> Int?, hold: Boolean): ActionRef? {
    fun card() = if (spec != null && spec.card != "none" && (tile.entityId != null || !spec.needsEntity)) ActionRef(tile.id, "card") else null
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

fun DocContext.tile(tile: Tile, box: TileBox, pageIndex: (String) -> Int?) {
    val kind = tile.effectiveKind()
    val spec = Catalogue.of(kind)
    val entityId = tile.entityId
    val e = entityId?.let { states[it] }
    val pastel = Pastel.of(tile.color) ?: Pastel.forDomain(tile.domain ?: kind)
    if (entityId != null) bindEntity(entityId, tile.name, tile.icon)
    bindTileColors(tile.id, entityId, pastel, alwaysOn = kind in ALWAYS_LIT)

    val tapRef = tapRef(tile, tile.tap, spec, pageIndex, hold = false)
    val holdRef = tapRef(tile, tile.hold, spec, pageIndex, hold = true)
    if (tapRef != null && entityId != null && spec?.predict?.containsKey(tapRef.verb) == true) predict(tapRef, entityId)

    val m = Modifier.offset(u.px(box.x), u.px(box.y)).size(u.px(box.w), u.px(box.h))
        .clip(rounded(if (box.full) 28f else 20f)).background(color(V.t(tile.id, "bg")))
    scope.Box(tap(m, tapRef, holdRef)) {
        val ctx = this@tile
        when (kind) {
            "clock" -> ctx.clockTile(tile, box)
            "bedside" -> ctx.bedsideTile(tile, box)
            "nav" -> ctx.navTile(tile, box)
            "weather" -> ctx.weatherTile(tile, box, e)
            "climate" -> ctx.climateTile(tile, box, e)
            "media_player" -> ctx.mediaTile(tile, box, e)
            "camera" -> ctx.cameraTile(tile, box)
            "history" -> ctx.historyTile(tile, box)
            "energy" -> ctx.energyFlow(tile.id, box.w, box.h, compact = !box.full && box.w < 400f)
            "map" -> ctx.mapTile(tile, box)
            "person", "device_tracker" -> if (tile.options["map"] == "true") ctx.mapTile(tile, box) else ctx.personTile(tile, box, e)
            "favourite" -> ctx.favouriteTile(tile, box)
            "sensor" -> if (tile.options["graph"] == "true" && box.h >= 150f) ctx.historyTile(tile, box) else ctx.entityTile(tile, box, spec, e)
            else -> ctx.entityTile(tile, box, spec, e)
        }
    }
}

private fun DocContext.tileIcon(tile: Tile, spec: TypeSpec?, e: EntityState?, sizeDp: Float) {
    val id = tile.entityId
    val ink = color(V.t(tile.id, "icon"))
    val override = tile.icon ?: e?.attr("icon")
    when {
        override != null -> icon(override, sizeDp, ink)
        id != null && e != null && spec != null && spec.toggle && (spec.domain != "cover" && spec.domain != "lock") ->
            toggleIcon(id, spec.icon, spec.iconOn, sizeDp, ink)
        e != null -> {
            structural(e.entityId) { s -> s?.let { Present.view(it, i18n).icon } ?: "" }
            icon(Present.view(e, i18n).icon, sizeDp, ink)
        }
        else -> icon(spec?.icon, sizeDp, ink)
    }
}

/** The tile most entities get: an icon, the name and a line about the state, with a control where there is room. */
private fun DocContext.entityTile(tile: Tile, box: TileBox, spec: TypeSpec?, e: EntityState?) {
    val id = tile.entityId
    val name = if (id != null) text(V.e(id, "name")) else null
    val state = if (id != null) text(V.e(id, "state")) else null
    val ink = color(V.t(tile.id, "ink"))
    val ink2 = color(V.t(tile.id, "ink2"))
    val pad = if (box.w < 130f) 12f else 16f
    val wide = box.w >= 2 * box.h * 0.9f && box.h < 150f
    val hasLevel = id != null && box.w >= 150f && e != null && when (tile.domain) {
        "light" -> e.list("supported_color_modes").any { it != "onoff" }
        "fan" -> e.supports(1)
        "cover" -> e.supports(4)
        "number", "input_number", "media_player" -> true
        else -> false
    } && (box.h >= 150f || wide)
    val bigValue = tile.domain in setOf("sensor", "number", "input_number")

    if (box.full) {
        scope.Column(Modifier.fillMaxSize().padding(u.px(24f)), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
            this@entityTile.tileIcon(tile, spec, e, 120f)
            this@entityTile.gap(16f)
            if (name != null) this@entityTile.label(name, Type.DISPLAY * 0.8f, ink, 600f, align = RcTextAlign.Center)
            if (state != null) this@entityTile.label(state, Type.HEADLINE, ink2, align = RcTextAlign.Center)
        }
        return
    }
    if (wide && !hasLevel) {
        scope.Row(Modifier.fillMaxSize().padding(u.px(pad)), vertical = RcVerticalPositioning.Center) {
            this@entityTile.tileIcon(tile, spec, e, 36f)
            Spacer(Modifier.width(this@entityTile.u.px(14f)))
            Column(Modifier.horizontalWeight(1f)) {
                if (name != null) this@entityTile.label(name, Type.TITLE, ink, 600f)
                if (state != null) this@entityTile.label(state, if (bigValue) Type.HEADLINE else Type.BODY, ink2, if (bigValue) 500f else 400f)
            }
        }
        return
    }
    scope.Column(Modifier.fillMaxSize().padding(u.px(pad))) {
        val c = this@entityTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Top) {
            c.tileIcon(tile, spec, e, if (box.h >= 150f) 40f else 30f)
            Spacer(Modifier.horizontalWeight(1f))
            if (tile.domain == "lock") c.lockConfirmBadge(tile)
        }
        Spacer(Modifier.verticalWeight(1f))
        if (bigValue && state != null) c.label(state, if (box.h >= 150f) Type.VALUE else Type.HEADLINE, ink, 500f)
        if (name != null) c.label(name, Type.BODY + if (box.h >= 150f) 2f else 0f, ink, 600f, lines = if (state == null || bigValue) 2 else 1)
        if (state != null && !bigValue) c.label(state, Type.CAPTION + 1f, ink2)
        if (hasLevel && id != null) {
            c.gap(10f)
            val verb = when (tile.domain) { "light" -> "brightness"; "fan" -> "percentage"; "cover" -> "position"; "media_player" -> "volume"; else -> "value" }
            val (min, max) = if (tile.domain in setOf("number", "input_number")) (e?.number("min") ?: 0.0) to (e?.number("max") ?: 100.0) else 0.0 to 100.0
            c.levelBar(c.float(V.e(id, "level")), box.w - 2 * pad, 28f, color(V.t(tile.id, "ink")), color(V.t(tile.id, "fill")), 10) { i ->
                val v = min + (max - min) * i / 10.0
                ActionRef(tile.id, verb, if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, v))
            }
        }
    }
}

/** "Tap again to unlock" on a lock tile, shown by the add-on between the two taps. */
private fun DocContext.lockConfirmBadge(tile: Tile) {
    val t = text(V.t(tile.id, "confirm"))
    label(t, Type.CAPTION, role(Role.WARN), 600f)
}

private fun DocContext.clockTile(tile: Tile, box: TileBox) {
    val ink = color(V.t(tile.id, "ink"))
    val ink2 = color(V.t(tile.id, "ink2"))
    val big = minOf(box.h * 0.38f, box.w * 0.22f).coerceAtLeast(Type.HEADLINE)
    scope.Column(Modifier.fillMaxSize().padding(u.px(14f)), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
        this@clockTile.label(this@clockTile.text(V.TIME), big, ink, 300f, align = RcTextAlign.Center)
        this@clockTile.label(this@clockTile.text(V.DATE), Type.BODY, ink2, align = RcTextAlign.Center, lines = 2)
    }
}

/** The bedside clock: the time as large as the glass allows and up to three round keys for the night. */
private fun DocContext.bedsideTile(tile: Tile, box: TileBox) {
    val ink = role(Role.INK_SOFT)
    val keys = tile.options["keys"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
    scope.Column(Modifier.fillMaxSize().padding(u.px(24f)), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
        val c = this@bedsideTile
        c.label(c.text(V.TIME), minOf(box.w * 0.24f, box.h * 0.4f), ink, 200f, align = RcTextAlign.Center)
        c.gap(24f)
        Row(horizontal = RcRowHorizontalPositioning.Center) {
            keys.forEachIndexed { i, entityId ->
                if (i > 0) Spacer(Modifier.width(c.u.px(28f)))
                val spec = Catalogue.of(entityId.substringBefore('.'))
                val keyTile = Tile(id = "${tile.id}.k$i", entityId = entityId)
                c.bindEntity(entityId)
                c.bindTileColors(keyTile.id, entityId, Pastel.forDomain(spec?.domain))
                val ref = tapRef(keyTile, TapAction(), spec, { null }, hold = false)
                if (ref != null && spec?.predict?.containsKey(ref.verb) == true) c.predict(ref, entityId)
                Box(c.tap(Modifier.size(c.u.px(76f), c.u.px(76f)).clip(CircleShape()).background(c.color(V.t(keyTile.id, "bg"))), ref),
                    horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                    c.tileIcon(keyTile, spec, c.states[entityId], 34f)
                }
            }
        }
    }
}

private fun DocContext.navTile(tile: Tile, box: TileBox) {
    val ink = color(V.t(tile.id, "ink"))
    scope.Column(Modifier.fillMaxSize().padding(u.px(16f))) {
        this@navTile.icon(tile.icon ?: "arrow-right-circle", 32f, ink)
        Spacer(Modifier.verticalWeight(1f))
        this@navTile.label(tile.name ?: "", Type.TITLE, ink, 600f, lines = 2)
    }
}

private fun DocContext.personTile(tile: Tile, box: TileBox, e: EntityState?) {
    val id = tile.entityId ?: return
    val ink = color(V.t(tile.id, "ink"))
    val ink2 = color(V.t(tile.id, "ink2"))
    scope.Column(Modifier.fillMaxSize().padding(u.px(14f))) {
        val c = this@personTile
        val pic = e?.attr("entity_picture")
        if (pic != null) {
            c.picture(PictureSlot("pic.$id", pic, c.u.px(44f).toInt(), c.u.px(44f).toInt(), 0, round = true))
            Image(c.image("pic.$id", c.u.px(44f).toInt(), c.u.px(44f).toInt()), Modifier.size(c.u.px(44f), c.u.px(44f)).clip(CircleShape()), contentScale = RcContentScale.Crop)
        } else {
            c.initials(e?.name ?: id, 44f, c.color(V.t(tile.id, "fill")), ink)
        }
        Spacer(Modifier.verticalWeight(1f))
        c.label(c.text(V.e(id, "name")), Type.BODY, ink, 600f)
        c.label(c.text(V.e(id, "state")), Type.CAPTION + 1f, ink2)
    }
}

private fun DocContext.weatherTile(tile: Tile, box: TileBox, e: EntityState?) {
    val id = tile.entityId ?: return
    val ink = color(V.t(tile.id, "ink"))
    val ink2 = color(V.t(tile.id, "ink2"))
    structural(id) { it?.state ?: "" }
    refreshEvery(1800)
    val days = data.forecasts[id].orEmpty()
    val zone = runCatching { ZoneId.of(ha.timeZone) }.getOrDefault(ZoneId.of("UTC"))
    val dayCount = when {
        box.w >= 300f -> 5
        box.w >= 200f -> 3
        else -> 0
    }.coerceAtMost(days.size)
    scope.Column(Modifier.fillMaxSize().padding(u.px(14f))) {
        val c = this@weatherTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            c.icon(Present.weatherIcon(e?.state ?: ""), if (box.h >= 150f) 52f else 36f, ink)
            Spacer(Modifier.width(c.u.px(10f)))
            Column(Modifier.horizontalWeight(1f)) {
                c.label(c.text(V.e(id, "value")), Type.HEADLINE, ink, 500f)
                c.label(c.i18n.state("weather", e?.state ?: "unknown"), Type.CAPTION + 1f, ink2)
            }
        }
        Spacer(Modifier.verticalWeight(1f))
        if (dayCount > 0) {
            Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.SpaceBetween) {
                for (d in days.take(dayCount)) {
                    Column(horizontal = RcHorizontalPositioning.Center) {
                        c.label(c.i18n.weekday(d.time.withZoneSameInstant(zone)), Type.CAPTION, ink2, align = RcTextAlign.Center)
                        c.icon(Present.weatherIcon(d.condition), 26f, ink)
                        c.label(listOfNotNull(d.high?.let { "${it.roundToInt()}°" }, d.low?.let { "${it.roundToInt()}°" }).joinToString(" "), Type.CAPTION, ink, align = RcTextAlign.Center)
                    }
                }
            }
        } else {
            c.label(c.text(V.e(id, "name")), Type.BODY, ink, 600f)
        }
    }
}

private fun DocContext.climateTile(tile: Tile, box: TileBox, e: EntityState?) {
    val id = tile.entityId ?: return
    val ink = color(V.t(tile.id, "ink"))
    val ink2 = color(V.t(tile.id, "ink2"))
    val step = e?.number("target_temp_step") ?: if (ha.temperatureUnit.contains("F")) 1.0 else 0.5
    val stepper = box.w >= 200f || box.h >= 150f
    val modes = e?.list("hvac_modes").orEmpty()
    structural(id) { it?.list("hvac_modes")?.joinToString() ?: "" }
    predict(ActionRef(tile.id, "temperature", "+$step"), id)
    predict(ActionRef(tile.id, "temperature", "-$step"), id)
    scope.Column(Modifier.fillMaxSize().padding(u.px(14f))) {
        val c = this@climateTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            c.tileIcon(tile, Catalogue.of("climate"), e, 30f)
            Spacer(Modifier.width(c.u.px(10f)))
            Column(Modifier.horizontalWeight(1f)) {
                c.label(c.text(V.e(id, "name")), Type.BODY, ink, 600f)
                c.label(c.text(V.e(id, "state")), Type.CAPTION + 1f, ink2)
            }
        }
        Spacer(Modifier.verticalWeight(1f))
        if (stepper) {
            Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
                c.key(ActionRef(tile.id, "temperature", "-$step"), icon = "minus", heightDp = 48f, widthDp = 48f, round = true, bg = c.color(V.t(tile.id, "fill")), ink = ink)
                Box(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
                    c.label(c.text(V.e(id, "value")), Type.VALUE + 4f, ink, 500f, align = RcTextAlign.Center)
                }
                c.key(ActionRef(tile.id, "temperature", "+$step"), icon = "plus", heightDp = 48f, widthDp = 48f, round = true, bg = c.color(V.t(tile.id, "fill")), ink = ink)
            }
            if (box.h >= 260f && modes.isNotEmpty()) {
                c.gap(10f)
                c.bindChoices(tile.id, "mode", id, modes) { it.state }
                c.choiceRow(tile.id, "mode", modes.take(4).map { it to c.i18n.state("climate", it) }, { ActionRef(tile.id, "mode", it) }, 40f, MODE_ICONS)
            }
        } else {
            c.label(c.text(V.e(id, "value")), Type.HEADLINE, ink, 500f)
        }
    }
}

val MODE_ICONS = mapOf(
    "off" to "power", "heat" to "fire", "cool" to "snowflake", "heat_cool" to "sun-snowflake-variant",
    "auto" to "thermostat-auto", "dry" to "water-percent", "fan_only" to "fan",
)

private fun DocContext.mediaTile(tile: Tile, box: TileBox, e: EntityState?) {
    val id = tile.entityId ?: return
    val ink = color(V.t(tile.id, "ink"))
    val ink2 = color(V.t(tile.id, "ink2"))
    val big = box.w >= 200f && box.h >= 150f
    bind(id) { states ->
        val s = states[id]
        listOf(
            dev.remotescreens.shared.VarValue.string(V.e(id, "title"), s?.attr("media_title") ?: s?.name ?: ""),
            dev.remotescreens.shared.VarValue.string(V.e(id, "artist"), listOfNotNull(s?.attr("media_artist"), s?.attr("media_album_name")).joinToString(" · ").ifEmpty { i18n.state("media_player", s?.state ?: "off") }),
            dev.remotescreens.shared.VarValue.int(V.e(id, "playing"), if (s?.state == "playing") V.VISIBLE else V.GONE),
            dev.remotescreens.shared.VarValue.int(V.e(id, "notplaying"), if (s?.state == "playing") V.GONE else V.VISIBLE),
        )
    }
    predict(ActionRef(tile.id, "play_pause"), id)
    scope.Box(Modifier.fillMaxSize()) {
        val c = this@mediaTile
        if (big) {
            val w = c.u.px(box.w).toInt(); val h = c.u.px(box.h).toInt()
            c.picture(PictureSlot("art.$id", "attr:$id:entity_picture", w, h, 0))
            Image(c.image("art.$id", w, h), Modifier.fillMaxSize(), contentScale = RcContentScale.Crop, alpha = 0.35f)
        }
        Column(Modifier.fillMaxSize().padding(c.u.px(14f))) {
            c.tileIcon(tile, Catalogue.of("media_player"), e, 28f)
            Spacer(Modifier.verticalWeight(1f))
            c.label(c.text(V.e(id, "title")), if (big) Type.TITLE + 2f else Type.BODY, ink, 600f, lines = if (big) 2 else 1)
            c.label(c.text(V.e(id, "artist")), Type.CAPTION + 1f, ink2)
            if (box.w >= 200f || box.h >= 150f) {
                c.gap(8f)
                Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.SpaceBetween, vertical = RcVerticalPositioning.Center) {
                    c.key(ActionRef(tile.id, "previous"), icon = "skip-previous", heightDp = 44f, widthDp = 44f, round = true, bg = c.color(V.t(tile.id, "fill")), ink = ink)
                    Box(c.tap(Modifier.size(c.u.px(52f), c.u.px(52f)).clip(CircleShape()).background(ink), ActionRef(tile.id, "play_pause")),
                        horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                        c.icon("pause", 28f, c.color(V.t(tile.id, "bg")), c.int(V.e(id, "playing")))
                        c.icon("play", 28f, c.color(V.t(tile.id, "bg")), c.int(V.e(id, "notplaying")))
                    }
                    c.key(ActionRef(tile.id, "next"), icon = "skip-next", heightDp = 44f, widthDp = 44f, round = true, bg = c.color(V.t(tile.id, "fill")), ink = ink)
                }
            }
        }
    }
}

private fun DocContext.cameraTile(tile: Tile, box: TileBox) {
    val id = tile.entityId ?: return
    val w = u.px(box.w).toInt()
    val h = u.px(box.h).toInt()
    val every = tile.options["live"]?.toIntOrNull()?.coerceIn(5, 30) ?: 60
    picture(PictureSlot("cam.$id.${tile.id}", "/api/camera_proxy/$id", w, h, every, crop = tile.options["fit"] != "whole"))
    scope.Box(Modifier.fillMaxSize().background(role(Role.SCRIM)), vertical = RcVerticalPositioning.Bottom) {
        val c = this@cameraTile
        Image(c.image("cam.$id.${tile.id}", w, h), Modifier.fillMaxSize(), contentScale = if (tile.options["fit"] == "whole") RcContentScale.Fit else RcContentScale.Crop)
        if (tile.options["name"] != "false") {
            Box(Modifier.fillMaxWidth().background(c.role(Role.SCRIM)).padding(c.u.px(10f))) {
                c.label(c.text(V.e(id, "name")), Type.BODY, c.fixed(0xFFFFFFFF.toInt()), 600f)
            }
        }
    }
}

private fun DocContext.favouriteTile(tile: Tile, box: TileBox) {
    val ink = color(V.t(tile.id, "ink"))
    val art = tile.options["image"]
    scope.Box(Modifier.fillMaxSize()) {
        val c = this@favouriteTile
        if (art != null && box.w >= 120f) {
            val w = c.u.px(box.w).toInt(); val h = c.u.px(box.h).toInt()
            c.picture(PictureSlot("fav.${tile.id}", art, w, h, 0))
            Image(c.image("fav.${tile.id}", w, h), Modifier.fillMaxSize(), contentScale = RcContentScale.Crop, alpha = 0.45f)
        }
        Column(Modifier.fillMaxSize().padding(c.u.px(14f))) {
            c.icon(tile.icon ?: "playlist-music", 28f, ink)
            Spacer(Modifier.verticalWeight(1f))
            c.label(tile.name ?: tile.options["title"] ?: "", Type.BODY + 1f, ink, 600f, lines = 2)
        }
    }
}

/** A history graph drawn from the recorder, with its highest and lowest value. */
fun DocContext.historyTile(tile: Tile, box: TileBox) {
    val id = tile.entityId ?: return
    val ink = color(V.t(tile.id, "ink"))
    val ink2 = color(V.t(tile.id, "ink2"))
    refreshEvery(300)
    val points = data.history[id].orEmpty()
    scope.Column(Modifier.fillMaxSize().padding(u.px(14f))) {
        val c = this@historyTile
        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            Column(Modifier.horizontalWeight(1f)) {
                c.label(c.text(V.e(id, "name")), Type.BODY, ink, 600f)
                c.label(c.text(V.e(id, "state")), Type.HEADLINE, ink, 500f)
            }
        }
        c.gap(6f)
        c.graph(points, box.w - 28f, (box.h - 90f).coerceAtLeast(30f), ink, ink2)
    }
}

fun DocContext.graph(points: List<Pair<Long, Double>>, wDp: Float, hDp: Float, line: androidx.compose.remote.creation.dsl.RcColor, soft: androidx.compose.remote.creation.dsl.RcColor) {
    val w = u.px(wDp)
    val h = u.px(hDp)
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
    val stroke = u.px(2.5f)
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

private fun DocContext.mapTile(tile: Tile, box: TileBox) {
    val w = u.px(box.w).toInt()
    val h = u.px(box.h).toInt()
    val who = (listOfNotNull(tile.entityId) + tile.options["people"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() })
        .ifEmpty { states.keys.filter { it.startsWith("person.") } }
    for (p in who) structural(p) { s -> "${s?.number("latitude")},${s?.number("longitude")}" }
    picture(PictureSlot("map.${tile.id}", "map:${if (dark) "dark" else "light"}:${who.joinToString(",")}", w, h, 0))
    scope.Box(Modifier.fillMaxSize(), vertical = RcVerticalPositioning.Bottom) {
        val c = this@mapTile
        Image(c.image("map.${tile.id}", w, h), Modifier.fillMaxSize(), contentScale = RcContentScale.Crop)
        Box(Modifier.fillMaxWidth().background(c.role(Role.CARD)).padding(c.u.px(8f))) {
            c.label(tile.name ?: c.i18n.t("ui.people_home", "count" to who.count { c.states[it]?.state == "home" }), Type.CAPTION + 1f, c.role(Role.INK), 600f)
        }
    }
}

/** Solar, the grid and the battery around the house, with the power of each, as Home Assistant's energy settings name them. */
fun DocContext.energyFlow(scopeId: String, wDp: Float, hDp: Float, compact: Boolean) {
    val prefs = data.energy ?: return
    val nodes = listOfNotNull(
        prefs.solar?.let { Triple("solar", it, "solar-power-variant") },
        prefs.grid?.let { Triple("grid", it, "transmission-tower") },
        prefs.battery?.let { Triple("battery", it, "home-battery") },
    )
    for ((_, entity, _) in nodes) bindEntity(entity)
    val r = if (compact) 30f else 42f
    val cx = wDp / 2f
    val cy = hDp / 2f + 6f
    val spots = mapOf("solar" to (cx to r + 16f), "grid" to (r + 14f to cy), "battery" to (wDp - r - 14f to cy), "home" to (cx to hDp - r - 26f))
    val ink = role(Role.INK)
    val ink2 = role(Role.INK_SOFT)
    scope.Box(Modifier.fillMaxSize()) {
        val c = this@energyFlow
        Canvas(Modifier.fillMaxSize()) {
            paint { color(c.role(Role.DIVIDER)); strokeWidth(c.u.px(3f)); style(RcPaintStyle.Stroke) }
            val home = spots.getValue("home")
            for ((key, _, _) in nodes) {
                val p = spots.getValue(key)
                drawLine(c.u.px(p.first), c.u.px(p.second), c.u.px(home.first), c.u.px(home.second))
            }
        }
        for ((key, entity, icon) in nodes + Triple("home", "", "home")) {
            val (x, y) = spots.getValue(key)
            val tint = when (key) { "solar" -> Pastel.YELLOW; "grid" -> Pastel.BLUE; "battery" -> Pastel.GREEN; else -> Pastel.GREY }
            Column(
                Modifier.offset(c.u.px(x - r), c.u.px(y - r)).size(c.u.px(2 * r), c.u.px(2 * r + 22f)),
                horizontal = RcHorizontalPositioning.Center,
            ) {
                val hold = if (entity.isNotEmpty()) ActionRef("e:$entity", "card", "history") else null
                Box(c.tap(Modifier.size(c.u.px(2 * r), c.u.px(2 * r)).clip(CircleShape()).background(c.fixed(tint.bg(c.dark))), hold),
                    horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                    Column(horizontal = RcHorizontalPositioning.Center) {
                        c.icon(icon, if (compact) 18f else 24f, c.fixed(tint.ink(c.dark)))
                        if (entity.isNotEmpty()) c.label(c.text(V.e(entity, "state")), if (compact) Type.CAPTION - 1f else Type.CAPTION, c.fixed(tint.ink(c.dark)), 600f, align = RcTextAlign.Center)
                    }
                }
                c.label(c.i18n.t(if (key == "battery") "ui.battery_source" else if (key == "home") "ui.house" else "ui.$key"), Type.CAPTION, ink2, align = RcTextAlign.Center)
            }
        }
        if (!compact && prefs.devices.isNotEmpty()) {
            Column(Modifier.offset(c.u.px(12f), c.u.px(hDp - 20f - 22f * prefs.devices.size.coerceAtMost(4)))) {
                for ((name, entity) in prefs.devices.take(4)) {
                    c.bindEntity(entity)
                    Row(vertical = RcVerticalPositioning.Center) {
                        c.label(name, Type.CAPTION, ink2)
                        Spacer(Modifier.width(c.u.px(6f)))
                        c.label(c.text(V.e(entity, "state")), Type.CAPTION, ink, 600f)
                    }
                }
            }
        }
    }
}