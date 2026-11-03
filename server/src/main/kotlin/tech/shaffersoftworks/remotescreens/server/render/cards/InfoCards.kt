@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.bindChoices
import tech.shaffersoftworks.remotescreens.server.render.panels.calendar.calendarEvent
import tech.shaffersoftworks.remotescreens.server.render.widgets.gap
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.panels.calendar.isToday
import tech.shaffersoftworks.remotescreens.server.render.widgets.weatherStateIcon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.freeform.paint
import tech.shaffersoftworks.remotescreens.server.render.tiles.graph

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.time.ZoneId
import kotlin.math.roundToInt

internal fun DocContext.weatherCard(entityId: String, entityState: EntityState?, width: Float) {
    structural(entityId) { it?.state ?: "" }
    refreshEvery(CardChrome.WEATHER_REFRESH_S)
    val ink = role(Role.INK)
    val soft = role(Role.INK_SOFT)
    val zone = runCatching { ZoneId.of(
        homeAssistantConfig.timeZone,
    ) }.getOrDefault(ZoneId.of("UTC"))
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@weatherCard
        doc.weatherStateIcon(entityState?.state ?: "", CardChrome.STEPPER_KEY_DP, fallbackColor = ink)
        Spacer(Modifier.width(doc.units.px(CardChrome.CARD_GAP_DP)))
        Column {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                sp = Type.DISPLAY,
                color = ink,
                weight = Weight.NORMAL,
            )
            val details = listOfNotNull(
                entityState?.number(
                    "humidity",
                )?.let { doc.i18n.t("ui.humidity", "value" to doc.i18n.percent(it.roundToInt())) },
                entityState?.number(
                    "wind_speed",
                )?.let { doc.i18n.t(
                    "ui.wind",
                    "value" to "${it.roundToInt()} ${entityState.attr("wind_speed_unit") ?: "km/h"}",
                ) },
            ).joinToString(" · ")
            doc.label(details, Type.BODY, soft)
        }
    }
    gap(CardChrome.CARD_GAP_DP)
    val days = data.forecasts[entityId].orEmpty()
    val lo = days.mapNotNull { it.low }.minOrNull() ?: 0.0
    val hi = days.mapNotNull { it.high }.maxOrNull() ?: 1.0
    for (day in days.take(CardChrome.FORECAST_DAYS)) {
        scope.Row(
            Modifier.fillMaxWidth().padding(
                start = 0f,
                top = units.px(CardChrome.ROW_GAP_DP),
                end = 0f,
                bottom = units.px(CardChrome.ROW_GAP_DP),
            ),
            vertical = RcVerticalPositioning.Center,
        ) {
            val doc = this@weatherCard
            doc.label(
                text = doc.i18n.weekday(day.time.withZoneSameInstant(zone)),
                sp = Type.TITLE,
                color = ink,
                weight = Weight.MEDIUM,
                modifier = Modifier.width(doc.units.px(CardChrome.MEDIA_CTRL_DP)),
            )
            doc.weatherStateIcon(day.condition, CardChrome.WEATHER_ICON_ROW_DP, fallbackColor = ink)
            Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
            doc.label(
                text = day.rain?.let { "$it%" } ?: "",
                sp = Type.BODY,
                color = doc.role(Role.ACCENT),
                modifier = Modifier.width(doc.units.px(CardChrome.FLOW_KEY_H_DP)),
            )
            doc.label(
                text = day.low?.let { "${it.roundToInt()}°" } ?: "",
                sp = Type.BODY,
                color = soft,
                modifier = Modifier.width(doc.units.px(CardChrome.CLOSE_KEY_DP)),
            )
            val span = (hi - lo).coerceAtLeast(1.0)
            val start = (((day.low ?: lo) - lo) / span).toFloat().coerceIn(0f, 1f)
            val end = (((day.high ?: hi) - lo) / span).toFloat().coerceIn(start, 1f)
            val barW = doc.units.px(
                (width - CardChrome.MEDIA_CTRL_DP - CardChrome.WEATHER_ICON_ROW_DP - CardChrome.PANEL_CORNER_DP - CardChrome.FLOW_KEY_H_DP - CardChrome.CLOSE_KEY_DP - CardChrome.CLOSE_KEY_DP - CardChrome.SECTION_GAP_DP).coerceAtLeast(
                    CardChrome.CLOSE_KEY_DP,
                ),
            )
            val barH = doc.units.px(CardChrome.ROW_GAP_DP)
            Spacer(Modifier.width(doc.units.px(CardChrome.ROW_GAP_DP)))
            Canvas(Modifier.size(barW, barH)) {
                paint { color(doc.role(Role.DIVIDER)); style(RcPaintStyle.Fill) }
                drawRoundRect(
                    left = 0f,
                    top = 0f,
                    right = barW,
                    bottom = barH,
                    radiusX = barH / 2f,
                    radiusY = barH / 2f,
                )
                paint { color(doc.role(Role.WARN)) }
                val barLeft = start * barW
                drawRoundRect(
                    left = barLeft,
                    top = 0f,
                    right = maxOf(end * barW, barLeft + barH),
                    bottom = barH,
                    radiusX = barH / 2f,
                    radiusY = barH / 2f,
                )
            }
            Spacer(Modifier.width(doc.units.px(CardChrome.ROW_GAP_DP)))
            doc.label(
                text = day.high?.let { "${it.roundToInt()}°" } ?: "",
                sp = Type.BODY,
                color = ink,
                weight = Weight.SEMI_BOLD,
                modifier = Modifier.width(doc.units.px(CardChrome.CLOSE_KEY_DP)),
            )
        }
    }
}

internal fun DocContext.historyCard(entityId: String, w: Float, h: Float) {
    refreshEvery(CardChrome.HISTORY_REFRESH_S)
    val points = data.history[entityId].orEmpty()
    val entityState = states[entityId]
    if (points.isNotEmpty() && entityState != null) {
        val max = points.maxBy { it.second }
        val min = points.minBy { it.second }
        val unit = entityState.attr("unit_of_measurement").orEmpty()
        scope.Row(Modifier.fillMaxWidth()) {
            val doc = this@historyCard
            doc.label(
                text = doc.i18n.t("ui.max", "value" to "${doc.i18n.number(max.second, 1)} $unit"),
                sp = Type.BODY,
                color = doc.role(Role.DANGER),
                weight = Weight.MEDIUM,
                modifier = Modifier.horizontalWeight(1f),
            )
            doc.label(
                text = doc.i18n.t("ui.min", "value" to "${doc.i18n.number(min.second, 1)} $unit"),
                sp = Type.BODY,
                color = doc.role(Role.ACCENT),
                weight = Weight.MEDIUM,
            )
        }
        gap(CardChrome.PANEL_CORNER_DP)
    }
    graph(
        points = points,
        wDp = w,
        hDp = (h - CardChrome.MEDIA_CTRL_DP).coerceAtLeast(CardChrome.PLAY_KEY_DP),
        line = role(Role.ACCENT),
        soft = role(Role.INK_SOFT),
    )
}

internal fun DocContext.alarmCard(entityId: String, entityState: EntityState?, w: Float) {
    val features = entityState?.features() ?: 0
    val modes = listOfNotNull(
        "arm_home".takeIf { features and CardChrome.ALARM_FEAT_ARM_HOME != 0 },
        "arm_away".takeIf { features and CardChrome.ALARM_FEAT_ARM_AWAY != 0 },
        "arm_night".takeIf { features and CardChrome.ALARM_FEAT_ARM_NIGHT != 0 },
        "arm_vacation".takeIf { features and CardChrome.ALARM_FEAT_ARM_VACATION != 0 },
    )
    bind(entityId) { states ->
        listOf(VarValue.string(Vars.tile("e:$entityId", "code"), ""))
    }
    gap(CardChrome.FLOW_PAD_DP)
    scope.Flow(Modifier.fillMaxWidth()) {
        val doc = this@alarmCard
        for (mode in modes + "disarm") {
            Box(
                Modifier.padding(
                    start = 0f,
                    top = 0f,
                    end = doc.units.px(CardChrome.FLOW_PAD_DP),
                    bottom = doc.units.px(CardChrome.FLOW_PAD_DP),
                ),
            ) {
                doc.key(
                    ref = ref(entityId, mode),
                    text = doc.i18n.state(
                        "alarm_control_panel",
                        if (mode == "disarm") "disarmed" else mode.replace("arm_", "armed_"),
                    ),
                    icon = if (mode == "disarm") "shield-off" else "shield-lock",
                    heightDp = CardChrome.LEVEL_BAR_H_DP,
                )
            }
        }
    }
    if (entityState?.attr("code_format") != null) {
        section(i18n.t("ui.code"))
        keypad(entityId, w)
    }
}

internal fun DocContext.keypad(entityId: String, w: Float) {
    scope.Box(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
        this@keypad.label(
            text = this@keypad.text(Vars.tile("e:$entityId", "code")),
            sp = Type.DISPLAY,
            color = this@keypad.role(Role.INK),
            weight = Weight.MEDIUM,
            align = RcTextAlign.Center,
        )
    }
    gap(CardChrome.FLOW_PAD_DP)
    val keyW = minOf(
        (w - CardChrome.KEYPAD_ROW_GAP_DP) / CardChrome.KEYPAD_COLS,
        CardChrome.KEYPAD_KEY_MAX_DP,
    )
    for (row in listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("clear", "0", "back"),
    )) {
        scope.Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.Center) {
            val doc = this@keypad
            row.forEachIndexed { i, digit ->
                if (i > 0) Spacer(Modifier.width(doc.units.px(CardChrome.SECTION_GAP_DP)))
                when (digit) {
                    "clear" -> doc.key(
                        ref = ref(entityId, "code_clear"),
                        icon = "close",
                        heightDp = CardChrome.MEDIA_CTRL_DP,
                        widthDp = keyW,
                    )
                    "back" -> doc.key(
                        ref = ref(entityId, "code_back"),
                        icon = "backspace-outline",
                        heightDp = CardChrome.MEDIA_CTRL_DP,
                        widthDp = keyW,
                    )
                    else -> doc.key(
                        ref = ref(entityId, "code_digit", digit),
                        text = digit,
                        heightDp = CardChrome.MEDIA_CTRL_DP,
                        widthDp = keyW,
                    )
                }
            }
        }
        gap(CardChrome.PANEL_CORNER_DP)
    }
}

internal fun DocContext.selectCard(entityId: String, entityState: EntityState?) {
    val options = entityState?.list("options").orEmpty()
    structural(entityId) { it?.list("options")?.joinToString() ?: "" }
    gap(CardChrome.FLOW_PAD_DP)
    bindChoices(scopeId = entityId, group = "option", entityId = entityId, choices = options) { it.state }
    options.forEachIndexed { i, o ->
        key(
            ref = ref(entityId, "option", o),
            text = o,
            heightDp = CardChrome.LEVEL_BAR_H_DP,
            modifier = Modifier.fillMaxWidth(),
            bg = color(Vars.option(scope = entityId, group = "option", index = i, key = Vars.KEY_BG)),
            ink = color(Vars.option(scope = entityId, group = "option", index = i, key = Vars.KEY_INK)),
        )
        gap(CardChrome.FLOW_PAD_DP)
    }
}

internal fun DocContext.numberCard(entityId: String, entityState: EntityState?, w: Float) {
    val min = entityState?.number("min") ?: 0.0
    val max = entityState?.number("max") ?: CardChrome.LEVEL_DEFAULT_MAX
    val step = entityState?.number("step") ?: 1.0
    gap(CardChrome.PANEL_CORNER_DP)
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@numberCard
        doc.key(
            ref = ref(entityId, "value", "-$step"),
            icon = "minus",
            heightDp = CardChrome.TOGGLE_ICON_DP,
            widthDp = CardChrome.TOGGLE_ICON_DP,
            round = true,
        )
        Box(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_STATE)),
                sp = Type.DISPLAY,
                color = doc.role(Role.INK),
                weight = Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }
        doc.key(
            ref = ref(entityId, "value", "+$step"),
            icon = "plus",
            heightDp = CardChrome.TOGGLE_ICON_DP,
            widthDp = CardChrome.TOGGLE_ICON_DP,
            round = true,
        )
    }
    gap(CardChrome.CARD_GAP_DP)
    bar(entityId = entityId, verb = "value", width = w, min = min, max = max)
}

internal fun DocContext.calendarCard(@Suppress(
    "UNUSED_PARAMETER",
) entityId: String, entityState: EntityState?,) {
    val zone = runCatching { ZoneId.of(
        homeAssistantConfig.timeZone,
    ) }.getOrDefault(ZoneId.of("UTC"))
    val event = entityState?.let { calendarEvent(it, zone) }
    gap(CardChrome.PANEL_CORNER_DP)
    label(
        text = event?.title ?: i18n.t("ui.no_events"),
        sp = Type.DISPLAY,
        color = role(Role.INK),
        weight = Weight.SEMI_BOLD,
    )
    gap(CardChrome.FLOW_PAD_DP)
    val whenText = when {
        event == null -> "—"
        event.allDay && isToday(event, zone) -> i18n.t("ui.today") + " · " + i18n.t("ui.all_day")
        event.allDay -> i18n.fullWeekday(event.start) + " · " + i18n.t("ui.all_day")
        isToday(event, zone) -> i18n.t("ui.today") + " · " + i18n.time(event.start)
        else -> i18n.fullWeekday(event.start) + " · " + i18n.time(event.start)
    }
    label(whenText, Type.HEADLINE, role(Role.INK_SOFT))
    event?.location?.takeIf { it.isNotBlank() }?.let {
        gap(CardChrome.FLOW_PAD_DP)
        label(it, Type.BODY, role(Role.INK_SOFT))
    }
}

internal fun DocContext.todoCard(entityId: String) {
    val items = data.todos[entityId].orEmpty()
    gap(CardChrome.FLOW_PAD_DP)
    if (items.isEmpty()) {
        label(i18n.t("ui.no_todos"), Type.HEADLINE, role(Role.INK_SOFT))
        return
    }
    items.take(CardChrome.TODO_CARD_TAKE).forEach { item ->
        key(
            ref = ref(entityId, "complete", item.uid),
            text = item.summary,
            icon = "checkbox-blank-outline",
            heightDp = CardChrome.FLOW_KEY_H_DP,
            modifier = Modifier.fillMaxWidth(),
        )
        gap(CardChrome.FLOW_PAD_DP)
    }
}

internal fun DocContext.timerCard(entityId: String, entityState: EntityState?) {
    gap(CardChrome.PANEL_CORNER_DP)
    label(
        text = text(Vars.entity(entityId, Vars.KEY_VALUE)),
        sp = Type.DISPLAY,
        color = role(Role.INK),
        weight = Weight.NORMAL,
        align = RcTextAlign.Center,
    )
    gap(CardChrome.CARD_GAP_DP)
    scope.Row(Modifier.fillMaxWidth()) {
        val doc = this@timerCard
        doc.key(
            ref = ref(entityId, "start"),
            text = doc.i18n.t("ui.start"),
            icon = "play",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref = ref(entityId, "pause"),
            text = doc.i18n.t("ui.pause"),
            icon = "pause",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
        Spacer(Modifier.width(doc.units.px(CardChrome.PANEL_CORNER_DP)))
        doc.key(
            ref = ref(entityId, "cancel"),
            text = doc.i18n.t("ui.cancel"),
            icon = "close",
            heightDp = 56f,
            modifier = Modifier.horizontalWeight(1f),
        )
    }
    if (entityState?.state == "active" || entityState?.state == "paused") {
        gap(CardChrome.PANEL_CORNER_DP)
        key(
            ref = ref(entityId, "finish"),
            text = i18n.t("ui.finish"),
            icon = "check",
            heightDp = CardChrome.FLOW_KEY_H_DP,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun DocContext.counterCard(entityId: String, @Suppress(
    "UNUSED_PARAMETER",
) entityState: EntityState?,) {
    gap(CardChrome.PANEL_CORNER_DP)
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@counterCard
        doc.key(
            ref = ref(entityId, "decrement"),
            icon = "minus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            doc.label(
                text = doc.text(Vars.entity(entityId, Vars.KEY_VALUE)),
                sp = Type.DISPLAY,
                color = doc.role(Role.INK),
                weight = Weight.NORMAL,
                align = RcTextAlign.Center,
            )
        }
        doc.key(
            ref = ref(entityId, "increment"),
            icon = "plus",
            heightDp = CardChrome.STEPPER_KEY_DP,
            widthDp = CardChrome.STEPPER_KEY_DP,
            round = true,
        )
    }
    gap(CardChrome.CARD_GAP_DP)
    key(
        ref = ref(entityId, "reset"),
        text = i18n.t("ui.reset"),
        heightDp = CardChrome.FLOW_KEY_H_DP,
        modifier = Modifier.fillMaxWidth(),
        round = true,
    )
}

internal fun DocContext.inputTextCard(entityId: String, entityState: EntityState?) {
    gap(CardChrome.PANEL_CORNER_DP)
    label(
        text = entityState?.state?.ifBlank { i18n.t("ui.empty_text") } ?: "—",
        sp = Type.HEADLINE,
        color = role(Role.INK),
        weight = Weight.MEDIUM,
    )
    gap(CardChrome.CARD_GAP_DP)
    key(
        ref = ref(entityId, "clear"),
        text = i18n.t("ui.clear"),
        icon = "close",
        heightDp = CardChrome.FLOW_KEY_H_DP,
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun DocContext.inputDatetimeCard(
    @Suppress("UNUSED_PARAMETER") entityId: String,
    entityState: EntityState?,
) {
    gap(CardChrome.PANEL_CORNER_DP)
    label(
        text = entityState?.state ?: "—",
        sp = Type.DISPLAY,
        color = role(Role.INK),
        weight = Weight.NORMAL,
        align = RcTextAlign.Center,
    )
    gap(CardChrome.FLOW_PAD_DP)
    label(text = i18n.t("ui.datetime_hint"), sp = Type.BODY, color = role(Role.INK_SOFT), align = RcTextAlign.Center)
}
