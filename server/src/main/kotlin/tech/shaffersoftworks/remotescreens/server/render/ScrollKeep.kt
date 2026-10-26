@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.horizontalScroll
import androidx.compose.remote.creation.dsl.verticalScroll

/**
 * Scroll lists keep their place when a new document replaces the one on screen.
 * Each scroller gets a stable id, above the video, map, and search ranges, so the player can
 * put the old offset back on the same list.
 */
internal const val SCROLL_COMPONENT_BASE = 0x60_0000

private const val SCROLL_COMPONENT_MASK = 0xFFFF

internal fun scrollComponentId(name: String): Int =
    SCROLL_COMPONENT_BASE + (name.hashCode() and SCROLL_COMPONENT_MASK)

internal fun Modifier.keptVerticalScroll(name: String): Modifier =
    componentId(scrollComponentId(name)).verticalScroll()

internal fun Modifier.keptHorizontalScroll(name: String): Modifier =
    componentId(scrollComponentId(name)).horizontalScroll()
