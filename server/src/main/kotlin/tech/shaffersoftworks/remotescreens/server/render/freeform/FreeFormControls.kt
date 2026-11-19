@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ControlVerb
import tech.shaffersoftworks.remotescreens.server.render.cards.climate.MODE_ICONS

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.bindChoices
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.widgets.choiceRow
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.levelBar
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
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
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain

/** A stepped bar over the entity's level, as on a tile: a tap on step i sets i tenths of its range. */
internal fun DocContext.sliderNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId

    if (id == null) {
        rcScope.Box(styled(node = node, axis = axis, pages = pages))

        return
    }

    bindEntity(id)

    bindTileColors(
        tileId = node.id,
        entityId = id,
        accent = accent(node.props["color"], Pastel.forDomain(EntityId(id).domain.value)),
    )

    val domain = EntityId(id).domain
    val entityState = states[id]
    val verb = sliderVerb(domain)
    val (min, max) = sliderRange(domain, entityState)

    val steps = node.props["steps"]?.toIntOrNull()?.coerceIn(
        Metrics.SLIDER_MIN_STEPS,
        Metrics.SLIDER_MAX_STEPS,
    ) ?: Metrics.SLIDER_DEFAULT_STEPS

    val h = if (node.size.height == "wrap") Metrics.SLIDER_NATURAL_H else self.h
    val track = node.props["track"]?.let { paint(it) } ?: role(Role.TRACK)
    val borderInk = node.style.borderColor?.let { paint(it) }

    rcScope.Box(styled(node = node, axis = axis, pages = pages, applyBorder = false)) {
        val doc = this@sliderNode

        doc.levelBar(
            level = doc.float(Vars.entity(id, Vars.KEY_LEVEL)),
            widthDp = self.w - node.style.padX,
            heightDp = h - node.style.padY,
            fill = doc.color(Vars.tile(node.id, Vars.KEY_ICON)),
            track = track,
            steps = steps,
            borderDp = node.style.border,
            border = borderInk,
        ) { i -> ActionRef(
            tile = node.id,
            verb = verb,
            arg = sliderStepValue(min = min, max = max, step = i, steps = steps)
        ) }
    }
}

private fun sliderVerb(domain: Domain) = when (domain) {
    Domain.LIGHT -> ControlVerb.BRIGHTNESS
    Domain.FAN -> ControlVerb.PERCENTAGE
    Domain.COVER -> ControlVerb.POSITION
    Domain.MEDIA_PLAYER -> ControlVerb.VOLUME
    else -> ControlVerb.VALUE
}

private fun sliderRange(domain: Domain, entityState: EntityState?): Pair<Double, Double> =
    if (domain == Domain.NUMBER || domain == Domain.INPUT_NUMBER) {
        (entityState?.input?.min ?: 0.0) to
            (entityState?.input?.max ?: Metrics.LEVEL_RANGE_MAX)
    } else {
        0.0 to Metrics.LEVEL_RANGE_MAX
    }

private fun sliderStepValue(min: Double, max: Double, step: Int, steps: Int): String {
    val value = min + (max - min) * step / steps

    return if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.ROOT, value)
}

/** An on and off switch: the track lights up in the entity's colour and the knob moves over while it is on. */
internal fun DocContext.switchNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId

    val track = if (id != null) {
        bindEntity(id)

        bindTileColors(
            tileId = node.id,
            entityId = id,
            accent = accent(node.props["color"], Pastel.forDomain(EntityId(id).domain.value)),
        )

        color(Vars.tile(node.id, Vars.KEY_BG))
    } else {
        role(Role.KEY)
    }

    rcScope.Box(
        modifier = styled(node = node, axis = axis, pages = pages),
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
                    modifier = modifier,
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
internal fun DocContext.stepOf(node: Node, entityState: EntityState?, domain: Domain?): Pair<String, Double>? {
    val pair = when (domain) {
        Domain.CLIMATE, Domain.WATER_HEATER -> "temperature" to (
            entityState?.climate?.targetTempStep
                ?: if (homeAssistantConfig.temperatureUnit.contains(
                        "F",
                    )) {
                    Metrics.STEP_TEMP_F
                } else {
                    Metrics.STEP_TEMP_C
                }
            )
        Domain.HUMIDIFIER -> "humidity" to (entityState?.humidifier?.humidityStep ?: Metrics.STEP_PERCENTAGE)
        Domain.NUMBER, Domain.INPUT_NUMBER -> ControlVerb.VALUE to (entityState?.input?.step ?: 1.0)
        Domain.LIGHT -> ControlVerb.BRIGHTNESS to Metrics.STEP_BRIGHTNESS
        Domain.FAN -> ControlVerb.PERCENTAGE to Metrics.STEP_PERCENTAGE
        Domain.COVER, Domain.VALVE -> ControlVerb.POSITION to Metrics.STEP_POSITION
        Domain.MEDIA_PLAYER -> ControlVerb.VOLUME to Metrics.STEP_VOLUME
        else -> return null
    }

    val step = node.props["step"]?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: pair.second

    return pair.first to step
}

/** Minus, the value, plus: a thermostat's target, a number, a light's brightness. */
internal fun DocContext.stepperNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val domain = id?.let { EntityId(it).domain }
    val ink = role(Role.INK)
    val how = id?.let { stepOf(node = node, entityState = states[it], domain = domain) }

    if (id != null) bindEntity(id)

    val step = how?.second?.let { if (it % 1.0 == 0.0) {
        it.toInt().toString()
    } else {
        "%.1f".format(
            Locale.ROOT,
            it,
        )
    } }

    val down = how?.let { ActionRef(tile = node.id, verb = it.first, arg = "-$step") }
    val up = how?.let { ActionRef(tile = node.id, verb = it.first, arg = "+$step") }
    val isPredictable = how != null && domain?.let { Catalogue.of(it.value) }?.predict?.containsKey(how.first) == true

    if (id != null && isPredictable) {
        down?.let { predict(it, id) }
        up?.let { predict(it, id) }
    }

    stepperRow(node = node, axis = axis, pages = pages, ink = ink, id = id, down = down, up = up)
}

private fun DocContext.stepperRow(
    node: Node,
    axis: Axis,
    pages: Pages,
    ink: RcColor,
    id: String?,
    down: ActionRef?,
    up: ActionRef?,
) {
    rcScope.Row(
        modifier = styled(node = node, axis = axis, pages = pages),
        horizontal = RcRowHorizontalPositioning.SpaceBetween,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@stepperRow

        fun key(ref: ActionRef?, icon: String) {
            if (ref != null) {
                doc.key(
                    ref = ref,
                    icon = icon,
                    heightDp = Metrics.STEPPER_KEY_DP,
                    widthDp = Metrics.STEPPER_KEY_DP,
                    round = true,
                    ink = ink,
                )
            } else {
                Box(
                    modifier = Modifier.size(
                        doc.units.px(Metrics.STEPPER_KEY_DP),
                        doc.units.px(Metrics.STEPPER_KEY_DP),
                    ).clip(doc.rounded(Metrics.STEPPER_KEY_CORNER_DP)).background(doc.role(Role.KEY)),
                    horizontal = RcHorizontalPositioning.Center,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    doc.icon(name = icon, sizeDp = Metrics.STEPPER_KEY_ICON_DP, color = doc.role(Role.INK_DIM))
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
            modifier = middle,
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            if (id != null) {
                doc.label(
                    text = doc.text(Vars.entity(id, Vars.KEY_VALUE)),
                    sp = Type.HEADLINE,
                    color = ink,
                    weight = Weight.MEDIUM,
                    align = RcTextAlign.Center,
                )
            } else {
                doc.label(
                    text = "–",
                    sp = Type.HEADLINE,
                    color = doc.role(Role.INK_DIM),
                    weight = Weight.MEDIUM,
                    align = RcTextAlign.Center,
                )
            }
        }

        key(up, "plus")
    }
}

/** What a row of choices offers for an entity: the verb, the choices, how each reads, and which is chosen. */
internal class ChoiceSet(
    val verb: String,
    val attribute: String,
    val choices: List<String>,
    val word: (String) -> String,
    val chosen: (EntityState) -> String?,
    val icons: Map<String, String> = emptyMap(),
)

internal fun DocContext.choicesOf(entityState: EntityState?, domain: Domain?): ChoiceSet? = when (domain) {
    Domain.SELECT, Domain.INPUT_SELECT -> ChoiceSet(
        verb = "option",
        attribute = "options",
        choices = entityState?.input?.options.orEmpty(),
        word = { it },
        chosen = { it.state },
    )
    Domain.CLIMATE -> ChoiceSet(
        verb = "mode",
        attribute = "hvac_modes",
        choices = entityState?.climate?.hvacModes.orEmpty(),
        word = { i18n.state(Domain.CLIMATE.value, it) },
        chosen = { it.state },
        icons = MODE_ICONS,
    )
    Domain.LIGHT -> ChoiceSet(
        verb = "effect",
        attribute = "effect_list",
        choices = entityState?.light?.effectList.orEmpty(),
        word = { it },
        chosen = { it.light.effect },
    )
    else -> null
}

/** A row of keys, one per option: a select's options, a thermostat's modes, a light's effects. */
internal fun DocContext.choicesNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId

    val modifier = styled(
        node = node,
        axis = axis,
        pages = pages,
        base = measured(node = node, axis = axis, self = self)
    )

    val set = id?.let { choicesOf(states[it], EntityId(it).domain) }

    if (id == null || set == null || set.choices.isEmpty()) {
        rcScope.Box(
            modifier.clip(rounded(Metrics.CHOICES_EMPTY_CORNER_DP)).background(role(Role.TRACK)),
        )

        return
    }

    structural(id) { it?.attributeList(set.attribute)?.joinToString().orEmpty() }

    val shown = set.choices.take(
        node.props["max"]?.toIntOrNull()?.coerceIn(
            Metrics.CHOICES_MIN,
            Metrics.CHOICES_MAX,
        ) ?: Metrics.CHOICES_DEFAULT_MAX,
    )

    bindChoices(scopeId = node.id, group = "c", entityId = id, choices = shown, chosen = set.chosen)
    val h = (self.h - node.style.padY).coerceAtLeast(Metrics.CHOICES_MIN_H)

    rcScope.Box(modifier, vertical = RcVerticalPositioning.Center) {
        this@choicesNode.choiceRow(
            scopeId = node.id,
            group = "c",
            choices = shown.map { it to set.word(it) },
            refFor = { ActionRef(tile = node.id, verb = set.verb, arg = it) },
            heightDp = h,
            icons = set.icons,
        )
    }
}
