@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.security

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.security.SecuritySection
import tech.shaffersoftworks.remotescreens.server.data.security.SecurityUiState
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.fill
import tech.shaffersoftworks.remotescreens.server.render.keptVerticalScroll
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.LockState

private object Metrics {
    const val BASE_W = 1280f
    const val BASE_H = 650f
    const val LAYOUT_SCALE_MIN = 0.4f
    const val LAYOUT_SCALE_MAX = 1.5f
    const val SMALL_LABEL_MIN_SP = 12f
    const val LIST_W_DP = 350f
    const val LIST_TOP_DP = 28f
    const val ACCENT_BAR_START_DP = 20f
    const val ACCENT_BAR_W_DP = 120f
    const val ACCENT_BAR_H_DP = 10f
    const val TITLE_GAP_DP = 10f
    const val TITLE_START_DP = 20f
    const val TITLE_SP = 15f
    const val SECTION_H_DP = 88f
    const val SECTION_PAD_START_DP = 20f
    const val SECTION_ICON_DP = 28f
    const val SECTION_ICON_GAP_DP = 12f
    const val SECTION_SP = 22f
    const val SECTION_SUB_SP = 14f
    const val SECTION_SUB_GAP_DP = 4f
    const val DETAIL_PAD_DP = 28f
    const val DETAIL_TITLE_SP = 22f
    const val DETAIL_GAP_DP = 16f
    const val ROW_H_DP = 72f
    const val ROW_GAP_DP = 10f
    const val ROW_ICON_DP = 28f
    const val ROW_ICON_GAP_DP = 12f
    const val ROW_TITLE_SP = 18f
    const val ROW_SUB_SP = 14f
    const val KEY_H_DP = 48f
    const val KEY_GAP_DP = 8f
    const val OVERVIEW_LINE_SP = 18f
    const val OVERVIEW_LINE_GAP_DP = 12f
}

private val OPENING_DEVICE_CLASSES = setOf("door", "window", "garage_door", "opening")

private class Layout(val scale: Float) {
    fun dp(value: Float): Float = value * scale
    fun sp(value: Float): Float = maxOf(value * scale, Metrics.SMALL_LABEL_MIN_SP)
    val listW: Float get() = dp(Metrics.LIST_W_DP)
}

/**
 * Full security page for locks and door/window sensors — not the alarm keypad.
 * Left: Overview, Locks, Doors & windows. Right: counts or entity rows with lock/unlock.
 */
fun DocContext.securityPanel(tile: Tile, box: TileBox) {
    val ui = data.securityUi[tile.id] ?: SecurityUiState()
    val locks = states.keys.filter { EntityId(it).domain == Domain.LOCK }.sorted()

    val openings = states.values
        .filter { entity ->
            entity.domain == Domain.BINARY_SENSOR && entity.common.deviceClass in OPENING_DEVICE_CLASSES
        }
        .map { it.entityId }
        .sorted()

    val layout = Layout(
        minOf(box.w / Metrics.BASE_W, box.h / Metrics.BASE_H)
            .coerceIn(Metrics.LAYOUT_SCALE_MIN, Metrics.LAYOUT_SCALE_MAX),
    )

    rcScope.Row(Modifier.fillMaxSize().background(fill(Role.PAGE))) {
        val doc = this@securityPanel

        doc.sectionList(
            tileId = tile.id,
            selected = ui.selectedSection,
            unlockedCount = locks.count { states[it]?.lockState != LockState.LOCKED },
            openCount = openings.count { states[it]?.isOn == true },
            layout = layout,
        )

        doc.detailPane(
            section = ui.selectedSection,
            locks = locks,
            openings = openings,
            layout = layout,
        )
    }
}

private fun DocContext.sectionList(
    tileId: String,
    selected: SecuritySection,
    unlockedCount: Int,
    openCount: Int,
    layout: Layout,
) {
    rcScope.Column(
        Modifier.width(units.px(layout.listW)).fillMaxHeight().background(fill(Role.STRIP))
            .padding(start = 0f, top = units.px(layout.dp(Metrics.LIST_TOP_DP)), end = 0f, bottom = 0f),
    ) {
        val doc = this@sectionList

        Row {
            Spacer(Modifier.width(doc.units.px(layout.dp(Metrics.ACCENT_BAR_START_DP))))

            Box(
                Modifier.size(
                    doc.units.px(layout.dp(Metrics.ACCENT_BAR_W_DP - Metrics.ACCENT_BAR_START_DP)),
                    doc.units.px(layout.dp(Metrics.ACCENT_BAR_H_DP)),
                ).background(doc.fill(Role.ACCENT_DARK)),
            )
        }

        Spacer(Modifier.height(doc.units.px(layout.dp(Metrics.TITLE_GAP_DP))))

        doc.label(
            text = doc.i18n.t("ui.security_title"),
            sp = layout.sp(Metrics.TITLE_SP),
            color = doc.role(Role.INK),
            modifier = Modifier.padding(
                start = doc.units.px(layout.dp(Metrics.TITLE_START_DP)),
                top = 0f,
                end = 0f,
                bottom = 0f,
            ),
        )

        doc.sectionRow(
            tileId = tileId,
            section = SecuritySection.OVERVIEW,
            icon = "shield-home",
            title = doc.i18n.t("ui.security_overview"),
            subtitle = doc.i18n.t(
                key = "ui.security_overview_sub",
                "unlocked" to unlockedCount,
                "open" to openCount,
            ),
            isSelected = selected == SecuritySection.OVERVIEW,
            layout = layout,
        )

        doc.sectionRow(
            tileId = tileId,
            section = SecuritySection.LOCKS,
            icon = "lock",
            title = doc.i18n.t("ui.security_locks"),
            subtitle = doc.i18n.t("ui.security_locks_sub", "count" to unlockedCount),
            isSelected = selected == SecuritySection.LOCKS,
            layout = layout,
        )

        doc.sectionRow(
            tileId = tileId,
            section = SecuritySection.OPENINGS,
            icon = "door-open",
            title = doc.i18n.t("ui.security_openings"),
            subtitle = doc.i18n.t("ui.security_openings_sub", "count" to openCount),
            isSelected = selected == SecuritySection.OPENINGS,
            layout = layout,
        )
    }
}

@Suppress("LongParameterList")
private fun DocContext.sectionRow(
    tileId: String,
    section: SecuritySection,
    icon: String,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    layout: Layout,
) {
    rcScope.Row(
        tap(
            Modifier.fillMaxWidth().height(units.px(layout.dp(Metrics.SECTION_H_DP)))
                .padding(
                    start = units.px(layout.dp(Metrics.SECTION_PAD_START_DP)),
                    top = 0f,
                    end = units.px(layout.dp(Metrics.SECTION_PAD_START_DP)),
                    bottom = 0f,
                ),
            ActionRef(tile = tileId, verb = "security_section", arg = section.key),
        ),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@sectionRow
        val ink = doc.role(if (isSelected) Role.ACCENT else Role.INK)
        doc.icon(name = icon, sizeDp = layout.dp(Metrics.SECTION_ICON_DP), color = ink)
        Spacer(Modifier.width(doc.units.px(layout.dp(Metrics.SECTION_ICON_GAP_DP))))

        Column(Modifier.horizontalWeight(1f)) {
            doc.label(text = title, sp = layout.sp(Metrics.SECTION_SP), color = ink)
            Spacer(Modifier.height(doc.units.px(layout.dp(Metrics.SECTION_SUB_GAP_DP))))
            doc.label(text = subtitle, sp = layout.sp(Metrics.SECTION_SUB_SP), color = doc.role(Role.INK_DIM))
        }
    }
}

private fun DocContext.detailPane(
    section: SecuritySection,
    locks: List<String>,
    openings: List<String>,
    layout: Layout,
) {
    val pad = units.px(layout.dp(Metrics.DETAIL_PAD_DP))

    rcScope.Column(
        Modifier.horizontalWeight(1f).fillMaxHeight()
            .padding(start = pad, top = pad, end = pad, bottom = pad),
    ) {
        val doc = this@detailPane

        val title = when (section) {
            SecuritySection.OVERVIEW -> doc.i18n.t("ui.security_overview")
            SecuritySection.LOCKS -> doc.i18n.t("ui.security_locks")
            SecuritySection.OPENINGS -> doc.i18n.t("ui.security_openings")
        }

        doc.label(text = title, sp = layout.sp(Metrics.DETAIL_TITLE_SP), color = doc.role(Role.INK))
        Spacer(Modifier.height(doc.units.px(layout.dp(Metrics.DETAIL_GAP_DP))))

        Column(Modifier.fillMaxWidth().verticalWeight(1f).keptVerticalScroll("security.$section")) {
            when (section) {
                SecuritySection.OVERVIEW -> doc.overviewBody(locks = locks, openings = openings, layout = layout)
                SecuritySection.LOCKS -> locks.forEachIndexed { index, entityId ->
                    if (index > 0) Spacer(Modifier.height(doc.units.px(layout.dp(Metrics.ROW_GAP_DP))))

                    doc.lockRow(entityId, layout)
                }
                SecuritySection.OPENINGS -> openings.forEachIndexed { index, entityId ->
                    if (index > 0) Spacer(Modifier.height(doc.units.px(layout.dp(Metrics.ROW_GAP_DP))))

                    doc.openingRow(entityId, layout)
                }
            }
        }
    }
}

private fun DocContext.overviewBody(locks: List<String>, openings: List<String>, layout: Layout) {
    val unlocked = locks.count { states[it]?.lockState != LockState.LOCKED }
    val open = openings.count { states[it]?.isOn == true }

    rcScope.Column(Modifier.fillMaxWidth()) {
        val doc = this@overviewBody

        doc.label(
            text = doc.i18n.t("ui.security_locks_sub", "count" to unlocked),
            sp = layout.sp(Metrics.OVERVIEW_LINE_SP),
            color = doc.role(Role.INK),
        )

        Spacer(Modifier.height(doc.units.px(layout.dp(Metrics.OVERVIEW_LINE_GAP_DP))))

        doc.label(
            text = doc.i18n.t("ui.security_openings_sub", "count" to open),
            sp = layout.sp(Metrics.OVERVIEW_LINE_SP),
            color = doc.role(Role.INK),
        )

        Spacer(Modifier.height(doc.units.px(layout.dp(Metrics.OVERVIEW_LINE_GAP_DP))))

        doc.label(
            text = doc.i18n.t("ui.security_overview_hint"),
            sp = layout.sp(Metrics.ROW_SUB_SP),
            color = doc.role(Role.INK_DIM),
        )
    }
}

private fun DocContext.lockRow(entityId: String, layout: Layout) {
    val entityState = states[entityId] ?: return
    val name = entityState.name.ifBlank { entityId }
    val isLocked = entityState.lockState == LockState.LOCKED

    rcScope.Column(Modifier.fillMaxWidth()) {
        val doc = this@lockRow

        Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
            doc.icon(
                name = if (isLocked) "lock" else "lock-open-variant",
                sizeDp = layout.dp(Metrics.ROW_ICON_DP),
                color = doc.role(if (isLocked) Role.INK else Role.WARN),
            )

            Spacer(Modifier.width(doc.units.px(layout.dp(Metrics.ROW_ICON_GAP_DP))))

            Column(Modifier.horizontalWeight(1f)) {
                doc.label(text = name, sp = layout.sp(Metrics.ROW_TITLE_SP), color = doc.role(Role.INK))
                doc.label(text = entityState.state, sp = layout.sp(Metrics.ROW_SUB_SP), color = doc.role(Role.INK_DIM))
            }
        }

        Spacer(Modifier.height(doc.units.px(layout.dp(Metrics.KEY_GAP_DP))))

        Row(Modifier.fillMaxWidth()) {
            doc.key(
                ref = ActionRef("e:$entityId", "lock"),
                text = doc.i18n.t("ui.lock"),
                icon = "lock",
                heightDp = layout.dp(Metrics.KEY_H_DP),
                modifier = Modifier.horizontalWeight(1f),
            )

            Spacer(Modifier.width(doc.units.px(layout.dp(Metrics.KEY_GAP_DP))))

            doc.key(
                ref = ActionRef("e:$entityId", "unlock"),
                text = doc.i18n.t("ui.unlock"),
                icon = "lock-open-variant",
                heightDp = layout.dp(Metrics.KEY_H_DP),
                modifier = Modifier.horizontalWeight(1f),
            )
        }
    }
}

private fun DocContext.openingRow(entityId: String, layout: Layout) {
    val entityState = states[entityId] ?: return
    val name = entityState.name.ifBlank { entityId }
    val isOpen = entityState.isOn

    rcScope.Row(
        Modifier.fillMaxWidth().height(units.px(layout.dp(Metrics.ROW_H_DP))),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@openingRow

        doc.icon(
            name = openingIcon(entityState),
            sizeDp = layout.dp(Metrics.ROW_ICON_DP),
            color = doc.role(if (isOpen) Role.WARN else Role.INK),
        )

        Spacer(Modifier.width(doc.units.px(layout.dp(Metrics.ROW_ICON_GAP_DP))))

        Column(Modifier.horizontalWeight(1f), vertical = RcColumnVerticalPositioning.Center) {
            doc.label(text = name, sp = layout.sp(Metrics.ROW_TITLE_SP), color = doc.role(Role.INK))

            doc.label(
                text = if (isOpen) doc.i18n.t("ui.security_open") else doc.i18n.t("ui.security_closed"),
                sp = layout.sp(Metrics.ROW_SUB_SP),
                color = doc.role(Role.INK_DIM),
            )
        }
    }
}

private fun openingIcon(entityState: EntityState): String = when (entityState.common.deviceClass) {
    "window" -> if (entityState.isOn) "window-open" else "window-closed-variant"
    "garage_door" -> "garage"
    else -> if (entityState.isOn) "door-open" else "door-closed"
}
