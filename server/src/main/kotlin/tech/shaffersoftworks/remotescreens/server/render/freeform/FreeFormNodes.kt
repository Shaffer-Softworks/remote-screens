@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import androidx.compose.remote.core.operations.layout.animation.AnimationSpec
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.animationSpec
import androidx.compose.remote.creation.dsl.RcBorderShape
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcInteger
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
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun DocContext.textNode(node: Node, axis: Axis, pages: Pages) {
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.TITLE
    val weight = node.props["weight"]?.toFloatOrNull() ?: Weight.NORMAL
    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val marquee = node.props["marquee"] == "true"
    val lines = if (marquee) 1 else node.props["lines"]?.toIntOrNull()?.coerceIn(1, Metrics.TEXT_MAX_LINES) ?: 1
    val textAlign = align(node.props["align"])
    // Words wider than their space glide back and forth at 30 dp a second, forever.
    // Put taps on a Box: like Image, Text can keep a ClickModifier that still misses taps on device.
    val modifier = styled(node, axis, pages).let {
        if (marquee) it.marquee(Int.MAX_VALUE, 0, Metrics.MARQUEE_GAP_PX, Metrics.MARQUEE_SPACE_PX, 0f, Metrics.MARQUEE_SPEED_DP) else it
    }
    val binding = node.bind
    if (binding == null) {
        scope.Box(modifier) { this@textNode.label(node.props["text"].orEmpty(), sp, ink, weight, lines, textAlign) }
        return
    }
    bind(binding.entityId) { states ->
        val entityState = states[binding.entityId]
        val view = Present.view(entityState, i18n)
        val raw = when (binding.format) {
            "value" -> view.value
            "name" -> view.name
            "attribute" -> binding.attribute?.let { entityState?.attr(it) } ?: ""
            else -> view.stateText
        }
        listOf(VarValue.string(nodeVar(node.id), binding.prefix + raw + binding.suffix))
    }
    scope.Box(modifier) { this@textNode.label(this@textNode.text(nodeVar(node.id)), sp, ink, weight, lines, textAlign) }
}

/** An icon bound to an entity lights up in its pastel while the entity is on, like a tile's. */
internal fun DocContext.iconNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull() ?: Metrics.ICON_DEFAULT_DP
    val id = node.bind?.entityId
    val domain = id?.substringBefore('.')
    val name = node.props["icon"] ?: id?.let { states[it]?.attr("icon") } ?: domain?.let { Catalogue.of(it)?.icon }
    val explicit = node.props["color"]?.let { paint(it) }
    val ink = when {
        explicit != null -> explicit
        id != null -> {
            bindTileColors(node.id, id, Pastel.forDomain(domain))
            color(Vars.tile(node.id, Vars.KEY_ICON))
        }
        else -> role(Role.INK)
    }
    scope.Box(styled(node, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@iconNode
        val on = node.props["iconOn"]
        if (id != null && on != null) {
            doc.bindEntity(id)
            doc.toggleIcon(id, name ?: Icons.FALLBACK, on, sizeDp, ink)
        } else {
            doc.icon(name, sizeDp, ink)
        }
    }
}

internal fun DocContext.buttonNode(node: Node, axis: Axis, pages: Pages) {
    val bg = node.props["color"]?.let { paint(it) } ?: role(Role.KEY)
    val ink = node.props["ink"]?.let { paint(it) } ?: role(Role.INK)
    var base = sized(node, axis)
    if (node.size.height == "wrap") base = base.height(units.px(Metrics.BUTTON_NATURAL_H))
    base = base.clip(rounded(if (node.style.corner > 0f) node.style.corner else Metrics.BUTTON_NATURAL_H / Metrics.BUTTON_CORNER_DIVISOR))
    if (node.style.background == null) base = base.background(bg)
    val modifier = styled(node, axis, pages, base).let {
        if (!node.style.padded) it.padding(units.px(Metrics.BUTTON_PAD_H_DP), 0f, units.px(Metrics.BUTTON_PAD_H_DP), 0f) else it
    }
    val word = node.props["text"]?.takeIf { it.isNotEmpty() }
    val icon = node.props["icon"]?.takeIf { it.isNotEmpty() }
    scope.Row(modifier, horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@buttonNode
        if (icon != null) doc.icon(icon, if (word == null) Metrics.BUTTON_ICON_ONLY_DP else Metrics.BUTTON_ICON_WITH_TEXT_DP, ink)
        if (icon != null && word != null) Spacer(Modifier.width(doc.units.px(Metrics.BUTTON_ICON_GAP_DP)))
        if (word != null) doc.label(word, Type.BODY, ink, weight = Weight.MEDIUM)
    }
}

/** A stepped bar over the entity's level, as on a tile: a tap on step i sets i tenths of its range. */
internal fun DocContext.imageNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val modifier = styled(node, axis, pages)
    if (id == null) {
        scope.Box(modifier.background(role(Role.TRACK)))
        return
    }
    val w = units.px(self.w).toInt().coerceAtLeast(1)
    val h = units.px(self.h).toInt().coerceAtLeast(1)
    val camera = id.startsWith("camera.")
    val fit = node.props["fit"] == "whole"
    val slot = "img.${node.id}"
    picture(PictureSlot(slot, if (camera) "/api/camera_proxy/$id" else "attr:$id:entity_picture", w, h, if (camera) Metrics.CAMERA_REFRESH_SEC else 0, crop = !fit))
    // Put taps on a Box: Image drops onClick in the document (alpha21), so a camera never opened its card.
    scope.Box(modifier) {
        Image(
            this@imageNode.image(slot, w, h),
            Modifier.fillMaxSize(),
            contentScale = if (fit) RcContentScale.Fit else RcContentScale.Crop,
        )
    }
}

/** One of the grid's cards, at the size the layout gives it. */
internal fun DocContext.tileNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val tile = node.tile
    val spec = tile?.let { Catalogue.of(it.effectiveKind()) }
    val modifier = sized(node, axis).let { if (node.size.width == "wrap" && node.size.height == "wrap") it.size(units.px(self.w), units.px(self.h)) else it }
    val lacksEntity = tile?.entityId == null && spec?.needsEntity == true
    if (tile == null || spec == null || lacksEntity) {
        scope.Box(modifier)
        return
    }
    val named = if (tile.effectiveKind() == "nav" && tile.name == null) tile.copy(name = tile.options["page"]?.let(pages.title)) else tile
    scope.Box(modifier) {
        this@tileNode.tile(named, TileBox(0f, 0f, self.w, self.h, 1, 1, full = false), pages.index)
    }
}

/** The time, the date, or the time over the date, from the values the player keeps itself. */
internal fun DocContext.clockNode(node: Node, axis: Axis, pages: Pages) {
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.DISPLAY
    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val show = node.props["show"] ?: "time"
    val textAlign = align(node.props["align"])
    scope.Column(styled(node, axis, pages), horizontal = horizontal(node.props["align"])) {
        val doc = this@clockNode
        if (show != "date") doc.label(doc.text(Vars.TIME), sp, ink, Weight.LIGHT, align = textAlign)
        if (show == "date") doc.label(doc.text(Vars.DATE), sp, ink, align = textAlign, lines = 2)
        if (show == "both") doc.label(doc.text(Vars.DATE), maxOf(sp * Metrics.CLOCK_DATE_SCALE, Type.BODY), doc.role(Role.INK_SOFT), align = textAlign, lines = 2)
    }
}

/** A pill with an icon and a word; bound, it shows the entity's state and lights up while it is on. */
internal fun DocContext.badgeNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val domain = id?.substringBefore('.')
    if (id != null) bindEntity(id)
    val explicit = node.props["color"]?.let { paint(it) }
    val (bg, ink) = when {
        explicit != null -> explicit to role(Role.KEY_ON_INK)
        id != null -> {
            bindTileColors(node.id, id, Pastel.forDomain(domain))
            color(Vars.tile(node.id, Vars.KEY_BG)) to color(Vars.tile(node.id, Vars.KEY_BG_INK))
        }
        else -> role(Role.KEY) to role(Role.INK)
    }
    var base = sized(node, axis)
    if (node.size.height == "wrap") base = base.height(units.px(Metrics.BADGE_NATURAL_H))
    base = base.clip(rounded(if (node.style.corner > 0f) node.style.corner else Metrics.BADGE_CORNER_DP))
    if (node.style.background == null) base = base.background(bg)
    val modifier = styled(node, axis, pages, base).let {
        if (!node.style.padded) it.padding(units.px(Metrics.BADGE_PAD_H_DP), 0f, units.px(Metrics.BADGE_PAD_H_DP), 0f) else it
    }
    val icon = node.props["icon"]?.takeIf { it.isNotEmpty() } ?: id?.let { states[it]?.attr("icon") } ?: domain?.let { Catalogue.of(it)?.icon }
    scope.Row(modifier, horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@badgeNode
        if (icon != null) {
            doc.icon(icon, Metrics.BADGE_ICON_DP, ink)
            Spacer(Modifier.width(doc.units.px(Metrics.BADGE_ICON_GAP_DP)))
        }
        if (id != null && node.props["text"].isNullOrEmpty()) doc.label(doc.text(Vars.entity(id, Vars.KEY_STATE)), Type.BODY, ink, Weight.MEDIUM)
        else doc.label(node.props["text"].orEmpty(), Type.BODY, ink, Weight.MEDIUM)
    }
}

/** A person's photo in a circle, or initials: the entity's name, or the text when it shows none. */
internal fun DocContext.avatarNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(Metrics.AVATAR_MIN_DP, Metrics.AVATAR_MAX_DP) ?: Metrics.AVATAR_DEFAULT_DP
    val id = node.bind?.entityId
    scope.Box(styled(node, axis, pages), horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        val doc = this@avatarNode
        if (id != null) {
            doc.bindEntity(id)
            doc.photo(doc.states[id], id, "pic.${node.id}", sizeDp)
        } else {
            doc.initials(node.props["text"]?.takeIf { it.isNotBlank() } ?: "?", sizeDp, doc.role(Role.KEY), doc.role(Role.INK))
        }
    }
}

/** The name of the variable a progress bar's fill, from 0 to 1, is sent in. */
fun nodeLevel(nodeId: String) = "n.$nodeId.level"

/** Sends [nodeLevel] of [node]: the entity's own level, or its number between `min` and `max`. */
internal fun DocContext.bindLevel(node: Node, id: String) {
    bind(id) { states ->
        val entityState = states[id]
        val level = Present.view(entityState, i18n).level ?: entityState?.numericState()?.let { value ->
            val lo = node.props["min"]?.toDoubleOrNull() ?: entityState.number("min") ?: 0.0
            val hi = node.props["max"]?.toDoubleOrNull() ?: entityState.number("max") ?: Metrics.LEVEL_RANGE_MAX
            if (hi > lo) ((value - lo) / (hi - lo)).toFloat() else 0f
        } ?: 0f
        listOf(VarValue.float(nodeLevel(node.id), level.coerceIn(0f, 1f)))
    }
}

/**
 * A bar that fills with the entity's level: a light's brightness, a cover's position, or a number between [min] and
 * [max] (the entity's own limits, or 0 to 100) for anything else, such as a battery.
 */
internal fun DocContext.progressNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val w = units.px((self.w - node.style.padX).coerceAtLeast(1f))
    val h = (self.h - node.style.padY).coerceAtLeast(1f)
    val fill = node.props["color"]?.let { paint(it) } ?: fixed(Pastel.forDomain(id?.substringBefore('.')).argb)
    if (id != null) bindLevel(node, id)
    scope.Box(styled(node, axis, pages, measured(node, axis, self))) {
        val doc = this@progressNode
        Box(Modifier.size(w, doc.units.px(h)).clip(doc.rounded(h / 2f)).background(doc.role(Role.TRACK))) {
            if (id != null) {
                val level = doc.float(nodeLevel(node.id))
                Canvas(Modifier.size(w, doc.units.px(h))) {
                    paint { color(fill); style(RcPaintStyle.Fill) }
                    drawRect(0f.rf, 0f.rf, level * w, doc.units.px(h).rf)
                }
            }
        }
    }
}

/** The entity's history from the recorder as a line, over the last `hours` (24 unless set). */
internal fun DocContext.graphNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val modifier = styled(node, axis, pages, measured(node, axis, self))
    if (id == null) {
        scope.Box(modifier.background(role(Role.TRACK)))
        return
    }
    refreshEvery(Metrics.GRAPH_REFRESH_SEC)
    val line = node.props["color"]?.let { paint(it) } ?: fixed(Pastel.BLUE.argb)
    val w = (self.w - node.style.padX).coerceAtLeast(1f)
    val h = (self.h - node.style.padY).coerceAtLeast(1f)
    scope.Box(modifier) {
        val doc = this@graphNode
        doc.graph(doc.data.history[id].orEmpty(), w, h, line, doc.role(Role.INK_SOFT))
    }
}

/** An on and off switch: the track lights up in the entity's colour and the knob moves over while it is on. */
