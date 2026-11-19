@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.camera

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.visibility
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.spinner
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.Tile.Companion.OPTION_FALSE
import tech.shaffersoftworks.remotescreens.shared.Tile.Companion.OPTION_FIT
import tech.shaffersoftworks.remotescreens.shared.Tile.Companion.OPTION_NAME
import tech.shaffersoftworks.remotescreens.shared.Tile.Companion.OPTION_STREAM
import tech.shaffersoftworks.remotescreens.shared.Tile.Companion.OPTION_TRUE
import tech.shaffersoftworks.remotescreens.shared.Tile.Companion.OPTION_WHOLE

internal fun DocContext.cameraTile(
    tile: Tile,
    box: TileBox,
    named: Boolean,
) {
    val entityId = tile.entityId ?: return
    val widthPx = units.px(box.w).toInt()
    val heightPx = units.px(box.h).toInt()
    val isCropped = tile.options[OPTION_FIT] != OPTION_WHOLE
    val contentScale = if (isCropped) RcContentScale.Crop else RcContentScale.Fit
    val canStream = tile.options[OPTION_STREAM] == OPTION_TRUE && !hasLiveVideoOnCurrentPage()

    val refreshSeconds = if (canStream) {
        0
    } else {
        tile.options["live"]?.toIntOrNull()
            ?.coerceIn(C.CAMERA_LIVE_MIN_S, C.CAMERA_LIVE_MAX_S)
            ?: C.CAMERA_LIVE_DEFAULT_S
    }

    val pictureName = "cam.$entityId.${tile.id}"

    picture(
        PictureSlot(
            name = pictureName,
            source = "/api/camera_proxy/$entityId",
            widthPx = widthPx,
            heightPx = heightPx,
            refreshSeconds = refreshSeconds,
            crop = isCropped,
        ),
    )

    if (canStream) {
        cameraStreamTile(
            entityId = entityId,
            pictureName = pictureName,
            widthPx = widthPx,
            heightPx = heightPx,
            isCropped = isCropped,
            contentScale = contentScale,
            showName = named && tile.options[OPTION_NAME] != OPTION_FALSE,
        )

        return
    }

    cameraSnapshotTile(
        entityId = entityId,
        pictureName = pictureName,
        widthPx = widthPx,
        heightPx = heightPx,
        contentScale = contentScale,
        showName = named && tile.options[OPTION_NAME] != OPTION_FALSE,
    )
}

private fun DocContext.cameraStreamTile(
    entityId: String,
    pictureName: String,
    widthPx: Int,
    heightPx: Int,
    isCropped: Boolean,
    contentScale: RcContentScale,
    showName: Boolean,
) {
    val video = liveVideo(entityId, crop = isCropped)
    val waiting = int(Vars.entity(entityId, Vars.KEY_CAM_WAITING), Vars.VISIBLE)
    val still = int(Vars.entity(entityId, Vars.KEY_CAM_STILL), Vars.GONE)

    rcScope.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(role(Role.TRACK))
            .componentId(video),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@cameraStreamTile

        Image(
            image = doc.image(
                name = pictureName,
                width = widthPx,
                height = heightPx,
            ),
            modifier = Modifier
                .fillMaxSize()
                .visibility(still),
            contentScale = contentScale,
        )

        doc.spinner(
            sizeDp = CAMERA_SPINNER_DP,
            color = doc.role(Role.ACCENT),
            visible = waiting,
        )

        if (showName) {
            Box(
                Modifier.fillMaxSize(),
                vertical = RcVerticalPositioning.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(doc.role(Role.SCRIM))
                        .padding(doc.units.px(C.CARD_CORNER_DP)),
                ) {
                    doc.label(
                        text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
                        sp = Type.BODY,
                        color = doc.role(Role.INK),
                        weight = Weight.SEMI_BOLD,
                    )
                }
            }
        }
    }
}

private fun DocContext.cameraSnapshotTile(
    entityId: String,
    pictureName: String,
    widthPx: Int,
    heightPx: Int,
    contentScale: RcContentScale,
    showName: Boolean,
) {
    rcScope.Box(
        Modifier.fillMaxSize().background(role(Role.SCRIM)),
        vertical = RcVerticalPositioning.Bottom,
    ) {
        val doc = this@cameraSnapshotTile

        Image(
            image = doc.image(
                name = pictureName,
                width = widthPx,
                height = heightPx,
            ),
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )

        if (showName) {
            Box(
                Modifier
                    .fillMaxWidth().background(
                        doc.role(Role.SCRIM),
                    ).padding(doc.units.px(C.CARD_CORNER_DP)),
            ) {
                doc.label(
                    text = doc.text(Vars.entity(entityId, Vars.KEY_NAME)),
                    sp = Type.BODY,
                    color = doc.role(Role.INK),
                    weight = Weight.SEMI_BOLD,
                )
            }
        }
    }
}
