@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.cards.map

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.padding
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.PresenceState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.shared.Tile

internal fun DocContext.mapPeople(tile: Tile): List<String> =
    (listOfNotNull(
        tile.entityId,
    ) + tile.options["people"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() })
        .ifEmpty { states.keys.filter(Domain.PERSON::owns) }

internal fun DocContext.mapTile(tile: Tile, box: TileBox, named: Boolean) {
    val w = units.px(box.w).toInt()
    val h = units.px(box.h).toInt()
    val who = mapPeople(tile)

    for (personId in who) {
        structural(personId) { person ->
            "${person?.common?.latitude},${person?.common?.longitude}"
        }
    }

    picture(
        PictureSlot(
            name = "map.${tile.id}",
            source = "map:${mapStyle()}:${who.joinToString(",")}",
            widthPx = w,
            heightPx = h,
            refreshSeconds = 0,
        ),
    )

    rcScope.Box(Modifier.fillMaxSize(), vertical = RcVerticalPositioning.Bottom) {
        val doc = this@mapTile

        Image(
            image = doc.image(name = "map.${tile.id}", width = w, height = h),
            modifier = Modifier.fillMaxSize(),
            contentScale = RcContentScale.Crop,
        )

        if (named) {
            Box(
                Modifier.fillMaxWidth().background(
                    doc.role(Role.CARD_HEADER),
                ).padding(doc.units.px(C.PAD_SM_DP)),
            ) {
                doc.label(
                    text = tile.name ?: doc.i18n.t(
                        "ui.people_home",
                        "count" to who.count { doc.states[it]?.presenceState == PresenceState.HOME },
                    ),
                    sp = Type.CAPTION + 1f,
                    color = doc.role(Role.INK),
                    weight = Weight.SEMI_BOLD,
                )
            }
        }
    }
}
