@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.cameras

import tech.shaffersoftworks.remotescreens.server.data.cameras.CamerasUiState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spinner
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Tile

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.fillMaxHeight
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import tech.shaffersoftworks.remotescreens.server.render.keptVerticalScroll
import androidx.compose.remote.creation.dsl.verticalWeight
import androidx.compose.remote.creation.dsl.visibility
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.panels.cameras.CamerasMetrics as M

private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0xFFFFFF

/** The page's scale, and its size in dp. */
internal class CamerasLayout(val scale: Float, val pageW: Float, val pageH: Float) {
    fun dp(value: Float): Float = value * scale
    fun sp(value: Float): Float = maxOf(value * scale, M.SMALL_LABEL_MIN_SP)

    val listW: Float get() = dp(M.LIST_W_DP)

    /** The right pane's width and the height under the camera's name, which the video fills. */
    val videoW: Float get() = pageW - listW - 2 * dp(M.DETAIL_PAD_DP)
    val videoH: Float
        get() = pageH - 2 * dp(M.DETAIL_PAD_DP) - sp(M.DETAIL_NAME_SP) * M.LINE_HEIGHT_RATIO - dp(M.DETAIL_GAP_DP)
}

/**
 * RPI's cameras page (`CamerasScreen`): the tile's cameras in a list on the left, and the live stream of the one
 * chosen on the right. Nothing is chosen until a row is tapped.
 */
fun DocContext.camerasPanel(tile: Tile, box: TileBox) {
    val ui = data.camerasUi[tile.id] ?: CamerasUiState()
    val cameras = tile.cameraEntries()
    val selected = cameras.selectedCamera(ui.selectedCamera)
    val layout = CamerasLayout(pageScale(box), box.w, box.h)
    scope.Row(Modifier.fillMaxSize().background(role(Role.PAGE))) {
        val doc = this@camerasPanel
        doc.cameraList(tile.id, cameras, layout)
        doc.selectedCameraPane(selected, layout)
    }
}

private fun pageScale(box: TileBox): Float =
    minOf(box.w / M.BASE_W, box.h / M.BASE_H).coerceIn(M.LAYOUT_SCALE_MIN, M.LAYOUT_SCALE_MAX)

/** The grey list: RPI's teal bar, "CAMERAS", and a row per camera. */
private fun DocContext.cameraList(tileId: String, cameras: List<CameraEntry>, layout: CamerasLayout) {
    scope.Column(
        Modifier.width(units.px(layout.listW)).fillMaxHeight().background(role(Role.STRIP))
            .padding(0f, units.px(layout.dp(M.LIST_TOP_DP)), 0f, 0f),
    ) {
        val doc = this@cameraList
        Row {
            Spacer(Modifier.width(doc.units.px(layout.dp(M.ACCENT_BAR_START_DP))))
            Box(
                Modifier.size(
                    doc.units.px(layout.dp(M.ACCENT_BAR_W_DP - M.ACCENT_BAR_START_DP)),
                    doc.units.px(layout.dp(M.ACCENT_BAR_H_DP)),
                ).background(doc.role(Role.ACCENT_DARK)),
            )
        }
        Spacer(Modifier.height(doc.units.px(layout.dp(M.TITLE_GAP_DP))))
        doc.label(
            doc.i18n.t("ui.cameras_title"),
            layout.sp(M.TITLE_SP),
            doc.role(Role.INK),
            modifier = Modifier.padding(doc.units.px(layout.dp(M.TITLE_START_DP)), 0f, 0f, 0f),
        )
        val pad = doc.units.px(layout.dp(M.LIST_PAD_DP))
        Column(
            Modifier.fillMaxWidth()
                .verticalWeight(1f)
                .keptVerticalScroll("cameras.$tileId")
                .padding(pad, pad, pad, pad),
        ) {
            cameras.forEachIndexed { index, camera ->
                if (index > 0) Spacer(Modifier.height(doc.units.px(layout.dp(M.ITEM_GAP_DP))))
                doc.cameraRow(tileId, camera, layout)
            }
        }
    }
}

/**
 * RPI's `CameraListItemView`: the camera's still cropped to fill the row, a half-transparent teal bar along its
 * bottom, and the name on the bar.
 */
private fun DocContext.cameraRow(tileId: String, camera: CameraEntry, layout: CamerasLayout) {
    val pictureName = "cam.list.${camera.entityId}"
    val imageW = units.px(layout.dp(M.ITEM_IMAGE_W_DP))
    val rowH = units.px(layout.dp(M.ITEM_H_DP))
    picture(
        PictureSlot(
            pictureName,
            "/api/camera_proxy/${camera.entityId}",
            imageW.toInt().coerceAtLeast(1),
            rowH.toInt().coerceAtLeast(1),
            0,
        ),
    )
    val barColor = fixed((M.ITEM_BAR_ALPHA shl ALPHA_SHIFT) or (palette[Role.ACCENT_DARK] and RGB_MASK))
    scope.Box(
        tap(
            Modifier.fillMaxWidth().height(rowH),
            ActionRef(tileId, "cameras_select", camera.entityId),
        ),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Bottom,
    ) {
        val doc = this@cameraRow
        Image(
            doc.image(pictureName, imageW.toInt().coerceAtLeast(1), rowH.toInt().coerceAtLeast(1)),
            Modifier.size(imageW, rowH),
            contentScale = RcContentScale.Crop,
        )
        Box(
            Modifier.fillMaxWidth().height(doc.units.px(layout.dp(M.ITEM_BAR_H_DP))).background(barColor),
            vertical = RcVerticalPositioning.Center,
        ) {
            doc.label(
                camera.displayName(doc.states[camera.entityId]),
                layout.sp(M.ITEM_LABEL_SP),
                doc.role(Role.INK),
                modifier = Modifier.padding(doc.units.px(layout.dp(M.ITEM_LABEL_START_DP)), 0f, 0f, 0f),
            )
        }
    }
}

/** The chosen camera's name over its live video; blank until a camera is chosen, as RPI's right pane is. */
private fun DocContext.selectedCameraPane(selected: CameraEntry?, layout: CamerasLayout) {
    val pad = units.px(layout.dp(M.DETAIL_PAD_DP))
    scope.Column(Modifier.horizontalWeight(1f).fillMaxHeight().padding(pad, pad, pad, pad)) {
        val doc = this@selectedCameraPane
        if (selected == null) return@Column
        doc.label(
            selected.displayName(doc.states[selected.entityId]),
            layout.sp(M.DETAIL_NAME_SP),
            doc.role(Role.INK),
            modifier = Modifier.padding(doc.units.px(layout.dp(M.DETAIL_NAME_START_DP)), 0f, 0f, 0f),
        )
        Spacer(Modifier.height(doc.units.px(layout.dp(M.DETAIL_GAP_DP))))
        doc.liveVideoBox(selected.entityId, layout)
    }
}

/**
 * Where the camera's live video plays: the same spinner then video, falling back to a still, as the camera card.
 * The still is only shown when the stream fails (`cam_still`), and the spinner hides then (`cam_waiting`).
 */
private fun DocContext.liveVideoBox(entityId: String, layout: CamerasLayout) {
    val pictureName = "cam.card.$entityId"
    val pictureW = units.px(layout.videoW).toInt().coerceAtLeast(1)
    val pictureH = units.px(layout.videoH).toInt().coerceAtLeast(1)
    picture(
        PictureSlot(pictureName, "/api/camera_proxy/$entityId", pictureW, pictureH, 0, crop = false),
    )
    val video = liveVideo(entityId, crop = false)
    val waiting = int(Vars.entity(entityId, Vars.KEY_CAM_WAITING), Vars.VISIBLE)
    val still = int(Vars.entity(entityId, Vars.KEY_CAM_STILL), Vars.GONE)
    scope.Box(
        Modifier.fillMaxWidth().verticalWeight(1f).background(role(Role.TRACK)).componentId(video),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@liveVideoBox
        Image(
            doc.image(pictureName, pictureW, pictureH),
            Modifier.fillMaxSize().visibility(still),
            contentScale = RcContentScale.Fit,
        )
        doc.spinner(M.SPINNER_DP * layout.scale, doc.role(Role.ACCENT), waiting)
    }
}
