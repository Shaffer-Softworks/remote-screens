@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.camera

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.visibility
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.spinner

internal const val CAMERA_CARD_REFRESH_S = 4
internal const val CAMERA_SPINNER_DP = 48f

internal fun DocContext.cameraCard(
    entityId: String,
    width: Float,
    height: Float,
) {
    val pw = units.px(width).toInt()
    val ph = units.px(height).toInt()

    picture(
        PictureSlot(
            name = "cam.card.$entityId",
            source = "/api/camera_proxy/$entityId",
            widthPx = pw,
            heightPx = ph,
            refreshSeconds = CAMERA_CARD_REFRESH_S,
            crop = false,
        ),
    )

    val video = liveVideo(entityId, crop = false)
    val waiting = int(Vars.entity(entityId, Vars.KEY_CAM_WAITING), Vars.VISIBLE)
    val still = int(Vars.entity(entityId, Vars.KEY_CAM_STILL), Vars.GONE)

    val frame = Modifier
        .size(units.px(width), units.px(height))
        .clip(rounded(CardChrome.CARD_GAP_DP))
        .background(role(Role.TRACK))
        .componentId(video)

    rcScope.Box(
        modifier = frame,
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@cameraCard

        Image(
            image = doc.image(name = "cam.card.$entityId", width = pw, height = ph),
            modifier = Modifier.fillMaxSize().visibility(still),
            contentScale = RcContentScale.Fit,
        )

        doc.spinner(
            sizeDp = CAMERA_SPINNER_DP,
            color = doc.role(Role.ACCENT),
            visible = waiting,
        )
    }
}


/** True when this page already registered a live stream (one decoder per visible page). */
internal fun DocContext.hasLiveVideoOnCurrentPage(): Boolean = videos.any { it.page == renderingPage }
