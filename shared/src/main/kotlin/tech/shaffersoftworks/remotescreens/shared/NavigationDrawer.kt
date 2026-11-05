package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.Serializable

/** Native Android navigation drawer accompanying the main Remote Compose document. */
@Serializable
data class NavigationDrawer(
    val title: String,
    val items: List<NavigationDrawerItem>,
    val side: String = "start",
    val width: Int = 300,
    val background: Int? = null,
    val active: Int? = null,
    val ink: Int? = null,
)

/** A visible page, or Settings when [page] is null. Icons use SVG paths on a 24 dp viewport. */
@Serializable
data class NavigationDrawerItem(
    val title: String,
    val iconPath: String = "",
    val page: Int? = null,
)
