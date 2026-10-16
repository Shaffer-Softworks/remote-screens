@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.shared.ActionRef

private const val CHOICE_GAP_DP = 8f
private const val CHOICE_HEIGHT_DP = 44f

/**
 * A bar from 0 to 1 that follows [level], with [steps] taps across it: a tap on step i sends [refAt] (i + 1).
 * Remote Compose has no drag that reports a value, so a slider is a row of steps. The fill is painted: a width
 * bound to a variable is measured once and does not follow it.
 */
fun DocContext.levelBar(
    level: RcFloat,
    widthDp: Float,
    heightDp: Float,
    fill: RcColor,
    track: RcColor,
    steps: Int,
    refAt: (Int) -> ActionRef,
) {
    val widthPx = units.px(widthDp)
    val heightPx = units.px(heightDp)
    scope.Box(Modifier.size(widthPx, heightPx).clip(rounded(heightDp / 2f)).background(track)) {
        Canvas(Modifier.size(widthPx, heightPx)) {
            paint { color(fill); style(RcPaintStyle.Fill) }
            drawRect(0f.rf, 0f.rf, level * widthPx, heightPx.rf)
        }
        Row(Modifier.size(widthPx, heightPx)) {
            for (i in 1..steps) Box(this@levelBar.tap(Modifier.horizontalWeight(1f).fillMaxHeight(), refAt(i)))
        }
    }
}

/** A row of choices, the chosen one lit by [bindChoices]. */
fun DocContext.choiceRow(
    scopeId: String,
    group: String,
    choices: List<Pair<String, String>>,
    refFor: (String) -> ActionRef,
    heightDp: Float = CHOICE_HEIGHT_DP,
    icons: Map<String, String> = emptyMap(),
) {
    val row = Modifier.fillMaxWidth()
    scope.Row(row, horizontal = RcRowHorizontalPositioning.Start, vertical = RcVerticalPositioning.Center) {
        choices.forEachIndexed { i, (value, word) ->
            if (i > 0) Spacer(Modifier.width(this@choiceRow.units.px(CHOICE_GAP_DP)))
            this@choiceRow.key(
                ref = refFor(value),
                text = if (icons[value] == null) word else null,
                icon = icons[value],
                bg = this@choiceRow.color(Vars.option(scopeId, group, i, Vars.KEY_BG)),
                ink = this@choiceRow.color(Vars.option(scopeId, group, i, Vars.KEY_INK)),
                heightDp = heightDp,
                modifier = Modifier.horizontalWeight(1f),
            )
        }
    }
}

fun DocContext.gap(dp: Float) = scope.Spacer(Modifier.size(units.px(dp), units.px(dp)))
