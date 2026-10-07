@file:Suppress("RestrictedApi")

package dev.remotescreens.server.render

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import dev.remotescreens.shared.ActionRef
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
    fun render(alert: Alert, device: Device, input: RenderInput): RenderedDoc {
        val panelW = minOf(device.widthDp - 48f, 560f)
        val camH = if (alert.camera != null) minOf(panelW * 9f / 16f, device.heightDp * 0.45f) else 0f
        val (bytes, ctx) = DocContext.build(device.widthPx, device.heightPx, alert.title, { input.context(device) }) { ctx ->
            val tone = when (alert.level) {
                "danger" -> Role.DANGER
                "warning" -> Role.WARN
                else -> Role.ACCENT
            }
            Box(ctx.tap(Modifier.fillMaxSize().background(ctx.role(Role.SCRIM)), ActionRef("alert", "dismiss")),
                horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                Column(ctx.tap(Modifier.width(ctx.u.px(panelW)).clip(ctx.rounded(28f)).background(ctx.role(Role.CARD)), ActionRef("_", "noop"))
                    .padding(ctx.u.px(24f)), horizontal = RcHorizontalPositioning.Center) {
                    Box(Modifier.size(ctx.u.px(64f), ctx.u.px(64f)).clip(ctx.rounded((64f) / 2f)).background(ctx.role(tone)),
                        horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
                        ctx.icon(alert.icon ?: if (alert.level == "info") "bell-ring" else "alert", 36f, ctx.role(Role.KEY_ON_INK))
                    }
                    ctx.gap(14f)
                    ctx.label(alert.title, Type.HEADLINE, ctx.role(Role.INK), 600f, lines = 2, align = RcTextAlign.Center)
                    if (alert.message.isNotBlank()) {
                        ctx.gap(6f)
                        ctx.label(alert.message, Type.TITLE, ctx.role(Role.INK_SOFT), lines = 6, align = RcTextAlign.Center)
                    }
                    if (alert.camera != null) {
                        val w = panelW - 48f
                        val pw = ctx.u.px(w).toInt()
                        val ph = ctx.u.px(camH).toInt()
                        ctx.picture(PictureSlot("alert.cam", "/api/camera_proxy/${alert.camera}", pw, ph, 3))
                        ctx.gap(14f)
                        Image(ctx.image("alert.cam", pw, ph), Modifier.size(ctx.u.px(w), ctx.u.px(camH)).clip(ctx.rounded(16f)), contentScale = RcContentScale.Crop)
                    }
                    ctx.gap(20f)
                    val buttons = alert.buttons.take(3)
                    Row(Modifier.fillMaxWidth()) {
                        buttons.forEachIndexed { i, b ->
                            if (i > 0) Spacer(Modifier.width(ctx.u.px(10f)))
                            ctx.key(ActionRef("alert", "button", i.toString()), text = b.title, heightDp = 52f, modifier = Modifier.horizontalWeight(1f),
                                bg = if (i == 0) ctx.role(Role.KEY_ON) else ctx.role(Role.KEY), ink = if (i == 0) ctx.role(Role.KEY_ON_INK) else ctx.role(Role.INK))
                        }
                        if (buttons.isNotEmpty()) Spacer(Modifier.width(ctx.u.px(10f)))
                        ctx.key(ActionRef("alert", "dismiss"), text = ctx.i18n.t("ui.close"), heightDp = 52f, modifier = Modifier.horizontalWeight(1f))
                    }
                }
            }
        }
        return RenderedDoc(bytes, ctx)
    }
}
