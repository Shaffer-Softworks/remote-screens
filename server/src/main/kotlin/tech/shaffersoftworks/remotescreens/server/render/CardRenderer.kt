@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
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
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.awt.Color
import java.time.ZoneId
import kotlin.math.roundToInt


private const val HUE_SATURATION = 0.55f
private const val CAMERA_RESERVE_DP = 120f
private const val TOGGLE_DP = 132f
private const val HISTORY_RESERVE_DP = 140f
private const val HUE_DEGREES = 360f
private const val PANEL_CORNER_DP = 10f
private const val SECTION_GAP_DP = 12f
private const val HEADER_GAP_DP = 14f
private const val CARD_GAP_DP = 16f
private const val CAMERA_SPINNER_DP = 48f
private const val SECTION_TITLE_GAP_DP = 18f
private const val PANEL_PAD_DP = 20f
private const val WEATHER_ICON_ROW_DP = 28f
private const val CLOSE_KEY_DP = 40f
private const val SWATCH_DP = 46f
private const val FLOW_KEY_H_DP = 48f
private const val LEVEL_BAR_H_DP = 52f
private const val MEDIA_CTRL_DP = 60f
private const val TOGGLE_ICON_DP = 64f
private const val STEPPER_KEY_DP = 72f
private const val PLAY_KEY_DP = 80f
private const val ROW_GAP_DP = 6f
private const val FLOW_PAD_DP = 8f
private const val PANEL_MARGIN_DP = 32f
private const val PANEL_MAX_WIDTH_DP = 620f
private const val LEVEL_STEPS = 10
private const val LEVEL_DEFAULT_MAX = 100.0
private const val FAN_PERCENTAGE_FLAG = 1
private const val COVER_POSITION_FLAG = 4
private const val COVER_TILT_FLAG = 128
private const val WEATHER_REFRESH_S = 1800
private const val HISTORY_REFRESH_S = 300
/** Opaque alpha in the high byte; not a theme colour (kept out of Themes.kt hex inventory). */
private const val OPAQUE_ALPHA_BYTE = 0xFF
private const val OPAQUE_ARGB = OPAQUE_ALPHA_BYTE shl 24
private const val ALPHA_OPAQUE = 0xFF
private const val ALPHA_SHIFT = 24
private const val KELVIN_MIN = 2200
private const val KELVIN_SPAN = 4300f
private const val KELVIN_GREEN_BASE = 190
private const val KELVIN_GREEN_RANGE = 60
private const val KELVIN_BLUE_BASE = 120
private const val KELVIN_BLUE_RANGE = 135
private const val EFFECTS_TAKE = 24
private const val SOURCES_TAKE = 16
private const val LIBRARY_TAKE = 20
private const val FORECAST_DAYS = 7

/**
 * The card a long press opens: the whole of one entity, on a panel over the page. A card is its own document in the
 * overlay slot; its taps name the entity as `e:<entity_id>`, so they work whichever tile opened it.
 */
object CardRenderer {
    fun render(entityId: String, kind: String?, device: Device, input: RenderInput): RenderedDoc {
        val entityState = input.states[entityId]
        val spec = Catalogue.of(kind ?: entityId.substringBefore('.'))
        val card = when {
            kind == "history" -> "history"
            kind == "energy" -> "energy"
            else -> spec?.card ?: "toggle"
        }
        val panelW = minOf(device.widthDp - PANEL_MARGIN_DP, PANEL_MAX_WIDTH_DP)
        val panelH = device.heightDp - CLOSE_KEY_DP
        val (bytes, docContext) = DocContext.build(device.widthPx, device.heightPx, entityState?.name ?: entityId, { input.context(device) }) { docContext ->
            if (card != "energy") docContext.bindEntity(entityId)
            docContext.bindTileColors(TILE, entityId, Pastel.forDomain(entityState?.domain), alwaysOn = card in setOf("camera", "map", "energy", "history"))
            Box(docContext.tap(Modifier.fillMaxSize().background(docContext.role(Role.SCRIM)), ActionRef("_", "close")),
                horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                Column(docContext.tap(Modifier.size(docContext.units.px(panelW), docContext.units.px(panelH)).clip(docContext.rounded(PANEL_CORNER_DP)).background(docContext.role(Role.CARD)), ActionRef("_", "noop"))
                    .padding(docContext.units.px(PANEL_PAD_DP))) {
                    docContext.header(entityId, card)
                    docContext.gap(SECTION_GAP_DP)
                    Column(Modifier.fillMaxWidth().verticalWeight(1f).verticalScroll()) {
                        val body = panelW - CLOSE_KEY_DP
                        when (card) {
                            "light" -> docContext.lightCard(entityId, entityState, body)
                            "fan" -> docContext.fanCard(entityId, entityState, body)
                            "cover" -> docContext.coverCard(entityId, entityState, body)
                            "lock" -> docContext.lockCard(entityId, entityState)
                            "climate" -> docContext.climateCard(entityId, entityState)
                            "media" -> docContext.mediaCard(entityId, entityState, body)
                            "weather" -> docContext.weatherCard(entityId, entityState, body)
                            "history" -> docContext.historyCard(entityId, body, panelH - HISTORY_RESERVE_DP)
                            "camera" -> docContext.cameraCard(entityId, body, panelH - CAMERA_RESERVE_DP)
                            "alarm" -> docContext.alarmCard(entityId, entityState, body)
                            "select" -> docContext.selectCard(entityId, entityState)
                            "number" -> docContext.numberCard(entityId, entityState, body)
                            "vacuum" -> docContext.vacuumCard(entityId, entityState)
                            "map" -> docContext.mapCard(entityId, body, panelH - CAMERA_RESERVE_DP)
                            "energy" -> docContext.energyFlow(body, panelH - CAMERA_RESERVE_DP, compact = false)
                            else -> docContext.toggleCard(entityId, entityState)
                        }
                    }
                }
            }
        }
        return RenderedDoc(bytes, docContext)
    }

    /** Colours a card reads, under a tile id of its own. */
    const val TILE = "card"

    private fun ref(entityId: String, verb: String, arg: String = "") = ActionRef("e:$entityId", verb, arg)

    private fun DocContext.header(entityId: String, card: String) {
        val ink = role(Role.INK)
        val soft = role(Role.INK_SOFT)
        scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            val doc = this@header
            doc.key(ActionRef("_", "close"), icon = "close", heightDp = CLOSE_KEY_DP, widthDp = CLOSE_KEY_DP, round = true)
            Spacer(Modifier.width(doc.units.px(HEADER_GAP_DP)))
            Column(Modifier.horizontalWeight(1f)) {
                if (card == "energy") {
                    doc.label(doc.i18n.t("ui.house"), Type.HEADLINE, ink, Weight.SEMI_BOLD)
                } else {
                    doc.label(doc.text(Vars.entity(entityId, Vars.KEY_NAME)), Type.HEADLINE, ink, Weight.SEMI_BOLD)
                    doc.label(doc.text(Vars.entity(entityId, Vars.KEY_STATE)), Type.BODY, soft)
                }
            }
        }
    }

    private fun DocContext.bigToggle(entityId: String, entityState: EntityState?, verb: String = "toggle") {
        val spec = Catalogue.of(entityState?.domain ?: "")
        val r = ref(entityId, verb)
        predict(r, entityId)
        scope.Box(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
            val doc = this@bigToggle
            Box(doc.tap(Modifier.size(doc.units.px(TOGGLE_DP), doc.units.px(TOGGLE_DP)).clip(doc.rounded((TOGGLE_DP) / 2f)).background(doc.color(Vars.tile(TILE, Vars.KEY_BG))), r),
                horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                doc.toggleIcon(entityId, spec?.icon ?: "power", spec?.iconOn, TOGGLE_ICON_DP, doc.color(Vars.tile(TILE, Vars.KEY_BG_INK)))
            }
        }
    }

    private fun DocContext.section(title: String) {
        gap(SECTION_TITLE_GAP_DP)
        label(title, Type.BODY, role(Role.INK_SOFT), Weight.SEMI_BOLD)
        gap(FLOW_PAD_DP)
    }

    private fun DocContext.bar(entityId: String, verb: String, width: Float, min: Double = 0.0, max: Double = LEVEL_DEFAULT_MAX, level: String = Vars.KEY_LEVEL) {
        levelBar(float(Vars.entity(entityId, level)), width, LEVEL_BAR_H_DP, role(Role.INK), role(Role.KEY), LEVEL_STEPS) { i ->
            val v = min + (max - min) * i / LEVEL_STEPS.toDouble()
            ref(entityId, verb, if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, v))
        }
    }

    private fun DocContext.toggleCard(entityId: String, entityState: EntityState?) {
        gap(PANEL_PAD_DP)
        bigToggle(entityId, entityState)
    }

    private fun DocContext.lightCard(entityId: String, entityState: EntityState?, w: Float) {
        val modes = entityState?.list("supported_color_modes").orEmpty()
        bigToggle(entityId, entityState)
        if (modes.any { it != "onoff" }) {
            section(i18n.t("ui.brightness"))
            bar(entityId, "brightness", w)
        }
        if (modes.any { it in setOf("hs", "rgb", "rgbw", "rgbww", "xy") }) {
            section(i18n.t("ui.colour"))
            swatches(HUES.map { h -> Color.HSBtoRGB(h / HUE_DEGREES, HUE_SATURATION, 1f) to ref(entityId, "hue", h.toString()) })
        }
        if ("color_temp" in modes) {
            section(i18n.t("ui.white"))
            swatches(KELVINS.map { kelvin -> kelvinColor(kelvin) to ref(entityId, "kelvin", kelvin.toString()) })
        }
        val effects = entityState?.list("effect_list").orEmpty()
        if (effects.isNotEmpty()) {
            structural(entityId) { it?.list("effect_list")?.joinToString() ?: "" }
            section(i18n.t("ui.effects"))
            flowKeys(entityId, "effect", effects.take(EFFECTS_TAKE).map { it to it }) { it.attr("effect") }
        }
    }

    private fun DocContext.swatches(colors: List<Pair<Int, ActionRef>>) {
        scope.Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.SpaceBetween) {
            val doc = this@swatches
            for ((argb, r) in colors) {
                Box(doc.tap(Modifier.size(doc.units.px(SWATCH_DP), doc.units.px(SWATCH_DP)).clip(doc.rounded((SWATCH_DP) / 2f)).background(doc.fixed(argb or OPAQUE_ARGB.toInt())), r))
            }
        }
    }

    /** Choices that wrap onto as many rows as they need. */
    private fun DocContext.flowKeys(entityId: String, verb: String, choices: List<Pair<String, String>>, chosen: (EntityState) -> String?) {
        bindChoices(entityId, verb, entityId, choices.map { it.first }, chosen)
        scope.Flow(Modifier.fillMaxWidth()) {
            val doc = this@flowKeys
            choices.forEachIndexed { i, (value, word) ->
                Box(Modifier.padding(0f, 0f, doc.units.px(FLOW_PAD_DP), doc.units.px(FLOW_PAD_DP))) {
                    doc.key(ref(entityId, verb, value), text = word, bg = doc.color(Vars.option(entityId, verb, i, Vars.KEY_BG)), ink = doc.color(Vars.option(entityId, verb, i, Vars.KEY_INK)), heightDp = 42f)
                }
            }
        }
    }

    private fun DocContext.fanCard(entityId: String, entityState: EntityState?, w: Float) {
        bigToggle(entityId, entityState)
        if (entityState?.supports(FAN_PERCENTAGE_FLAG) == true) {
            section(i18n.t("ui.speed"))
            bar(entityId, "percentage", w)
        }
    }

    private fun DocContext.coverCard(entityId: String, entityState: EntityState?, w: Float) {
        gap(FLOW_PAD_DP)
        scope.Row(Modifier.fillMaxWidth()) {
            val doc = this@coverCard
            doc.key(ref(entityId, "open"), text = doc.i18n.t("ui.open"), icon = "arrow-up", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
            Spacer(Modifier.width(doc.units.px(PANEL_CORNER_DP)))
            doc.key(ref(entityId, "stop"), text = doc.i18n.t("ui.stop"), icon = "stop", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
            Spacer(Modifier.width(doc.units.px(PANEL_CORNER_DP)))
            doc.key(ref(entityId, "close"), text = doc.i18n.t("ui.close_cover"), icon = "arrow-down", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
        }
        if (entityState?.supports(COVER_POSITION_FLAG) == true) {
            section(i18n.t("ui.position"))
            bar(entityId, "position", w)
        }
        if (entityState?.supports(COVER_TILT_FLAG) == true) {
            bind(entityId) { s -> listOf(VarValue.float(Vars.entity(entityId, "tilt"), ((s[entityId]?.number("current_tilt_position") ?: 0.0) / 100).toFloat())) }
            section(i18n.t("ui.tilt"))
            bar(entityId, "tilt", w, level = "tilt")
        }
    }

    private fun DocContext.lockCard(entityId: String, entityState: EntityState?) {
        gap(CARD_GAP_DP)
        bigToggle(entityId, entityState)
        gap(PANEL_CORNER_DP)
        scope.Box(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
            this@lockCard.label(this@lockCard.text(Vars.tile("e:$entityId", Vars.KEY_CONFIRM)), Type.TITLE, this@lockCard.role(Role.WARN), Weight.SEMI_BOLD, align = RcTextAlign.Center)
        }
        if (entityState?.supports(FAN_PERCENTAGE_FLAG) == true) {
            gap(HEADER_GAP_DP)
            key(ref(entityId, "open"), text = i18n.t("ui.open_door"), icon = "door-open", heightDp = LEVEL_BAR_H_DP, modifier = Modifier.fillMaxWidth())
        }
    }

    private fun DocContext.climateCard(entityId: String, entityState: EntityState?) {
        val step = entityState?.number("target_temp_step") ?: if (homeAssistantConfig.temperatureUnit.contains("F")) 1.0 else 0.5
        predict(ref(entityId, "temperature", "+$step"), entityId)
        predict(ref(entityId, "temperature", "-$step"), entityId)
        gap(PANEL_CORNER_DP)
        scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            val doc = this@climateCard
            doc.key(ref(entityId, "temperature", "-$step"), icon = "minus", heightDp = STEPPER_KEY_DP, widthDp = STEPPER_KEY_DP, round = true)
            Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
                doc.label(doc.i18n.t("ui.target"), Type.BODY, doc.role(Role.INK_SOFT), align = RcTextAlign.Center)
                doc.label(doc.text(Vars.entity(entityId, Vars.KEY_VALUE)), Type.DISPLAY, doc.role(Role.INK), Weight.NORMAL, align = RcTextAlign.Center)
            }
            doc.key(ref(entityId, "temperature", "+$step"), icon = "plus", heightDp = STEPPER_KEY_DP, widthDp = STEPPER_KEY_DP, round = true)
        }
        val modes = entityState?.list("hvac_modes").orEmpty()
        structural(entityId) { listOf("hvac_modes", "preset_modes", "fan_modes").joinToString("|") { key -> it?.list(key)?.joinToString() ?: "" } }
        if (modes.isNotEmpty()) {
            section(i18n.t("ui.mode"))
            flowKeys(entityId, "mode", modes.map { it to i18n.state("climate", it) }) { it.state }
        }
        val presets = entityState?.list("preset_modes").orEmpty()
        if (presets.isNotEmpty()) {
            section(i18n.t("ui.preset"))
            flowKeys(entityId, "preset", presets.map { it to it.replace('_', ' ').replaceFirstChar(Char::uppercase) }) { it.attr("preset_mode") }
        }
        val fans = entityState?.list("fan_modes").orEmpty()
        if (fans.isNotEmpty()) {
            section(i18n.t("ui.speed"))
            flowKeys(entityId, "fan", fans.map { it to it.replaceFirstChar(Char::uppercase) }) { it.attr("fan_mode") }
        }
    }

    private fun DocContext.mediaCard(entityId: String, entityState: EntityState?, w: Float) {
        val art = minOf(w, 280f)
        val px = units.px(art).toInt()
        picture(PictureSlot("art.card.$entityId", "attr:$entityId:entity_picture", px, px, 0))
        bind(entityId) { states ->
            val s = states[entityId]
            listOf(
                VarValue.string(Vars.entity(entityId, "title"), s?.attr("media_title") ?: ""),
                VarValue.string(Vars.entity(entityId, "artist"), listOfNotNull(s?.attr("media_artist"), s?.attr("media_album_name")).joinToString(" · ")),
                VarValue.int(Vars.entity(entityId, "playing"), if (s?.state == "playing") Vars.VISIBLE else Vars.GONE),
                VarValue.int(Vars.entity(entityId, "notplaying"), if (s?.state == "playing") Vars.GONE else Vars.VISIBLE),
            )
        }
        predict(ref(entityId, "play_pause"), entityId)
        scope.Column(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
            val doc = this@mediaCard
            Image(doc.image("art.card.$entityId", px, px), Modifier.size(doc.units.px(art), doc.units.px(art)).clip(doc.rounded(PANEL_PAD_DP)), contentScale = RcContentScale.Crop)
            doc.gap(HEADER_GAP_DP)
            doc.label(doc.text(Vars.entity(entityId, "title")), Type.HEADLINE, doc.role(Role.INK), Weight.SEMI_BOLD, align = RcTextAlign.Center)
            doc.label(doc.text(Vars.entity(entityId, "artist")), Type.BODY, doc.role(Role.INK_SOFT), align = RcTextAlign.Center)
            doc.gap(HEADER_GAP_DP)
            Row(horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                doc.key(ref(entityId, "previous"), icon = "skip-previous", heightDp = MEDIA_CTRL_DP, widthDp = MEDIA_CTRL_DP, round = true)
                Spacer(Modifier.width(doc.units.px(SECTION_TITLE_GAP_DP)))
                Box(doc.tap(Modifier.size(doc.units.px(PLAY_KEY_DP), doc.units.px(PLAY_KEY_DP)).clip(doc.rounded((PLAY_KEY_DP) / 2f)).background(doc.role(Role.KEY_ON)), ref(entityId, "play_pause")),
                    horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                    doc.icon("pause", CLOSE_KEY_DP, doc.role(Role.KEY_ON_INK), doc.int(Vars.entity(entityId, "playing")))
                    doc.icon("play", CLOSE_KEY_DP, doc.role(Role.KEY_ON_INK), doc.int(Vars.entity(entityId, "notplaying")))
                }
                Spacer(Modifier.width(doc.units.px(SECTION_TITLE_GAP_DP)))
                doc.key(ref(entityId, "next"), icon = "skip-next", heightDp = MEDIA_CTRL_DP, widthDp = MEDIA_CTRL_DP, round = true)
            }
        }
        section(i18n.t("ui.volume"))
        bar(entityId, "volume", w)
        val sources = entityState?.list("source_list").orEmpty()
        if (sources.isNotEmpty()) {
            structural(entityId) { it?.list("source_list")?.joinToString() ?: "" }
            section(i18n.t("ui.sources"))
            flowKeys(entityId, "source", sources.take(SOURCES_TAKE).map { it to it }) { it.attr("source") }
        }
        val library = data.browse[entityId].orEmpty()
        if (library.isNotEmpty()) {
            section(i18n.t("ui.library"))
            for (item in library.filter { it.canPlay }.take(LIBRARY_TAKE)) {
                key(ref(entityId, "play_media", "${item.contentType}|${item.contentId}"), text = item.title, icon = "play-circle-outline", heightDp = FLOW_KEY_H_DP, modifier = Modifier.fillMaxWidth())
                gap(ROW_GAP_DP)
            }
        }
    }

    private fun DocContext.weatherCard(entityId: String, entityState: EntityState?, width: Float) {
        structural(entityId) { it?.state ?: "" }
        refreshEvery(WEATHER_REFRESH_S)
        val ink = role(Role.INK)
        val soft = role(Role.INK_SOFT)
        val zone = runCatching { ZoneId.of(homeAssistantConfig.timeZone) }.getOrDefault(ZoneId.of("UTC"))
        scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            val doc = this@weatherCard
            doc.icon(Present.weatherIcon(entityState?.state ?: ""), STEPPER_KEY_DP, ink)
            Spacer(Modifier.width(doc.units.px(CARD_GAP_DP)))
            Column {
                doc.label(doc.text(Vars.entity(entityId, Vars.KEY_VALUE)), Type.DISPLAY, ink, Weight.NORMAL)
                val details = listOfNotNull(
                    entityState?.number("humidity")?.let { doc.i18n.t("ui.humidity", "value" to doc.i18n.percent(it.roundToInt())) },
                    entityState?.number("wind_speed")?.let { doc.i18n.t("ui.wind", "value" to "${it.roundToInt()} ${entityState.attr("wind_speed_unit") ?: "km/h"}") },
                ).joinToString(" · ")
                doc.label(details, Type.BODY, soft)
            }
        }
        gap(CARD_GAP_DP)
        val days = data.forecasts[entityId].orEmpty()
        val lo = days.mapNotNull { it.low }.minOrNull() ?: 0.0
        val hi = days.mapNotNull { it.high }.maxOrNull() ?: 1.0
        for (d in days.take(FORECAST_DAYS)) {
            scope.Row(Modifier.fillMaxWidth().padding(0f, units.px(ROW_GAP_DP), 0f, units.px(ROW_GAP_DP)), vertical = RcVerticalPositioning.Center) {
                val doc = this@weatherCard
                doc.label(doc.i18n.weekday(d.time.withZoneSameInstant(zone)), Type.TITLE, ink, Weight.MEDIUM, modifier = Modifier.width(doc.units.px(MEDIA_CTRL_DP)))
                doc.icon(Present.weatherIcon(d.condition), WEATHER_ICON_ROW_DP, ink)
                Spacer(Modifier.width(doc.units.px(PANEL_CORNER_DP)))
                doc.label(d.rain?.let { "$it%" } ?: "", Type.BODY, doc.role(Role.ACCENT), modifier = Modifier.width(doc.units.px(FLOW_KEY_H_DP)))
                doc.label(d.low?.let { "${it.roundToInt()}°" } ?: "", Type.BODY, soft, modifier = Modifier.width(doc.units.px(CLOSE_KEY_DP)))
                val span = (hi - lo).coerceAtLeast(1.0)
                val start = (((d.low ?: lo) - lo) / span).toFloat().coerceIn(0f, 1f)
                val end = (((d.high ?: hi) - lo) / span).toFloat().coerceIn(start, 1f)
                val barW = doc.units.px((width - MEDIA_CTRL_DP - WEATHER_ICON_ROW_DP - PANEL_CORNER_DP - FLOW_KEY_H_DP - CLOSE_KEY_DP - CLOSE_KEY_DP - SECTION_GAP_DP).coerceAtLeast(CLOSE_KEY_DP))
                val barH = doc.units.px(ROW_GAP_DP)
                Spacer(Modifier.width(doc.units.px(ROW_GAP_DP)))
                Canvas(Modifier.size(barW, barH)) {
                    paint { color(doc.role(Role.DIVIDER)); style(RcPaintStyle.Fill) }
                    drawRoundRect(0f, 0f, barW, barH, barH / 2f, barH / 2f)
                    paint { color(doc.role(Role.WARN)) }
                    val l = start * barW
                    drawRoundRect(l, 0f, maxOf(end * barW, l + barH), barH, barH / 2f, barH / 2f)
                }
                Spacer(Modifier.width(doc.units.px(ROW_GAP_DP)))
                doc.label(d.high?.let { "${it.roundToInt()}°" } ?: "", Type.BODY, ink, Weight.SEMI_BOLD, modifier = Modifier.width(doc.units.px(CLOSE_KEY_DP)))
            }
        }
    }

    private fun DocContext.historyCard(entityId: String, w: Float, h: Float) {
        refreshEvery(HISTORY_REFRESH_S)
        val points = data.history[entityId].orEmpty()
        val entityState = states[entityId]
        if (points.isNotEmpty() && entityState != null) {
            val max = points.maxBy { it.second }
            val min = points.minBy { it.second }
            val unit = entityState.attr("unit_of_measurement").orEmpty()
            scope.Row(Modifier.fillMaxWidth()) {
                val doc = this@historyCard
                doc.label(doc.i18n.t("ui.max", "value" to "${doc.i18n.number(max.second, 1)} $unit"), Type.BODY, doc.role(Role.DANGER), Weight.MEDIUM, modifier = Modifier.horizontalWeight(1f))
                doc.label(doc.i18n.t("ui.min", "value" to "${doc.i18n.number(min.second, 1)} $unit"), Type.BODY, doc.role(Role.ACCENT), Weight.MEDIUM)
            }
            gap(PANEL_CORNER_DP)
        }
        graph(points, w, (h - MEDIA_CTRL_DP).coerceAtLeast(PLAY_KEY_DP), role(Role.ACCENT), role(Role.INK_SOFT))
    }

    private fun DocContext.cameraCard(entityId: String, w: Float, h: Float) {
        val pw = units.px(w).toInt()
        val ph = units.px(h).toInt()
        picture(PictureSlot("cam.card.$entityId", "/api/camera_proxy/$entityId", pw, ph, 4, crop = false))
        val video = liveVideo(entityId, crop = false)
        val waiting = int(Vars.entity(entityId, Vars.KEY_CAM_WAITING), Vars.VISIBLE)
        val still = int(Vars.entity(entityId, Vars.KEY_CAM_STILL), Vars.GONE)
        val frame = Modifier.size(units.px(w), units.px(h))
            .clip(rounded(CARD_GAP_DP))
            .background(role(Role.TRACK))
            .componentId(video)
        scope.Box(frame, horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
            val doc = this@cameraCard
            Image(
                doc.image("cam.card.$entityId", pw, ph),
                Modifier.fillMaxSize().visibility(still),
                contentScale = RcContentScale.Fit,
            )
            doc.spinner(CAMERA_SPINNER_DP, doc.role(Role.ACCENT), waiting)
        }
    }

    private fun DocContext.alarmCard(entityId: String, entityState: EntityState?, w: Float) {
        val features = entityState?.features() ?: 0
        val modes = listOfNotNull(
            "arm_home".takeIf { features and 1 != 0 }, "arm_away".takeIf { features and 2 != 0 },
            "arm_night".takeIf { features and 4 != 0 }, "arm_vacation".takeIf { features and 32 != 0 },
        )
        bind(entityId) { states ->
            listOf(VarValue.string(Vars.tile("e:$entityId", "code"), ""))
        }
        gap(FLOW_PAD_DP)
        scope.Flow(Modifier.fillMaxWidth()) {
            val doc = this@alarmCard
            for (mode in modes + "disarm") {
                Box(Modifier.padding(0f, 0f, doc.units.px(FLOW_PAD_DP), doc.units.px(FLOW_PAD_DP))) {
                    doc.key(ref(entityId, mode), text = doc.i18n.state("alarm_control_panel", if (mode == "disarm") "disarmed" else mode.replace("arm_", "armed_")),
                        icon = if (mode == "disarm") "shield-off" else "shield-lock", heightDp = LEVEL_BAR_H_DP)
                }
            }
        }
        if (entityState?.attr("code_format") != null) {
            section(i18n.t("ui.code"))
            keypad(entityId, w)
        }
    }

    private fun DocContext.keypad(entityId: String, w: Float) {
        scope.Box(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
            this@keypad.label(this@keypad.text(Vars.tile("e:$entityId", "code")), Type.DISPLAY, this@keypad.role(Role.INK), Weight.MEDIUM, align = RcTextAlign.Center)
        }
        gap(FLOW_PAD_DP)
        val keyW = minOf((w - 24f) / 3f, 110f)
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("clear", "0", "back"))) {
            scope.Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.Center) {
                val doc = this@keypad
                row.forEachIndexed { i, digit ->
                    if (i > 0) Spacer(Modifier.width(doc.units.px(SECTION_GAP_DP)))
                    when (digit) {
                        "clear" -> doc.key(ref(entityId, "code_clear"), icon = "close", heightDp = MEDIA_CTRL_DP, widthDp = keyW)
                        "back" -> doc.key(ref(entityId, "code_back"), icon = "backspace-outline", heightDp = MEDIA_CTRL_DP, widthDp = keyW)
                        else -> doc.key(ref(entityId, "code_digit", digit), text = digit, heightDp = MEDIA_CTRL_DP, widthDp = keyW)
                    }
                }
            }
            gap(PANEL_CORNER_DP)
        }
    }

    private fun DocContext.selectCard(entityId: String, entityState: EntityState?) {
        val options = entityState?.list("options").orEmpty()
        structural(entityId) { it?.list("options")?.joinToString() ?: "" }
        gap(FLOW_PAD_DP)
        bindChoices(entityId, "option", entityId, options) { it.state }
        options.forEachIndexed { i, o ->
            key(ref(entityId, "option", o), text = o, heightDp = LEVEL_BAR_H_DP, modifier = Modifier.fillMaxWidth(),
                bg = color(Vars.option(entityId, "option", i, Vars.KEY_BG)), ink = color(Vars.option(entityId, "option", i, Vars.KEY_INK)))
            gap(FLOW_PAD_DP)
        }
    }

    private fun DocContext.numberCard(entityId: String, entityState: EntityState?, w: Float) {
        val min = entityState?.number("min") ?: 0.0
        val max = entityState?.number("max") ?: LEVEL_DEFAULT_MAX
        val step = entityState?.number("step") ?: 1.0
        gap(PANEL_CORNER_DP)
        scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            val doc = this@numberCard
            doc.key(ref(entityId, "value", "-$step"), icon = "minus", heightDp = TOGGLE_ICON_DP, widthDp = TOGGLE_ICON_DP, round = true)
            Box(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
                doc.label(doc.text(Vars.entity(entityId, Vars.KEY_STATE)), Type.DISPLAY, doc.role(Role.INK), Weight.NORMAL, align = RcTextAlign.Center)
            }
            doc.key(ref(entityId, "value", "+$step"), icon = "plus", heightDp = TOGGLE_ICON_DP, widthDp = TOGGLE_ICON_DP, round = true)
        }
        gap(CARD_GAP_DP)
        bar(entityId, "value", w, min, max)
    }

    private fun DocContext.vacuumCard(entityId: String, entityState: EntityState?) {
        gap(FLOW_PAD_DP)
        scope.Row(Modifier.fillMaxWidth()) {
            val doc = this@vacuumCard
            doc.key(ref(entityId, "start"), text = doc.i18n.t("ui.start"), icon = "play", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
            Spacer(Modifier.width(doc.units.px(PANEL_CORNER_DP)))
            doc.key(ref(entityId, "pause"), text = doc.i18n.t("ui.pause"), icon = "pause", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
            Spacer(Modifier.width(doc.units.px(PANEL_CORNER_DP)))
            doc.key(ref(entityId, "dock"), text = doc.i18n.t("ui.dock"), icon = "home-import-outline", heightDp = 56f, modifier = Modifier.horizontalWeight(1f))
        }
        gap(PANEL_CORNER_DP)
        key(ref(entityId, "locate"), text = i18n.t("ui.locate"), icon = "map-marker", heightDp = FLOW_KEY_H_DP, modifier = Modifier.fillMaxWidth())
        val speeds = entityState?.list("fan_speed_list").orEmpty()
        if (speeds.isNotEmpty()) {
            structural(entityId) { it?.list("fan_speed_list")?.joinToString() ?: "" }
            section(i18n.t("ui.suction"))
            flowKeys(entityId, "fan_speed", speeds.map { it to it }) { it.attr("fan_speed") }
        }
    }

    private fun DocContext.mapCard(entityId: String, w: Float, h: Float) {
        val pw = units.px(w).toInt()
        val ph = units.px(h).toInt()
        val who = if (entityId.startsWith("person.") || entityId.startsWith("device_tracker.")) listOf(entityId) else states.keys.filter { it.startsWith("person.") }
        for (p in who) structural(p) { s -> "${s?.number("latitude")},${s?.number("longitude")}" }
        picture(PictureSlot("map.card", "map:${mapStyle()}:${who.joinToString(",")}", pw, ph, 0))
        scope.Image(image("map.card", pw, ph), Modifier.size(units.px(w), units.px(h)).clip(rounded(CARD_GAP_DP)), contentScale = RcContentScale.Crop)
        label(i18n.t("ui.map_credit"), Type.CAPTION, role(Role.INK_SOFT))
    }

    val HUES = listOf(0, 30, 50, 120, 180, 220, 270, 320)
    val KELVINS = listOf(2200, 2700, 3500, 4500, 5500, 6500)

    /** The colour of white light at [k] kelvin, near enough for a swatch. */
    fun kelvinColor(k: Int): Int {
        val t = ((k - KELVIN_MIN) / KELVIN_SPAN).coerceIn(0f, 1f)
        val r = 255
        val g = (KELVIN_GREEN_BASE + KELVIN_GREEN_RANGE * t).roundToInt()
        val b = (KELVIN_BLUE_BASE + KELVIN_BLUE_RANGE * t).roundToInt()
        return (ALPHA_OPAQUE shl ALPHA_SHIFT) or (r shl 16) or (g shl 8) or b
    }
}
