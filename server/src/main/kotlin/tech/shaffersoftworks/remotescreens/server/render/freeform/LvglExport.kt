package tech.shaffersoftworks.remotescreens.server.render.freeform

import kotlinx.serialization.json.Json
import tech.shaffersoftworks.remotescreens.server.render.theme.Role

private val PAYLOAD = Json {
    explicitNulls = false
    encodeDefaults = false
}

/** Turns a free-form page into ESPHome LVGL YAML and the payload a panel applies without a reflash. */
object LvglExport {
    fun render(page: LvglPage): LvglDocument {
        val skipped = mutableListOf<String>()

        val root = LvglTree(palette = page.palette, sources = page.sources, skipped = skipped)
            .build(page.root)
            .copy(w = "fill", h = "fill")

        val background = page.palette.panelColor(key = null, fallback = Role.PAGE)

        val yaml = LvglYaml(
            screenId = page.screenId,
            pageId = page.pageId,
            widthPx = page.widthPx,
            heightPx = page.heightPx,
            background = background,
            skipped = skipped,
        ).write(root)

        val payload = PAYLOAD.encodeToString(
            LvglPayload.serializer(),
            LvglPayload(
                v = LvglLimits.PAYLOAD_VERSION,
                w = page.widthPx,
                h = page.heightPx,
                bg = background,
                screen = page.screenId,
                page = page.pageId,
                widgets = listOf(root),
            ),
        )

        return LvglDocument(yaml = yaml, payload = payload, skipped = skipped)
    }
}

@kotlinx.serialization.Serializable
private data class LvglPayload(
    val v: Int,
    val w: Int,
    val h: Int,
    val bg: String,
    val screen: String,
    val page: String,
    val widgets: List<LvglWidget>,
)
