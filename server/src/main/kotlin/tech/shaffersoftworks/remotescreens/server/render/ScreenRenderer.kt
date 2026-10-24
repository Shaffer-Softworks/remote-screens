@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.render.theme.Palette
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.server.render.theme.Units
import tech.shaffersoftworks.remotescreens.server.render.tiles.edgeToEdge

import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.tile

import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
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
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.at
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Grid
import tech.shaffersoftworks.remotescreens.shared.GridSpec
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Reflow
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.TopBar
import tech.shaffersoftworks.remotescreens.shared.VarValue

private const val CLOCK_WIDTH_SHARE = 0.42f
private const val NAV_BOTH_ICON_SHARE = 0.4f
private const val TOP_BAR_MIN_SCALE = 0.7f
private const val CLOCK_MAX_WIDTH_DP = 350f
private const val NAV_LABEL_BASE_SP = 10f
private const val STAT_GAP_DP = 15f
private const val STAT_ICON_DP = 26f
private const val NAV_BOTH_ICON_MAX_DP = 40f
private const val STAT_ICON_GAP_DP = 4f
private const val NAV_ICON_LABEL_GAP_DP = 5f
private const val CLOCK_PAD_EXTRA_DP = 8f
private const val MS_PER_SECOND = 1000L
private const val PERCENT = 100f
private const val LAYOUT_SCALE_MAX = 2f
private const val BACK_KEY_BASE_DP = 34f
private const val BACK_KEY_INSET_DP = 6f

/** The device a document is built for. */
data class Device(val widthDp: Int, val heightDp: Int, val density: Float) {
    val widthPx: Int get() = (widthDp * density).toInt()
    val heightPx: Int get() = (heightDp * density).toInt()
}

/** A built document, and what it needs to stay alive: the values of its variables and the pictures it shows. */
class RenderedDoc(
    val bytes: ByteArray,
    val docContext: DocContext,
    val pageIds: List<String> = emptyList(),
    val homePage: Int = 0,
    val swipePages: List<Int> = emptyList(),
    val builtAt: Long = System.currentTimeMillis(),
) {
    val entities: Set<String> = (docContext.bindings.mapNotNull { it.entityId } + docContext.structure.map { it.first }).toSet()

    fun vars(states: Map<String, EntityState>): Map<String, VarValue> {
        val out = LinkedHashMap<String, VarValue>()
        for (binding in docContext.bindings) {
            for (value in runCatching { binding.compute(states) }.getOrDefault(emptyList())) {
                if (docContext.declares(value.name)) out[value.name] = value
            }
        }
        return out
    }

    /** For each action that can be foreseen: the values that will change once Home Assistant has done it. */
    fun predictions(states: Map<String, EntityState>, foresee: (ActionRef, EntityState) -> EntityState?): Map<String, List<VarValue>> {
        val now = vars(states)
        val out = LinkedHashMap<String, List<VarValue>>()
        for ((meta, pair) in docContext.predicted) {
            val (ref, entityId) = pair
            val next = states[entityId]?.let { foresee(ref, it) } ?: continue
            val after = HashMap(states).also { it[entityId] = next }
            val changed = docContext.bindings.filter { it.entityId == entityId }
                .flatMap { runCatching { it.compute(after) }.getOrDefault(emptyList()) }
                .filter { docContext.declares(it.name) && now[it.name] != it }
            if (changed.isNotEmpty()) out[meta] = changed
        }
        return out
    }

    /** Whether a structural entity changed, so the document must be built again. */
    fun structureChanged(states: Map<String, EntityState>): Boolean =
        docContext.structure.any { (id, what, was) -> what(states[id]) != was }

    /** Whether a timed refresh (forecast, to-do list, …) is due. */
    fun isTimedRefreshDue(now: Long = System.currentTimeMillis()): Boolean =
        docContext.refreshSeconds > 0 && now - builtAt > docContext.refreshSeconds * MS_PER_SECOND

    /** Whether something drawn into the document (not a variable) has changed, so it must be built again. */
    fun stale(states: Map<String, EntityState>, now: Long = System.currentTimeMillis()): Boolean =
        structureChanged(states) || isTimedRefreshDue(now)
}

class RenderInput(
    val states: Map<String, EntityState>,
    val i18n: I18n,
    val homeAssistantConfig: HomeAssistantConfig,
    val data: RenderData,
    val palette: Palette = Themes.RPI,
    val supportsCalendarDialogs: Boolean = false,
) {
    fun context(device: Device) = DocContext(
        Units(device.density),
        palette,
        i18n,
        states,
        homeAssistantConfig,
        data,
        supportsCalendarDialogs,
    )
}

object ScreenRenderer {
    /**
     * Every page of [screen] in one document, the page shown chosen by the named integer `page`.
     * [initialPage] is the page the tablet is on, so a rebuild does not flash the home page before the player
     * restores it.
     */
    fun render(screen: Screen, device: Device, input: RenderInput, initialPage: Int = 0): RenderedDoc {
        val pages = screen.pages.ifEmpty { listOf(Page("home", input.i18n.t("state.home"))) }
        val pageIndex: (String) -> Int? = { id -> pages.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
        val home = pageIndex(screen.homePage) ?: 0
        val pageCount = pages.size.coerceAtLeast(1)
        val startPage = initialPage.coerceIn(0, pageCount - 1)
        val visible = pages.withIndex().filter { !it.value.hidden }.map { it.index }
        val showBar = screen.topBar.show
        val widthDp = device.widthDp.toFloat()
        val topBar = Grid.topBarHeight(screen.topBar)
        val navBar = Grid.navBarHeight(screen.navBar, widthDp)
        val spec = Grid.spec(screen.grid, widthDp, device.heightDp.toFloat(), topBar, navBar)

        val (bytes, docContext) = DocContext.build(
            device.widthPx,
            device.heightPx,
            screen.name,
            { input.context(device) },
        ) { docContext ->
            docContext.bindSystem(screen)
            val page = docContext.int(Vars.PAGE, startPage)
            Box(Modifier.fillMaxSize().background(docContext.role(Role.PAGE))) {
                StateLayout(page, Modifier.fillMaxSize()) {
                    pages.forEachIndexed { pageNumber, screenPage ->
                        Box(Modifier.fillMaxSize()) {
                            docContext.page(
                                screen,
                                screenPage,
                                pageNumber,
                                spec,
                                pageIndex,
                                visible,
                                home,
                                showBar,
                            )
                        }
                    }
                }
            }
        }
        return RenderedDoc(bytes, docContext, pages.map { it.id }, home, visible)
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
        renderingPage = index
        if (showBar) topBar(screen, page, spec, back = page.hidden, home = home)
        val contentH = spec.heightDp - spec.topBar - spec.navBar
        val root = page.root
        if (root != null) {
            freeForm(
                root,
                0f,
                spec.topBar,
                spec.widthDp,
                contentH,
                pageIndex,
            ) { id -> screen.pages.firstOrNull { it.id == id }?.title }
            navBar(screen, spec, visible, index, home)
            return
        }
        val placed = Reflow.place(page.tiles.filter { tileUsable(it) }, screen.grid)
        if (placed.isEmpty()) {
            at(0f, spec.topBar) {
                scope.Box(
                    Modifier.size(units.px(spec.widthDp), units.px(contentH)),
                    horizontal = RcHorizontalPositioning.Center,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    this@page.label(
                        this@page.i18n.t("ui.empty_page"),
                        Type.TITLE,
                        this@page.role(Role.INK_SOFT),
                        align = RcTextAlign.Center,
                        lines = 3,
                        modifier = Modifier.width(
                            this@page.units.px(spec.widthDp * TOP_BAR_MIN_SCALE),
                        ),
                    )
                }
            }
        }
        for (placement in placed) {
            val tile = placement.tile
            val box = tileBox(spec, placement, contentH)
            val named = if (tile.effectiveKind() == "nav" && tile.name == null) {
                tile.copy(
                    name = screen.pages.firstOrNull { it.id == tile.options["page"] }?.title,
                )
            } else {
                tile
            }
            tile(named, box, pageIndex)
        }
        navBar(screen, spec, visible, index, home)
    }

    private fun tileBox(spec: GridSpec, placement: Reflow.Placed, contentH: Float): TileBox {
        val tile = placement.tile
        val full = tile.full || (placement.w == spec.cols && placement.h == spec.rows)
        return if (tile.edgeToEdge()) {
            TileBox(
                0f,
                spec.topBar,
                spec.widthDp,
                contentH,
                placement.w,
                placement.h,
                full = true,
                edge = true,
            )
        } else {
            TileBox(
                spec.x(placement.col),
                spec.y(placement.row),
                spec.w(placement.w),
                spec.h(placement.h),
                placement.w,
                placement.h,
                full,
            )
        }
    }

    private fun DocContext.tileUsable(tile: tech.shaffersoftworks.remotescreens.shared.Tile): Boolean {
        val spec = Catalogue.of(tile.effectiveKind())
        return spec != null && (tile.entityId != null || !spec.needsEntity)
    }

    /**
     * The RPI header: the time and date on a dark block at the left, the outdoor temperature and who is home at the
     * right. A hidden page puts a Back key and its title in between. Text and icons grow and shrink with the bar.
     */
    private fun DocContext.topBar(screen: Screen, page: Page, spec: GridSpec, back: Boolean, home: Int) {
        val bar = screen.topBar
        val ink = role(Role.INK)
        val layoutScale = (spec.topBar / Grid.TOP_BAR).coerceIn(TOP_BAR_MIN_SCALE, LAYOUT_SCALE_MAX)
        val headerSp = HEADER_SP * layoutScale
        val clockW = bar.clockWidth?.let { spec.widthDp * it / PERCENT } ?: minOf(
            CLOCK_MAX_WIDTH_DP,
            spec.widthDp * CLOCK_WIDTH_SHARE,
        )
        val background = bar.background?.let { paint(it) } ?: role(Role.HEADER)
        val clockBackground = bar.clockBackground?.let { paint(it) } ?: role(Role.HEADER_DARK)
        scope.Row(
            Modifier.size(units.px(spec.widthDp), units.px(spec.topBar)).background(background),
            vertical = RcVerticalPositioning.Center,
        ) {
            val doc = this@topBar
            if (bar.clock) {
                Row(
                    Modifier.width(
                        doc.units.px(clockW),
                    ).fillMaxHeight().background(clockBackground),
                    horizontal = RcRowHorizontalPositioning.SpaceEvenly,
                    vertical = RcVerticalPositioning.Center,
                ) {
                    doc.label(doc.text(Vars.TIME), headerSp, ink, Weight.BOLD)
                    if (bar.date && spec.widthDp >= Grid.NARROW) {
                        doc.label(
                            doc.text(Vars.DAY),
                            headerSp,
                            ink,
                        )
                    }
                }
            }
            Spacer(Modifier.width(doc.units.px(spec.padding + CLOCK_PAD_EXTRA_DP)))
            Row(Modifier.horizontalWeight(1f), vertical = RcVerticalPositioning.Center) {
                if (back) {
                    val keyDp = minOf(BACK_KEY_BASE_DP * layoutScale, spec.topBar - BACK_KEY_INSET_DP)
                    val backRef = ActionRef("_", "back", home.toString())
                    doc.key(
                        backRef,
                        icon = "arrow-left",
                        heightDp = keyDp,
                        widthDp = keyDp,
                        round = true,
                    )
                    Spacer(Modifier.width(doc.units.px(NAV_LABEL_BASE_SP * layoutScale)))
                    doc.label(page.title, Type.TITLE * layoutScale, ink, Weight.MEDIUM)
                }
            }
            doc.topBarTrailing(bar, headerSp, layoutScale, ink)
            Spacer(Modifier.width(doc.units.px(NAV_ICON_LABEL_GAP_DP)))
        }
    }

    /** Outdoor temperature, people at home, Wi‑Fi and battery on the right of the header. */
    private fun DocContext.topBarTrailing(bar: TopBar, headerSp: Float, layoutScale: Float, ink: RcColor) {
        if (bar.temperatureEntity != null) {
            topBarStat("thermometer", Vars.TEMP, headerSp, layoutScale, ink, role(Role.BLUE))
        }
        if (bar.people) topBarStat("home-account", Vars.PEOPLE, headerSp, layoutScale, ink)
        if (bar.wifi) topBarStat("wifi", Vars.WIFI, headerSp, layoutScale, ink)
        if (bar.battery) topBarBattery(headerSp, layoutScale, ink)
    }

    /** An icon and a value on the right of the header, with the usual gap after it. */
    private fun DocContext.topBarStat(
        icon: String,
        value: String,
        headerSp: Float,
        layoutScale: Float,
        ink: RcColor,
        iconColor: RcColor = role(Role.ACCENT_SOFT),
    ) {
        this.icon(icon, STAT_ICON_DP * layoutScale, iconColor)
        scope.Spacer(Modifier.width(units.px(STAT_ICON_GAP_DP * layoutScale)))
        label(text(value), headerSp, ink)
        scope.Spacer(Modifier.width(units.px(STAT_GAP_DP * layoutScale)))
    }

    /** Battery level with the idle / charging icons swapped by visibility. */
    private fun DocContext.topBarBattery(headerSp: Float, layoutScale: Float, ink: RcColor) {
        val tint = role(Role.ACCENT_SOFT)
        scope.Box(
            Modifier.size(
                units.px(STAT_ICON_DP * layoutScale),
                units.px(STAT_ICON_DP * layoutScale),
            ),
        ) {
            icon("battery", STAT_ICON_DP * layoutScale, tint, int(Vars.BATTERY_IDLE))
            icon("battery-charging", STAT_ICON_DP * layoutScale, tint, int(Vars.BATTERY_CHARGING))
        }
        scope.Spacer(Modifier.width(units.px(STAT_ICON_GAP_DP * layoutScale)))
        label(text(Vars.BATTERY), headerSp, ink)
        scope.Spacer(Modifier.width(units.px(STAT_GAP_DP * layoutScale)))
    }

    /**
     * The RPI navigation bar: a teal line over a dark row of buttons, one per visible page and Settings last, the
     * current page's button filled teal. Square buttons narrow when there are more than the width holds.
     */
    private fun DocContext.navBar(screen: Screen, spec: GridSpec, visible: List<Int>, current: Int, home: Int) {
        val bar = screen.navBar
        val barH = spec.navBar - NAV_LINE
        val count = visible.size + 1
        val buttonW = if (bar.layout == "fill") {
            spec.widthDp / count
        } else {
            minOf(
                barH,
                spec.widthDp / count,
            )
        }
        val layoutScale = barH / (Grid.navBar(spec.widthDp) - NAV_LINE)
        val style = NavStyle(
            bar.labels,
            layoutScale,
            bar.active?.let { paint(it) } ?: role(Role.NAV_ON),
        )
        val lineColor = bar.line?.let { paint(it) } ?: role(Role.NAV_LINE)
        val background = bar.background?.let { paint(it) } ?: role(Role.NAV)
        at(
            0f,
            spec.heightDp - spec.navBar,
        ) { scope.Column(Modifier.size(units.px(spec.widthDp), units.px(spec.navBar))) {
            val doc = this@navBar
            Box(
                Modifier.size(
                    doc.units.px(spec.widthDp),
                    doc.units.px(NAV_LINE),
                ).background(lineColor),
            )
            Row(
                Modifier.size(
                    doc.units.px(spec.widthDp),
                    doc.units.px(barH),
                ).background(background),
                horizontal = navArrangement(bar.layout, count),
                vertical = RcVerticalPositioning.Center,
            ) {
                for (i in visible) {
                    val page = screen.pages[i]
                    val icon = page.icon ?: if (i == home) "home" else "view-dashboard"
                    doc.navButton(
                        page.title,
                        icon,
                        buttonW,
                        barH,
                        i == current,
                        ActionRef("_", "page", i.toString()),
                        style,
                    )
                }
                doc.navButton(
                    doc.i18n.t("ui.settings"),
                    "cog",
                    buttonW,
                    barH,
                    false,
                    ActionRef("_", "settings"),
                    style,
                )
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

    /** How navigation buttons look: icon, word or both, [layoutScale] against the default bar, the current page's fill. */
    private class NavStyle(val labels: String, val layoutScale: Float, val on: RcColor)

    private fun DocContext.navButton(
        title: String,
        icon: String,
        wDp: Float,
        hDp: Float,
        on: Boolean,
        ref: ActionRef,
        style: NavStyle,
    ) {
        var modifier = Modifier.size(units.px(wDp), units.px(hDp))
        if (on) modifier = modifier.background(style.on)
        scope.Column(
            tap(modifier, ref),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcColumnVerticalPositioning.Center,
        ) {
            val doc = this@navButton
            val word = title.uppercase(doc.i18n.locale)
            val wordW = Modifier.width(doc.units.px(wDp - STAT_ICON_GAP_DP))
            // Icon and label stay page ink on the teal pill (selected does not flip to KEY_ON_INK).
            val ink = doc.role(Role.INK)
            when (style.labels) {
                "icons" -> doc.icon(icon, minOf(NAV_ICON_ONLY_MAX, hDp * NAV_ICON_ONLY_SHARE), ink)
                "text" -> {
                    val sp = (NAV_WORD_ONLY_SP * style.layoutScale).coerceIn(
                        NAV_WORD_ONLY_MIN_SP,
                        NAV_WORD_ONLY_MAX_SP,
                    )
                    doc.label(
                        word,
                        sp,
                        ink,
                        Weight.MEDIUM,
                        lines = 2,
                        align = RcTextAlign.Center,
                        modifier = wordW,
                    )
                }
                else -> {
                    doc.icon(icon, minOf(NAV_BOTH_ICON_MAX_DP, hDp * NAV_BOTH_ICON_SHARE), ink)
                    Spacer(
                        Modifier.height(
                            doc.units.px(
                                NAV_ICON_LABEL_GAP_DP * style.layoutScale.coerceAtMost(
                                    NAV_GAP_MAX_SCALE,
                                ),
                            ),
                        ),
                    )
                    val sp = (NAV_LABEL_BASE_SP * style.layoutScale).coerceIn(
                        NAV_LABEL_MIN_SP,
                        NAV_LABEL_MAX_SP,
                    )
                    doc.label(word, sp, ink, align = RcTextAlign.Center, modifier = wordW)
                }
            }
        }
    }

    private const val HEADER_SP = 20f
    private const val NAV_LINE = 2f
    private const val NAV_ICON_ONLY_MAX = 48f
    private const val NAV_ICON_ONLY_SHARE = 0.55f
    private const val NAV_WORD_ONLY_SP = 14f
    private const val NAV_WORD_ONLY_MIN_SP = NAV_LABEL_BASE_SP
    private const val NAV_WORD_ONLY_MAX_SP = 24f
    private const val NAV_LABEL_MIN_SP = 9f
    private const val NAV_LABEL_MAX_SP = 16f
    private const val NAV_GAP_MAX_SCALE = 1.5f

    /** The clock, the outdoor temperature and who is home: values every page shows. */
    private fun DocContext.bindSystem(screen: Screen) {
        val tempEntity = screen.topBar.temperatureEntity
        bind(tempEntity) { states ->
            val entityState = tempEntity?.let { states[it] }
            val text = when {
                entityState == null -> ""
                entityState.domain == "weather" -> entityState.number(
                    "temperature",
                )?.let { i18n.number(it, 0) + "°" } ?: ""
                else -> entityState.numericState()?.let { i18n.number(
                    it,
                    0,
                ) + "°" } ?: entityState.state
            }
            listOf(VarValue.string(Vars.TEMP, text))
        }
        for (personId in states.keys.filter { it.startsWith("person.") }) {
            bind(personId) { emptyList() }
        }
        bind(null) { states ->
            val home = states.values.count { it.domain == "person" && it.state == "home" }
            listOf(VarValue.string(Vars.PEOPLE, home.toString()))
        }
    }
}
