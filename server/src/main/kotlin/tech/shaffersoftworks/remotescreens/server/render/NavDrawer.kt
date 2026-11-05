@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.theme.Palette
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.server.render.widgets.Icons
import tech.shaffersoftworks.remotescreens.server.render.widgets.at
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Drawer
import tech.shaffersoftworks.remotescreens.shared.Grid
import tech.shaffersoftworks.remotescreens.shared.GridSpec
import tech.shaffersoftworks.remotescreens.shared.NavigationDrawer
import tech.shaffersoftworks.remotescreens.shared.NavigationDrawerItem
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen

private const val FLOATING_MENU_KEY_DP = 48f

private val OPEN_DRAWER = ActionRef("_", "set", "${Vars.DRAWER}|${Vars.VISIBLE}")

/** Button data for the existing bottom navigation bar. */
internal class NavItem(val title: String, val icon: String, val ref: ActionRef, val isCurrent: Boolean)

/** The remote menu button opens the native Material 3 drawer on the Android player. */
internal fun DocContext.menuKey(drawer: Drawer, end: Boolean, sizeDp: Float, gapDp: Float): Boolean {
    if (!drawer.modal || drawer.end != end) return false
    scope.Spacer(Modifier.width(units.px(gapDp)))
    key(ref = OPEN_DRAWER, icon = "menu", heightDp = sizeDp, widthDp = sizeDp, round = true)
    scope.Spacer(Modifier.width(units.px(gapDp)))
    return true
}

/** A menu button for screens without a top bar. */
internal fun DocContext.floatingMenuKey(drawer: Drawer, spec: GridSpec) {
    val x = if (drawer.end) spec.widthDp - spec.padding - FLOATING_MENU_KEY_DP else spec.padding
    at(x, spec.padding) {
        key(
            ref = OPEN_DRAWER,
            icon = "menu",
            heightDp = FLOATING_MENU_KEY_DP,
            widthDp = FLOATING_MENU_KEY_DP,
            round = true,
        )
    }
}

/** Metadata for the Android drawer; no drawer panel or scrim is baked into the remote document. */
internal fun DocContext.navigationDrawer(
    screen: Screen,
    pages: List<Page>,
    home: Int,
): NavigationDrawer? {
    if (!screen.drawer.modal) return null
    return NavigationDrawer(
        title = screen.name,
        items = pages.withIndex().filter { !it.value.hidden }.map { (index, page) ->
            NavigationDrawerItem(
                title = page.title,
                iconPath = Icons.path(page.icon ?: if (index == home) "home" else "view-dashboard"),
                page = index,
            )
        } + NavigationDrawerItem(title = i18n.t("ui.settings"), iconPath = Icons.path("cog")),
        side = screen.drawer.side,
        width = screen.drawer.width ?: Grid.MODAL_DRAWER.toInt(),
        background = drawerColor(screen.drawer.background, palette, Role.NAV),
        active = drawerColor(screen.drawer.active, palette, Role.NAV_ON),
        ink = palette[Role.INK],
    )
}

private fun drawerColor(key: String?, palette: Palette, fallback: Role): Int =
    Pastel.of(key)?.let { palette[it] }
        ?: key?.let { Themes.parse(it) }
        ?: palette[Role.of(key) ?: fallback]
