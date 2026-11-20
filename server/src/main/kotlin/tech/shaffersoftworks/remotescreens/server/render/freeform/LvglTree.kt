package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.render.theme.Palette
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.tapRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Node
import kotlin.math.roundToInt

private val CONTAINERS = setOf("column", "row", "box", "flow")

private val PANEL_LEAVES = setOf(
    "text", "clock", "badge", "countdown", "icon", "iconbutton", "button", "switch",
    "slider", "progress", "gauge", "qr", "image", "spacer", "divider",
)

/** The page tree as the widgets a panel can draw. Anything else is named in [skipped]. */
internal class LvglTree(
    private val palette: Palette,
    private val sources: LvglSources,
    private val skipped: MutableList<String>,
) {
    private var count = 0

    fun build(root: Node): LvglWidget = widget(node = root, depth = 1) ?: emptyPage(root.id)

    private fun widget(node: Node, depth: Int): LvglWidget? {
        val built = if (refused(node = node, depth = depth)) null else draw(node = node, depth = depth)

        if (built != null) count += 1

        return built
    }

    private fun refused(node: Node, depth: Int): Boolean {
        val tooDeep = "${node.type} ${node.id} is nested too deep for the panel"
        val tooMany = "This page has more than ${LvglLimits.MAX_WIDGETS} components; the rest stay off the panel"

        val message = when {
            node.hidden -> ""
            depth > LvglLimits.MAX_DEPTH -> tooDeep
            count >= LvglLimits.MAX_WIDGETS -> tooMany
            else -> null
        }

        if (message != null && message.isNotEmpty() && message !in skipped) skipped += message

        return message != null
    }

    private fun draw(node: Node, depth: Int): LvglWidget? = when (node.type) {
        in CONTAINERS -> container(node = node, depth = depth)
        "tabs" -> tabs(node = node, depth = depth)
        in PANEL_LEAVES -> leaf(node)
        else -> {
            skipped += "${node.type} ${node.id} stays on the tablet"
            null
        }
    }

    private fun container(node: Node, depth: Int): LvglWidget {
        val width = lvglSize(node.size.width)
        val height = lvglSize(node.size.height)
        val nested = node.children.mapNotNull { widget(node = it, depth = depth + 1) }

        return styled(
            node = node,
            kind = node.type,
            width = width,
            height = height,
            children = nested.takeIf { it.isNotEmpty() },
        ).copy(
            gap = gapOf(node),
            align = alignOf(node),
            scroll = true.takeIf { node.props["scroll"] == "true" },
        )
    }

    private fun tabs(node: Node, depth: Int): LvglWidget {
        val labels = node.props["labels"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

        val pages = node.children.mapIndexedNotNull { index, child ->
            widget(node = child, depth = depth + 1)?.let { index to it }
        }

        return styled(
            node = node,
            kind = "tabs",
            width = lvglSize(node.size.width),
            height = lvglSize(node.size.height),
            children = pages.map { it.second }.takeIf { it.isNotEmpty() },
        ).copy(tabs = pages.map { (index, _) -> labels.getOrNull(index) ?: "Tab ${index + 1}" })
    }

    private fun leaf(node: Node): LvglWidget = when (node.type) {
        "icon", "iconbutton" -> icon(node)
        "button" -> button(node)
        "switch", "slider", "progress", "gauge" -> control(node)
        "qr" -> qr(node)
        "image" -> image(node)
        "spacer" -> spacer(node)
        "divider" -> divider(node)
        else -> text(node)
    }

    private fun text(node: Node): LvglWidget {
        val fallback = when (node.type) {
            "clock", "countdown" -> LvglLimits.DEFAULT_CLOCK_PX
            "badge" -> LvglLimits.DEFAULT_BADGE_PX
            else -> LvglLimits.DEFAULT_TEXT_PX
        }

        val badge = node.type == "badge"

        val width = lvglSize(node.size.width)
        val height = lvglSize(node.size.height)

        return styled(node = node, kind = "text", width = width, height = height)
            .copy(
                text = sources.text(node),
                color = palette.panelColor(key = if (badge) null else node.props["color"], fallback = Role.INK),
                bg = if (badge) {
                    palette.panelColor(key = node.props["color"], fallback = Role.KEY)
                } else {
                    node.style.background?.let { palette.panelColor(key = it, fallback = Role.CARD) }
                },
                size = fontPx(node, fallback),
                align = alignOf(node),
                radius = radiusOf(node.style) ?: if (badge) LvglLimits.BADGE_RADIUS_PX else null,
            )
    }

    private fun icon(node: Node): LvglWidget {
        val on = sources.isOn(node)
        val lit = node.props["colorOn"] ?: node.props["color"]
        val dim = node.props["colorOff"] ?: node.props["color"]
        val key = if (on) lit else dim
        val width = lvglSize(node.size.width)
        val height = lvglSize(node.size.height)

        return styled(node = node, kind = "icon", width = width, height = height)
            .copy(
                text = sources.icon(node),
                color = palette.panelColor(key, if (on) Role.ACCENT else Role.INK),
                size = node.props["size"]?.toFloatOrNull()?.roundToInt() ?: LvglLimits.DEFAULT_ICON_PX,
            )
    }

    private fun button(node: Node): LvglWidget = styled(
        node = node,
        kind = "button",
        width = lvglSize(node.size.width),
        height = lvglSize(node.size.height).let { size ->
            if (size.token == "wrap") LvglSize(token = LvglLimits.BUTTON_HEIGHT_PX.toString(), grow = null) else size
        },
    ).copy(
        text = sources.text(node).ifEmpty { node.props["text"].orEmpty() },
        color = palette.panelColor(node.props["ink"], Role.INK),
        bg = palette.panelColor(node.props["color"], Role.ACCENT),
        size = fontPx(node, LvglLimits.DEFAULT_BUTTON_PX),
    )

    private fun control(node: Node): LvglWidget {
        val min = node.props["min"]?.toFloatOrNull()?.roundToInt() ?: 0
        val max = node.props["max"]?.toFloatOrNull()?.roundToInt() ?: LvglLimits.DEFAULT_RANGE
        val span = (max - min).coerceAtLeast(1)
        val level = sources.level(node)

        val value = if (node.type == "switch") {
            if (sources.isOn(node)) 1 else 0
        } else {
            level?.let { (min + it * span).roundToInt().coerceIn(min, max) } ?: min
        }

        val kind = when (node.type) {
            "switch" -> "switch"
            "slider" -> "slider"
            "gauge" -> "arc"
            else -> "bar"
        }

        return styled(node = node, kind = kind, width = lvglSize(node.size.width), height = lvglSize(node.size.height))
            .copy(
                value = value,
                min = if (kind == "switch") null else min,
                max = if (kind == "switch") null else max,
                color = palette.panelColor(node.props["color"], Role.ACCENT),
                text = node.props["caption"],
            )
    }

    private fun qr(node: Node): LvglWidget = styled(
        node = node,
        kind = "qr",
        width = lvglSize(node.size.width),
        height = lvglSize(node.size.height),
    ).copy(text = node.props["text"].orEmpty(), size = node.props["size"]?.toFloatOrNull()?.roundToInt())

    private fun image(node: Node): LvglWidget = styled(
        node = node,
        kind = "image",
        width = lvglSize(node.size.width),
        height = lvglSize(node.size.height),
    ).copy(text = sources.text(node).ifEmpty { node.bind?.entityId.orEmpty() })

    private fun spacer(node: Node): LvglWidget {
        val height = lvglSize(node.size.height).let { size ->
            if (size.token == "wrap") LvglSize(token = LvglLimits.SPACER_PX.toString(), grow = null) else size
        }

        return styled(node = node, kind = "spacer", width = lvglSize(node.size.width), height = height)
    }

    private fun divider(node: Node): LvglWidget = styled(
        node = node,
        kind = "divider",
        width = filled(lvglSize(node.size.width)),
        height = LvglSize(token = LvglLimits.DIVIDER_PX.toString(), grow = null),
    ).copy(bg = palette.panelColor(node.props["color"], Role.DIVIDER))

    private fun filled(size: LvglSize): LvglSize =
        if (size.token == "wrap") LvglSize(token = "fill", grow = null) else size

    private fun styled(
        node: Node,
        kind: String,
        width: LvglSize,
        height: LvglSize,
        children: List<LvglWidget>? = null,
    ) =
        LvglWidget(
            id = node.id,
            k = kind,
            w = width.token,
            h = height.token,
            grow = width.grow ?: height.grow,
            pad = padOf(node.style),
            radius = radiusOf(node.style),
            border = borderOf(node.style),
            bg = node.style.background?.let { palette.panelColor(it, Role.CARD) },
            edge = node.style.borderColor?.let { palette.panelColor(it, Role.DIVIDER) },
            tap = reportsTap(node).takeIf { it },
            c = children,
        )

    private fun emptyPage(id: String) = LvglWidget(id = id, k = "column", w = "fill", h = "fill")
}

private val DIRECT_TAPS = setOf("button", "switch", "iconbutton", "slider")

/** True when a tap on [node] would go through the same router as a tile. */
internal fun reportsTap(node: Node): Boolean {
    val tile = node.asTile()
    val spec = tile?.let { Catalogue.of(it.effectiveKind()) }

    val tap = tile?.let {
        tapRef(tile = it, action = it.tap, spec = spec, pageIndex = { _ -> 0 }, hold = false)
    }

    val hold = tile?.let {
        tapRef(tile = it, action = it.hold, spec = spec, pageIndex = { _ -> 0 }, hold = true)
    }

    return node.type in DIRECT_TAPS || tap != null || hold != null
}
