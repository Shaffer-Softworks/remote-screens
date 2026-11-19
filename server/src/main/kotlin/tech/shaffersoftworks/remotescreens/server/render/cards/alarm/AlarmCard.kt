@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.alarm

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.AlarmState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.AlarmVerb
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.cards.ref
import tech.shaffersoftworks.remotescreens.server.render.cards.section
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.shared.VarValue

private const val ALARM_FEAT_ARM_HOME = 1
private const val ALARM_FEAT_ARM_AWAY = 2
private const val ALARM_FEAT_ARM_NIGHT = 4
private const val ALARM_FEAT_ARM_VACATION = 32
private const val KEYPAD_ROW_GAP_DP = 24f
private const val KEYPAD_COLS = 3f
private const val KEYPAD_KEY_MAX_DP = 110f

internal fun DocContext.alarmCard(entityId: String, entityState: EntityState?, width: Float) {
    val features = entityState?.features() ?: 0

    val modes = listOfNotNull(
        AlarmVerb.ARM_HOME.takeIf { features and ALARM_FEAT_ARM_HOME != 0 },
        AlarmVerb.ARM_AWAY.takeIf { features and ALARM_FEAT_ARM_AWAY != 0 },
        AlarmVerb.ARM_NIGHT.takeIf { features and ALARM_FEAT_ARM_NIGHT != 0 },
        AlarmVerb.ARM_VACATION.takeIf { features and ALARM_FEAT_ARM_VACATION != 0 },
    )

    bind(entityId) {
        listOf(
            VarValue.string(
                name = Vars.tile("e:$entityId", "code"),
                value = "",
            ),
        )
    }

    spacer(CardChrome.FLOW_PAD_DP)

    rcScope.Flow(Modifier.fillMaxWidth()) {
        val doc = this@alarmCard

        for (mode in modes + AlarmVerb.DISARM) {
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
                        Domain.ALARM_PANEL.value,
                        if (mode == AlarmVerb.DISARM) {
                            AlarmState.DISARMED.wire
                        } else {
                            mode.replace(AlarmVerb.ARM_PREFIX, AlarmVerb.ARMED_PREFIX)
                        },
                    ),
                    icon = if (mode == AlarmVerb.DISARM) {
                        "shield-off"
                    } else {
                        "shield-lock"
                    },
                    heightDp = CardChrome.LEVEL_BAR_H_DP,
                )
            }
        }
    }

    if (entityState?.alarm?.codeFormat != null) {
        section(i18n.t("ui.code"))
        keypad(entityId, width)
    }
}

internal fun DocContext.keypad(entityId: String, w: Float) {
    rcScope.Box(Modifier.fillMaxWidth(), horizontal = RcHorizontalPositioning.Center) {
        this@keypad.label(
            text = this@keypad.text(Vars.tile("e:$entityId", "code")),
            sp = Type.DISPLAY,
            color = this@keypad.role(Role.INK),
            weight = Weight.MEDIUM,
            align = RcTextAlign.Center,
        )
    }

    spacer(CardChrome.FLOW_PAD_DP)

    val keyW = minOf(
        (w - KEYPAD_ROW_GAP_DP) / KEYPAD_COLS,
        KEYPAD_KEY_MAX_DP,
    )

    for (row in listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("clear", "0", "back"),
    )) {
        rcScope.Row(Modifier.fillMaxWidth(), horizontal = RcRowHorizontalPositioning.Center) {
            val doc = this@keypad

            row.forEachIndexed { i, digit ->
                if (i > 0) {
                    Spacer(Modifier.width(doc.units.px(CardChrome.SECTION_GAP_DP)))
                }

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
                        ref = ref(entityId = entityId, verb = "code_digit", arg = digit),
                        text = digit,
                        heightDp = CardChrome.MEDIA_CTRL_DP,
                        widthDp = keyW,
                    )
                }
            }
        }

        spacer(CardChrome.PANEL_CORNER_DP)
    }
}
