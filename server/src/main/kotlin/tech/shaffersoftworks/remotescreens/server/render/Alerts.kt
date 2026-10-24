@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.gap
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A message sent to screens by the `remote_screens.show_alert` service or the `remote_screens_alert` event. */
@Serializable
data class Alert(
    val title: String,
    val message: String = "",
    val icon: String? = null,
    /** `info`, `warning` or `danger`. */
    val level: String = "info",
    /** A camera shown under the message, such as the doorbell. */
    val camera: String? = null,
    val buttons: List<AlertButton> = emptyList(),
    /** Seconds before it closes by itself; 0 keeps it until tapped. */
    val timeout: Int = 30,
    /** Screen ids or player device ids; empty is every screen. */
    val targets: List<String> = emptyList(),
    /** Sent back in the `remote_screens_alert_action` event so an automation can tell alerts apart. */
    val tag: String? = null,
)

@Serializable
data class AlertButton(
    val title: String,
    /** `domain.service` to call, or none to only fire the event. */
    val service: String? = null,
    val data: JsonObject? = null,
    /** An id for the event; the title when there is none. */
    val action: String? = null,
)

object AlertRenderer {
    private const val SCREEN_MARGIN_DP = 48f
    private const val MAX_PANEL_WIDTH_DP = 560f
    private const val CAMERA_ASPECT = 9f / 16f
    private const val MAX_CAMERA_HEIGHT_RATIO = 0.45f
    private const val PANEL_CORNER_DP = 10f
    private const val PANEL_PADDING_DP = 24f
    private const val BADGE_DP = 64f
    private const val BADGE_ICON_DP = 36f
    private const val TITLE_GAP_DP = 14f
    private const val MESSAGE_GAP_DP = 6f
    private const val MESSAGE_LINES = 6
    private const val CAMERA_CORNER_DP = 16f
    private const val CAMERA_FIT = 3
    private const val BUTTONS_GAP_DP = 20f
    private const val MAX_BUTTONS = 3
    private const val BUTTON_SPACING_DP = 10f
    private const val BUTTON_HEIGHT_DP = 52f

    fun render(alert: Alert, device: Device, input: RenderInput): RenderedDoc {
        val panelW = minOf(device.widthDp - SCREEN_MARGIN_DP, MAX_PANEL_WIDTH_DP)
        val camH = if (alert.camera != null) {
            minOf(panelW * CAMERA_ASPECT, device.heightDp * MAX_CAMERA_HEIGHT_RATIO)
        } else {
            0f
        }
        val (bytes, docContext) = DocContext.build(
            device.widthPx,
            device.heightPx,
            alert.title,
            { input.context(device) },
        ) { docContext ->
            val scrim = docContext.tap(
                Modifier.fillMaxSize().background(docContext.role(Role.SCRIM)),
                ActionRef("alert", "dismiss"),
            )
            Box(
                scrim,
                horizontal = RcHorizontalPositioning.Center,
                vertical = RcVerticalPositioning.Center,
            ) {
                val panel = Modifier.width(docContext.units.px(panelW))
                    .clip(docContext.rounded(PANEL_CORNER_DP))
                    .background(docContext.role(Role.CARD))
                Column(
                    docContext.tap(
                        panel,
                        ActionRef("_", "noop"),
                    ).padding(docContext.units.px(PANEL_PADDING_DP)),
                    horizontal = RcHorizontalPositioning.Center,
                ) {
                    docContext.badge(alert)
                    docContext.gap(TITLE_GAP_DP)
                    docContext.label(
                        alert.title,
                        Type.HEADLINE,
                        docContext.role(Role.INK),
                        Weight.SEMI_BOLD,
                        lines = 2,
                        align = RcTextAlign.Center,
                    )
                    if (alert.message.isNotBlank()) {
                        docContext.gap(MESSAGE_GAP_DP)
                        docContext.label(
                            alert.message,
                            Type.TITLE,
                            docContext.role(Role.INK_SOFT),
                            lines = MESSAGE_LINES,
                            align = RcTextAlign.Center,
                        )
                    }
                    if (alert.camera != null) {
                        docContext.camera(
                            alert.camera,
                            panelW - SCREEN_MARGIN_DP,
                            camH,
                        )
                    }
                    docContext.gap(BUTTONS_GAP_DP)
                    docContext.buttons(alert.buttons.take(MAX_BUTTONS))
                }
            }
        }
        return RenderedDoc(bytes, docContext)
    }

    private fun DocContext.badge(alert: Alert) {
        val tone = when (alert.level) {
            "danger" -> Role.DANGER
            "warning" -> Role.WARN
            else -> Role.ACCENT
        }
        val badge = Modifier.size(
            units.px(BADGE_DP),
            units.px(BADGE_DP),
        ).clip(rounded(BADGE_DP / 2f)).background(role(tone))
        scope.Box(
            badge,
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            val icon = alert.icon ?: if (alert.level == "info") "bell-ring" else "alert"
            this@badge.icon(icon, BADGE_ICON_DP, role(Role.KEY_ON_INK))
        }
    }

    private fun DocContext.camera(camera: String, widthDp: Float, heightDp: Float) {
        val pw = units.px(widthDp).toInt()
        val ph = units.px(heightDp).toInt()
        picture(PictureSlot("alert.cam", "/api/camera_proxy/$camera", pw, ph, CAMERA_FIT))
        val video = liveVideo(camera, crop = true)
        gap(TITLE_GAP_DP)
        scope.Image(
            image("alert.cam", pw, ph),
            Modifier.size(
                units.px(widthDp),
                units.px(heightDp),
            ).clip(rounded(CAMERA_CORNER_DP)).componentId(video),
            contentScale = RcContentScale.Crop,
        )
    }

    private fun DocContext.buttons(buttons: List<AlertButton>) {
        scope.Row(Modifier.fillMaxWidth()) {
            val docContext = this@buttons
            buttons.forEachIndexed { i, b ->
                if (i > 0) Spacer(Modifier.width(docContext.units.px(BUTTON_SPACING_DP)))
                val isPrimary = i == 0
                docContext.key(
                    ActionRef("alert", "button", i.toString()),
                    text = b.title,
                    heightDp = BUTTON_HEIGHT_DP,
                    modifier = Modifier.horizontalWeight(1f),
                    bg = if (isPrimary) docContext.role(Role.KEY_ON) else docContext.role(Role.KEY),
                    ink = if (isPrimary) {
                        docContext.role(
                            Role.KEY_ON_INK,
                        )
                    } else {
                        docContext.role(Role.INK)
                    },
                )
            }
            if (buttons.isNotEmpty()) Spacer(Modifier.width(docContext.units.px(BUTTON_SPACING_DP)))
            docContext.key(
                ActionRef("alert", "dismiss"),
                text = docContext.i18n.t("ui.close"),
                heightDp = BUTTON_HEIGHT_DP,
                modifier = Modifier.horizontalWeight(1f),
            )
        }
    }
}
