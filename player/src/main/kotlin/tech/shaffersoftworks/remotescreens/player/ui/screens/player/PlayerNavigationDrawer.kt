package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.RemoteScreenState
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.NavigationDrawer
import tech.shaffersoftworks.remotescreens.shared.NavigationDrawerItem

private const val DRAWER_PADDING_DP = 16
private const val DRAWER_ICON_DP = 24

/** Material 3 owns sliding, scrim, gestures, scrolling and accessibility; page actions stay local. */
@Composable
internal fun PlayerNavigationDrawer(
    modifier: Modifier = Modifier,
    state: RemoteScreenState,
    isEnabled: Boolean = true,
    onAction: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val navigation = state.navigationDrawer
    // Keep the content in the same composition branch while dialogs disable the drawer.
    // Moving it would dispose and recreate the Remote Compose document players.
    if (navigation == null) {
        Box(modifier) { content() }
        return
    }
    val drawerState = rememberDrawerState(if (state.isDrawerOpen) DrawerValue.Open else DrawerValue.Closed)
    val currentAction by rememberUpdatedState(onAction)
    LaunchedEffect(state.isDrawerOpen, isEnabled) {
        if (state.isDrawerOpen && isEnabled) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState) {
        // Skip the initial value: the effect above may still be animating a requested open.
        snapshotFlow { drawerState.currentValue }.drop(1).collect { value ->
            currentAction(ActionRef("_", "set", "drawer|${if (value == DrawerValue.Open) 1 else 0}").encode())
        }
    }
    BackHandler(enabled = isEnabled && (drawerState.isOpen || state.isDrawerOpen)) {
        currentAction(ActionRef("_", "set", "drawer|0").encode())
    }
    val contentDirection = LocalLayoutDirection.current
    val drawerDirection = if (navigation.side == "end") LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides drawerDirection) {
        ModalNavigationDrawer(
            modifier = modifier,
            drawerState = drawerState,
            gesturesEnabled = isEnabled,
            drawerContent = {
                PlayerDrawerSheet(
                    navigation = navigation,
                    page = state.page,
                    contentDirection = contentDirection,
                    onAction = currentAction,
                )
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides contentDirection) { content() }
        }
    }
}

@Composable
private fun PlayerDrawerSheet(
    modifier: Modifier = Modifier,
    navigation: NavigationDrawer,
    page: Int,
    contentDirection: LayoutDirection,
    onAction: (String) -> Unit,
) {
    val ink = navigation.ink?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface
    ModalDrawerSheet(
        modifier = modifier.widthIn(max = navigation.width.dp),
        drawerContainerColor = navigation.background?.let { Color(it) }
            ?: MaterialTheme.colorScheme.surfaceContainerLow,
        drawerContentColor = ink,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = navigation.title,
                        modifier = Modifier.padding(DRAWER_PADDING_DP.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                items(navigation.items) { item ->
                    if (item.page == null) HorizontalDivider(Modifier.padding(DRAWER_PADDING_DP.dp))
                    val image = remember(item.iconPath) { drawerIcon(item.iconPath) }
                    NavigationDrawerItem(
                        modifier = Modifier.padding(horizontal = DRAWER_PADDING_DP.dp),
                        label = { Text(item.title) },
                        selected = item.page == page,
                        icon = image?.let { vector ->
                            {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = null,
                                    modifier = Modifier.size(DRAWER_ICON_DP.dp),
                                )
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = navigation.active?.let { Color(it) }
                                ?: MaterialTheme.colorScheme.secondaryContainer,
                            selectedTextColor = ink,
                            unselectedTextColor = ink,
                            selectedIconColor = ink,
                            unselectedIconColor = ink,
                            unselectedContainerColor = Color.Transparent,
                        ),
                        onClick = {
                            val action = item.page?.let { ActionRef("_", "page", it.toString()) }
                                ?: ActionRef("_", "settings")
                            onAction(action.encode())
                        },
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun PlayerDrawerSheetPreview() {
    RemoteScreensTheme {
        PlayerDrawerSheet(
            navigation = NavigationDrawer(
                title = "Kitchen",
                items = listOf(
                    NavigationDrawerItem("Home", page = 0),
                    NavigationDrawerItem("Lights", page = 1),
                    NavigationDrawerItem("Settings"),
                ),
            ),
            page = 0,
            contentDirection = LayoutDirection.Ltr,
            onAction = {},
        )
    }
}

private fun drawerIcon(path: String): ImageVector? = path.takeIf { it.isNotBlank() }?.let {
    runCatching {
        ImageVector.Builder(
            defaultWidth = DRAWER_ICON_DP.dp,
            defaultHeight = DRAWER_ICON_DP.dp,
            viewportWidth = DRAWER_ICON_DP.toFloat(),
            viewportHeight = DRAWER_ICON_DP.toFloat(),
        ).addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)).build()
    }.getOrNull()
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun PlayerNavigationDrawerPreview() {
    RemoteScreensTheme {
        PlayerNavigationDrawer(
            state = RemoteScreenState(
                localInts = mapOf("drawer" to 1),
                navigationDrawer = NavigationDrawer(
                    title = "Kitchen",
                    items = listOf(
                        NavigationDrawerItem("Home", page = 0),
                        NavigationDrawerItem("Lights", page = 1),
                        NavigationDrawerItem("Settings"),
                    ),
                ),
            ),
            onAction = {},
        ) { Box(Modifier.fillMaxSize()) }
    }
}
