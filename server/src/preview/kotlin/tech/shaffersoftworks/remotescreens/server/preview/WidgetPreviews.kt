@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.preview

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.RenderInput
import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.initials
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.levelBar
import tech.shaffersoftworks.remotescreens.server.render.widgets.segmentedLevelBar
import tech.shaffersoftworks.remotescreens.server.render.widgets.spinner
import tech.shaffersoftworks.remotescreens.server.render.widgets.weatherStateIcon
import tech.shaffersoftworks.remotescreens.server.render.widgets.numericLabel
import tech.shaffersoftworks.remotescreens.shared.ActionRef

private const val TYPE_GAP = 8f
private const val CONTROL_GAP = 20f
private const val ICON_SIZE = 48f
private const val SPINNER_SIZE = 36f
private const val BAR_WIDTH = 400f
private const val BAR_HEIGHT = 32f
private const val SEGMENT_HEIGHT = 40f
private const val BAR_STEPS = 10
private const val SAMPLE_LEVEL = 0.6f
private const val CLOCK_GLYPHS = 8

internal fun widgetGallery(device: Device, input: RenderInput, typography: Boolean): RenderedDoc {
    val (bytes, context) = DocContext.build(
        widthPx = device.widthPx,
        heightPx = device.heightPx,
        description = "Shared widgets",
        newContext = { input.context(device) },
    ) { doc ->
        Column(Modifier.fillMaxSize().background(doc.role(Role.PAGE)).padding(SAMPLE_PADDING)) {
            if (typography) {
                doc.typographySamples()
            } else {
                doc.controlSamples()
            }
        }
    }

    return RenderedDoc(bytes, context)
}

private fun DocContext.typographySamples() {
    for (size in listOf(Type.CAPTION, Type.BODY, Type.TITLE, Type.HEADLINE, Type.VALUE, Type.DISPLAY)) {
        label(
            text = "Living room · ${size.toInt()} sp",
            sp = size,
            color = role(Role.INK),
        )

        spacer(TYPE_GAP)
    }

    for (weight in listOf(Weight.LIGHT, Weight.NORMAL, Weight.MEDIUM, Weight.SEMI_BOLD, Weight.BOLD)) {
        label(text = "Font weight ${weight.toInt()}", sp = Type.TITLE, color = role(Role.INK), weight = weight)
    }

    spacer(TYPE_GAP)
    numericLabel(text = literal("11:08 PM"), sp = Type.HEADLINE, color = role(Role.INK), glyphs = CLOCK_GLYPHS)
}

private fun DocContext.controlSamples() {
    rcScope.Row {
        icon(
            name = "lightbulb-on",
            sizeDp = ICON_SIZE,
            color = role(Role.ACCENT),
        )

        weatherStateIcon(
            condition = "partlycloudy",
            sizeDp = ICON_SIZE,
            fallbackColor = role(Role.INK),
        )

        initials(name = "Sam Taylor", sizeDp = ICON_SIZE, bg = role(Role.KEY), ink = role(Role.INK))
        spinner(SPINNER_SIZE, role(Role.ACCENT))
    }

    spacer(CONTROL_GAP)

    rcScope.Row {
        key(
            ref = ActionRef(tile = "sample", verb = "toggle"),
            text = "Lights",
            icon = "lightbulb",
        )

        key(
            ref = ActionRef(tile = "sample", verb = "plus"),
            icon = "plus",
            round = true,
        )
    }

    spacer(CONTROL_GAP)
    barSamples()
    spacer(CONTROL_GAP)

    label(
        text = "A long label that wraps across the available width to show how shared text behaves.",
        sp = Type.HEADLINE,
        color = role(Role.INK),
        lines = 2,
    )
}

private fun DocContext.barSamples() {
    levelBar(
        level = float("preview.level", SAMPLE_LEVEL),
        widthDp = BAR_WIDTH,
        heightDp = BAR_HEIGHT,
        fill = role(Role.ACCENT),
        track = role(Role.TRACK),
        steps = BAR_STEPS,
    ) {
        ActionRef(
            tile = "sample",
            verb = "level",
            arg = it.toString(),
        )
    }

    spacer(CONTROL_GAP)

    segmentedLevelBar(
        entityId = "sample",
        levelKey = "preview",
        widthDp = BAR_WIDTH,
        heightDp = SEGMENT_HEIGHT,
        steps = BAR_STEPS,
        levelOf = { SAMPLE_LEVEL },
        fillRole = Role.ACCENT,
        trackRole = Role.TRACK,
    ) {
        ActionRef(
            tile = "sample",
            verb = "level",
            arg = it.toString(),
        )
    }
}
