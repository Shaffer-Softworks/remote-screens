@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.lights

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.lights.LightDetailMode
import tech.shaffersoftworks.remotescreens.server.data.lights.LightsUiState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.theme.Rpi
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.VarValue

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.requiredHeightIn
import androidx.compose.remote.creation.dsl.size
import tech.shaffersoftworks.remotescreens.server.render.keptVerticalScroll
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.RectShape
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.LightsDialogMetrics as D

private const val KEY_HEADLINE = "lightsHeadline"

/**
 * RPI's `LightDeviceDetailDialog` with its `LightControlDetailCard`: the room and light, the brightness or "Off",
 * when it last changed, the control of the chosen mode, the mode bar, the palette and the effects. The card scrolls
 * where the page is shorter than RPI's dialog.
 */
internal fun DocContext.lightDetailDialog(
    tileId: String,
    entityId: String,
    roomName: String,
    ui: LightsUiState,
    layout: LightsLayout,
) {
    val entityState = states[entityId] ?: return
    val mode = ui.detailMode ?: entityState.startMode()
    bindDetail(tileId, entityId)
    val dismiss = ActionRef(tileId, "lights_dismiss")
    val cardW = minOf(layout.dp(D.CARD_W_DP), layout.pageW - 2 * layout.dp(D.DIALOG_PAD_H_DP))
    val cardH = layout.pageH - 2 * layout.dp(D.DIALOG_PAD_V_DP)
    scope.Box(
        tap(Modifier.fillMaxSize().background(role(Role.SCRIM)), dismiss),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@lightDetailDialog
        val padH = doc.units.px(layout.dp(D.CARD_PAD_H_DP))
        val padV = doc.units.px(layout.dp(D.CARD_PAD_V_DP))
        Column(
            doc.tap(
                Modifier.size(doc.units.px(cardW), doc.units.px(cardH))
                    .clip(doc.rounded(layout.dp(D.CARD_CORNER_DP)))
                    .background(doc.fixed(Rpi.LightDetailCard)),
                ActionRef("_", "noop"),
            ).padding(padH, padV, padH, padV),
        ) {
            // Weight on a scroller is ignored and the content paints past the card, as in MusicPanel: an exact
            // viewport height, capped and rect-clipped, keeps it inside.
            val viewportH = doc.units.px(cardH) - 2 * padV
            Column(
                Modifier.fillMaxWidth()
                    .height(viewportH)
                    .requiredHeightIn(max = viewportH)
                    .clip(RectShape(0f, 0f, 0f, 0f))
                    .keptVerticalScroll("lights.$tileId.detail"),
                horizontal = RcHorizontalPositioning.Center,
            ) {
                doc.detailHeader(roomName, entityState.name, dismiss, layout)
                doc.detailHeadline(tileId, entityState, layout)
                Spacer(Modifier.height(doc.units.px(layout.dp(D.CONTROL_GAP_DP))))
                when (mode) {
                    LightDetailMode.BRIGHTNESS -> doc.verticalBrightnessSlider(tileId, entityState, layout)
                    LightDetailMode.COLOR_WHEEL -> doc.colorWheel(entityState, layout)
                    LightDetailMode.COLOR_TEMPERATURE -> doc.verticalTemperatureSlider(entityState, layout)
                    LightDetailMode.POWER -> Spacer(Modifier.height(doc.units.px(layout.dp(D.CONTROL_H_DP))))
                }
                Spacer(Modifier.height(doc.units.px(layout.dp(D.MODE_BAR_GAP_DP))))
                doc.modeBar(tileId, entityId, mode, layout)
                Spacer(Modifier.height(doc.units.px(layout.dp(D.PALETTE_GAP_DP))))
                doc.palette(tileId, ui.paletteIndex, layout)
                val effects = entityState.list("effect_list").filter { it.isNotBlank() }
                if (effects.isNotEmpty()) {
                    Spacer(Modifier.height(doc.units.px(layout.dp(D.EFFECT_GAP_DP))))
                    doc.effectButton(tileId, layout)
                    if (ui.isEffectMenuOpen) doc.effectMenu(tileId, effects, entityState.attr("effect"), layout)
                }
            }
        }
    }
}

/**
 * What changes the dialog's look rebuilds it; brightness only moves the fill and the headline through variables.
 */
private fun DocContext.bindDetail(tileId: String, entityId: String) {
    structural(entityId) { current ->
        listOf(
            current?.state,
            current?.attr("color_mode"),
            current?.list("supported_color_modes")?.joinToString(),
            current?.list("effect_list")?.joinToString(),
            current?.attr("effect"),
            current?.list("rgb_color")?.joinToString(),
            current?.attr("color_temp_kelvin"),
        ).joinToString("|")
    }
    bindDetailLevel(tileId, entityId)
    bind(entityId) { current ->
        listOf(VarValue.string(Vars.tile(tileId, KEY_HEADLINE), "${current[entityId].brightnessPercent()}%"))
    }
}

/** The close button, then the room in grey over the light's name. */
private fun DocContext.detailHeader(roomName: String, deviceName: String, dismiss: ActionRef, layout: LightsLayout) {
    scope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@detailHeader
        val closePx = doc.units.px(layout.dp(D.CLOSE_DP))
        Box(
            doc.tap(Modifier.size(closePx, closePx), dismiss),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            doc.icon("close", layout.dp(D.CLOSE_ICON_DP), doc.fixed(Rpi.White))
        }
        val textPad = doc.units.px(layout.dp(D.HEADER_TEXT_PAD_DP))
        Column(Modifier.horizontalWeight(1f).padding(textPad, 0f, textPad, 0f)) {
            doc.label(roomName, layout.sp(D.ROOM_SP), doc.fixed(Rpi.LightDetailSecondary))
            doc.label(deviceName, layout.sp(D.DEVICE_SP), doc.fixed(Rpi.White), Weight.SEMI_BOLD)
        }
    }
}

/** "NN%" while on, "Off" while off, and how long ago the light changed. */
private fun DocContext.detailHeadline(tileId: String, entityState: EntityState, layout: LightsLayout) {
    scope.Spacer(Modifier.height(units.px(layout.dp(D.HEADLINE_GAP_DP))))
    val ink = fixed(Rpi.White)
    if (entityState.state == "on") {
        label(text(Vars.tile(tileId, KEY_HEADLINE)), layout.sp(D.HEADLINE_SP), ink, Weight.MEDIUM)
    } else {
        label(i18n.t("ui.lights_off"), layout.sp(D.HEADLINE_SP), ink, Weight.MEDIUM)
    }
    label(
        entityState.updatedLabel(i18n),
        layout.sp(D.UPDATED_SP),
        fixed(Rpi.LightDetailSecondary),
        modifier = Modifier.padding(0f, units.px(layout.dp(D.UPDATED_TOP_DP)), 0f, 0f),
    )
}

/** Power, brightness, colour wheel and colour temperature, the chosen one ringed in white. */
private fun DocContext.modeBar(tileId: String, entityId: String, mode: LightDetailMode, layout: LightsLayout) {
    val pad = units.px(layout.dp(D.MODE_BAR_PAD_DP))
    scope.Row(
        Modifier.fillMaxWidth().clip(rounded(layout.dp(D.MODE_BAR_CORNER_DP)))
            .background(fixed(Rpi.LightDetailModeBar)).padding(pad, pad, pad, pad),
        horizontal = RcRowHorizontalPositioning.SpaceEvenly,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@modeBar
        doc.modeButton(ActionRef("e:$entityId", "toggle"), mode == LightDetailMode.POWER, layout) {
            doc.icon("power", layout.dp(D.MODE_ICON_DP), doc.fixed(Rpi.White))
        }
        doc.modeButton(modeRef(tileId, LightDetailMode.BRIGHTNESS), mode == LightDetailMode.BRIGHTNESS, layout) {
            val platePx = doc.units.px(layout.dp(D.MODE_SUN_PLATE_DP))
            Box(
                Modifier.size(platePx, platePx).clip(doc.rounded(layout.dp(D.MODE_SUN_PLATE_DP) / 2f))
                    .background(doc.fixed(Rpi.White)),
                horizontal = RcHorizontalPositioning.Center,
                vertical = RcVerticalPositioning.Center,
            ) {
                doc.icon("white-balance-sunny", layout.dp(D.MODE_SUN_DP), doc.fixed(Rpi.Black))
            }
        }
        doc.modeButton(modeRef(tileId, LightDetailMode.COLOR_WHEEL), mode == LightDetailMode.COLOR_WHEEL, layout) {
            doc.gradientChip(isRadial = true, layout)
        }
        doc.modeButton(
            modeRef(tileId, LightDetailMode.COLOR_TEMPERATURE),
            mode == LightDetailMode.COLOR_TEMPERATURE,
            layout,
        ) {
            doc.gradientChip(isRadial = false, layout)
        }
    }
}

private fun modeRef(tileId: String, mode: LightDetailMode) = ActionRef(tileId, "lights_mode", mode.key)

/** RPI's `ModeCircleButton`: a 48dp round button, with a 3dp white ring when it is the chosen mode. */
private fun DocContext.modeButton(ref: ActionRef, isSelected: Boolean, layout: LightsLayout, content: () -> Unit) {
    val sizeDp = layout.dp(D.MODE_BUTTON_DP)
    val border = if (isSelected) layout.dp(D.MODE_BORDER_DP) else 0f
    scope.Box(
        tap(
            Modifier.size(units.px(sizeDp), units.px(sizeDp)).clip(rounded(sizeDp / 2f))
                .background(fixed(if (isSelected) Rpi.White else Rpi.LightDetailModeBar))
                .padding(units.px(border)),
            ref,
        ),
    ) {
        val doc = this@modeButton
        Box(
            Modifier.fillMaxSize().clip(doc.rounded(sizeDp / 2f - border))
                .background(doc.fixed(Rpi.LightDetailModeBar)),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            content()
        }
    }
}

/** The colour wheel chip (radial, white to cyan) or the colour temperature chip (cool to warm, left to right). */
private fun DocContext.gradientChip(isRadial: Boolean, layout: LightsLayout) {
    val px = units.px(layout.dp(D.MODE_CHIP_DP))
    scope.Canvas(Modifier.size(px, px)) {
        paint {
            style(RcPaintStyle.Fill)
            if (isRadial) {
                radialGradient(px / 2f, px / 2f, px / 2f, Rpi.LightDetailWheelChip)
            } else {
                linearGradient(0f, 0f, px, 0f, Rpi.LightDetailTemperatureChip)
            }
        }
        drawCircle(px / 2f, px / 2f, px / 2f)
        paint { clearShader() }
    }
}

/** RPI's default palette, four to a row; the chosen swatch has an orange ring. */
private fun DocContext.palette(tileId: String, selectedIndex: Int?, layout: LightsLayout) {
    val swatchDp = layout.dp(D.SWATCH_DP)
    val swatchPx = units.px(swatchDp)
    scope.Column(Modifier.fillMaxWidth()) {
        val doc = this@palette
        Rpi.LightDetailPalette.chunked(D.PALETTE_COLUMNS).forEachIndexed { rowIndex, colours ->
            if (rowIndex > 0) Spacer(Modifier.height(doc.units.px(layout.dp(D.PALETTE_ROW_GAP_DP))))
            Row(
                Modifier.fillMaxWidth(),
                horizontal = RcRowHorizontalPositioning.SpaceEvenly,
                vertical = RcVerticalPositioning.Center,
            ) {
                colours.forEachIndexed { columnIndex, colour ->
                    val index = rowIndex * D.PALETTE_COLUMNS + columnIndex
                    val border = if (index == selectedIndex) doc.units.px(layout.dp(D.SWATCH_BORDER_DP)) else 0f
                    Box(
                        doc.tap(
                            Modifier.size(swatchPx, swatchPx).clip(doc.rounded(swatchDp / 2f))
                                .background(doc.fixed(if (border > 0f) Rpi.LightDetailAccent else colour))
                                .padding(border),
                            ActionRef(tileId, "lights_palette", "$index"),
                        ),
                    ) {
                        Box(Modifier.fillMaxSize().clip(doc.rounded(swatchDp / 2f)).background(doc.fixed(colour)))
                    }
                }
            }
        }
    }
}

/** The grey "Effect" button with RPI's sparkles; it opens the list of the light's effects. */
private fun DocContext.effectButton(tileId: String, layout: LightsLayout) {
    scope.Row(
        tap(
            Modifier.fillMaxWidth().height(units.px(layout.dp(D.EFFECT_H_DP)))
                .clip(rounded(layout.dp(D.EFFECT_CORNER_DP))).background(fixed(Rpi.LightDetailEffect)),
            ActionRef(tileId, "lights_effects"),
        ),
        horizontal = RcRowHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@effectButton
        doc.icon("creation", layout.dp(D.EFFECT_ICON_DP), doc.fixed(Rpi.White))
        Spacer(Modifier.width(doc.units.px(layout.dp(D.EFFECT_ICON_GAP_DP))))
        doc.label(doc.i18n.t("ui.lights_effect"), layout.sp(D.EFFECT_SP), doc.fixed(Rpi.White))
    }
}

/** RPI's effect `DropdownMenu`: up to 272dp of effects, a white dot by the running one. */
private fun DocContext.effectMenu(tileId: String, effects: List<String>, current: String?, layout: LightsLayout) {
    val itemH = layout.dp(D.MENU_ITEM_H_DP)
    val menuH = minOf(layout.dp(D.MENU_MAX_H_DP), itemH * effects.size)
    val padH = units.px(layout.dp(D.MENU_ITEM_PAD_H_DP))
    scope.Column(
        Modifier.fillMaxWidth().height(units.px(menuH)).requiredHeightIn(max = units.px(menuH))
            .clip(rounded(layout.dp(D.MENU_CORNER_DP)))
            .background(fixed(Rpi.LightDetailModeBar))
            .keptVerticalScroll("lights.$tileId.effects"),
    ) {
        val doc = this@effectMenu
        for (effect in effects) {
            val isCurrent = current != null && effect.equals(current, ignoreCase = true)
            Row(
                doc.tap(
                    Modifier.fillMaxWidth().height(doc.units.px(itemH)).padding(padH, 0f, padH, 0f),
                    ActionRef(tileId, "lights_effect", effect),
                ),
                vertical = RcVerticalPositioning.Center,
            ) {
                val dotDp = layout.dp(D.MENU_DOT_DP)
                Box(
                    Modifier.size(doc.units.px(dotDp), doc.units.px(dotDp)).clip(doc.rounded(dotDp / 2f))
                        .background(doc.fixed(if (isCurrent) Rpi.White else Rpi.LightDetailSecondary)),
                )
                Spacer(Modifier.width(doc.units.px(layout.dp(D.MENU_DOT_GAP_DP))))
                doc.label(effect, layout.sp(D.MENU_SP), doc.fixed(Rpi.White))
            }
        }
    }
}
