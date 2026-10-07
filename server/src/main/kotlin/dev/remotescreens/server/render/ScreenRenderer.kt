@file:Suppress("RestrictedApi")

package dev.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.offset
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.CircleShape
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
        return RenderedDoc(bytes, ctx, pages.map { it.id }, home)
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
        if (placed.isEmpty()) {
            scope.Box(Modifier.offset(0f, u.px(spec.topBar)).size(u.px(spec.widthDp), u.px(spec.heightDp - spec.topBar - spec.pageBar)),
                horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                this@page.label(this@page.i18n.t("ui.empty_page"), Type.TITLE, this@page.role(Role.INK_SOFT), align = RcTextAlign.Center, lines = 3,
                    modifier = Modifier.width(this@page.u.px(spec.widthDp * 0.7f)))
            }
        }
        for (p in placed) {
            val full = p.tile.full || (p.w == spec.cols && p.h == spec.rows)
            val box = TileBox(spec.x(p.col), spec.y(p.row), spec.w(p.w), spec.h(p.h), p.w, p.h, full)
            tile(p.tile, box, pageIndex)
        }
        if (visible.size > 1 && !page.hidden) pageDots(spec, visible, index)
    }

    private fun DocContext.tileUsable(t: dev.remotescreens.shared.Tile): Boolean {
        val spec = Catalogue.of(t.effectiveKind())
        return spec != null && (t.entityId != null || !spec.needsEntity)
    }

    private fun DocContext.topBar(screen: Screen, page: Page, spec: GridSpec, back: Boolean, home: Int) {
        val ink = role(Role.INK)
        val soft = role(Role.INK_SOFT)
        val settings = ActionRef("_", "settings")
        scope.Row(
            tap(Modifier.size(u.px(spec.widthDp), u.px(spec.topBar)).padding(u.px(spec.padding + 4f), 0f, u.px(spec.padding + 4f), 0f), null, settings),
            vertical = RcVerticalPositioning.Center,
        ) {
            val c = this@topBar
            if (back) {
                c.key(ActionRef("_", "back", home.toString()), icon = "arrow-left", heightDp = 34f, widthDp = 34f, round = true)
                Spacer(Modifier.width(c.u.px(10f)))
            }
            c.label(page.title, Type.TITLE + 2f, ink, 600f, modifier = Modifier.horizontalWeight(1f))
            if (screen.topBar.temperatureEntity != null) {
                c.icon("thermometer", 18f, soft)
                c.label(c.text(V.TEMP), Type.BODY, soft, 500f)
                Spacer(Modifier.width(c.u.px(14f)))
            }
            if (screen.topBar.people) {
                c.icon("home-account", 18f, soft)
                Spacer(Modifier.width(c.u.px(4f)))
                c.label(c.text(V.PEOPLE), Type.BODY, soft, 500f)
                Spacer(Modifier.width(c.u.px(14f)))
            }
            if (screen.topBar.clock) c.label(c.text(V.TIME), Type.TITLE, ink, 600f)
        }
    }

    private fun DocContext.pageDots(spec: GridSpec, visible: List<Int>, current: Int) {
        val dot = 8f
        val hit = 28f
        val rowW = visible.size * hit
        scope.Row(
            Modifier.offset(u.px((spec.widthDp - rowW) / 2f), u.px(spec.heightDp - spec.pageBar - 2f)).size(u.px(rowW), u.px(hit)),
            horizontal = RcRowHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center,
        ) {
            val c = this@pageDots
            for (i in visible) {
                Box(c.tap(Modifier.size(c.u.px(hit), c.u.px(hit)), ActionRef("_", "page", i.toString())),
                    horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                    Box(Modifier.size(c.u.px(dot), c.u.px(dot)).clip(CircleShape()).background(c.role(if (i == current) Role.INK else Role.INK_FAINT)))
                }
            }
        }
    }

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
