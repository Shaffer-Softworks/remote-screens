@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.map

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.size
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.cards.CardChrome
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded

private const val MAP_MARKER_DP = 48f

internal fun DocContext.mapCard(entityId: String, width: Float, height: Float) {
    val pw = units.px(width).toInt()
    val ph = units.px(height).toInt()

    val who = if (Domain.PERSON.owns(entityId) || Domain.DEVICE_TRACKER.owns(entityId)) {
        listOf(entityId)
    } else {
        states.keys.filter(Domain.PERSON::owns)
    }

    for (personId in who) {
        structural(personId) { state -> "${state?.common?.latitude},${state?.common?.longitude}" }
        val picturePath = states[personId]?.common?.entityPicture

        if (!picturePath.isNullOrBlank()) {
            val markerPx = units.px(MAP_MARKER_DP).toInt().coerceAtLeast(1)

            picture(
                PictureSlot(
                    name = "${Vars.MAP_PIC_PREFIX}$personId",
                    source = "attr:$personId:entity_picture",
                    widthPx = markerPx,
                    heightPx = markerPx,
                    refreshSeconds = 0,
                ),
            )
        }
    }

    picture(
        PictureSlot(
            name = "map.card",
            source = "map:${mapStyle()}:${who.joinToString(",")}",
            widthPx = pw,
            heightPx = ph,
            refreshSeconds = 0,
        ),
    )

    val mapComponent = liveMap(who)

    val frame = Modifier.size(units.px(width), units.px(height))
        .clip(rounded(CardChrome.CARD_GAP_DP))
        .background(role(Role.TRACK))
        .componentId(mapComponent)

    rcScope.Box(frame) {
        val doc = this@mapCard

        Image(
            image = doc.image(name = "map.card", width = pw, height = ph),
            modifier = Modifier.fillMaxSize(),
            contentScale = RcContentScale.Crop,
        )
    }

    label(
        text = i18n.t("ui.map_credit"),
        sp = Type.CAPTION,
        color = role(Role.INK_SOFT),
    )
}


/** The component id to give the box an interactive map of [entityIds] plays over, with `Modifier.componentId`. */
internal fun DocContext.liveMap(entityIds: List<String>): Int {
    val componentId = LiveMap.FIRST_COMPONENT_ID + maps.size
    maps += LiveMap(entityIds = entityIds, componentId = componentId, dark = palette.dark)

    return componentId
}
