@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles.person

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Availability
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.PresenceState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.initials
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.VarValue

/** A person as the RPI home strip shows one: a round photo, the name, and HOME or AWAY in teal. */
internal fun DocContext.personTile(tile: Tile, box: TileBox, entityState: EntityState?) {
    val entityId = tile.entityId ?: return
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    bindWhere(Vars.tile(tile.id, "where"), entityId)

    if (box.h < C.PERSON_SHORT_MAX_DP) {
        shortPerson(tile = tile, box = box, entityState = entityState, entityId = entityId, ink = ink)
    } else {
        tallPerson(tile = tile, box = box, entityState = entityState, entityId = entityId, ink = ink)
    }
}

private fun DocContext.shortPerson(
    tile: Tile,
    box: TileBox,
    entityState: EntityState?,
    entityId: String,
    ink: RcColor,
) {
    val photo = minOf(a = C.NAV_ICON_MAX_DP, b = box.h - C.PAD_XXL_DP, c = box.w * C.PERSON_PHOTO_W_SHARE)
        .coerceAtLeast(C.LEVEL_BAR_HEIGHT_DP)

    rcScope.Row(
        modifier = Modifier.fillMaxSize().padding(units.px(C.PAD_MD_DP)),
        horizontal = RcRowHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@shortPerson
        doc.photo(entityState = entityState, entityId = entityId, slot = "pic.$entityId", sizeDp = photo)
        Spacer(Modifier.width(doc.units.px(C.PAD_MD_DP)))
        Column { doc.personCaption(tile = tile, entityId = entityId, ink = ink, align = RcTextAlign.Start) }
    }
}

private fun DocContext.tallPerson(
    tile: Tile,
    box: TileBox,
    entityState: EntityState?,
    entityId: String,
    ink: RcColor,
) {
    val photo = minOf(a = C.PERSON_TALL_MAX_DP, b = box.h * C.NAV_ICON_SHARE, c = box.w * C.BEDSIDE_TIME_H)
        .coerceAtLeast(C.NAV_ICON_NAMED_DP)

    rcScope.Column(
        modifier = Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP)),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        val doc = this@tallPerson
        doc.photo(entityState = entityState, entityId = entityId, slot = "pic.$entityId", sizeDp = photo)
        doc.spacer(C.PAD_SM_DP)
        doc.personCaption(tile = tile, entityId = entityId, ink = ink, align = RcTextAlign.Center)
    }
}

private fun DocContext.personCaption(tile: Tile, entityId: String, ink: RcColor, align: RcTextAlign) {
    label(
        text = text(Vars.entity(entityId, Vars.KEY_NAME)),
        sp = Type.TITLE,
        color = ink,
        weight = Weight.MEDIUM,
        align = align,
    )

    label(
        text = text(Vars.tile(tile.id, "where")),
        sp = Type.BODY,
        color = role(Role.ACCENT_SOFT),
        weight = Weight.SEMI_BOLD,
        align = align,
    )
}

/** A person's photo in a circle, or their initials when Home Assistant has no picture. */
fun DocContext.photo(
    entityState: EntityState?,
    entityId: String,
    slot: String,
    sizeDp: Float,
    ink: RcColor = role(Role.INK),
) {
    val pic = entityState?.common?.entityPicture
    val px = units.px(sizeDp).toInt()

    if (pic != null) {
        picture(PictureSlot(name = slot, source = pic, widthPx = px, heightPx = px, refreshSeconds = 0, round = true))

        rcScope.Box(
            Modifier
                .size(
                    units.px(sizeDp),
                    units.px(sizeDp),
                )
                .clip(rounded(sizeDp / 2f))
                .background(fixed(0xFF000000.toInt()))
                .padding(1f),
        ) {
            Image(
                image = image(name = slot, width = px, height = px),
                modifier = Modifier.fillMaxSize().clip(rounded((sizeDp / 2f - 1f / units.density).coerceAtLeast(0f))),
                contentScale = RcContentScale.Crop,
            )
        }
    } else {
        initials(name = entityState?.name ?: entityId, sizeDp = sizeDp, bg = role(Role.KEY), ink = ink)
    }
}

/** HOME, or AWAY (or the zone's name), in capitals as the RPI panel writes it. */
fun DocContext.bindWhere(varName: String, personId: String) {
    bind(personId) { states ->
        val place = states[personId]?.state ?: Availability.UNKNOWN.wire

        val word = if (PresenceState.of(place) != PresenceState.ZONE) {
            i18n.state(Domain.PERSON.value, place)
        } else {
            place.replace('_', ' ')
        }

        listOf(VarValue.string(varName, word.uppercase(i18n.locale)))
    }
}
