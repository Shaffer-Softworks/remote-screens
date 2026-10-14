@file:Suppress("RestrictedApi")

package dev.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import dev.remotescreens.server.data.homeassistant.EntityState
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.Catalogue
import dev.remotescreens.shared.VarValue
import java.awt.Color
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The card a long press opens: the whole of one entity, on a panel over the page. A card is its own document in the
 * overlay slot; its taps name the entity as `e:<entity_id>`, so they work whichever tile opened it.
 */
object CardRenderer {
    fun render(entityId: String, kind: String?, device: Device, input: RenderInput): RenderedDoc {
        val e = input.states[entityId]
        val spec = Catalogue.of(kind ?: entityId.substringBefore('.'))
        val card = when {
            kind == "history" -> "history"
            kind == "energy" -> "energy"
            else -> spec?.card ?: "toggle"
        }
        val panelW = minOf(device.widthDp - 32f, 620f)
        val panelH = device.heightDp - 40f
        val (bytes, ctx) = DocContext.build(device.widthPx, device.heightPx, e?.name ?: entityId, { input.context(device) }) { ctx ->
            if (card != "energy") ctx.bindEntity(entityId)
            ctx.bindTileColors(TILE, entityId, Pastel.forDomain(e?.domain), alwaysOn = card in setOf("camera", "map", "energy", "history"))
            Box(ctx.tap(Modifier.fillMaxSize().background(ctx.role(Role.SCRIM)), ActionRef("_", "close")),
                horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                Column(ctx.tap(Modifier.size(ctx.u.px(panelW), ctx.u.px(panelH)).clip(ctx.rounded(10f)).background(ctx.role(Role.CARD)), ActionRef("_", "noop"))
                    .padding(ctx.u.px(20f))) {
                    ctx.header(entityId, card)
                    ctx.gap(12f)
                    Column(Modifier.fillMaxWidth().verticalWeight(1f).verticalScroll()) {
                        val body = panelW - 40f
                        when (card) {
                            "light" -> ctx.lightCard(entityId, e, body)
                            "fan" -> ctx.fanCard(entityId, e, body)
                            "cover" -> ctx.coverCard(entityId, e, body)
                            "lock" -> ctx.lockCard(entityId, e)
                            "climate" -> ctx.climateCard(entityId, e, body)
                            "media" -> ctx.mediaCard(entityId, e, body)
                            "weather" -> ctx.weatherCard(entityId, e, body)
                            "history" -> ctx.historyCard(entityId, body, panelH - 140f)
                            "camera" -> ctx.cameraCard(entityId, body, panelH - 120f)
                            "alarm" -> ctx.alarmCard(entityId, e, body)
                            "select" -> ctx.selectCard(entityId, e)
                            "number" -> ctx.numberCard(entityId, e, body)
                            "vacuum" -> ctx.vacuumCard(entityId, e, body)
                            "map" -> ctx.mapCard(entityId, body, panelH - 120f)
                            "energy" -> ctx.energyFlow("card", body, panelH - 120f, compact = false)
                            else -> ctx.toggleCard(entityId, e)
                        }
                    }
                }
            }
        }
        return RenderedDoc(bytes, ctx)
    }

    /** Colours a card reads, under a tile id of its own. */
    const val TILE = "card"

    private fun ref(entityId: String, verb: String, arg: String = "") = ActionRef("e:$entityId", verb, arg)

    private fun DocContext.header(entityId: String, card: String) {
        val ink = role(Role.INK)
        val soft = role(Role.INK_SOFT)
        scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            val c = this@header
            c.key(ActionRef("_", "close"), icon = "close", heightDp = 40f, widthDp = 40f, round = true)
            Spacer(Modifier.width(c.u.px(14f)))
            Column(Modifier.horizontalWeight(1f)) {
                if (card == "energy") {
                    c.label(c.i18n.t("ui.house"), Type.HEADLINE, ink, 600f)
                } else {
                    c.label(c.text(V.e(entityId, "name")), Type.HEADLINE, ink, 600f)
                    c.label(c.text(V.e(entityId, "state")), Type.BODY, soft)
                }
            }
        }
    }

    private fun DocContext.bigToggle(entityId: String, e: EntityState?, verb: String = "toggle") {
        val spec = Catalogue.of(e?.domain ?: "")
        val r = ref(entityId, verb)
        predict(r, entityId)
        scope.Box(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
            val c = this@bigToggle
            Box(c.tap(Modifier.size(c.u.px(132f), c.u.px(132f)).clip(c.rounded((132f) / 2f)).background(c.color(V.t(TILE, "bg"))), r),
                horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                c.toggleIcon(entityId, spec?.icon ?: "power", spec?.iconOn, 64f, c.color(V.t(TILE, "bgink")))
            }
        }
    }

    private fun DocContext.section(title: String) {
        gap(18f)
        label(title, Type.BODY, role(Role.INK_SOFT), 600f)
        gap(8f)
    }

    private fun DocContext.bar(entityId: String, verb: String, width: Float, min: Double = 0.0, max: Double = 100.0, level: String = "level") {
        levelBar(float(V.e(entityId, level)), width, 52f, role(Role.INK), role(Role.KEY), 10) { i ->
            val v = min + (max - min) * i / 10.0
            ref(entityId, verb, if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, v))
        }
    }

    private fun DocContext.toggleCard(entityId: String, e: EntityState?) {
        gap(20f)
        bigToggle(entityId, e)
    }

    private fun DocContext.lightCard(entityId: String, e: EntityState?, w: Float) {
        val modes = e?.list("supported_color_modes").orEmpty()
        bigToggle(entityId, e)
        if (modes.any { it != "onoff" }) {
            section(i18n.t("ui.brightness"))
            bar(entityId, "brightness", w)
        }
        if (modes.any { it in setOf("hs", "rgb", "rgbw", "rgbww", "xy") }) {
            section(i18n.t("ui.colour"))
            swatches(HUES.map { h -> Color.HSBtoRGB(h / 360f, 0.55f, 1f) to ref(entityId, "hue", h.toString()) })
        }
        if ("color_temp" in modes) {
            section(i18n.t("ui.white"))
            swatches(KELVINS.map { k -> kelvinColor(k) to ref(entityId, "kelvin", k.toString()) })
        }
        val effects = e?.list("effect_list").orEmpty()
        if (effects.isNotEmpty()) {
            structural(entityId) { it?.list("effect_list")?.joinToString() ?: "" }
            section(i18n.t("ui.effects"))
            flowKeys(entityId, "effect", effects.take(24).map { it to it }) { it.attr("effect") }
        }
    }

    private fun DocContext.swatches(colors: List<Pair<Int, ActionRef>>) {
        scope.Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.SpaceBetween) {
            val c = this@swatches
            for ((argb, r) in colors) {
                Box(c.tap(Modifier.size(c.u.px(46f), c.u.px(46f)).clip(c.rounded((46f) / 2f)).background(c.fixed(argb or 0xFF000000.toInt())), r))
            }
        }
    }

    /** Choices that wrap onto as many rows as they need. */
    private fun DocContext.flowKeys(entityId: String, verb: String, choices: List<Pair<String, String>>, chosen: (EntityState) -> String?) {
        bindChoices(entityId, verb, entityId, choices.map { it.first }, chosen)
        scope.Flow(Modifier.fillMaxWidth()) {
            val c = this@flowKeys
            choices.forEachIndexed { i, (value, word) ->
                Box(Modifier.padding(0f, 0f, c.u.px(8f), c.u.px(8f))) {
                    c.key(ref(entityId, verb, value), text = word, bg = c.color(V.o(entityId, verb, i, "bg")), ink = c.color(V.o(entityId, verb, i, "ink")), heightDp = 42f)
                }
            }
        }
    }

    private fun DocContext.fanCard(entityId: String, e: EntityState?, w: Float) {
        bigToggle(entityId, e)
        if (e?.supports(1) == true) {
            section(i18n.t("ui.speed"))
            bar(entityId, "percentage", w)
        }
    }

    private fun DocContext.coverCard(entityId: String, e: EntityState?, w: Float) {
        gap(8f)
        scope.Row(Modifier.fillMaxWidth()) {
            val c = this@coverCard
            c.key(ref(entityId, "open"), text = c.i18n.t("ui.open"), icon = "arrow-up", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
            Spacer(Modifier.width(c.u.px(10f)))
            c.key(ref(entityId, "stop"), text = c.i18n.t("ui.stop"), icon = "stop", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
            Spacer(Modifier.width(c.u.px(10f)))
            c.key(ref(entityId, "close"), text = c.i18n.t("ui.close_cover"), icon = "arrow-down", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
        }
        if (e?.supports(4) == true) {
            section(i18n.t("ui.position"))
            bar(entityId, "position", w)
        }
        if (e?.supports(128) == true) {
            bind(entityId) { s -> listOf(VarValue.float(V.e(entityId, "tilt"), ((s[entityId]?.number("current_tilt_position") ?: 0.0) / 100).toFloat())) }
            section(i18n.t("ui.tilt"))
            bar(entityId, "tilt", w, level = "tilt")
        }
    }

    private fun DocContext.lockCard(entityId: String, e: EntityState?) {
        gap(16f)
        bigToggle(entityId, e)
        gap(10f)
        scope.Box(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
            this@lockCard.label(this@lockCard.text(V.t("e:$entityId", "confirm")), Type.TITLE, this@lockCard.role(Role.WARN), 600f, align = RcTextAlign.Center)
        }
        if (e?.supports(1) == true) {
            gap(14f)
            key(ref(entityId, "open"), text = i18n.t("ui.open_door"), icon = "door-open", heightDp = 52f, modifier = Modifier.fillMaxWidth())
        }
    }

    private fun DocContext.climateCard(entityId: String, e: EntityState?, w: Float) {
        val step = e?.number("target_temp_step") ?: if (ha.temperatureUnit.contains("F")) 1.0 else 0.5
        predict(ref(entityId, "temperature", "+$step"), entityId)
        predict(ref(entityId, "temperature", "-$step"), entityId)
        gap(10f)
        scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            val c = this@climateCard
            c.key(ref(entityId, "temperature", "-$step"), icon = "minus", heightDp = 72f, widthDp = 72f, round = true)
            Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
                c.label(c.i18n.t("ui.target"), Type.BODY, c.role(Role.INK_SOFT), align = RcTextAlign.Center)
                c.label(c.text(V.e(entityId, "value")), Type.DISPLAY, c.role(Role.INK), 400f, align = RcTextAlign.Center)
            }
            c.key(ref(entityId, "temperature", "+$step"), icon = "plus", heightDp = 72f, widthDp = 72f, round = true)
        }
        val modes = e?.list("hvac_modes").orEmpty()
        structural(entityId) { listOf("hvac_modes", "preset_modes", "fan_modes").joinToString("|") { k -> it?.list(k)?.joinToString() ?: "" } }
        if (modes.isNotEmpty()) {
            section(i18n.t("ui.mode"))
            flowKeys(entityId, "mode", modes.map { it to i18n.state("climate", it) }) { it.state }
        }
        val presets = e?.list("preset_modes").orEmpty()
        if (presets.isNotEmpty()) {
            section(i18n.t("ui.preset"))
            flowKeys(entityId, "preset", presets.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) }) { it.attr("preset_mode") }
        }
        val fans = e?.list("fan_modes").orEmpty()
        if (fans.isNotEmpty()) {
            section(i18n.t("ui.speed"))
            flowKeys(entityId, "fan", fans.map { it to it.replaceFirstChar(Char::uppercase) }) { it.attr("fan_mode") }
        }
    }

    private fun DocContext.mediaCard(entityId: String, e: EntityState?, w: Float) {
        val art = minOf(w, 280f)
        val px = u.px(art).toInt()
        picture(PictureSlot("art.card.$entityId", "attr:$entityId:entity_picture", px, px, 0))
        bind(entityId) { states ->
            val s = states[entityId]
            listOf(
                VarValue.string(V.e(entityId, "title"), s?.attr("media_title") ?: ""),
                VarValue.string(V.e(entityId, "artist"), listOfNotNull(s?.attr("media_artist"), s?.attr("media_album_name")).joinToString(" · ")),
                VarValue.int(V.e(entityId, "playing"), if (s?.state == "playing") V.VISIBLE else V.GONE),
                VarValue.int(V.e(entityId, "notplaying"), if (s?.state == "playing") V.GONE else V.VISIBLE),
            )
        }
        predict(ref(entityId, "play_pause"), entityId)
        scope.Column(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
            val c = this@mediaCard
            Image(c.image("art.card.$entityId", px, px), Modifier.size(c.u.px(art), c.u.px(art)).clip(c.rounded(20f)), contentScale = RcContentScale.Crop)
            c.gap(14f)
            c.label(c.text(V.e(entityId, "title")), Type.HEADLINE, c.role(Role.INK), 600f, align = RcTextAlign.Center)
            c.label(c.text(V.e(entityId, "artist")), Type.BODY, c.role(Role.INK_SOFT), align = RcTextAlign.Center)
            c.gap(14f)
            Row(horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                c.key(ref(entityId, "previous"), icon = "skip-previous", heightDp = 60f, widthDp = 60f, round = true)
                Spacer(Modifier.width(c.u.px(18f)))
                Box(c.tap(Modifier.size(c.u.px(80f), c.u.px(80f)).clip(c.rounded((80f) / 2f)).background(c.role(Role.KEY_ON)), ref(entityId, "play_pause")),
                    horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                    c.icon("pause", 40f, c.role(Role.KEY_ON_INK), c.int(V.e(entityId, "playing")))
                    c.icon("play", 40f, c.role(Role.KEY_ON_INK), c.int(V.e(entityId, "notplaying")))
                }
                Spacer(Modifier.width(c.u.px(18f)))
                c.key(ref(entityId, "next"), icon = "skip-next", heightDp = 60f, widthDp = 60f, round = true)
            }
        }
        section(i18n.t("ui.volume"))
        bar(entityId, "volume", w)
        val sources = e?.list("source_list").orEmpty()
        if (sources.isNotEmpty()) {
            structural(entityId) { it?.list("source_list")?.joinToString() ?: "" }
            section(i18n.t("ui.sources"))
            flowKeys(entityId, "source", sources.take(16).map { it to it }) { it.attr("source") }
        }
        val library = data.browse[entityId].orEmpty()
        if (library.isNotEmpty()) {
            section(i18n.t("ui.library"))
            for (item in library.filter { it.canPlay }.take(20)) {
                key(ref(entityId, "play_media", "${item.contentType}|${item.contentId}"), text = item.title, icon = "play-circle-outline", heightDp = 48f, modifier = Modifier.fillMaxWidth())
                gap(6f)
            }
        }
    }

    private fun DocContext.weatherCard(entityId: String, e: EntityState?, width: Float) {
        structural(entityId) { it?.state ?: "" }
        refreshEvery(1800)
        val ink = role(Role.INK)
        val soft = role(Role.INK_SOFT)
        val zone = runCatching { ZoneId.of(ha.timeZone) }.getOrDefault(ZoneId.of("UTC"))
        scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            val c = this@weatherCard
            c.icon(Present.weatherIcon(e?.state ?: ""), 72f, ink)
            Spacer(Modifier.width(c.u.px(16f)))
            Column {
                c.label(c.text(V.e(entityId, "value")), Type.DISPLAY, ink, 400f)
                val details = listOfNotNull(
                    e?.number("humidity")?.let { c.i18n.t("ui.humidity", "value" to c.i18n.percent(it.roundToInt())) },
                    e?.number("wind_speed")?.let { c.i18n.t("ui.wind", "value" to "${it.roundToInt()} ${e.attr("wind_speed_unit") ?: "km/h"}") },
                ).joinToString(" · ")
                c.label(details, Type.BODY, soft)
            }
        }
        gap(16f)
        val days = data.forecasts[entityId].orEmpty()
        val lo = days.mapNotNull { it.low }.minOrNull() ?: 0.0
        val hi = days.mapNotNull { it.high }.maxOrNull() ?: 1.0
        for (d in days.take(7)) {
            scope.Row(Modifier.fillMaxWidth().padding(0f, u.px(6f), 0f, u.px(6f)), vertical = RcVerticalPositioning.Center) {
                val c = this@weatherCard
                c.label(c.i18n.weekday(d.time.withZoneSameInstant(zone)), Type.TITLE, ink, 500f, modifier = Modifier.width(c.u.px(60f)))
                c.icon(Present.weatherIcon(d.condition), 28f, ink)
                Spacer(Modifier.width(c.u.px(10f)))
                c.label(d.rain?.let { "$it%" } ?: "", Type.BODY, c.role(Role.ACCENT), modifier = Modifier.width(c.u.px(48f)))
                c.label(d.low?.let { "${it.roundToInt()}°" } ?: "", Type.BODY, soft, modifier = Modifier.width(c.u.px(40f)))
                val span = (hi - lo).coerceAtLeast(1.0)
                val start = (((d.low ?: lo) - lo) / span).toFloat().coerceIn(0f, 1f)
                val end = (((d.high ?: hi) - lo) / span).toFloat().coerceIn(start, 1f)
                val barW = c.u.px((width - 60f - 28f - 10f - 48f - 40f - 40f - 12f).coerceAtLeast(40f))
                val barH = c.u.px(6f)
                Spacer(Modifier.width(c.u.px(6f)))
                Canvas(Modifier.size(barW, barH)) {
                    paint { color(c.role(Role.DIVIDER)); style(RcPaintStyle.Fill) }
                    drawRoundRect(0f, 0f, barW, barH, barH / 2f, barH / 2f)
                    paint { color(c.role(Role.WARN)) }
                    val l = start * barW
                    drawRoundRect(l, 0f, maxOf(end * barW, l + barH), barH, barH / 2f, barH / 2f)
                }
                Spacer(Modifier.width(c.u.px(6f)))
                c.label(d.high?.let { "${it.roundToInt()}°" } ?: "", Type.BODY, ink, 600f, modifier = Modifier.width(c.u.px(40f)))
            }
        }
    }

    private fun DocContext.historyCard(entityId: String, w: Float, h: Float) {
        refreshEvery(300)
        val points = data.history[entityId].orEmpty()
        val e = states[entityId]
        if (points.isNotEmpty() && e != null) {
            val max = points.maxBy { it.second }
            val min = points.minBy { it.second }
            val unit = e.attr("unit_of_measurement").orEmpty()
            scope.Row(Modifier.fillMaxWidth()) {
                val c = this@historyCard
                c.label(c.i18n.t("ui.max", "value" to "${c.i18n.number(max.second, 1)} $unit"), Type.BODY, c.role(Role.DANGER), 500f, modifier = Modifier.horizontalWeight(1f))
                c.label(c.i18n.t("ui.min", "value" to "${c.i18n.number(min.second, 1)} $unit"), Type.BODY, c.role(Role.ACCENT), 500f)
            }
            gap(10f)
        }
        graph(points, w, (h - 60f).coerceAtLeast(80f), role(Role.ACCENT), role(Role.INK_SOFT))
    }

    private fun DocContext.cameraCard(entityId: String, w: Float, h: Float) {
        val pw = u.px(w).toInt()
        val ph = u.px(h).toInt()
        picture(PictureSlot("cam.card.$entityId", "/api/camera_proxy/$entityId", pw, ph, 4, crop = false))
        scope.Image(image("cam.card.$entityId", pw, ph), Modifier.size(u.px(w), u.px(h)).clip(rounded(16f)), contentScale = RcContentScale.Fit)
    }

    private fun DocContext.alarmCard(entityId: String, e: EntityState?, w: Float) {
        val features = e?.features() ?: 0
        val modes = listOfNotNull(
            "arm_home".takeIf { features and 1 != 0 }, "arm_away".takeIf { features and 2 != 0 },
            "arm_night".takeIf { features and 4 != 0 }, "arm_vacation".takeIf { features and 32 != 0 },
        )
        bind(entityId) { states ->
            listOf(VarValue.string(V.t("e:$entityId", "code"), ""))
        }
        gap(8f)
        scope.Flow(Modifier.fillMaxWidth()) {
            val c = this@alarmCard
            for (m in modes + "disarm") {
                Box(Modifier.padding(0f, 0f, c.u.px(8f), c.u.px(8f))) {
                    c.key(ref(entityId, m), text = c.i18n.state("alarm_control_panel", if (m == "disarm") "disarmed" else m.replace("arm_", "armed_")),
                        icon = if (m == "disarm") "shield-off" else "shield-lock", heightDp = 52f)
                }
            }
        }
        if (e?.attr("code_format") != null) {
            section(i18n.t("ui.code"))
            keypad(entityId, w)
        }
    }

    private fun DocContext.keypad(entityId: String, w: Float) {
        scope.Box(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
            this@keypad.label(this@keypad.text(V.t("e:$entityId", "code")), Type.DISPLAY, this@keypad.role(Role.INK), 500f, align = RcTextAlign.Center)
        }
        gap(8f)
        val keyW = minOf((w - 24f) / 3f, 110f)
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("clear", "0", "back"))) {
            scope.Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.Center) {
                val c = this@keypad
                row.forEachIndexed { i, k ->
                    if (i > 0) Spacer(Modifier.width(c.u.px(12f)))
                    when (k) {
                        "clear" -> c.key(ref(entityId, "code_clear"), icon = "close", heightDp = 60f, widthDp = keyW)
                        "back" -> c.key(ref(entityId, "code_back"), icon = "backspace-outline", heightDp = 60f, widthDp = keyW)
                        else -> c.key(ref(entityId, "code_digit", k), text = k, heightDp = 60f, widthDp = keyW)
                    }
                }
            }
            gap(10f)
        }
    }

    private fun DocContext.selectCard(entityId: String, e: EntityState?) {
        val options = e?.list("options").orEmpty()
        structural(entityId) { it?.list("options")?.joinToString() ?: "" }
        gap(8f)
        bindChoices(entityId, "option", entityId, options) { it.state }
        options.forEachIndexed { i, o ->
            key(ref(entityId, "option", o), text = o, heightDp = 52f, modifier = Modifier.fillMaxWidth(),
                bg = color(V.o(entityId, "option", i, "bg")), ink = color(V.o(entityId, "option", i, "ink")))
            gap(8f)
        }
    }

    private fun DocContext.numberCard(entityId: String, e: EntityState?, w: Float) {
        val min = e?.number("min") ?: 0.0
        val max = e?.number("max") ?: 100.0
        val step = e?.number("step") ?: 1.0
        gap(10f)
        scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            val c = this@numberCard
            c.key(ref(entityId, "value", "-$step"), icon = "minus", heightDp = 64f, widthDp = 64f, round = true)
            Box(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
                c.label(c.text(V.e(entityId, "state")), Type.DISPLAY, c.role(Role.INK), 400f, align = RcTextAlign.Center)
            }
            c.key(ref(entityId, "value", "+$step"), icon = "plus", heightDp = 64f, widthDp = 64f, round = true)
        }
        gap(16f)
        bar(entityId, "value", w, min, max)
    }

    private fun DocContext.vacuumCard(entityId: String, e: EntityState?, w: Float) {
        gap(8f)
        scope.Row(Modifier.fillMaxWidth()) {
            val c = this@vacuumCard
            c.key(ref(entityId, "start"), text = c.i18n.t("ui.start"), icon = "play", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
            Spacer(Modifier.width(c.u.px(10f)))
            c.key(ref(entityId, "pause"), text = c.i18n.t("ui.pause"), icon = "pause", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
            Spacer(Modifier.width(c.u.px(10f)))
            c.key(ref(entityId, "dock"), text = c.i18n.t("ui.dock"), icon = "home-import-outline", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
        }
        gap(10f)
        key(ref(entityId, "locate"), text = i18n.t("ui.locate"), icon = "map-marker", heightDp = 48f, modifier = Modifier.fillMaxWidth())
        val speeds = e?.list("fan_speed_list").orEmpty()
        if (speeds.isNotEmpty()) {
            structural(entityId) { it?.list("fan_speed_list")?.joinToString() ?: "" }
            section(i18n.t("ui.suction"))
            flowKeys(entityId, "fan_speed", speeds.map { it to it }) { it.attr("fan_speed") }
        }
    }

    private fun DocContext.mapCard(entityId: String, w: Float, h: Float) {
        val pw = u.px(w).toInt()
        val ph = u.px(h).toInt()
        val who = if (entityId.startsWith("person.") || entityId.startsWith("device_tracker.")) listOf(entityId) else states.keys.filter { it.startsWith("person.") }
        for (p in who) structural(p) { s -> "${s?.number("latitude")},${s?.number("longitude")}" }
        picture(PictureSlot("map.card", "map:${mapStyle()}:${who.joinToString(",")}", pw, ph, 0))
        scope.Image(image("map.card", pw, ph), Modifier.size(u.px(w), u.px(h)).clip(rounded(16f)), contentScale = RcContentScale.Crop)
        label(i18n.t("ui.map_credit"), Type.CAPTION, role(Role.INK_SOFT))
    }

    val HUES = listOf(0, 30, 50, 120, 180, 220, 270, 320)
    val KELVINS = listOf(2200, 2700, 3500, 4500, 5500, 6500)

    /** The colour of white light at [k] kelvin, near enough for a swatch. */
    fun kelvinColor(k: Int): Int {
        val t = ((k - 2200) / 4300f).coerceIn(0f, 1f)
        val r = 255
        val g = (190 + 60 * t).roundToInt()
        val b = (120 + 135 * t).roundToInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}