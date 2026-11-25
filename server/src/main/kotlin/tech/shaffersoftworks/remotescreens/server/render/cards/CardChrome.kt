@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.fill
import tech.shaffersoftworks.remotescreens.server.render.HaRenderFlags
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.bindChoices
import tech.shaffersoftworks.remotescreens.server.render.widgets.choiceRow
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.levelBar
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.server.render.widgets.toggleIcon

import androidx.compose.remote.creation.dsl.Modifier
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
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue

internal object CardChrome {
    const val CAMERA_RESERVE_DP = 120f
    const val TOGGLE_DP = 132f
    const val PANEL_CORNER_DP = 10f
    const val SECTION_GAP_DP = 12f
    const val HEADER_GAP_DP = 14f
    const val CARD_GAP_DP = 16f
    const val SECTION_TITLE_GAP_DP = 18f
    const val PANEL_PAD_DP = 20f
    const val CLOSE_KEY_DP = 40f
    const val SWATCH_DP = 46f
    const val FLOW_KEY_H_DP = 48f
    const val LEVEL_BAR_H_DP = 52f
    const val MEDIA_CTRL_DP = 60f
    const val TOGGLE_ICON_DP = 64f
    const val STEPPER_KEY_DP = 72f
    const val ROW_GAP_DP = 6f
    const val FLOW_PAD_DP = 8f
    const val PANEL_MARGIN_DP = 32f
    /** Space above and below the card, matching the lights detail dialog. */
    const val PANEL_MARGIN_V_DP = 24f
    const val PANEL_MAX_WIDTH_DP = 620f
    const val LEVEL_STEPS = HaRenderFlags.CARD_LEVEL_STEPS
    const val LEVEL_DEFAULT_MAX = 100.0
    const val FLOW_KEYS_SEGMENT_MAX = 5
    const val FLOW_KEY_SEG_H_DP = 42f
    /** Opaque alpha in the high byte; not a theme colour (see Color.kt / Themes.kt inventory). */
    const val OPAQUE_ALPHA_BYTE = 0xFF
    const val OPAQUE_ARGB = OPAQUE_ALPHA_BYTE shl 24
    const val SOURCES_TAKE = 16
}

internal fun ref(
    entityId: String,
    verb: String,
    arg: String = "",
) = ActionRef(
    tile = "e:$entityId",
    verb = verb,
    arg = arg,
)

internal fun DocContext.header(entityId: String, card: CardType) {
    val ink = role(Role.INK)
    val soft = role(Role.INK_SOFT)

    // Mockup: circular close on the left, title centred (balanced by a matching spacer).
    rcScope.Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
        val doc = this@header

        doc.key(
            ref = ActionRef("_", "close"),
            icon = "close",
            heightDp = CardChrome.CLOSE_KEY_DP,
            widthDp = CardChrome.CLOSE_KEY_DP,
            round = true,
        )

        Column(Modifier.horizontalWeight(1f), horizontal = RcHorizontalPositioning.Center) {
            if (card == CardType.ENERGY) {
                doc.label(
                    text = doc.i18n.t("ui.house"),
                    sp = Type.HEADLINE,
                    color = ink,
                    weight = Weight.SEMI_BOLD,
                    align = RcTextAlign.Center,
                )
            } else {
                doc.label(
                    text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
                    sp = Type.HEADLINE,
                    color = ink,
                    weight = Weight.SEMI_BOLD,
                    align = RcTextAlign.Center,
                )

                doc.label(
                    text = doc.text(Vars.entity(entityId, Vars.KEY_STATE)),
                    sp = Type.BODY,
                    color = soft,
                    align = RcTextAlign.Center,
                )
            }
        }

        Spacer(
            Modifier.size(
                width = doc.units.px(CardChrome.CLOSE_KEY_DP),
                height = doc.units.px(CardChrome.CLOSE_KEY_DP),
            ),
        )
    }
}

internal fun DocContext.bigToggle(
    entityId: String,
    entityState: EntityState?,
    verb: String = "toggle",
) {
    val spec = Catalogue.of(entityState?.domain?.value.orEmpty())
    val action = ref(entityId, verb)
    predict(action, entityId)

    rcScope.Box(
        Modifier.fillMaxWidth(),
        horizontal = RcHorizontalPositioning.Center,
    ) {
        val doc = this@bigToggle

        Box(
            modifier = doc.tap(
                Modifier.size(
                    width = doc.units.px(CardChrome.TOGGLE_DP),
                    height = doc.units.px(CardChrome.TOGGLE_DP),
                ).clip(
                    doc.rounded((CardChrome.TOGGLE_DP) / 2f),
                ).background(
                    color = doc.color(
                        name = Vars.tile(
                            tileId = CardRenderer.TILE,
                            key = Vars.KEY_BG,
                        ),
                    ),
                ),
                action,
            ),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            doc.toggleIcon(
                entityId = entityId,
                off = spec?.icon ?: "power",
                on = spec?.iconOn,
                sizeDp = CardChrome.TOGGLE_ICON_DP,
                color = doc.color(
                    Vars.tile(
                        tileId = CardRenderer.TILE,
                        key = Vars.KEY_BG_INK,
                    ),
                ),
            )
        }
    }
}

internal fun DocContext.section(title: String) {
    spacer(CardChrome.SECTION_TITLE_GAP_DP)

    label(
        text = title,
        sp = Type.BODY,
        color = role(Role.INK_SOFT),
        weight = Weight.SEMI_BOLD,
    )

    spacer(CardChrome.FLOW_PAD_DP)
}

internal fun DocContext.bar(
    entityId: String,
    verb: String,
    width: Float,
    min: Double = 0.0,
    max: Double = CardChrome.LEVEL_DEFAULT_MAX,
    level: String = Vars.KEY_LEVEL,
) {
    levelBar(
        level = float(Vars.entity(entityId, level)),
        widthDp = width,
        heightDp = CardChrome.LEVEL_BAR_H_DP,
        fill = fill(Role.ACCENT),
        track = role(Role.TRACK),
        steps = CardChrome.LEVEL_STEPS,
    ) { step ->
        val value = min + (max - min) * step / CardChrome.LEVEL_STEPS.toDouble()

        val action = ref(
            entityId = entityId,
            verb = verb,
            arg = if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                "%.1f".format(
                    java.util.Locale.ROOT,
                    value,
                )
            },
        )

        predict(action, entityId)
        action
    }
}

internal fun DocContext.swatches(colors: List<Pair<Int, ActionRef>>) {
    rcScope.Row(
        Modifier.fillMaxWidth(),
        horizontal = RcRowHorizontalPositioning.SpaceBetween,
    ) {
        val doc = this@swatches

        for ((argb, r) in colors) {
            Box(
                doc.tap(
                    Modifier.size(
                        doc.units.px(CardChrome.SWATCH_DP),
                        doc.units.px(CardChrome.SWATCH_DP),
                    ).clip(
                        doc.rounded((CardChrome.SWATCH_DP) / 2f),
                    ).background(doc.fixed(argb or CardChrome.OPAQUE_ARGB)),
                    r,
                ),
            )
        }
    }
}

internal fun DocContext.flowKeys(
    entityId: String,
    verb: String,
    choices: List<Pair<String, String>>,
    isSegmented: Boolean = false,
    chosen: (EntityState) -> String?,
) {
    bindChoices(
        scopeId = entityId,
        group = verb,
        entityId = entityId,
        choices = choices.map { it.first },
        chosen = chosen,
    )

    if (isSegmented || choices.size in 2..CardChrome.FLOW_KEYS_SEGMENT_MAX) {
        choiceRow(
            scopeId = entityId,
            group = verb,
            choices = choices,
            refFor = { ref(entityId = entityId, verb = verb, arg = it) },
            heightDp = CardChrome.FLOW_KEY_SEG_H_DP,
        )

        return
    }

    rcScope.Flow(Modifier.fillMaxWidth()) {
        val doc = this@flowKeys

        choices.forEachIndexed { index, (value, word) ->
            Box(
                Modifier.padding(
                    start = 0f,
                    top = 0f,
                    end = doc.units.px(CardChrome.FLOW_PAD_DP),
                    bottom = doc.units.px(CardChrome.FLOW_PAD_DP),
                ),
            ) {
                doc.key(
                    ref = ref(entityId = entityId, verb = verb, arg = value),
                    text = word,
                    bg = doc.color(
                        Vars.option(
                            scope = entityId,
                            group = verb,
                            index = index,
                            key = Vars.KEY_BG,
                        ),
                    ),
                    ink = doc.color(
                        Vars.option(
                            scope = entityId,
                            group = verb,
                            index = index,
                            key = Vars.KEY_INK,
                        ),
                    ),
                    heightDp = CardChrome.FLOW_KEY_SEG_H_DP,
                )
            }
        }
    }
}
