package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.Present
import tech.shaffersoftworks.remotescreens.shared.Node

/** Resolves each component's words, icon and level from the entities Home Assistant last reported. */
fun lvglSources(states: Map<String, EntityState>, i18n: I18n): LvglSources = LvglSources(
    text = { node -> boundText(node = node, states = states, i18n = i18n) },
    icon = { node -> boundIcon(node = node, states = states, i18n = i18n) },
    level = { node -> boundLevel(node = node, states = states, i18n = i18n) },
    isOn = { node -> boundOn(node = node, states = states, i18n = i18n) },
)

private fun boundText(node: Node, states: Map<String, EntityState>, i18n: I18n): String {
    val binding = node.bind

    if (binding == null) {
        val written = node.props["text"].orEmpty()

        return if (written.isEmpty() && node.type == "clock") LvglLimits.CLOCK_PLACEHOLDER else written
    }

    val entity = states[binding.entityId]
    val view = Present.view(entityState = entity, i18n = i18n)

    val raw = when (binding.format) {
        "value" -> view.value
        "name" -> view.name
        "attribute" -> binding.attribute?.let { entity?.attributeText(it) }.orEmpty()
        else -> view.stateText
    }

    return binding.prefix + raw + binding.suffix
}

private fun boundIcon(node: Node, states: Map<String, EntityState>, i18n: I18n): String {
    val named = node.props["icon"]

    if (named != null) return named.removePrefix("mdi:")

    val entity = node.bind?.entityId?.let { states[it] }
    val icon = Present.view(entityState = entity, i18n = i18n).icon

    return icon.removePrefix("mdi:")
}

private fun boundLevel(node: Node, states: Map<String, EntityState>, i18n: I18n): Float? {
    val entityId = node.bind?.entityId ?: return null

    return Present.view(entityState = states[entityId], i18n = i18n).level
}

private fun boundOn(node: Node, states: Map<String, EntityState>, i18n: I18n): Boolean {
    val entityId = node.bind?.entityId ?: return false

    return Present.view(entityState = states[entityId], i18n = i18n).on
}
