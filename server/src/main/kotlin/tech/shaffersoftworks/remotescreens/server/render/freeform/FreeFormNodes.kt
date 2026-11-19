@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TileKind
import tech.shaffersoftworks.remotescreens.server.render.widgets.Icons
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.Present
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.cards.camera.CAMERA_SPINNER_DP
import tech.shaffersoftworks.remotescreens.server.render.cards.camera.hasLiveVideoOnCurrentPage
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.initials
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.cards.history.graph
import tech.shaffersoftworks.remotescreens.server.render.tiles.person.photo
import tech.shaffersoftworks.remotescreens.server.render.tiles.tile
import tech.shaffersoftworks.remotescreens.server.render.widgets.toggleIcon

import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.marquee
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.widgets.spinner
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.VarValue
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.shared.NodeBinding

private fun DocContext.boundCopy(binding: NodeBinding, entityState: EntityState?): String {
    val view = Present.view(entityState, i18n)

    val raw = when (binding.format) {
        "value" -> view.value
        "name" -> view.name
        "attribute" -> binding.attribute?.let { entityState?.attributeText(it) }.orEmpty()
        else -> view.stateText
    }

    return binding.prefix + raw + binding.suffix
}

internal fun DocContext.textNode(node: Node, axis: Axis, pages: Pages) {
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.TITLE
    val weight = node.props["weight"]?.toFloatOrNull() ?: Weight.NORMAL
    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val marquee = node.props["marquee"] == Tile.OPTION_TRUE
    val lines = if (marquee) 1 else node.props["lines"]?.toIntOrNull()?.coerceIn(1, Metrics.TEXT_MAX_LINES) ?: 1
    val textAlign = align(node.props["align"])

    // Words wider than their space glide back and forth at 30 dp a second, forever.
    // Put taps on a Box: like Image, Text can keep a ClickModifier that still misses taps on device.
    val modifier = styled(node = node, axis = axis, pages = pages).let {
        if (marquee) {
            it.marquee(
                iterations = Int.MAX_VALUE,
                animationMode = 0,
                repeatDelayMillis = Metrics.MARQUEE_REPEAT_DELAY_MS,
                initialDelayMillis = Metrics.MARQUEE_INITIAL_DELAY_MS,
                spacing = 0f,
                velocity = Metrics.MARQUEE_SPEED_DP,
            )
        } else {
            it
        }
    }

    val binding = node.bind

    if (binding == null) {
        rcScope.Box(
            modifier,
        ) { this@textNode.label(
            text = node.props["text"].orEmpty(),
            sp = sp,
            color = ink,
            weight = weight,
            lines = lines,
            align = textAlign,
        ) }

        return
    }

    bind(binding.entityId) { states ->
        listOf(VarValue.string(nodeVar(node.id), boundCopy(binding, states[binding.entityId])))
    }

    rcScope.Box(
        modifier,
    ) { this@textNode.label(
        text = this@textNode.text(nodeVar(node.id)),
        sp = sp,
        color = ink,
        weight = weight,
        lines = lines,
        align = textAlign,
    ) }
}

/**
 * An icon bound to an entity lights up while the entity is on.
 * `colorOff` and `colorOn` replace the dim and lit colours.
 */
internal fun DocContext.iconNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull() ?: Metrics.ICON_DEFAULT_DP
    val entityId = node.bind?.entityId
    val domain = entityId?.let { EntityId(it).domain }

    val name = node.props["icon"]
        ?: entityId?.let { states[it]?.common?.icon }
        ?: domain?.let { Catalogue.of(it.value)?.icon }

    val ink = iconInk(node = node, entityId = entityId, domain = domain)

    rcScope.Box(
        modifier = styled(node = node, axis = axis, pages = pages),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@iconNode
        val iconOn = node.props["iconOn"]

        if (entityId != null && iconOn != null) {
            doc.bindEntity(entityId)
            doc.toggleIcon(entityId = entityId, off = name ?: Icons.FALLBACK, on = iconOn, sizeDp = sizeDp, color = ink)
        } else {
            doc.icon(name = name, sizeDp = sizeDp, color = ink)
        }
    }
}

/**
 * The ink of an icon. Unbound, or a bound icon with only the old `color`, stays that one colour.
 * A bound icon otherwise follows the entity: dim (or `colorOff`) while off, and while on its
 * `rgb_color` when Home Assistant sends one, else its pastel (or `colorOn`).
 */
private fun DocContext.iconInk(node: Node, entityId: String?, domain: Domain?): RcColor {
    val fixed = node.props["color"]?.let { paint(it) }
    val choosesEachState = node.props["colorOff"] != null || node.props["colorOn"] != null

    return if (entityId == null || (!choosesEachState && fixed != null)) {
        fixed ?: role(Role.INK)
    } else {
        val offColor = inkArgb(node.props["colorOff"], Role.INK_DIM.argb)
        val onColor = inkArgb(node.props["colorOn"], Pastel.forDomain(domain?.value).argb)

        bind(entityId) { states ->
            val view = Present.view(states[entityId], i18n)
            val entityRgb = if (node.props["colorOn"] == null) states[entityId]?.readRgb() else null

            val argb = when {
                !view.isAvailable -> Role.INK_FAINT.argb
                view.on -> entityRgb ?: onColor
                else -> offColor
            }

            listOf(VarValue.color(Vars.tile(node.id, Vars.KEY_ICON), argb))
        }

        color(Vars.tile(node.id, Vars.KEY_ICON))
    }
}

private fun DocContext.buttonFace(node: Node, axis: Axis, pages: Pages, bg: RcColor): Modifier {
    var base = sized(node, axis)

    if (node.size.height == "wrap") base = base.height(units.px(Metrics.BUTTON_NATURAL_H))

    val corner = if (node.style.corner > 0f) {
        node.style.corner
    } else {
        Metrics.BUTTON_NATURAL_H / Metrics.BUTTON_CORNER_DIVISOR
    }

    base = base.clip(rounded(corner))

    if (node.style.background == null) base = base.background(bg)

    val framed = styled(node = node, axis = axis, pages = pages, base = base)

    if (node.style.padded) return framed

    return framed.padding(
        start = units.px(Metrics.BUTTON_PAD_H_DP),
        top = 0f,
        end = units.px(Metrics.BUTTON_PAD_H_DP),
        bottom = 0f,
    )
}

internal fun DocContext.buttonNode(node: Node, axis: Axis, pages: Pages) {
    val bg = node.props["color"]?.let { paint(it) } ?: role(Role.KEY)
    val ink = node.props["ink"]?.let { paint(it) } ?: role(Role.INK)
    val modifier = buttonFace(node = node, axis = axis, pages = pages, bg = bg)

    val word = node.props["text"]?.takeIf { it.isNotEmpty() }
    val icon = node.props["icon"]?.takeIf { it.isNotEmpty() }
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.BODY
    val weight = node.props["weight"]?.toFloatOrNull() ?: Weight.MEDIUM

    rcScope.Row(
        modifier = modifier,
        horizontal = RcRowHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@buttonNode

        if (icon != null) {
            doc.icon(
                name = icon,
                sizeDp = if (word == null) Metrics.BUTTON_ICON_ONLY_DP else Metrics.BUTTON_ICON_WITH_TEXT_DP,
                color = ink,
            )
        }

        if (icon != null && word != null) {
            Spacer(
                Modifier.width(doc.units.px(Metrics.BUTTON_ICON_GAP_DP)),
            )
        }

        if (word != null) doc.label(text = word, sp = sp, color = ink, weight = weight)
    }
}

/** A camera's frames, or the entity's picture (a person, album art), fetched by the add-on. */
internal fun DocContext.imageNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val modifier = styled(node = node, axis = axis, pages = pages)

    if (id == null) {
        rcScope.Box(modifier.background(role(Role.TRACK)))

        return
    }

    val w = units.px(self.w).toInt().coerceAtLeast(1)
    val h = units.px(self.h).toInt().coerceAtLeast(1)
    val camera = EntityId(id).domain == Domain.CAMERA
    val fit = node.props[Tile.OPTION_FIT] == Tile.OPTION_WHOLE
    val contentScale = if (fit) RcContentScale.Fit else RcContentScale.Crop
    val canStream = camera && node.props[Tile.OPTION_STREAM] == Tile.OPTION_TRUE && !hasLiveVideoOnCurrentPage()
    val slot = "img.${node.id}"

    picture(
        PictureSlot(
            name = slot,
            source = if (camera) "/api/camera_proxy/$id" else "attr:$id:entity_picture",
            widthPx = w,
            heightPx = h,
            refreshSeconds = when {
                canStream -> 0
                camera -> Metrics.CAMERA_REFRESH_SEC
                else -> 0
            },
            crop = !fit,
        ),
    )

    // Put taps on a Box: Image drops onClick in the document (alpha22), so a camera never opened its card.
    if (canStream) {
        val video = liveVideo(id, crop = !fit)
        val waiting = int(Vars.entity(id, Vars.KEY_CAM_WAITING), Vars.VISIBLE)
        val still = int(Vars.entity(id, Vars.KEY_CAM_STILL), Vars.GONE)

        rcScope.Box(
            modifier = modifier.background(role(Role.TRACK)).componentId(video),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            val doc = this@imageNode

            Image(
                image = doc.image(name = slot, width = w, height = h),
                modifier = Modifier.fillMaxSize().visibility(still),
                contentScale = contentScale,
            )

            doc.spinner(sizeDp = CAMERA_SPINNER_DP, color = doc.role(Role.ACCENT), visible = waiting)
        }

        return
    }

    rcScope.Box(modifier) {
        Image(
            image = this@imageNode.image(name = slot, width = w, height = h),
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}

/** One of the grid's cards, at the size the layout gives it. */
internal fun DocContext.tileNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val tile = node.tile
    val spec = tile?.let { Catalogue.of(it.effectiveKind()) }

    val modifier = sized(
        node,
        axis,
    ).let { if (node.size.width == "wrap" && node.size.height == "wrap") {
        it.size(
            units.px(self.w),
            units.px(self.h),
        )
    } else {
        it
    } }

    val lacksEntity = tile?.entityId == null && spec?.needsEntity == true

    if (tile == null || spec == null || lacksEntity) {
        rcScope.Box(modifier)

        return
    }

    val named = if (tile.tileKind() == TileKind.NAV && tile.name == null) {
        tile.copy(
            name = tile.options["page"]?.let(pages.title),
        )
    } else {
        tile
    }

    rcScope.Box(modifier) {
        this@tileNode.tile(
            tile = named,
            box = TileBox(x = 0f, y = 0f, w = self.w, h = self.h, cols = 1, rows = 1, full = false),
            pageIndex = pages.index,
        )
    }
}

/** The time, the date, or the time over the date, from the values the player keeps itself. */
internal fun DocContext.clockNode(node: Node, axis: Axis, pages: Pages) {
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.DISPLAY
    val weight = node.props["weight"]?.toFloatOrNull() ?: Weight.LIGHT
    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val show = node.props["show"] ?: "time"
    val textAlign = align(node.props["align"])

    rcScope.Column(styled(node = node, axis = axis, pages = pages), horizontal = horizontal(node.props["align"])) {
        val doc = this@clockNode

        if (show != "date") {
            doc.label(
                text = doc.text(Vars.TIME),
                sp = sp,
                color = ink,
                weight = weight,
                align = textAlign,
            )
        }

        if (show == "date") {
            doc.label(
                text = doc.text(Vars.DATE),
                sp = sp,
                color = ink,
                weight = weight,
                align = textAlign,
                lines = 2,
            )
        }

        if (show == "both") {
            doc.label(
                text = doc.text(Vars.DATE),
                sp = maxOf(sp * Metrics.CLOCK_DATE_SCALE, Type.BODY),
                color = doc.role(Role.INK_SOFT),
                weight = weight,
                align = textAlign,
                lines = 2,
            )
        }
    }
}

private fun DocContext.badgeFace(
    node: Node,
    axis: Axis,
    pages: Pages,
    id: String?,
    domain: Domain?,
): Pair<Modifier, RcColor> {
    val explicit = node.props["color"]?.let { paint(it) }

    val (bg, ink) = when {
        explicit != null -> explicit to role(Role.KEY_ON_INK)
        id != null -> {
            bindTileColors(tileId = node.id, entityId = id, pastel = Pastel.forDomain(domain?.value))
            color(Vars.tile(node.id, Vars.KEY_BG)) to color(Vars.tile(node.id, Vars.KEY_BG_INK))
        }
        else -> role(Role.KEY) to role(Role.INK)
    }

    var base = sized(node, axis)

    if (node.size.height == "wrap") base = base.height(units.px(Metrics.BADGE_NATURAL_H))

    val corner = if (node.style.corner > 0f) node.style.corner else Metrics.BADGE_CORNER_DP
    base = base.clip(rounded(corner))

    if (node.style.background == null) base = base.background(bg)

    val framed = styled(node = node, axis = axis, pages = pages, base = base)

    val modifier = if (node.style.padded) {
        framed
    } else {
        framed.padding(
            start = units.px(Metrics.BADGE_PAD_H_DP),
            top = 0f,
            end = units.px(Metrics.BADGE_PAD_H_DP),
            bottom = 0f,
        )
    }

    return modifier to ink
}

/** A pill with an icon and a word; bound, it shows the entity's state and lights up while it is on. */
internal fun DocContext.badgeNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val domain = id?.let { EntityId(it).domain }

    if (id != null) bindEntity(id)

    val (modifier, ink) = badgeFace(node = node, axis = axis, pages = pages, id = id, domain = domain)

    val icon = node.props["icon"]?.takeIf { it.isNotEmpty() }
        ?: id?.let { states[it]?.common?.icon }
        ?: domain?.let { Catalogue.of(it.value)?.icon }

    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.BODY
    val weight = node.props["weight"]?.toFloatOrNull() ?: Weight.MEDIUM

    rcScope.Row(
        modifier = modifier,
        horizontal = RcRowHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@badgeNode

        if (icon != null) {
            doc.icon(name = icon, sizeDp = Metrics.BADGE_ICON_DP, color = ink)
            Spacer(Modifier.width(doc.units.px(Metrics.BADGE_ICON_GAP_DP)))
        }

        if (id != null && node.props["text"].isNullOrEmpty()) {
            doc.label(
                text = doc.text(Vars.entity(id, Vars.KEY_STATE)),
                sp = sp,
                color = ink,
                weight = weight,
            )
        } else {
            doc.label(text = node.props["text"].orEmpty(), sp = sp, color = ink, weight = weight)
        }
    }
}

/** A person's photo in a circle, or initials: the entity's name, or the text when it shows none. */
internal fun DocContext.avatarNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.AVATAR_MIN_DP,
        Metrics.AVATAR_MAX_DP,
    ) ?: Metrics.AVATAR_DEFAULT_DP

    val id = node.bind?.entityId

    rcScope.Box(
        modifier = styled(node = node, axis = axis, pages = pages),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@avatarNode

        if (id != null) {
            doc.bindEntity(id)
            doc.photo(entityState = doc.states[id], entityId = id, slot = "pic.${node.id}", sizeDp = sizeDp)
        } else {
            doc.initials(
                name = node.props["text"]?.takeIf { it.isNotBlank() } ?: "?",
                sizeDp = sizeDp,
                bg = doc.role(Role.KEY),
                ink = doc.role(Role.INK),
            )
        }
    }
}

/** The name of the variable a progress bar's fill, from 0 to 1, is sent in. */
fun nodeLevel(nodeId: String) = "n.$nodeId.level"

/** Sends [nodeLevel] of [node]: the entity's own level, or its number between `min` and `max`. */
internal fun DocContext.bindLevel(node: Node, id: String) {
    bind(id) { states ->
        val entityState = states[id]

        val level = Present.view(
            entityState,
            i18n,
        ).level ?: entityState?.numericState()?.let { value ->
            val lo = node.props["min"]?.toDoubleOrNull() ?: entityState.input.min ?: 0.0
            val hi = node.props["max"]?.toDoubleOrNull() ?: entityState.input.max ?: Metrics.LEVEL_RANGE_MAX

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

    val fill = node.props["color"]?.let { paint(
        it,
    ) } ?: fixed(Pastel.forDomain(id?.let { EntityId(it).domain.value }).argb)

    if (id != null) bindLevel(node, id)

    rcScope.Box(
        styled(node = node, axis = axis, pages = pages, base = measured(node = node, axis = axis, self = self))
    ) {
        val doc = this@progressNode

        Box(
            Modifier.size(
                w,
                doc.units.px(h),
            ).clip(doc.rounded(h / 2f)).background(doc.role(Role.TRACK)),
        ) {
            if (id != null) {
                val level = doc.float(nodeLevel(node.id))

                Canvas(Modifier.size(w, doc.units.px(h))) {
                    paint { color(fill); style(RcPaintStyle.Fill) }
                    drawRect(left = 0f.rf, top = 0f.rf, right = level * w, bottom = doc.units.px(h).rf)
                }
            }
        }
    }
}

/** The entity's history from the recorder as a line, over the last `hours` (24 unless set). */
internal fun DocContext.graphNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId

    val modifier = styled(
        node = node,
        axis = axis,
        pages = pages,
        base = measured(node = node, axis = axis, self = self)
    )

    if (id == null) {
        rcScope.Box(modifier.background(role(Role.TRACK)))

        return
    }

    refreshEvery(Metrics.GRAPH_REFRESH_SEC)
    val line = node.props["color"]?.let { paint(it) } ?: fixed(Pastel.BLUE.argb)
    val w = (self.w - node.style.padX).coerceAtLeast(1f)
    val h = (self.h - node.style.padY).coerceAtLeast(1f)

    rcScope.Box(modifier) {
        val doc = this@graphNode

        doc.graph(
            points = doc.data.history[id].orEmpty(),
            wDp = w,
            hDp = h,
            line = line,
            soft = doc.role(Role.INK_SOFT),
        )
    }
}
