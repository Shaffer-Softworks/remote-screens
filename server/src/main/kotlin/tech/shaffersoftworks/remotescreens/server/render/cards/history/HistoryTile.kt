@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.history

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.shared.Tile

internal const val HISTORY_REFRESH_S = 300
internal const val HISTORY_RESERVE_DP = 140f

/** A history graph drawn from the recorder, with its highest and lowest value. */
fun DocContext.historyTile(tile: Tile, box: TileBox, named: Boolean = true) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    refreshEvery(HISTORY_REFRESH_S)
    val points = data.history[entityId].orEmpty()

    rcScope.Column(Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP))) {
        val doc = this@historyTile

        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            Column(Modifier.horizontalWeight(1f)) {
                if (named) {
                    doc.label(
                        text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
                        sp = Type.BODY,
                        color = ink,
                        weight = Weight.SEMI_BOLD,
                    )
                }

                doc.label(
                    text = doc.text(Vars.entity(entityId, Vars.KEY_STATE)),
                    sp = Type.HEADLINE,
                    color = ink,
                    weight = Weight.MEDIUM,
                )
            }
        }

        doc.spacer(C.HISTORY_GAP_DP)

        doc.graph(
            points = points,
            wDp = box.w - C.LEVEL_BAR_HEIGHT_DP,
            hDp = (box.h - if (named) C.GRAPH_NAMED_RESERVE_DP else C.PERSON_TALL_MAX_DP).coerceAtLeast(
                C.COL_ICON_SHORT_DP,
            ),
            line = doc.color(Vars.tile(tile.id, Vars.KEY_ICON)),
            soft = ink2,
        )
    }
}

fun DocContext.graph(
    points: List<Pair<Long, Double>>,
    wDp: Float,
    hDp: Float,
    line: RcColor,
    soft: RcColor,
) {
    val w = units.px(wDp)
    val h = units.px(hDp)

    if (points.size < 2) {
        rcScope.Box(
            modifier = Modifier.size(w, h),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            this@graph.label(text = this@graph.i18n.t("ui.no_history"), sp = Type.CAPTION, color = soft)
        }

        return
    }

    val t0 = points.first().first.toFloat()
    val t1 = points.last().first.toFloat().coerceAtLeast(t0 + 1)
    val lo = points.minOf { it.second }

    val hi = points.maxOf { it.second }.let {
        if (it - lo < C.GRAPH_FLAT_EPS) lo + 1 else it
    }

    val stroke = units.px(C.GRAPH_STROKE_DP)

    fun px(point: Pair<Long, Double>) =
        ((point.first - t0) / (t1 - t0) * w) to
            (h - stroke - ((point.second - lo) / (hi - lo)).toFloat() * (h - 2 * stroke))

    rcScope.Canvas(Modifier.size(w, h)) {
        paint { color(line); strokeWidth(stroke); style(RcPaintStyle.Stroke) }
        var prev = px(points.first())

        for (point in points.drop(1)) {
            val next = px(point)
            drawLine(x1 = prev.first, y1 = prev.second, x2 = next.first, y2 = next.second)
            prev = next
        }
    }
}
