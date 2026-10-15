@file:Suppress("RestrictedApi")

package dev.remotescreens.server.render

import androidx.compose.remote.core.operations.layout.animation.AnimationSpec
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.animationSpec
import androidx.compose.remote.creation.dsl.RcBorderShape
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcStrokeCap
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.border
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalScroll
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.marquee
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import dev.remotescreens.server.data.homeassistant.EntityState
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.Catalogue
import dev.remotescreens.shared.Node
import dev.remotescreens.shared.VarValue
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The axis of the container a component sits in; a weight only counts along it. */
private enum class Axis { ROW, COLUMN, BOX }

/** A component's size in dp, as near as the server can tell before the player measures it. */
internal data class Dims(val w: Float, val h: Float)

/** How sizes are written in a layout: `wrap`, `fill`, `dp:N` or `weight:N`. */
private sealed interface Spec {
    data object Wrap : Spec
    data object Fill : Spec
    data class Dp(val v: Float) : Spec
    data class Weight(val v: Float) : Spec

    companion object {
        fun of(s: String): Spec = when {
            s == "fill" -> Fill
            s.startsWith("dp:") -> s.removePrefix("dp:").toFloatOrNull()?.let { Dp(it) } ?: Wrap
            s.startsWith("weight:") -> s.removePrefix("weight:").toFloatOrNull()?.takeIf { it > 0f }?.let { Weight(it) } ?: Wrap
            else -> Wrap
        }
    }
}

/** What a wrapped leaf takes when nothing else says, so a weight or a card has something to share out. */
private fun Node.natural(): Dims = when (type) {
    "text" -> Dims(120f, (props["fontSize"]?.toFloatOrNull() ?: Type.TITLE) * 1.4f)
    "icon" -> (props["size"]?.toFloatOrNull() ?: 32f).let { Dims(it, it) }
    "button" -> Dims(140f, 48f)
    "spacer" -> Dims(8f, 8f)
    "divider" -> Dims(1f, 1f)
    "slider" -> Dims(240f, 28f)
    "image", "tile" -> Dims(200f, 160f)
    "clock" -> (props["fontSize"]?.toFloatOrNull() ?: Type.DISPLAY).let { sp ->
        Dims(maxOf(160f, sp * 3.2f), sp * 1.4f + if (props["show"] == "both") 24f else 0f)
    }
    "badge" -> Dims(120f, 32f)
    "avatar" -> (props["size"]?.toFloatOrNull() ?: 56f).let { Dims(it, it) }
    "progress" -> Dims(240f, 8f)
    "graph" -> Dims(280f, 120f)
    "switch" -> Dims(52f, 30f)
    "stepper" -> Dims(200f, 48f)
    "choices" -> Dims(320f, 44f)
    "gauge" -> (props["size"]?.toFloatOrNull() ?: 140f).let { Dims(it, it) }
    "analog" -> (props["size"]?.toFloatOrNull() ?: 160f).let { Dims(it, it) }
    "iconbutton" -> (props["size"]?.toFloatOrNull() ?: 64f).let { Dims(it, it) }
    "keys" -> (props["size"]?.toFloatOrNull() ?: 48f).let { Dims(it * 3 + 20f, it) }
    "swatches" -> (props["size"]?.toFloatOrNull() ?: 40f).let { s -> (if (props["show"] == "white") 6 else 8).let { k -> Dims(s * k + 10f * (k - 1), s) } }
    "keypad" -> (props["size"]?.toFloatOrNull() ?: 56f).let { Dims(it * 1.5f * 3 + 24f, 56f + 4 * (it + 10f)) }
    else -> Dims(0f, 0f)
}

/** About the size [n]'s content takes: a leaf's natural size, a container's children laid end to end, plus padding. */
internal fun content(n: Node): Dims {
    fun side(spec: String, natural: Float) = (Spec.of(spec) as? Spec.Dp)?.v ?: natural
    if (n.type !in setOf("column", "row", "box", "flow")) {
        val nat = n.natural()
        val lines = if (n.type == "text") (n.props["lines"]?.toFloatOrNull() ?: 1f).coerceAtLeast(1f) else 1f
        return Dims(side(n.size.width, nat.w), side(n.size.height, nat.h * lines))
    }
    val kids = n.children.filter { !it.hidden }.map(::content)
    val gap = (n.props["gap"]?.toFloatOrNull() ?: 0f) * (kids.size - 1).coerceAtLeast(0)
    val inner = when (n.type) {
        "row" -> Dims(kids.sumOf { it.w.toDouble() }.toFloat() + gap, kids.maxOfOrNull { it.h } ?: 0f)
        "column" -> Dims(kids.maxOfOrNull { it.w } ?: 0f, kids.sumOf { it.h.toDouble() }.toFloat() + gap)
        else -> Dims(kids.maxOfOrNull { it.w } ?: 0f, kids.maxOfOrNull { it.h } ?: 0f)
    }
    val pad = 2 * n.style.padding
    return Dims(side(n.size.width, inner.w + pad), side(n.size.height, inner.h + pad))
}

private fun fills(spec: String) = spec == "fill" || spec.startsWith("weight:")

/**
 * A free-form page: [root] laid out in the content area at ([xDp], [yDp]), [wDp] by [hDp]. Containers become the
 * player's rows, columns and boxes; leaves reuse the widgets the tiles draw with.
 */
fun DocContext.freeForm(
    root: Node,
    xDp: Float,
    yDp: Float,
    wDp: Float,
    hDp: Float,
    pageIndex: (String) -> Int?,
    pageTitle: (String) -> String? = { null },
) {
    val pages = Pages(pageIndex, pageTitle)
    at(xDp, yDp) {
        scope.Box(Modifier.size(u.px(wDp), u.px(hDp))) {
            this@freeForm.node(root, this@freeForm.resolve(root, Dims(wDp, hDp), Axis.BOX, Dims(wDp, hDp)), Axis.BOX, pages)
        }
    }
}

/** The screen's pages: the index a page tap goes to, and the title a navigation card shows when it names none. */
private class Pages(val index: (String) -> Int?, val title: (String) -> String?)

/** The size of [n] within its parent's inner space [avail]; [share] is what a weight of it comes to. */
private fun DocContext.resolve(n: Node, avail: Dims, axis: Axis, share: Dims): Dims {
    val nat = n.natural()
    fun one(s: Spec, room: Float, shared: Float, natural: Float, main: Boolean) = when (s) {
        Spec.Fill -> if (main) shared else room
        is Spec.Dp -> s.v
        is Spec.Weight -> shared
        Spec.Wrap -> if (natural > 0f) minOf(natural, room) else room
    }
    return Dims(
        one(Spec.of(n.size.width), avail.w, if (axis == Axis.ROW) share.w else avail.w, nat.w, axis == Axis.ROW),
        one(Spec.of(n.size.height), avail.h, if (axis == Axis.COLUMN) share.h else avail.h, nat.h, axis == Axis.COLUMN),
    )
}

/**
 * The modifier for [n]'s size: weights along the parent's axis, fills and dp otherwise. A fill along a row or a
 * column is a weight of 1, so components that fill share the room, as in the editor, rather than the first taking all.
 */
private fun DocContext.sized(n: Node, axis: Axis): Modifier {
    var m: Modifier = Modifier
    m = when (val s = Spec.of(n.size.width)) {
        Spec.Fill -> if (axis == Axis.ROW) m.horizontalWeight(1f) else m.fillMaxWidth()
        is Spec.Dp -> m.width(u.px(s.v))
        is Spec.Weight -> if (axis == Axis.ROW) m.horizontalWeight(s.v) else m.fillMaxWidth()
        Spec.Wrap -> m
    }
    m = when (val s = Spec.of(n.size.height)) {
        Spec.Fill -> if (axis == Axis.COLUMN) m.verticalWeight(1f) else m.fillMaxHeight()
        is Spec.Dp -> m.height(u.px(s.v))
        is Spec.Weight -> if (axis == Axis.COLUMN) m.verticalWeight(s.v) else m.fillMaxHeight()
        Spec.Wrap -> m
    }
    return m
}

/** Components whose keys inside take the taps, so the component as a whole takes none. */
private val OWN_TAPS = setOf("tile", "slider", "stepper", "choices", "keys", "swatches", "keypad")

/**
 * The size, the rounded background and outline, the taps, then the padding: the order `key` builds its own modifier
 * in. A bound component with `showWhen` is drawn only while its entity is on (or off).
 */
private fun DocContext.styled(n: Node, axis: Axis, pages: Pages, base: Modifier = sized(n, axis)): Modifier {
    var m = base
    val id = n.bind?.entityId
    val showWhen = n.props["showWhen"]
    if (id != null && (showWhen == "on" || showWhen == "off")) {
        bindEntity(id)
        m = m.visibility(int(Vars.e(id, showWhen)))
        appear(n.props["animate"])?.let { (enter, exit) -> m = m.animationSpec(enterAnimation = enter, exitAnimation = exit) }
    }
    val bg = n.style.background?.let { paint(it) }
    if (n.style.corner > 0f) m = m.clip(rounded(n.style.corner))
    if (bg != null) m = m.background(bg)
    if (n.style.border > 0f) {
        val ink = n.style.borderColor?.let { paint(it) } ?: role(Role.DIVIDER)
        m = m.border(u.px(n.style.border), u.px(n.style.corner), ink, if (n.style.corner > 0f) RcBorderShape.RoundedRectangle else RcBorderShape.Rectangle)
    }
    if (n.type !in OWN_TAPS) {
        val (onTap, onHold) = refs(n, pages)
        m = tap(m, onTap, onHold)
    }
    if (n.style.padding > 0f) m = m.padding(u.px(n.style.padding))
    return m
}

/** How a component that shows and hides comes and goes: a fade unless [animate] names a slide, or `none`. */
private fun appear(animate: String?): Pair<AnimationSpec.ANIMATION, AnimationSpec.ANIMATION>? = when (animate) {
    "none" -> null
    "left" -> AnimationSpec.ANIMATION.SLIDE_LEFT to AnimationSpec.ANIMATION.SLIDE_LEFT
    "right" -> AnimationSpec.ANIMATION.SLIDE_RIGHT to AnimationSpec.ANIMATION.SLIDE_RIGHT
    "up" -> AnimationSpec.ANIMATION.SLIDE_TOP to AnimationSpec.ANIMATION.SLIDE_TOP
    "down" -> AnimationSpec.ANIMATION.SLIDE_BOTTOM to AnimationSpec.ANIMATION.SLIDE_BOTTOM
    else -> AnimationSpec.ANIMATION.FADE_IN to AnimationSpec.ANIMATION.FADE_OUT
}

/** What a tap and a hold on [n] send, through the same router as a tile's. */
private fun DocContext.refs(n: Node, pages: Pages): Pair<ActionRef?, ActionRef?> {
    val t = n.asTile() ?: return null to null
    val spec = Catalogue.of(t.effectiveKind())
    val tap = tapRef(t, t.tap, spec, pages.index, hold = false)
    val hold = tapRef(t, t.hold, spec, pages.index, hold = true)
    val id = t.entityId
    if (tap != null && id != null && spec?.predict?.containsKey(tap.verb) == true) predict(tap, id)
    return tap to hold
}

/** A theme role (`card`, `ink_soft`), a pastel key (`yellow`) or a `#RRGGBB` / `#AARRGGBB` colour, or null for none. */
internal fun DocContext.paint(key: String): RcColor? =
    Pastel.of(key)?.let { pastel(it) } ?: Themes.parse(key)?.let { fixed(it) } ?: Role.of(key)?.let { role(it) }

/** The ARGB a tile lights up in: its pastel key or own hex colour, else [fallback]. */
internal fun DocContext.accent(key: String?, fallback: Pastel): Int =
    Pastel.of(key)?.argb ?: key?.let(Themes::parse) ?: fallback.argb

private fun DocContext.node(n: Node, self: Dims, axis: Axis, pages: Pages) {
    if (n.hidden) return
    when (n.type) {
        "column", "row", "box", "flow" -> container(n, self, axis, pages)
        "text" -> textNode(n, axis, pages)
        "icon" -> iconNode(n, axis, pages)
        "button" -> buttonNode(n, axis, pages)
        "spacer" -> scope.Spacer(styled(n, axis, pages, spacerSize(n, axis)))
        "divider" -> scope.Box(styled(n, axis, pages, dividerSize(n, axis)).background(paint(n.props["color"] ?: "divider") ?: role(Role.DIVIDER)))
        "slider" -> sliderNode(n, self, axis, pages)
        "image" -> imageNode(n, self, axis, pages)
        "tile" -> tileNode(n, self, axis, pages)
        "clock" -> clockNode(n, axis, pages)
        "badge" -> badgeNode(n, axis, pages)
        "avatar" -> avatarNode(n, axis, pages)
        "progress" -> progressNode(n, self, axis, pages)
        "graph" -> graphNode(n, self, axis, pages)
        "switch" -> switchNode(n, axis, pages)
        "stepper" -> stepperNode(n, axis, pages)
        "choices" -> choicesNode(n, self, axis, pages)
        "gauge" -> gaugeNode(n, axis, pages)
        "analog" -> analogNode(n, axis, pages)
        "iconbutton" -> iconButtonNode(n, axis, pages)
        "keys" -> keysNode(n, axis, pages)
        "swatches" -> swatchesNode(n, axis, pages)
        "keypad" -> keypadNode(n, axis, pages)
    }
}

/** [sized], with a wrapped side set to what the server worked out, for components that draw to a known size. */
private fun DocContext.measured(n: Node, axis: Axis, self: Dims): Modifier {
    var m = sized(n, axis)
    if (n.size.width == "wrap") m = m.width(u.px(self.w))
    if (n.size.height == "wrap") m = m.height(u.px(self.h))
    return m
}

private fun DocContext.spacerSize(n: Node, axis: Axis): Modifier {
    val m = sized(n, axis)
    return if (n.size.width == "wrap" && n.size.height == "wrap") m.size(u.px(8f), u.px(8f)) else m
}

private fun DocContext.dividerSize(n: Node, axis: Axis): Modifier {
    var m = sized(n, axis)
    if (n.size.width == "wrap" && n.size.height == "wrap") {
        m = if (axis == Axis.ROW) m.width(u.px(1f)).fillMaxHeight() else m.fillMaxWidth().height(u.px(1f))
    }
    return m
}

private fun DocContext.container(n: Node, given: Dims, axis: Axis, pages: Pages) {
    // A wrapped row is as wide as its children, so one that fills it takes its content's width, as in the editor,
    // rather than a weight that takes all the room the row could grow to and pushes its siblings off; a wrapped
    // column's children, likewise down it.
    val looseW = n.type == "row" && n.size.width == "wrap"
    val looseH = n.type == "column" && n.size.height == "wrap"
    val kids = n.children.filter { !it.hidden }.map { c ->
        when {
            looseW && fills(c.size.width) -> c.copy(size = c.size.copy(width = "wrap"))
            looseH && fills(c.size.height) -> c.copy(size = c.size.copy(height = "wrap"))
            else -> c
        }
    }
    // A wrapped row whose children fill its height is as tall as its tallest child, so cards side by side match,
    // rather than the fill stretching to the page; a wrapped column whose children fill its width, likewise.
    val pinH = n.type == "row" && n.size.height == "wrap" && kids.any { fills(it.size.height) }
    val pinW = n.type == "column" && n.size.width == "wrap" && kids.any { fills(it.size.width) }
    val self = if (pinH || pinW) content(n).let { Dims(if (pinW) minOf(it.w, given.w) else given.w, if (pinH) minOf(it.h, given.h) else given.h) } else given
    val pad = n.style.padding
    val inner = Dims((self.w - 2 * pad).coerceAtLeast(0f), (self.h - 2 * pad).coerceAtLeast(0f))
    val gap = n.props["gap"]?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
    val mine = when (n.type) { "row" -> Axis.ROW; "column" -> Axis.COLUMN; else -> Axis.BOX }
    val sizes = childSizes(kids, inner, mine, gap)
    var base = sized(n, axis)
    if (pinH) base = base.height(u.px(self.h))
    if (pinW) base = base.width(u.px(self.w))
    var m = styled(n, axis, pages, base)
    if (n.props["scroll"] == "true") m = if (mine == Axis.ROW) m.horizontalScroll() else if (mine == Axis.COLUMN) m.verticalScroll() else m
    val ctx = this
    fun children() {
        kids.forEachIndexed { i, c ->
            if (i > 0 && gap > 0f) scope.Spacer(if (mine == Axis.ROW) Modifier.width(u.px(gap)) else Modifier.height(u.px(gap)))
            val place = c.props["place"]
            if (place == null) ctx.node(c, sizes[i], mine, pages) else ctx.placed(c, place, sizes[i], mine, pages)
        }
    }
    val alignment = n.props["alignment"]
    val arrangement = n.props["arrangement"]
    if (n.type == "flow") {
        // A flow has no spacing of its own: each child carries the gap after it and below it.
        scope.Flow(m, horizontal = horizontal(alignment), vertical = RcVerticalPositioning.Top) {
            kids.forEachIndexed { i, c ->
                Box(Modifier.padding(0f, 0f, u.px(gap), u.px(gap))) { ctx.node(c, sizes[i], Axis.BOX, pages) }
            }
        }
        return
    }
    when (mine) {
        Axis.COLUMN -> scope.Column(m, horizontal = horizontal(alignment), vertical = columnArrangement(arrangement)) { children() }
        Axis.ROW -> scope.Row(m, horizontal = rowArrangement(arrangement), vertical = vertical(alignment)) { children() }
        Axis.BOX -> {
            val (v, h) = (alignment ?: "top-start").split('-').let { if (it.size == 2) it[0] to it[1] else it[0] to it[0] }
            scope.Box(m, horizontal = horizontal(h), vertical = vertical(v)) { children() }
        }
    }
}

/**
 * [c] aligned on its own within its parent ([place]): across a column (start, center, end), across a row (top,
 * center, bottom) or anywhere in a box (`top-end`). A box spanning the parent's cross axis, sized like [c] along
 * the main one, holds it where it asks.
 */
private fun DocContext.placed(c: Node, place: String, self: Dims, axis: Axis, pages: Pages) {
    val ctx = this
    fun main(spec: String, weight: (Float) -> Modifier, fill: Modifier, dp: (Float) -> Modifier): Modifier =
        when (val s = Spec.of(spec)) {
            is Spec.Weight -> weight(s.v)
            Spec.Fill -> fill
            is Spec.Dp -> dp(s.v)
            Spec.Wrap -> Modifier
        }
    val (v, h) = place.split('-').let { if (it.size == 2) it[0] to it[1] else it[0] to it[0] }
    when (axis) {
        Axis.COLUMN -> scope.Box(
            main(c.size.height, { Modifier.verticalWeight(it) }, Modifier.verticalWeight(1f), { Modifier.height(u.px(it)) }).fillMaxWidth(),
            horizontal = horizontal(place), vertical = RcVerticalPositioning.Top,
        ) { ctx.node(c, self, Axis.BOX, pages) }
        Axis.ROW -> scope.Box(
            main(c.size.width, { Modifier.horizontalWeight(it) }, Modifier.horizontalWeight(1f), { Modifier.width(u.px(it)) }).fillMaxHeight(),
            horizontal = RcHorizontalPositioning.Start, vertical = vertical(place),
        ) { ctx.node(c, self, Axis.BOX, pages) }
        Axis.BOX -> scope.Box(Modifier.fillMaxSize(), horizontal = horizontal(h), vertical = vertical(v)) { ctx.node(c, self, Axis.BOX, pages) }
    }
}

/** The size of each child: fixed and wrapped ones first, then what is left shared out by weight. */
private fun DocContext.childSizes(kids: List<Node>, inner: Dims, axis: Axis, gap: Float): List<Dims> {
    if (axis == Axis.BOX) return kids.map { resolve(it, inner, Axis.BOX, inner) }
    val main = { d: Dims -> if (axis == Axis.ROW) d.w else d.h }
    val mainSpec = { n: Node -> Spec.of(if (axis == Axis.ROW) n.size.width else n.size.height) }
    val weightOf = { n: Node -> when (val s = mainSpec(n)) { is Spec.Weight -> s.v; Spec.Fill -> 1f; else -> 0f } }
    val fixed = kids.filter { weightOf(it) == 0f }.sumOf { main(resolve(it, inner, axis, Dims(0f, 0f))).toDouble() }.toFloat()
    val weights = kids.sumOf { weightOf(it).toDouble() }.toFloat()
    val left = (main(inner) - fixed - gap * (kids.size - 1).coerceAtLeast(0)).coerceAtLeast(0f)
    return kids.map { c ->
        val w = weightOf(c)
        val part = if (weights > 0f) left * w / weights else 0f
        resolve(c, inner, axis, if (axis == Axis.ROW) Dims(part, inner.h) else Dims(inner.w, part))
    }
}

private fun horizontal(s: String?): RcHorizontalPositioning = when (s) {
    "center" -> RcHorizontalPositioning.Center
    "end" -> RcHorizontalPositioning.End
    else -> RcHorizontalPositioning.Start
}

private fun vertical(s: String?): RcVerticalPositioning = when (s) {
    "center" -> RcVerticalPositioning.Center
    "end", "bottom" -> RcVerticalPositioning.Bottom
    else -> RcVerticalPositioning.Top
}

private fun rowArrangement(s: String?): RcRowHorizontalPositioning = when (s) {
    "center" -> RcRowHorizontalPositioning.Center
    "end" -> RcRowHorizontalPositioning.End
    "spaceBetween" -> RcRowHorizontalPositioning.SpaceBetween
    "spaceEvenly" -> RcRowHorizontalPositioning.SpaceEvenly
    "spaceAround" -> RcRowHorizontalPositioning.SpaceAround
    else -> RcRowHorizontalPositioning.Start
}

private fun columnArrangement(s: String?): RcColumnVerticalPositioning = when (s) {
    "center" -> RcColumnVerticalPositioning.Center
    "end" -> RcColumnVerticalPositioning.Bottom
    "spaceBetween" -> RcColumnVerticalPositioning.SpaceBetween
    "spaceEvenly" -> RcColumnVerticalPositioning.SpaceEvenly
    "spaceAround" -> RcColumnVerticalPositioning.SpaceAround
    else -> RcColumnVerticalPositioning.Top
}

private fun align(s: String?): RcTextAlign = when (s) {
    "center" -> RcTextAlign.Center
    "end" -> RcTextAlign.End
    else -> RcTextAlign.Start
}

/** The name of the variable a bound component's words are sent in. */
fun nodeVar(nodeId: String) = "n.$nodeId.text"

private fun DocContext.textNode(n: Node, axis: Axis, pages: Pages) {
    val sp = n.props["fontSize"]?.toFloatOrNull() ?: Type.TITLE
    val weight = n.props["weight"]?.toFloatOrNull() ?: Weight.NORMAL
    val ink = n.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val marquee = n.props["marquee"] == "true"
    val lines = if (marquee) 1 else n.props["lines"]?.toIntOrNull()?.coerceIn(1, 20) ?: 1
    // Words wider than their space glide back and forth at 30 dp a second, forever.
    val m = styled(n, axis, pages).let { if (marquee) it.marquee(Int.MAX_VALUE, 0, 1000f, 1200f, 0f, 30f) else it }
    val b = n.bind
    if (b == null) {
        label(n.props["text"].orEmpty(), sp, ink, weight, lines, align(n.props["align"]), m)
        return
    }
    bind(b.entityId) { states ->
        val e = states[b.entityId]
        val v = Present.view(e, i18n)
        val raw = when (b.format) {
            "value" -> v.value
            "name" -> v.name
            "attribute" -> b.attribute?.let { e?.attr(it) } ?: ""
            else -> v.stateText
        }
        listOf(VarValue.string(nodeVar(n.id), b.prefix + raw + b.suffix))
    }
    label(text(nodeVar(n.id)), sp, ink, weight, lines, align(n.props["align"]), m)
}

/** An icon bound to an entity lights up in its pastel while the entity is on, like a tile's. */
private fun DocContext.iconNode(n: Node, axis: Axis, pages: Pages) {
    val sizeDp = n.props["size"]?.toFloatOrNull() ?: 32f
    val id = n.bind?.entityId
    val domain = id?.substringBefore('.')
    val name = n.props["icon"] ?: id?.let { states[it]?.attr("icon") } ?: domain?.let { Catalogue.of(it)?.icon }
    val explicit = n.props["color"]?.let { paint(it) }
    val ink = when {
        explicit != null -> explicit
        id != null -> {
            bindTileColors(n.id, id, Pastel.forDomain(domain))
            color(Vars.t(n.id, "icon"))
        }
        else -> role(Role.INK)
    }
    scope.Box(styled(n, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val c = this@iconNode
        val on = n.props["iconOn"]
        if (id != null && on != null) {
            c.bindEntity(id)
            c.toggleIcon(id, name ?: Icons.FALLBACK, on, sizeDp, ink)
        } else {
            c.icon(name, sizeDp, ink)
        }
    }
}

private fun DocContext.buttonNode(n: Node, axis: Axis, pages: Pages) {
    val bg = n.props["color"]?.let { paint(it) } ?: role(Role.KEY)
    val ink = n.props["ink"]?.let { paint(it) } ?: role(Role.INK)
    var base = sized(n, axis)
    if (n.size.height == "wrap") base = base.height(u.px(48f))
    base = base.clip(rounded(if (n.style.corner > 0f) n.style.corner else 48f / 2.6f))
    if (n.style.background == null) base = base.background(bg)
    val m = styled(n, axis, pages, base).let { if (n.style.padding == 0f) it.padding(u.px(12f), 0f, u.px(12f), 0f) else it }
    val word = n.props["text"]?.takeIf { it.isNotEmpty() }
    val icon = n.props["icon"]?.takeIf { it.isNotEmpty() }
    scope.Row(m, horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val c = this@buttonNode
        if (icon != null) c.icon(icon, if (word == null) 24f else 20f, ink)
        if (icon != null && word != null) Spacer(Modifier.width(c.u.px(8f)))
        if (word != null) c.label(word, Type.BODY, ink, weight = Weight.MEDIUM)
    }
}

/** A stepped bar over the entity's level, as on a tile: a tap on step i sets i tenths of its range. */
private fun DocContext.sliderNode(n: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    if (id == null) {
        scope.Box(styled(n, axis, pages))
        return
    }
    bindEntity(id)
    bindTileColors(n.id, id, accent(n.props["color"], Pastel.forDomain(id.substringBefore('.'))))
    val domain = id.substringBefore('.')
    val e = states[id]
    val verb = when (domain) { "light" -> "brightness"; "fan" -> "percentage"; "cover" -> "position"; "media_player" -> "volume"; else -> "value" }
    val (min, max) = if (domain in setOf("number", "input_number")) (e?.number("min") ?: 0.0) to (e?.number("max") ?: 100.0) else 0.0 to 100.0
    val steps = n.props["steps"]?.toIntOrNull()?.coerceIn(2, 20) ?: 10
    val h = if (n.size.height == "wrap") 28f else self.h
    scope.Box(styled(n, axis, pages)) {
        val c = this@sliderNode
        c.levelBar(c.float(Vars.e(id, "level")), self.w - 2 * n.style.padding, h - 2 * n.style.padding, c.color(Vars.t(n.id, "icon")), c.role(Role.TRACK), steps) { i ->
            val v = min + (max - min) * i / steps
            ActionRef(n.id, verb, if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(Locale.ROOT, v))
        }
    }
}

/** A camera's frames, or the entity's picture (a person, album art), fetched by the add-on. */
private fun DocContext.imageNode(n: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    val m = styled(n, axis, pages)
    if (id == null) {
        scope.Box(m.background(role(Role.TRACK)))
        return
    }
    val w = u.px(self.w).toInt().coerceAtLeast(1)
    val h = u.px(self.h).toInt().coerceAtLeast(1)
    val camera = id.startsWith("camera.")
    val fit = n.props["fit"] == "whole"
    val slot = "img.${n.id}"
    picture(PictureSlot(slot, if (camera) "/api/camera_proxy/$id" else "attr:$id:entity_picture", w, h, if (camera) 10 else 0, crop = !fit))
    scope.Image(image(slot, w, h), m, contentScale = if (fit) RcContentScale.Fit else RcContentScale.Crop)
}

/** One of the grid's cards, at the size the layout gives it. */
private fun DocContext.tileNode(n: Node, self: Dims, axis: Axis, pages: Pages) {
    val t = n.tile
    val spec = t?.let { Catalogue.of(it.effectiveKind()) }
    val m = sized(n, axis).let { if (n.size.width == "wrap" && n.size.height == "wrap") it.size(u.px(self.w), u.px(self.h)) else it }
    val lacksEntity = t?.entityId == null && spec?.needsEntity == true
    if (t == null || spec == null || lacksEntity) {
        scope.Box(m)
        return
    }
    val named = if (t.effectiveKind() == "nav" && t.name == null) t.copy(name = t.options["page"]?.let(pages.title)) else t
    scope.Box(m) {
        this@tileNode.tile(named, TileBox(0f, 0f, self.w, self.h, 1, 1, full = false), pages.index)
    }
}

/** The time, the date, or the time over the date, from the values the player keeps itself. */
private fun DocContext.clockNode(n: Node, axis: Axis, pages: Pages) {
    val sp = n.props["fontSize"]?.toFloatOrNull() ?: Type.DISPLAY
    val ink = n.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val show = n.props["show"] ?: "time"
    val a = align(n.props["align"])
    scope.Column(styled(n, axis, pages), horizontal = horizontal(n.props["align"])) {
        val c = this@clockNode
        if (show != "date") c.label(c.text(Vars.TIME), sp, ink, Weight.LIGHT, align = a)
        if (show == "date") c.label(c.text(Vars.DATE), sp, ink, align = a, lines = 2)
        if (show == "both") c.label(c.text(Vars.DATE), maxOf(sp * 0.33f, Type.BODY), c.role(Role.INK_SOFT), align = a, lines = 2)
    }
}

/** A pill with an icon and a word; bound, it shows the entity's state and lights up while it is on. */
private fun DocContext.badgeNode(n: Node, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    val domain = id?.substringBefore('.')
    if (id != null) bindEntity(id)
    val explicit = n.props["color"]?.let { paint(it) }
    val (bg, ink) = when {
        explicit != null -> explicit to role(Role.KEY_ON_INK)
        id != null -> {
            bindTileColors(n.id, id, Pastel.forDomain(domain))
            color(Vars.t(n.id, "bg")) to color(Vars.t(n.id, "bgink"))
        }
        else -> role(Role.KEY) to role(Role.INK)
    }
    var base = sized(n, axis)
    if (n.size.height == "wrap") base = base.height(u.px(32f))
    base = base.clip(rounded(if (n.style.corner > 0f) n.style.corner else 16f))
    if (n.style.background == null) base = base.background(bg)
    val m = styled(n, axis, pages, base).let { if (n.style.padding == 0f) it.padding(u.px(12f), 0f, u.px(12f), 0f) else it }
    val icon = n.props["icon"]?.takeIf { it.isNotEmpty() } ?: id?.let { states[it]?.attr("icon") } ?: domain?.let { Catalogue.of(it)?.icon }
    scope.Row(m, horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val c = this@badgeNode
        if (icon != null) {
            c.icon(icon, 18f, ink)
            Spacer(Modifier.width(c.u.px(6f)))
        }
        if (id != null && n.props["text"].isNullOrEmpty()) c.label(c.text(Vars.e(id, "state")), Type.BODY, ink, Weight.MEDIUM)
        else c.label(n.props["text"].orEmpty(), Type.BODY, ink, Weight.MEDIUM)
    }
}

/** A person's photo in a circle, or initials: the entity's name, or the text when it shows none. */
private fun DocContext.avatarNode(n: Node, axis: Axis, pages: Pages) {
    val sizeDp = n.props["size"]?.toFloatOrNull()?.coerceIn(16f, 400f) ?: 56f
    val id = n.bind?.entityId
    scope.Box(styled(n, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val c = this@avatarNode
        if (id != null) {
            c.bindEntity(id)
            c.photo(c.states[id], id, "pic.${n.id}", sizeDp)
        } else {
            c.initials(n.props["text"]?.takeIf { it.isNotBlank() } ?: "?", sizeDp, c.role(Role.KEY), c.role(Role.INK))
        }
    }
}

/** The name of the variable a progress bar's fill, from 0 to 1, is sent in. */
fun nodeLevel(nodeId: String) = "n.$nodeId.level"

/** Sends [nodeLevel] of [n]: the entity's own level, or its number between `min` and `max`. */
private fun DocContext.bindLevel(n: Node, id: String) {
    bind(id) { states ->
        val e = states[id]
        val level = Present.view(e, i18n).level ?: e?.numericState()?.let { v ->
            val lo = n.props["min"]?.toDoubleOrNull() ?: e.number("min") ?: 0.0
            val hi = n.props["max"]?.toDoubleOrNull() ?: e.number("max") ?: 100.0
            if (hi > lo) ((v - lo) / (hi - lo)).toFloat() else 0f
        } ?: 0f
        listOf(VarValue.float(nodeLevel(n.id), level.coerceIn(0f, 1f)))
    }
}

/**
 * A bar that fills with the entity's level: a light's brightness, a cover's position, or a number between [min] and
 * [max] (the entity's own limits, or 0 to 100) for anything else, such as a battery.
 */
private fun DocContext.progressNode(n: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    val pad = n.style.padding
    val w = u.px((self.w - 2 * pad).coerceAtLeast(1f))
    val h = (self.h - 2 * pad).coerceAtLeast(1f)
    val fill = n.props["color"]?.let { paint(it) } ?: fixed(Pastel.forDomain(id?.substringBefore('.')).argb)
    if (id != null) bindLevel(n, id)
    scope.Box(styled(n, axis, pages, measured(n, axis, self))) {
        val c = this@progressNode
        Box(Modifier.size(w, c.u.px(h)).clip(c.rounded(h / 2f)).background(c.role(Role.TRACK))) {
            if (id != null) {
                val level = c.float(nodeLevel(n.id))
                Canvas(Modifier.size(w, c.u.px(h))) {
                    paint { color(fill); style(RcPaintStyle.Fill) }
                    drawRect(0f.rf, 0f.rf, level * w, c.u.px(h).rf)
                }
            }
        }
    }
}

/** The entity's history from the recorder as a line, over the last `hours` (24 unless set). */
private fun DocContext.graphNode(n: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    val m = styled(n, axis, pages, measured(n, axis, self))
    if (id == null) {
        scope.Box(m.background(role(Role.TRACK)))
        return
    }
    refreshEvery(300)
    val pad = n.style.padding
    val line = n.props["color"]?.let { paint(it) } ?: fixed(Pastel.BLUE.argb)
    scope.Box(m) {
        val c = this@graphNode
        c.graph(c.data.history[id].orEmpty(), (self.w - 2 * pad).coerceAtLeast(1f), (self.h - 2 * pad).coerceAtLeast(1f), line, c.role(Role.INK_SOFT))
    }
}

/** An on and off switch: the track lights up in the entity's colour and the knob moves over while it is on. */
private fun DocContext.switchNode(n: Node, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    val track = if (id != null) {
        bindEntity(id)
        bindTileColors(n.id, id, accent(n.props["color"], Pastel.forDomain(id.substringBefore('.'))))
        color(Vars.t(n.id, "bg"))
    } else {
        role(Role.KEY)
    }
    scope.Box(styled(n, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val c = this@switchNode
        Box(Modifier.size(c.u.px(52f), c.u.px(30f)).clip(c.rounded(15f)).background(track)) {
            fun knob(end: Boolean, shown: String?) {
                var m = Modifier.fillMaxSize().padding(c.u.px(3f))
                if (shown != null) m = m.visibility(c.int(shown))
                Box(m, horizontal = if (end) RcHorizontalPositioning.End else RcHorizontalPositioning.Start, vertical = RcVerticalPositioning.Center) {
                    Box(Modifier.size(c.u.px(24f), c.u.px(24f)).clip(c.rounded(12f)).background(c.role(Role.INK)))
                }
            }
            if (id == null) {
                knob(false, null)
            } else {
                knob(false, Vars.e(id, "off"))
                knob(true, Vars.e(id, "on"))
            }
        }
    }
}

/** What a stepper's keys send for an entity, and by how much a tap moves it. */
private fun DocContext.stepOf(n: Node, e: EntityState?, domain: String?): Pair<String, Double>? {
    val pair = when (domain) {
        "climate" -> "temperature" to (e?.number("target_temp_step") ?: if (ha.temperatureUnit.contains("F")) 1.0 else 0.5)
        "number", "input_number" -> "value" to (e?.number("step") ?: 1.0)
        "light" -> "brightness" to 10.0
        "fan" -> "percentage" to 10.0
        "cover" -> "position" to 10.0
        "media_player" -> "volume" to 5.0
        else -> return null
    }
    val step = n.props["step"]?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: pair.second
    return pair.first to step
}

/** Minus, the value, plus: a thermostat's target, a number, a light's brightness. */
private fun DocContext.stepperNode(n: Node, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    val domain = id?.substringBefore('.')
    val ink = role(Role.INK)
    val how = id?.let { stepOf(n, states[it], domain) }
    if (id != null) bindEntity(id)
    val step = how?.second?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.ROOT, it) }
    val down = how?.let { ActionRef(n.id, it.first, "-$step") }
    val up = how?.let { ActionRef(n.id, it.first, "+$step") }
    val isPredictable = how != null && domain?.let { Catalogue.of(it) }?.predict?.containsKey(how.first) == true
    if (id != null && isPredictable) {
        down?.let { predict(it, id) }
        up?.let { predict(it, id) }
    }
    scope.Row(styled(n, axis, pages), horizontal = RcRowHorizontalPositioning.SpaceBetween, vertical = RcVerticalPositioning.Center) {
        val c = this@stepperNode
        fun key(ref: ActionRef?, icon: String) {
            if (ref != null) c.key(ref, icon = icon, heightDp = 44f, widthDp = 44f, round = true, ink = ink)
            else Box(Modifier.size(c.u.px(44f), c.u.px(44f)).clip(c.rounded(22f)).background(c.role(Role.KEY)), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                c.icon(icon, 22f, c.role(Role.INK_DIM))
            }
        }
        key(down, "minus")
        val middle = if (n.size.width == "wrap") Modifier.width(c.u.px(112f)) else Modifier.horizontalWeight(1f)
        Box(middle, horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
            if (id != null) c.label(c.text(Vars.e(id, "value")), Type.HEADLINE, ink, Weight.MEDIUM, align = RcTextAlign.Center)
            else c.label("–", Type.HEADLINE, c.role(Role.INK_DIM), Weight.MEDIUM, align = RcTextAlign.Center)
        }
        key(up, "plus")
    }
}

/** What a row of choices offers for an entity: the verb, the choices, how each reads, and which is chosen. */
private class ChoiceSet(val verb: String, val attribute: String, val choices: List<String>, val word: (String) -> String, val chosen: (EntityState) -> String?, val icons: Map<String, String> = emptyMap())

private fun DocContext.choicesOf(e: EntityState?, domain: String?): ChoiceSet? = when (domain) {
    "select", "input_select" -> ChoiceSet("option", "options", e?.list("options").orEmpty(), { it }, { it.state })
    "climate" -> ChoiceSet("mode", "hvac_modes", e?.list("hvac_modes").orEmpty(), { i18n.state("climate", it) }, { it.state }, MODE_ICONS)
    "light" -> ChoiceSet("effect", "effect_list", e?.list("effect_list").orEmpty(), { it }, { it.attr("effect") })
    else -> null
}

/** A row of keys, one per option: a select's options, a thermostat's modes, a light's effects. */
private fun DocContext.choicesNode(n: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    val m = styled(n, axis, pages, measured(n, axis, self))
    val set = id?.let { choicesOf(states[it], it.substringBefore('.')) }
    if (id == null || set == null || set.choices.isEmpty()) {
        scope.Box(m.clip(rounded(12f)).background(role(Role.TRACK)))
        return
    }
    structural(id) { it?.list(set.attribute)?.joinToString() ?: "" }
    val shown = set.choices.take(n.props["max"]?.toIntOrNull()?.coerceIn(2, 8) ?: 4)
    bindChoices(n.id, "c", id, shown, set.chosen)
    val h = (self.h - 2 * n.style.padding).coerceAtLeast(28f)
    scope.Box(m, vertical = RcVerticalPositioning.Center) {
        this@choicesNode.choiceRow(n.id, "c", shown.map { it to set.word(it) }, { ActionRef(n.id, set.verb, it) }, h, set.icons)
    }
}

/** A ring three quarters round that fills with the entity's level, its value in the middle. */
private fun DocContext.gaugeNode(n: Node, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    val sizeDp = n.props["size"]?.toFloatOrNull()?.coerceIn(48f, 600f) ?: 140f
    val px = u.px(sizeDp)
    val stroke = px * 0.1f
    val inset = stroke / 2f
    val fill = n.props["color"]?.let { paint(it) } ?: fixed(Pastel.forDomain(id?.substringBefore('.')).argb)
    val track = role(Role.TRACK)
    if (id != null) {
        bindEntity(id)
        bindLevel(n, id)
    }
    // In the gap under the ring: the entity's name for `@name`, else the words given.
    val caption = n.props["caption"]?.takeIf { it.isNotBlank() }?.let { if (it == "@name") id?.let { e -> text(Vars.e(e, "name")) } else literal(it) }
    scope.Box(styled(n, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val c = this@gaugeNode
        Box(Modifier.size(px, px), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
            Canvas(Modifier.size(px, px)) {
                paint { color(track); style(RcPaintStyle.Stroke); strokeWidth(stroke); strokeCap(RcStrokeCap.Round) }
                drawArc(inset, inset, px - inset, px - inset, 135f, 270f)
                if (id != null) {
                    paint { color(fill); style(RcPaintStyle.Stroke); strokeWidth(stroke); strokeCap(RcStrokeCap.Round) }
                    drawArc(inset.rf, inset.rf, (px - inset).rf, (px - inset).rf, 135f.rf, c.float(nodeLevel(n.id)) * 270f)
                }
                if (caption != null) {
                    paint { color(c.role(Role.INK_SOFT)); style(RcPaintStyle.Fill); textSize(px * 0.085f) }
                    drawTextAnchored(caption, px / 2f, px - px * 0.06f, 0f, 0f)
                }
            }
            if (id != null) c.label(c.text(Vars.e(id, "value")), maxOf(sizeDp * 0.16f, Type.CAPTION), c.role(Role.INK), Weight.MEDIUM, align = RcTextAlign.Center)
        }
    }
}

/** A clock face whose hands the player turns by its own clock, so it keeps time without the add-on. */
private fun DocContext.analogNode(n: Node, axis: Axis, pages: Pages) {
    val sizeDp = n.props["size"]?.toFloatOrNull()?.coerceIn(48f, 800f) ?: 160f
    val px = u.px(sizeDp)
    val mid = px / 2f
    val face = n.props["color"]?.let { paint(it) } ?: role(Role.CARD)
    val ink = role(Role.INK)
    val dim = role(Role.INK_DIM)
    val accent = role(Role.ACCENT)
    val seconds = n.props["seconds"] == "true"
    scope.Box(styled(n, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val c = this@analogNode
        Canvas(Modifier.size(px, px)) {
            paint { color(face); style(RcPaintStyle.Fill) }
            drawCircle(mid, mid, mid)
            paint { color(dim); style(RcPaintStyle.Stroke); strokeWidth(px * 0.02f); strokeCap(RcStrokeCap.Round) }
            for (i in 0 until 12) {
                val a = i * PI / 6
                val inner = if (i % 3 == 0) 0.74f else 0.82f
                drawLine(mid + (sin(a) * mid * inner).toFloat(), mid - (cos(a) * mid * inner).toFloat(), mid + (sin(a) * mid * 0.9f).toFloat(), mid - (cos(a) * mid * 0.9f).toFloat())
            }
            val hours = when (n.props["numbers"]) { "all" -> 1..12; "quarters" -> listOf(3, 6, 9, 12); else -> emptyList() }
            if (hours.any()) {
                val size = px * 0.1f
                paint { color(ink); style(RcPaintStyle.Fill); textSize(size) }
                for (h in hours) {
                    val a = h * PI / 6
                    drawTextAnchored(c.literal(h.toString()), mid + (sin(a) * mid * 0.6f).toFloat(), mid - (cos(a) * mid * 0.6f).toFloat(), 0f, 0f)
                }
            }
            // minutes() is the minute of the day and seconds() the second of the hour.
            fun hand(angle: RcFloat, length: Float, width: Float, colour: RcColor) {
                save {
                    rotate(angle, mid.rf, mid.rf)
                    paint { color(colour); style(RcPaintStyle.Stroke); strokeWidth(width); strokeCap(RcStrokeCap.Round) }
                    drawLine(mid, mid, mid, mid - mid * length)
                }
            }
            hand(minutes() * 0.5f, 0.5f, px * 0.05f, ink)
            hand(seconds() * 0.1f, 0.75f, px * 0.035f, ink)
            if (seconds) hand(continuousSeconds() * 6f, 0.82f, px * 0.012f, accent)
            paint { color(ink); style(RcPaintStyle.Fill) }
            drawCircle(mid, mid, px * 0.04f)
        }
    }
}

/** A round key with an icon that lights up in the entity's colour while it is on. */
private fun DocContext.iconButtonNode(n: Node, axis: Axis, pages: Pages) {
    val sizeDp = n.props["size"]?.toFloatOrNull()?.coerceIn(24f, 400f) ?: 64f
    val id = n.bind?.entityId
    val domain = id?.substringBefore('.')
    val (bg, ink) = if (id != null) {
        bindEntity(id)
        bindTileColors(n.id, id, accent(n.props["color"], Pastel.forDomain(domain)))
        color(Vars.t(n.id, "bg")) to color(Vars.t(n.id, "bgink"))
    } else {
        (n.props["color"]?.let { paint(it) } ?: role(Role.KEY)) to role(Role.INK)
    }
    val name = n.props["icon"]?.takeIf { it.isNotEmpty() } ?: id?.let { states[it]?.attr("icon") } ?: domain?.let { Catalogue.of(it)?.icon } ?: "gesture-tap"
    var base = sized(n, axis)
    if (n.size.width == "wrap" && n.size.height == "wrap") base = base.size(u.px(sizeDp), u.px(sizeDp))
    base = base.clip(rounded(if (n.style.corner > 0f) n.style.corner else sizeDp / 2f))
    if (n.style.background == null) base = base.background(bg)
    scope.Box(styled(n, axis, pages, base), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val c = this@iconButtonNode
        val on = n.props["iconOn"]
        if (id != null && on != null) c.toggleIcon(id, name, on, sizeDp * 0.45f, ink) else c.icon(name, sizeDp * 0.45f, ink)
    }
}

/** The keys an entity's remote has: a player's transport, a cover's open, stop and close, a vacuum's, a lock's. */
private fun DocContext.keysOf(e: EntityState?, domain: String?): List<Pair<String, String>> = when (domain) {
    "media_player" -> listOf("previous" to "skip-previous", "play_pause" to "play-pause", "next" to "skip-next")
    "cover" -> listOf("open" to "arrow-up", "stop" to "stop", "close" to "arrow-down")
    "vacuum" -> listOf("start" to "play", "pause" to "pause", "dock" to "home-import-outline", "locate" to "map-marker-outline")
    "lock" -> listOf("lock" to "lock", "unlock" to "lock-open-variant")
    "alarm_control_panel" -> (e?.features() ?: 0).let { f ->
        listOfNotNull(
            ("arm_home" to "shield-home").takeIf { f and 1 != 0 }, ("arm_away" to "shield-lock").takeIf { f and 2 != 0 },
            ("arm_night" to "shield-moon").takeIf { f and 4 != 0 },
        ) + ("disarm" to "shield-off")
    }
    "light" -> listOf("off" to "lightbulb-off-outline", "on" to "lightbulb-on")
    else -> emptyList()
}

/** A row of the entity's remote keys; a lock asks for a second tap before it opens, as on its tile. */
private fun DocContext.keysNode(n: Node, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    val domain = id?.substringBefore('.')
    val keys = id?.let { keysOf(states[it], domain) }.orEmpty()
    val sizeDp = n.props["size"]?.toFloatOrNull()?.coerceIn(32f, 200f) ?: 48f
    val m = styled(n, axis, pages)
    if (id == null || keys.isEmpty()) {
        scope.Box(m.size(u.px(sizeDp * 3 + 20f), u.px(sizeDp)).clip(rounded(12f)).background(role(Role.TRACK)))
        return
    }
    bindEntity(id)
    val spec = Catalogue.of(id.substringBefore('.'))
    val stretch = n.size.width != "wrap"
    scope.Column(m, horizontal = RcHorizontalPositioning.Center) {
        val c = this@keysNode
        Row(if (stretch) Modifier.fillMaxWidth() else Modifier, horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
            keys.forEachIndexed { i, (verb, icon) ->
                if (i > 0) Spacer(Modifier.width(c.u.px(10f)))
                val ref = ActionRef(n.id, verb)
                if (spec?.predict?.containsKey(verb) == true) c.predict(ref, id)
                c.key(ref, icon = icon, heightDp = sizeDp, widthDp = if (stretch) null else sizeDp, round = !stretch, modifier = if (stretch) Modifier.horizontalWeight(1f) else Modifier)
            }
        }
        if (domain == "lock") c.label(c.text(Vars.t(n.id, "confirm")), Type.CAPTION, c.role(Role.WARN), Weight.SEMI_BOLD)
    }
}

/** Round swatches that set a light's colour, or its white from warm to cool. */
private fun DocContext.swatchesNode(n: Node, axis: Axis, pages: Pages) {
    val sizeDp = n.props["size"]?.toFloatOrNull()?.coerceIn(20f, 160f) ?: 40f
    val white = n.props["show"] == "white"
    val colours = if (white) CardRenderer.KELVINS.map { CardRenderer.kelvinColor(it) to ActionRef(n.id, "kelvin", it.toString()) }
    else CardRenderer.HUES.map { h -> java.awt.Color.HSBtoRGB(h / 360f, 0.55f, 1f) to ActionRef(n.id, "hue", h.toString()) }
    val stretch = n.size.width != "wrap"
    scope.Row(styled(n, axis, pages), horizontal = if (stretch) RcRowHorizontalPositioning.SpaceBetween else RcRowHorizontalPositioning.Start, vertical = RcVerticalPositioning.Center) {
        val c = this@swatchesNode
        colours.forEachIndexed { i, (argb, ref) ->
            if (i > 0 && !stretch) Spacer(Modifier.width(c.u.px(10f)))
            Box(c.tap(Modifier.size(c.u.px(sizeDp), c.u.px(sizeDp)).clip(c.rounded(sizeDp / 2f)).background(c.fixed(argb or 0xFF000000.toInt())), if (n.bind != null) ref else null))
        }
    }
}

/** The digits of an alarm's or a lock's code, kept by the add-on and sent with the next arm or disarm. */
private fun DocContext.keypadNode(n: Node, axis: Axis, pages: Pages) {
    val id = n.bind?.entityId
    val sizeDp = n.props["size"]?.toFloatOrNull()?.coerceIn(32f, 160f) ?: 56f
    val keyW = sizeDp * 1.5f
    val codeVar = Vars.t("e:${id ?: n.id}", "code")
    if (id != null) bind(id) { listOf(VarValue.string(codeVar, "")) }
    scope.Column(styled(n, axis, pages), horizontal = RcHorizontalPositioning.Center) {
        val c = this@keypadNode
        Box(Modifier.height(c.u.px(56f)), vertical = RcVerticalPositioning.Center) {
            if (id != null) c.label(c.text(codeVar), Type.VALUE, c.role(Role.INK), Weight.MEDIUM, align = RcTextAlign.Center)
        }
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("clear", "0", "back"))) {
            Row(Modifier.padding(0f, 0f, 0f, c.u.px(10f)), horizontal = RcRowHorizontalPositioning.Center) {
                row.forEachIndexed { i, k ->
                    if (i > 0) Spacer(Modifier.width(c.u.px(12f)))
                    val ref = when (k) {
                        "clear" -> ActionRef(n.id, "code_clear")
                        "back" -> ActionRef(n.id, "code_back")
                        else -> ActionRef(n.id, "code_digit", k)
                    }
                    val icon = when (k) { "clear" -> "close"; "back" -> "backspace-outline"; else -> null }
                    c.key(ref, text = if (icon == null) k else null, icon = icon, heightDp = sizeDp, widthDp = keyW)
                }
            }
        }
    }
}
