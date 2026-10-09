@file:Suppress("RestrictedApi")

package dev.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
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
import dev.remotescreens.server.I18n
import dev.remotescreens.server.ha.EntityState
import dev.remotescreens.server.ha.HaConfig
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.Catalogue
import dev.remotescreens.shared.Grid
import dev.remotescreens.shared.GridSpec
import dev.remotescreens.shared.Page
import dev.remotescreens.shared.Reflow
import dev.remotescreens.shared.Screen
import dev.remotescreens.shared.VarValue

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
            val current = states[entityId] ?: continue
            val next = foresee(ref, current) ?: continue
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
    val ha: HaConfig,
    val data: RenderData,
    val dark: Boolean,
) {
    fun context(device: Device) = DocContext(Units(device.density), dark, i18n, states, ha, data)
}

object ScreenRenderer {
    /** Every page of [screen] in one document, the page shown chosen by the named integer `page`. */
    fun render(screen: Screen, device: Device, input: RenderInput): RenderedDoc {
        val pages = screen.pages.ifEmpty { listOf(Page("home", input.i18n.t("state.home"))) }
        val pageIndex: (String) -> Int? = { id -> pages.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
        val home = pageIndex(screen.homePage) ?: 0
        val visible = pages.withIndex().filter { !it.value.hidden }.map { it.index }
        val showBar = screen.topBar.show
        val spec = Grid.spec(screen.grid, device.widthDp.toFloat(), device.heightDp.toFloat(), showBar)

        val (bytes, ctx) = DocContext.build(device.widthPx, device.heightPx, screen.name, { input.context(device) }) { ctx ->
            ctx.bindSystem(screen)
            val page = ctx.int(V.PAGE, home)
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
        val placed = Reflow.place(page.tiles.filter { tileUsable(it) }, screen.grid)
        val contentH = spec.heightDp - spec.topBar - spec.navBar
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

    private fun DocContext.tileUsable(t: dev.remotescreens.shared.Tile): Boolean {
        val spec = Catalogue.of(t.effectiveKind())
        return spec != null && (t.entityId != null || !spec.needsEntity)
    }

    /**
     * The RPI header: the time and date on a dark block at the left, the outdoor temperature and who is home at the
     * right. A hidden page puts a Back key and its title in between.
     */
    private fun DocContext.topBar(screen: Screen, page: Page, spec: GridSpec, back: Boolean, home: Int) {
        val ink = role(Role.INK)
        scope.Row(
            Modifier.size(u.px(spec.widthDp), u.px(spec.topBar)).background(role(Role.HEADER)),
            vertical = RcVerticalPositioning.Center,
        ) {
            val c = this@topBar
            if (screen.topBar.clock) {
                Row(
                    Modifier.width(c.u.px(minOf(350f, spec.widthDp * 0.42f))).fillMaxHeight().background(c.role(Role.HEADER_DARK)),
                    horizontal = RcRowHorizontalPositioning.SpaceEvenly, vertical = RcVerticalPositioning.Center,
                ) {
                    c.label(c.text(V.TIME), HEADER_SP, ink, 700f)
                    c.label(c.text(V.DAY), HEADER_SP, ink)
                }
            }
            Spacer(Modifier.width(c.u.px(spec.padding + 8f)))
            Row(Modifier.horizontalWeight(1f), vertical = RcVerticalPositioning.Center) {
                if (back) {
                    c.key(ActionRef("_", "back", home.toString()), icon = "arrow-left", heightDp = 34f, widthDp = 34f, round = true)
                    Spacer(Modifier.width(c.u.px(10f)))
                    c.label(page.title, Type.TITLE, ink, 500f)
                }
            }
            if (screen.topBar.temperatureEntity != null) {
                c.icon("thermometer", 26f, c.role(Role.BLUE))
                Spacer(Modifier.width(c.u.px(4f)))
                c.label(c.text(V.TEMP), HEADER_SP, ink)
                Spacer(Modifier.width(c.u.px(15f)))
            }
            if (screen.topBar.people) {
                c.icon("home-account", 26f, c.role(Role.ACCENT_SOFT))
                Spacer(Modifier.width(c.u.px(4f)))
                c.label(c.text(V.PEOPLE), HEADER_SP, ink)
                Spacer(Modifier.width(c.u.px(15f)))
            }
            Spacer(Modifier.width(c.u.px(5f)))
        }
    }

    /**
     * The RPI navigation bar: a teal line over a dark row of square buttons, one per visible page and Settings last,
     * the current page's button filled teal. Buttons narrow when there are more than the width holds.
     */
    private fun DocContext.navBar(screen: Screen, spec: GridSpec, visible: List<Int>, current: Int, home: Int) {
        val line = 2f
        val barH = spec.navBar - line
        val count = visible.size + 1
        val buttonW = minOf(barH, spec.widthDp / count)
        at(0f, spec.heightDp - spec.navBar) { scope.Column(Modifier.size(u.px(spec.widthDp), u.px(spec.navBar))) {
            val c = this@navBar
            Box(Modifier.size(c.u.px(spec.widthDp), c.u.px(line)).background(c.role(Role.NAV_LINE)))
            Row(
                Modifier.size(c.u.px(spec.widthDp), c.u.px(barH)).background(c.role(Role.NAV)),
                horizontal = if (count > 2) RcRowHorizontalPositioning.SpaceBetween else RcRowHorizontalPositioning.SpaceEvenly,
                vertical = RcVerticalPositioning.Center,
            ) {
                for (i in visible) {
                    val p = screen.pages[i]
                    c.navButton(p.title, p.icon ?: if (i == home) "home" else "view-dashboard", buttonW, barH, i == current, ActionRef("_", "page", i.toString()))
                }
                c.navButton(c.i18n.t("ui.settings"), "cog", buttonW, barH, false, ActionRef("_", "settings"))
            }
        } }
    }

    private fun DocContext.navButton(title: String, icon: String, wDp: Float, hDp: Float, on: Boolean, ref: ActionRef) {
        var m = Modifier.size(u.px(wDp), u.px(hDp))
        if (on) m = m.background(role(Role.NAV_ON))
        scope.Column(tap(m, ref), horizontal = RcHorizontalPositioning.Center, vertical = RcColumnVerticalPositioning.Center) {
            val c = this@navButton
            c.icon(icon, minOf(40f, hDp * 0.4f), c.role(Role.INK))
            Spacer(Modifier.height(c.u.px(5f)))
            c.label(title.uppercase(c.i18n.locale), 10f, c.role(Role.INK), align = RcTextAlign.Center, modifier = Modifier.width(c.u.px(wDp - 4f)))
        }
    }

    private const val HEADER_SP = 20f

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
            listOf(VarValue.string(V.TEMP, text))
        }
        for (p in states.keys.filter { it.startsWith("person.") }) bind(p) { emptyList() }
        bind(null) { states ->
            val home = states.values.count { it.domain == "person" && it.state == "home" }
            listOf(VarValue.string(V.PEOPLE, home.toString()))
        }
    }
}
