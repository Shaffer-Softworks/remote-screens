@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantConfig
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Grid
import tech.shaffersoftworks.remotescreens.shared.GridSpec
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Reflow
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.VarValue

/** The device a document is built for. */
data class Device(val widthDp: Int, val heightDp: Int, val density: Float) {
    val widthPx: Int get() = (widthDp * density).toInt()
    val heightPx: Int get() = (heightDp * density).toInt()
}

/** A built document, and what it needs to stay alive: the values of its variables and the pictures it shows. */
class RenderedDoc(
    val bytes: ByteArray,
    val ctx: DocContext,
    val pageIds: List<String> = emptyList(),
    val homePage: Int = 0,
    val swipePages: List<Int> = emptyList(),
    val builtAt: Long = System.currentTimeMillis(),
) {
    val entities: Set<String> = (ctx.bindings.mapNotNull { it.entityId } + ctx.structure.map { it.first }).toSet()

    fun vars(states: Map<String, EntityState>): Map<String, VarValue> {
        val out = LinkedHashMap<String, VarValue>()
        for (b in ctx.bindings) for (v in runCatching { b.compute(states) }.getOrDefault(emptyList())) if (ctx.declares(v.name)) out[v.name] = v
        return out
    }

    /** For each action that can be foreseen: the values that will change once Home Assistant has done it. */
    fun predictions(states: Map<String, EntityState>, foresee: (ActionRef, EntityState) -> EntityState?): Map<String, List<VarValue>> {
        val now = vars(states)
        val out = LinkedHashMap<String, List<VarValue>>()
        for ((meta, pair) in ctx.predicted) {
            val (ref, entityId) = pair
            val next = states[entityId]?.let { foresee(ref, it) } ?: continue
            val after = HashMap(states).also { it[entityId] = next }
            val changed = ctx.bindings.filter { it.entityId == entityId }
                .flatMap { runCatching { it.compute(after) }.getOrDefault(emptyList()) }
                .filter { ctx.declares(it.name) && now[it.name] != it }
            if (changed.isNotEmpty()) out[meta] = changed
        }
        return out
    }

    /** Whether something drawn into the document (not a variable) has changed, so it must be built again. */
    fun stale(states: Map<String, EntityState>, now: Long = System.currentTimeMillis()): Boolean =
        ctx.structure.any { (id, what, was) -> what(states[id]) != was } ||
            (ctx.refreshSeconds > 0 && now - builtAt > ctx.refreshSeconds * 1000L)
}

class RenderInput(
    val states: Map<String, EntityState>,
    val i18n: I18n,
    val ha: HomeAssistantConfig,
    val data: RenderData,
    val palette: Palette = Themes.RPI,
) {
    fun context(device: Device) = DocContext(Units(device.density), palette, i18n, states, ha, data)
}

object ScreenRenderer {
    /** Every page of [screen] in one document, the page shown chosen by the named integer `page`. */
    fun render(screen: Screen, device: Device, input: RenderInput): RenderedDoc {
        val pages = screen.pages.ifEmpty { listOf(Page("home", input.i18n.t("state.home"))) }
        val pageIndex: (String) -> Int? = { id -> pages.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
        val home = pageIndex(screen.homePage) ?: 0
        val visible = pages.withIndex().filter { !it.value.hidden }.map { it.index }
        val showBar = screen.topBar.show
        val widthDp = device.widthDp.toFloat()
        val topBar = Grid.topBarHeight(screen.topBar)
        val navBar = Grid.navBarHeight(screen.navBar, widthDp)
        val spec = Grid.spec(screen.grid, widthDp, device.heightDp.toFloat(), topBar, navBar)

        val (bytes, ctx) = DocContext.build(device.widthPx, device.heightPx, screen.name, { input.context(device) }) { ctx ->
            ctx.bindSystem(screen)
            val page = ctx.int(Vars.PAGE, home)
            Box(Modifier.fillMaxSize().background(ctx.role(Role.PAGE))) {
                StateLayout(page, Modifier.fillMaxSize()) {
                    pages.forEachIndexed { i, p ->
                        Box(Modifier.fillMaxSize()) {
                            ctx.page(screen, p, i, spec, pageIndex, visible, home, showBar)
                        }
                    }
                }
            }
        }
        return RenderedDoc(bytes, ctx, pages.map { it.id }, home, visible)
    }

    private fun DocContext.page(
        screen: Screen,
        page: Page,
        index: Int,
        spec: GridSpec,
        pageIndex: (String) -> Int?,
        visible: List<Int>,
        home: Int,
        showBar: Boolean,
    ) {
        if (showBar) topBar(screen, page, spec, back = page.hidden, home = home)
        val contentH = spec.heightDp - spec.topBar - spec.navBar
        val root = page.root
        if (root != null) {
            freeForm(root, 0f, spec.topBar, spec.widthDp, contentH, pageIndex) { id -> screen.pages.firstOrNull { it.id == id }?.title }
            navBar(screen, spec, visible, index, home)
            return
        }
        val placed = Reflow.place(page.tiles.filter { tileUsable(it) }, screen.grid)
        if (placed.isEmpty()) {
            at(0f, spec.topBar) {
                scope.Box(Modifier.size(u.px(spec.widthDp), u.px(contentH)),
                    horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                    this@page.label(this@page.i18n.t("ui.empty_page"), Type.TITLE, this@page.role(Role.INK_SOFT), align = RcTextAlign.Center, lines = 3,
                        modifier = Modifier.width(this@page.u.px(spec.widthDp * 0.7f)))
                }
            }
        }
        for (p in placed) {
            val t = p.tile
            val full = t.full || (p.w == spec.cols && p.h == spec.rows)
            val box = if (t.edgeToEdge()) {
                TileBox(0f, spec.topBar, spec.widthDp, contentH, p.w, p.h, full = true, edge = true)
            } else {
                TileBox(spec.x(p.col), spec.y(p.row), spec.w(p.w), spec.h(p.h), p.w, p.h, full)
            }
            val named = if (t.effectiveKind() == "nav" && t.name == null) t.copy(name = screen.pages.firstOrNull { it.id == t.options["page"] }?.title) else t
            tile(named, box, pageIndex)
        }
        navBar(screen, spec, visible, index, home)
    }

    private fun DocContext.tileUsable(t: tech.shaffersoftworks.remotescreens.shared.Tile): Boolean {
        val spec = Catalogue.of(t.effectiveKind())
        return spec != null && (t.entityId != null || !spec.needsEntity)
    }

    /**
     * The RPI header: the time and date on a dark block at the left, the outdoor temperature and who is home at the
     * right. A hidden page puts a Back key and its title in between. Text and icons grow and shrink with the bar.
     */
    private fun DocContext.topBar(screen: Screen, page: Page, spec: GridSpec, back: Boolean, home: Int) {
        val bar = screen.topBar
        val ink = role(Role.INK)
        val k = (spec.topBar / Grid.TOP_BAR).coerceIn(0.7f, 2f)
        val headerSp = HEADER_SP * k
        val clockW = bar.clockWidth?.let { spec.widthDp * it / 100f } ?: minOf(350f, spec.widthDp * 0.42f)
        val background = bar.background?.let { paint(it) } ?: role(Role.HEADER)
        val clockBackground = bar.clockBackground?.let { paint(it) } ?: role(Role.HEADER_DARK)
        scope.Row(
            Modifier.size(u.px(spec.widthDp), u.px(spec.topBar)).background(background),
            vertical = RcVerticalPositioning.Center,
        ) {
            val c = this@topBar
            if (bar.clock) {
                Row(
                    Modifier.width(c.u.px(clockW)).fillMaxHeight().background(clockBackground),
                    horizontal = RcRowHorizontalPositioning.SpaceEvenly, vertical = RcVerticalPositioning.Center,
                ) {
                    c.label(c.text(Vars.TIME), headerSp, ink, Weight.BOLD)
                    if (bar.date && spec.widthDp >= Grid.NARROW) c.label(c.text(Vars.DAY), headerSp, ink)
                }
            }
            Spacer(Modifier.width(c.u.px(spec.padding + 8f)))
            Row(Modifier.horizontalWeight(1f), vertical = RcVerticalPositioning.Center) {
                if (back) {
                    val keyDp = minOf(34f * k, spec.topBar - 6f)
                    val backRef = ActionRef("_", "back", home.toString())
                    c.key(backRef, icon = "arrow-left", heightDp = keyDp, widthDp = keyDp, round = true)
                    Spacer(Modifier.width(c.u.px(10f * k)))
                    c.label(page.title, Type.TITLE * k, ink, Weight.MEDIUM)
                }
            }
            if (bar.temperatureEntity != null) {
                c.icon("thermometer", 26f * k, c.role(Role.BLUE))
                Spacer(Modifier.width(c.u.px(4f * k)))
                c.label(c.text(Vars.TEMP), headerSp, ink)
                Spacer(Modifier.width(c.u.px(15f * k)))
            }
            if (bar.people) {
                c.icon("home-account", 26f * k, c.role(Role.ACCENT_SOFT))
                Spacer(Modifier.width(c.u.px(4f * k)))
                c.label(c.text(Vars.PEOPLE), headerSp, ink)
                Spacer(Modifier.width(c.u.px(15f * k)))
            }
            if (bar.wifi) {
                c.icon("wifi", 26f * k, c.role(Role.ACCENT_SOFT))
                Spacer(Modifier.width(c.u.px(4f * k)))
                c.label(c.text(Vars.WIFI), headerSp, ink)
                Spacer(Modifier.width(c.u.px(15f * k)))
            }
            if (bar.battery) {
                Box(Modifier.size(c.u.px(26f * k), c.u.px(26f * k))) {
                    c.icon("battery", 26f * k, c.role(Role.ACCENT_SOFT), c.int(Vars.BATTERY_IDLE))
                    c.icon("battery-charging", 26f * k, c.role(Role.ACCENT_SOFT), c.int(Vars.BATTERY_CHARGING))
                }
                Spacer(Modifier.width(c.u.px(4f * k)))
                c.label(c.text(Vars.BATTERY), headerSp, ink)
                Spacer(Modifier.width(c.u.px(15f * k)))
            }
            Spacer(Modifier.width(c.u.px(5f)))
        }
    }

    /**
     * The RPI navigation bar: a teal line over a dark row of buttons, one per visible page and Settings last, the
     * current page's button filled teal. Square buttons narrow when there are more than the width holds.
     */
    private fun DocContext.navBar(screen: Screen, spec: GridSpec, visible: List<Int>, current: Int, home: Int) {
        val bar = screen.navBar
        val barH = spec.navBar - NAV_LINE
        val count = visible.size + 1
        val buttonW = if (bar.layout == "fill") spec.widthDp / count else minOf(barH, spec.widthDp / count)
        val scale = barH / (Grid.navBar(spec.widthDp) - NAV_LINE)
        val style = NavStyle(bar.labels, scale, bar.active?.let { paint(it) } ?: role(Role.NAV_ON))
        val lineColor = bar.line?.let { paint(it) } ?: role(Role.NAV_LINE)
        val background = bar.background?.let { paint(it) } ?: role(Role.NAV)
        at(0f, spec.heightDp - spec.navBar) { scope.Column(Modifier.size(u.px(spec.widthDp), u.px(spec.navBar))) {
            val c = this@navBar
            Box(Modifier.size(c.u.px(spec.widthDp), c.u.px(NAV_LINE)).background(lineColor))
            Row(
                Modifier.size(c.u.px(spec.widthDp), c.u.px(barH)).background(background),
                horizontal = navArrangement(bar.layout, count),
                vertical = RcVerticalPositioning.Center,
            ) {
                for (i in visible) {
                    val p = screen.pages[i]
                    val icon = p.icon ?: if (i == home) "home" else "view-dashboard"
                    c.navButton(p.title, icon, buttonW, barH, i == current, ActionRef("_", "page", i.toString()), style)
                }
                c.navButton(c.i18n.t("ui.settings"), "cog", buttonW, barH, false, ActionRef("_", "settings"), style)
            }
        } }
    }

    /** Spread out spaces the buttons apart, centred packs them in the middle, fill stretches them to the width. */
    private fun navArrangement(layout: String, count: Int) = when {
        layout == "center" -> RcRowHorizontalPositioning.Center
        layout == "fill" -> RcRowHorizontalPositioning.Start
        count > 2 -> RcRowHorizontalPositioning.SpaceBetween
        else -> RcRowHorizontalPositioning.SpaceEvenly
    }

    /** How navigation buttons look: icon, word or both, [scale] against the default bar, the current page's fill. */
    private class NavStyle(val labels: String, val scale: Float, val on: RcColor)

    private fun DocContext.navButton(
        title: String,
        icon: String,
        wDp: Float,
        hDp: Float,
        on: Boolean,
        ref: ActionRef,
        style: NavStyle,
    ) {
        var m = Modifier.size(u.px(wDp), u.px(hDp))
        if (on) m = m.background(style.on)
        scope.Column(tap(m, ref), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
            val c = this@navButton
            val word = title.uppercase(c.i18n.locale)
            val wordW = Modifier.width(c.u.px(wDp - 4f))
            val ink = c.role(Role.INK)
            when (style.labels) {
                "icons" -> c.icon(icon, minOf(NAV_ICON_ONLY_MAX, hDp * NAV_ICON_ONLY_SHARE), ink)
                "text" -> {
                    val sp = (NAV_WORD_ONLY_SP * style.scale).coerceIn(NAV_WORD_ONLY_MIN_SP, NAV_WORD_ONLY_MAX_SP)
                    c.label(word, sp, ink, Weight.MEDIUM, lines = 2, align = RcTextAlign.Center, modifier = wordW)
                }
                else -> {
                    c.icon(icon, minOf(40f, hDp * 0.4f), ink)
                    Spacer(Modifier.height(c.u.px(5f * style.scale.coerceAtMost(NAV_GAP_MAX_SCALE))))
                    val sp = (10f * style.scale).coerceIn(NAV_LABEL_MIN_SP, NAV_LABEL_MAX_SP)
                    c.label(word, sp, ink, align = RcTextAlign.Center, modifier = wordW)
                }
            }
        }
    }

    private const val HEADER_SP = 20f
    private const val NAV_LINE = 2f
    private const val NAV_ICON_ONLY_MAX = 48f
    private const val NAV_ICON_ONLY_SHARE = 0.55f
    private const val NAV_WORD_ONLY_SP = 14f
    private const val NAV_WORD_ONLY_MIN_SP = 10f
    private const val NAV_WORD_ONLY_MAX_SP = 24f
    private const val NAV_LABEL_MIN_SP = 9f
    private const val NAV_LABEL_MAX_SP = 16f
    private const val NAV_GAP_MAX_SCALE = 1.5f

    /** The clock, the outdoor temperature and who is home: values every page shows. */
    private fun DocContext.bindSystem(screen: Screen) {
        val tempEntity = screen.topBar.temperatureEntity
        bind(tempEntity) { states ->
            val t = tempEntity?.let { states[it] }
            val text = when {
                t == null -> ""
                t.domain == "weather" -> t.number("temperature")?.let { i18n.number(it, 0) + "°" } ?: ""
                else -> t.numericState()?.let { i18n.number(it, 0) + "°" } ?: t.state
            }
            listOf(VarValue.string(Vars.TEMP, text))
        }
        for (p in states.keys.filter { it.startsWith("person.") }) bind(p) { emptyList() }
        bind(null) { states ->
            val home = states.values.count { it.domain == "person" && it.state == "home" }
            listOf(VarValue.string(Vars.PEOPLE, home.toString()))
        }
    }
}
