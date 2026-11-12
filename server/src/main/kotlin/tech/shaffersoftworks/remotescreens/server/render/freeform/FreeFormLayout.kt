@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.render.DocContext

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.requiredHeightIn
import androidx.compose.remote.creation.dsl.requiredWidthIn
import androidx.compose.remote.creation.modifiers.RectShape
import tech.shaffersoftworks.remotescreens.server.render.keptHorizontalScroll
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import tech.shaffersoftworks.remotescreens.server.render.keptVerticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.shared.Node

internal fun DocContext.resolve(node: Node, avail: Dims, axis: Axis, share: Dims): Dims {
    val nat = node.natural()
    fun one(spec: Spec, room: Float, shared: Float, natural: Float, main: Boolean) = when (spec) {
        Spec.Fill -> if (main) shared else room
        is Spec.Dp -> spec.value
        is Spec.Weight -> shared
        Spec.Wrap -> if (natural > 0f) minOf(natural, room) else room
    }
    return Dims(
        one(
            spec = Spec.of(node.size.width),
            room = avail.w,
            shared = if (axis == Axis.ROW) share.w else avail.w,
            natural = nat.w,
            main = axis == Axis.ROW,
        ),
        one(
            spec = Spec.of(node.size.height),
            room = avail.h,
            shared = if (axis == Axis.COLUMN) share.h else avail.h,
            natural = nat.h,
            main = axis == Axis.COLUMN,
        ),
    )
}

/** [sized], with a wrapped side set to what the server worked out, for components that draw to a known size. */
internal fun DocContext.measured(node: Node, axis: Axis, self: Dims): Modifier {
    var modifier = sized(node, axis)
    if (node.size.width == "wrap") modifier = modifier.width(units.px(self.w))
    if (node.size.height == "wrap") modifier = modifier.height(units.px(self.h))
    return modifier
}

internal fun DocContext.spacerSize(node: Node, axis: Axis): Modifier {
    val modifier = sized(node, axis)
    return if (node.size.width == "wrap" && node.size.height == "wrap") {
        modifier.size(units.px(Metrics.SPACER_DEFAULT_DP), units.px(Metrics.SPACER_DEFAULT_DP))
    } else {
        modifier
    }
}

internal fun DocContext.dividerSize(node: Node, axis: Axis): Modifier {
    var modifier = sized(node, axis)
    if (node.size.width == "wrap" && node.size.height == "wrap") {
        modifier = if (axis == Axis.ROW) {
            modifier.width(units.px(Metrics.DIVIDER_THICKNESS_DP)).fillMaxHeight()
        } else {
            modifier.fillMaxWidth().height(units.px(Metrics.DIVIDER_THICKNESS_DP))
        }
    }
    return modifier
}

/** Children of a wrapped row, column or box: fills become wraps so they take content size, not leftover room. */
internal fun Node.looseChildren(looseW: Boolean, looseH: Boolean): List<Node> =
    children.filter { child -> !child.hidden }.map { child ->
        var size = child.size
        if (looseW && fills(size.width)) {
            size = size.copy(width = "wrap")
        }
        if (looseH && fills(size.height)) {
            size = size.copy(height = "wrap")
        }
        if (size == child.size) {
            child
        } else {
            child.copy(size = size)
        }
    }

internal fun DocContext.container(node: Node, given: Dims, axis: Axis, pages: Pages) {
    // A wrapped row is as wide as its children, so one that fills it takes its content's width, as in the editor,
    // rather than a weight that takes all the room the row could grow to and pushes its siblings off; a wrapped
    // column's children, likewise down it. A wrapped box uses the same rewrite on each wrap axis.
    val looseW = (node.type == "row" || node.type == "box") && node.size.width == "wrap"
    val looseH = (node.type == "column" || node.type == "box") && node.size.height == "wrap"
    val children = node.looseChildren(looseW = looseW, looseH = looseH)
    // A wrapped row whose children fill its height is as tall as its tallest child, so cards side by side match,
    // rather than the fill stretching to the page; a wrapped column whose children fill its width, likewise.
    // A wrapped box pins the same way on each wrap axis that still has fill children.
    val pinH = (node.type == "row" || node.type == "box") &&
        node.size.height == "wrap" &&
        children.any { child -> fills(child.size.height) }
    val pinW = (node.type == "column" || node.type == "box") &&
        node.size.width == "wrap" &&
        children.any { child -> fills(child.size.width) }
    val self = if (pinH || pinW) {
        content(
            node,
        ).let { Dims(
            if (pinW) minOf(it.w, given.w) else given.w,
            if (pinH) minOf(it.h, given.h) else given.h,
        ) }
    } else {
        given
    }
    val inner = Dims(
        (self.w - node.style.padX).coerceAtLeast(0f),
        (self.h - node.style.padY).coerceAtLeast(0f),
    )
    val gap = node.props["gap"]?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
    val mine = when (node.type) { "row" -> Axis.ROW; "column" -> Axis.COLUMN; else -> Axis.BOX }
    val sizes = childSizes(children = children, inner = inner, axis = mine, gap = gap)
    var base = sized(node, axis)
    if (pinH) base = base.height(units.px(self.h))
    if (pinW) base = base.width(units.px(self.w))
    var modifier = styled(node = node, axis = axis, pages = pages, base = base)
    if (node.props["scroll"] == "true") {
        // Weight and fillMaxHeight are ignored on a scroller, so the list never moves and its children
        // paint through the header. An exact viewport, a max, and a clip are what make the drag take.
        modifier = if (mine == Axis.ROW) {
            val viewport = units.px(self.w)
            modifier.width(viewport)
                .requiredWidthIn(max = viewport)
                .clip(RectShape(0f, 0f, 0f, 0f))
                .keptHorizontalScroll("freeform.${node.id}")
        } else if (mine == Axis.COLUMN) {
            val viewport = units.px(self.h)
            modifier.height(viewport)
                .requiredHeightIn(max = viewport)
                .clip(RectShape(0f, 0f, 0f, 0f))
                .keptVerticalScroll("freeform.${node.id}")
        } else {
            modifier
        }
    }
    val doc = this
    fun drawChildren() {
        children.forEachIndexed { i, child ->
            if (i > 0 && gap > 0f) {
                scope.Spacer(
                    if (mine == Axis.ROW) {
                        Modifier.width(
                            units.px(gap),
                        )
                    } else {
                        Modifier.height(units.px(gap))
                    },
                )
            }
            val place = child.props["place"]
            if (place == null) {
                doc.node(
                    node = child,
                    self = sizes[i],
                    axis = mine,
                    pages = pages,
                )
            } else {
                doc.placed(child = child, place = place, self = sizes[i], axis = mine, pages = pages)
            }
        }
    }
    val alignment = node.props["alignment"]
    val arrangement = node.props["arrangement"]
    if (node.type == "flow") {
        // A flow has no spacing of its own: each child carries the gap after it and below it.
        scope.Flow(
            modifier,
            horizontal = horizontal(alignment),
            vertical = RcVerticalPositioning.Top,
        ) {
            children.forEachIndexed { i, child ->
                Box(
                    Modifier.padding(start = 0f, top = 0f, end = units.px(gap), bottom = units.px(gap)),
                ) { doc.node(node = child, self = sizes[i], axis = Axis.BOX, pages = pages) }
            }
        }
        return
    }
    when (mine) {
        Axis.COLUMN -> scope.Column(
            modifier,
            horizontal = horizontal(alignment),
            vertical = columnArrangement(arrangement),
        ) { drawChildren() }
        Axis.ROW -> scope.Row(
            modifier,
            horizontal = rowArrangement(arrangement),
            vertical = vertical(alignment),
        ) { drawChildren() }
        Axis.BOX -> {
            val parts = (alignment ?: "top-start").split('-')
            val (vertAlign, horizAlign) = if (parts.size == 2) parts[0] to parts[1] else parts[0] to parts[0]
            scope.Box(
                modifier,
                horizontal = horizontal(horizAlign),
                vertical = vertical(vertAlign),
            ) { drawChildren() }
        }
    }
}

/**
 * [child] aligned on its own within its parent ([place]): across a column (start, center, end), across a row (top,
 * center, bottom) or anywhere in a box (`top-end`). A box spanning the parent's cross axis, sized like [child] along
 * the main one, holds it where it asks.
 */
internal fun DocContext.placed(child: Node, place: String, self: Dims, axis: Axis, pages: Pages) {
    val doc = this
    fun main(spec: String, weight: (Float) -> Modifier, fill: Modifier, dp: (Float) -> Modifier): Modifier =
        when (val sizeSpec = Spec.of(spec)) {
            is Spec.Weight -> weight(sizeSpec.value)
            Spec.Fill -> fill
            is Spec.Dp -> dp(sizeSpec.value)
            Spec.Wrap -> Modifier
        }
    val parts = place.split('-')
    val (vertAlign, horizAlign) = if (parts.size == 2) parts[0] to parts[1] else parts[0] to parts[0]
    when (axis) {
        Axis.COLUMN -> scope.Box(
            main(
                spec = child.size.height,
                weight = { Modifier.verticalWeight(it) },
                fill = Modifier.verticalWeight(1f),
                dp = { Modifier.height(units.px(it)) },
            ).fillMaxWidth(),
            horizontal = horizontal(place),
            vertical = RcVerticalPositioning.Top,
        ) { doc.node(node = child, self = self, axis = Axis.BOX, pages = pages) }
        Axis.ROW -> scope.Box(
            main(
                spec = child.size.width,
                weight = { Modifier.horizontalWeight(it) },
                fill = Modifier.horizontalWeight(1f),
                dp = { Modifier.width(units.px(it)) },
            ).fillMaxHeight(),
            horizontal = RcHorizontalPositioning.Start,
            vertical = vertical(place),
        ) { doc.node(node = child, self = self, axis = Axis.BOX, pages = pages) }
        Axis.BOX -> scope.Box(
            Modifier.fillMaxSize(),
            horizontal = horizontal(horizAlign),
            vertical = vertical(vertAlign),
        ) { doc.node(node = child, self = self, axis = Axis.BOX, pages = pages) }
    }
}

/** The size of each child: fixed and wrapped ones first, then what is left shared out by weight. */
internal fun DocContext.childSizes(children: List<Node>, inner: Dims, axis: Axis, gap: Float): List<Dims> {
    if (axis == Axis.BOX) return children.map { resolve(node = it, avail = inner, axis = Axis.BOX, share = inner) }
    val main = { dims: Dims -> if (axis == Axis.ROW) dims.w else dims.h }
    val mainSpec = { node: Node -> Spec.of(
        if (axis == Axis.ROW) node.size.width else node.size.height,
    ) }
    val weightOf = { node: Node -> when (val sizeSpec = mainSpec(
        node,
    )) { is Spec.Weight -> sizeSpec.value; Spec.Fill -> 1f; else -> 0f } }
    val fixed = children.filter { weightOf(
        it,
    ) == 0f }.sumOf { main(resolve(node = it, avail = inner, axis = axis, share = Dims(0f, 0f))).toDouble() }.toFloat()
    val weights = children.sumOf { weightOf(it).toDouble() }.toFloat()
    val left = (main(inner) - fixed - gap * (children.size - 1).coerceAtLeast(0)).coerceAtLeast(0f)
    return children.map { child ->
        val weight = weightOf(child)
        val part = if (weights > 0f) left * weight / weights else 0f
        resolve(
            node = child,
            avail = inner,
            axis = axis,
            share = if (axis == Axis.ROW) Dims(part, inner.h) else Dims(inner.w, part),
        )
    }
}

internal fun horizontal(spec: String?): RcHorizontalPositioning = when (spec) {
    "center" -> RcHorizontalPositioning.Center
    "end" -> RcHorizontalPositioning.End
    else -> RcHorizontalPositioning.Start
}

internal fun vertical(spec: String?): RcVerticalPositioning = when (spec) {
    "center" -> RcVerticalPositioning.Center
    "end", "bottom" -> RcVerticalPositioning.Bottom
    else -> RcVerticalPositioning.Top
}

internal fun rowArrangement(spec: String?): RcRowHorizontalPositioning = when (spec) {
    "center" -> RcRowHorizontalPositioning.Center
    "end" -> RcRowHorizontalPositioning.End
    "spaceBetween" -> RcRowHorizontalPositioning.SpaceBetween
    "spaceEvenly" -> RcRowHorizontalPositioning.SpaceEvenly
    "spaceAround" -> RcRowHorizontalPositioning.SpaceAround
    else -> RcRowHorizontalPositioning.Start
}

internal fun columnArrangement(spec: String?): RcColumnVerticalPositioning = when (spec) {
    "center" -> RcColumnVerticalPositioning.Center
    "end" -> RcColumnVerticalPositioning.Bottom
    "spaceBetween" -> RcColumnVerticalPositioning.SpaceBetween
    "spaceEvenly" -> RcColumnVerticalPositioning.SpaceEvenly
    "spaceAround" -> RcColumnVerticalPositioning.SpaceAround
    else -> RcColumnVerticalPositioning.Top
}

internal fun align(spec: String?): RcTextAlign = when (spec) {
    "center" -> RcTextAlign.Center
    "end" -> RcTextAlign.End
    else -> RcTextAlign.Start
}

/** The name of the variable a bound component's words are sent in. */
fun nodeVar(nodeId: String) = "n.$nodeId.text"

