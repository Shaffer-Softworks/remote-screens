@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.render.tiles.MODE_ICONS

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Pages
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.Weight
import tech.shaffersoftworks.remotescreens.server.render.accent
import tech.shaffersoftworks.remotescreens.server.render.bindChoices
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.choiceRow
import tech.shaffersoftworks.remotescreens.server.render.icon
import tech.shaffersoftworks.remotescreens.server.render.key
import tech.shaffersoftworks.remotescreens.server.render.label
import tech.shaffersoftworks.remotescreens.server.render.levelBar
import tech.shaffersoftworks.remotescreens.server.render.rounded
import tech.shaffersoftworks.remotescreens.server.render.styled

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Node
import java.util.Locale

/** A stepped bar over the entity's level, as on a tile: a tap on step i sets i tenths of its range. */
internal fun DocContext.sliderNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    if (id == null) {
        scope.Box(styled(node, axis, pages))
        return
    }
    bindEntity(id)
    bindTileColors(
        node.id,
        id,
        accent(node.props["color"], Pastel.forDomain(id.substringBefore('.'))),
    )
    val domain = id.substringBefore('.')
    val entityState = states[id]
    val verb = when (domain) { "light" -> "brightness"; "fan" -> "percentage"; "cover" -> "position"; "media_player" -> "volume"; else -> "value" }
    val (min, max) = if (domain in setOf("number", "input_number")) {
        (entityState?.number(
            "min",
        ) ?: 0.0) to (entityState?.number("max") ?: Metrics.LEVEL_RANGE_MAX)
    } else {
        0.0 to Metrics.LEVEL_RANGE_MAX
    }
    val steps = node.props["steps"]?.toIntOrNull()?.coerceIn(
        Metrics.SLIDER_MIN_STEPS,
        Metrics.SLIDER_MAX_STEPS,
    ) ?: Metrics.SLIDER_DEFAULT_STEPS
    val h = if (node.size.height == "wrap") Metrics.SLIDER_NATURAL_H else self.h
    scope.Box(styled(node, axis, pages)) {
        val doc = this@sliderNode
        doc.levelBar(
            doc.float(Vars.entity(id, Vars.KEY_LEVEL)),
            self.w - node.style.padX,
            h - node.style.padY,
            doc.color(Vars.tile(node.id, Vars.KEY_ICON)),
            doc.role(Role.TRACK),
            steps,
        ) { i ->
            val value = min + (max - min) * i / steps
            ActionRef(
                node.id,
                verb,
                if (value % 1.0 == 0.0) {
                    value.toInt().toString()
                } else {
                    "%.1f".format(
                        Locale.ROOT,
                        value,
                    )
                },
            )
        }
    }
}

/** An on and off switch: the track lights up in the entity's colour and the knob moves over while it is on. */
internal fun DocContext.switchNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val track = if (id != null) {
        bindEntity(id)
        bindTileColors(
            node.id,
            id,
            accent(node.props["color"], Pastel.forDomain(id.substringBefore('.'))),
        )
        color(Vars.tile(node.id, Vars.KEY_BG))
    } else {
        role(Role.KEY)
    }
    scope.Box(
        styled(node, axis, pages),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@switchNode
        Box(
            Modifier.size(
                doc.units.px(Metrics.SWITCH_NATURAL_W),
                doc.units.px(Metrics.SWITCH_NATURAL_H),
            ).clip(doc.rounded(Metrics.SWITCH_CORNER_DP)).background(track),
        ) {
            fun knob(end: Boolean, shown: String?) {
                var modifier = Modifier.fillMaxSize().padding(
                    doc.units.px(Metrics.SWITCH_KNOB_PAD_DP),
                )
                if (shown != null) modifier = modifier.visibility(doc.int(shown))
                Box(
                    modifier,
                    horizontal = if (end) RcHorizontalPositioning.End else RcHorizontalPositioning.Start,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    Box(
                        Modifier.size(
                            doc.units.px(Metrics.SWITCH_KNOB_DP),
                            doc.units.px(Metrics.SWITCH_KNOB_DP),
                        ).clip(
                            doc.rounded(Metrics.SWITCH_KNOB_CORNER_DP),
                        ).background(doc.role(Role.INK)),
                    )
                }
            }
            if (id == null) {
                knob(false, null)
            } else {
                knob(false, Vars.entity(id, Vars.KEY_OFF))
                knob(true, Vars.entity(id, Vars.KEY_ON))
            }
        }
    }
}

/** What a stepper's keys send for an entity, and by how much a tap moves it. */
internal fun DocContext.stepOf(node: Node, entityState: EntityState?, domain: String?): Pair<String, Double>? {
    val pair = when (domain) {
        "climate", "water_heater" -> "temperature" to (
            entityState?.number("target_temp_step")
                ?: if (homeAssistantConfig.temperatureUnit.contains(
                        "F",
                    )) {
                    Metrics.STEP_TEMP_F
                } else {
                    Metrics.STEP_TEMP_C
                }
            )
        "humidifier" -> "humidity" to (entityState?.number(
            "humidity_step",
        ) ?: Metrics.STEP_PERCENTAGE)
        "number", "input_number" -> "value" to (entityState?.number("step") ?: 1.0)
        "light" -> "brightness" to Metrics.STEP_BRIGHTNESS
        "fan" -> "percentage" to Metrics.STEP_PERCENTAGE
        "cover", "valve" -> "position" to Metrics.STEP_POSITION
        "media_player" -> "volume" to Metrics.STEP_VOLUME
        else -> return null
    }
    val step = node.props["step"]?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: pair.second
    return pair.first to step
}

/** Minus, the value, plus: a thermostat's target, a number, a light's brightness. */
internal fun DocContext.stepperNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val domain = id?.substringBefore('.')
    val ink = role(Role.INK)
    val how = id?.let { stepOf(node, states[it], domain) }
    if (id != null) bindEntity(id)
    val step = how?.second?.let { if (it % 1.0 == 0.0) {
        it.toInt().toString()
    } else {
        "%.1f".format(
            Locale.ROOT,
            it,
        )
    } }
    val down = how?.let { ActionRef(node.id, it.first, "-$step") }
    val up = how?.let { ActionRef(node.id, it.first, "+$step") }
    val isPredictable = how != null && domain?.let { Catalogue.of(
        it,
    ) }?.predict?.containsKey(how.first) == true
    if (id != null && isPredictable) {
        down?.let { predict(it, id) }
        up?.let { predict(it, id) }
    }
    scope.Row(
        styled(node, axis, pages),
        horizontal = RcRowHorizontalPositioning.SpaceBetween,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@stepperNode
        fun key(ref: ActionRef?, icon: String) {
            if (ref != null) {
                doc.key(
                    ref,
                    icon = icon,
                    heightDp = Metrics.STEPPER_KEY_DP,
                    widthDp = Metrics.STEPPER_KEY_DP,
                    round = true,
                    ink = ink,
                )
            } else {
                Box(
                    Modifier.size(
                        doc.units.px(Metrics.STEPPER_KEY_DP),
                        doc.units.px(Metrics.STEPPER_KEY_DP),
                    ).clip(doc.rounded(Metrics.STEPPER_KEY_CORNER_DP)).background(doc.role(Role.KEY)),
                    horizontal = RcHorizontalPositioning.Center,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    doc.icon(icon, Metrics.STEPPER_KEY_ICON_DP, doc.role(Role.INK_DIM))
                }
            }
        }
        key(down, "minus")
        val middle = if (node.size.width == "wrap") {
            Modifier.width(
                doc.units.px(Metrics.STEPPER_MIDDLE_W),
            )
        } else {
            Modifier.horizontalWeight(1f)
        }
        Box(
            middle,
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            if (id != null) {
                doc.label(
                    doc.text(Vars.entity(id, Vars.KEY_VALUE)),
                    Type.HEADLINE,
                    ink,
                    Weight.MEDIUM,
                    align = RcTextAlign.Center,
                )
            } else {
                doc.label(
                    "–",
                    Type.HEADLINE,
                    doc.role(Role.INK_DIM),
                    Weight.MEDIUM,
                    align = RcTextAlign.Center,
                )
            }
        }
        key(up, "plus")
    }
}

/** What a row of choices offers for an entity: the verb, the choices, how each reads, and which is chosen. */
internal class ChoiceSet(val verb: String, val attribute: String, val choices: List<String>, val word: (String) -> String, val chosen: (EntityState) -> String?, val icons: Map<String, String> = emptyMap())

internal fun DocContext.choicesOf(entityState: EntityState?, domain: String?): ChoiceSet? = when (domain) {
    "select", "input_select" -> ChoiceSet(
        "option",
        "options",
        entityState?.list("options").orEmpty(),
        { it },
        { it.state },
    )
    "climate" -> ChoiceSet(
        "mode",
        "hvac_modes",
        entityState?.list("hvac_modes").orEmpty(),
        { i18n.state("climate", it) },
        { it.state },
        MODE_ICONS,
    )
    "light" -> ChoiceSet(
        "effect",
        "effect_list",
        entityState?.list("effect_list").orEmpty(),
        { it },
        { it.attr("effect") },
    )
    else -> null
}

/** A row of keys, one per option: a select's options, a thermostat's modes, a light's effects. */
internal fun DocContext.choicesNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val modifier = styled(node, axis, pages, measured(node, axis, self))
    val set = id?.let { choicesOf(states[it], it.substringBefore('.')) }
    if (id == null || set == null || set.choices.isEmpty()) {
        scope.Box(
            modifier.clip(rounded(Metrics.CHOICES_EMPTY_CORNER_DP)).background(role(Role.TRACK)),
        )
        return
    }
    structural(id) { it?.list(set.attribute)?.joinToString() ?: "" }
    val shown = set.choices.take(
        node.props["max"]?.toIntOrNull()?.coerceIn(
            Metrics.CHOICES_MIN,
            Metrics.CHOICES_MAX,
        ) ?: Metrics.CHOICES_DEFAULT_MAX,
    )
    bindChoices(node.id, "c", id, shown, set.chosen)
    val h = (self.h - node.style.padY).coerceAtLeast(Metrics.CHOICES_MIN_H)
    scope.Box(modifier, vertical = RcVerticalPositioning.Center) {
        this@choicesNode.choiceRow(
            node.id,
            "c",
            shown.map { it to set.word(it) },
            { ActionRef(node.id, set.verb, it) },
            h,
            set.icons,
        )
    }
}
