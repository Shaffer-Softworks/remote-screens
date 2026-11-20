package tech.shaffersoftworks.remotescreens.server.render.freeform

/** ESPHome `lvgl:` and `font:` for one page. A box is drawn as a column. */
internal class LvglYaml(
    private val screenId: String,
    private val pageId: String,
    private val widthPx: Int,
    private val heightPx: Int,
    private val background: String,
    private val skipped: List<String>,
) {
    private val yaml = StringBuilder()
    private val usedIds = mutableSetOf<String>()

    fun write(root: LvglWidget): String {
        line(0, "# Remote Screens free-form page, for an ESPHome LVGL display.")
        line(0, "# Paste this into the device config and compile, or Save & send the payload")
        line(0, "# to a remote_screens_panel component. A box is a column on the panel.")
        line(0, "# Canvas ${widthPx}x$heightPx. Match it with the display resolution.")
        line(0, "")
        fonts(root)
        page(root)
        skipped.forEach { line(0, "# $it") }

        return yaml.toString().trimEnd() + "\n"
    }

    private fun kindOf(kind: String): String = when (kind) {
        "button" -> "button"
        "switch" -> "switch"
        "slider" -> "slider"
        "bar" -> "bar"
        "arc" -> "arc"
        "qr" -> "qrcode"
        "text", "icon", "image" -> "label"
        else -> "obj"
    }

    private fun sizeOf(token: String?): String = when (token) {
        null, "wrap" -> "SIZE_CONTENT"
        "fill" -> "100%"
        else -> token
    }

    private fun quote(text: String): String {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

        return "\"$escaped\""
    }

    private fun fonts(root: LvglWidget) {
        val sizes = linkedSetOf<Int>()
        collectSizes(root, sizes)

        if (sizes.isEmpty()) return

        line(0, "font:")

        sizes.sorted().forEach { size ->
            val item = LvglLimits.YAML_INDENT
            val field = item + LvglLimits.YAML_INDENT
            line(item, "- file: \"gfonts://Roboto\"")
            line(field, "id: rs_font_$size")
            line(field, "size: $size")
            line(field, "bpp: 4")
        }

        line(0, "")
    }

    private fun collectSizes(widget: LvglWidget, sizes: MutableSet<Int>) {
        widget.size?.let { sizes += it }
        widget.c?.forEach { collectSizes(widget = it, sizes = sizes) }
    }

    private fun page(root: LvglWidget) {
        val page = LvglLimits.YAML_INDENT
        val body = page + LvglLimits.YAML_INDENT
        val field = body + LvglLimits.YAML_INDENT
        val widgets = field + LvglLimits.YAML_INDENT
        line(0, "lvgl:")
        line(page, "pages:")
        line(body, "- id: rs_page")
        line(field, "bg_color: 0x$background")
        line(field, "widgets:")
        widget(widget = root.copy(w = "fill", h = "fill"), indent = widgets)
    }

    private fun widget(widget: LvglWidget, indent: Int) {
        line(indent, "- ${kindOf(widget.k)}:")
        val body = indent + LvglLimits.YAML_INDENT
        line(body, "id: ${yamlId(widget.id)}")
        dimensions(widget, body)
        chrome(widget, body)

        when (widget.k) {
            "column", "row", "box", "flow" -> layout(widget, body)
            "tabs" -> tabPages(widget, body)
            "button" -> button(widget, body)
            else -> leaf(widget, body)
        }

        if (widget.tap == true) click(widget, body)
    }

    private fun layout(widget: LvglWidget, body: Int) {
        val flow = when (widget.k) {
            "row" -> "row"
            "flow" -> "row_wrap"
            else -> "column"
        }

        val field = body + LvglLimits.YAML_INDENT

        if (widget.bg == null) {
            line(body, "bg_opa: transp")
        } else {
            line(body, "bg_color: 0x${widget.bg}")
        }

        line(body, "layout:")
        line(field, "type: flex")
        line(field, "flex_flow: $flow")
        widget.align?.let { line(field, "flex_align_main: $it") }
        widget.gap?.let { line(field, "pad_row: $it") }
        widget.gap?.let { line(field, "pad_column: $it") }
        children(widget, body)
    }

    private fun children(widget: LvglWidget, body: Int) {
        val nested = widget.c.orEmpty()

        if (nested.isEmpty()) return

        line(body, "widgets:")
        nested.forEach { widget(widget = it, indent = body + LvglLimits.YAML_INDENT) }
    }

    private fun tabPages(widget: LvglWidget, body: Int) {
        line(body, "tabs:")
        val labels = widget.tabs.orEmpty()
        val tab = body + LvglLimits.YAML_INDENT
        val field = tab + LvglLimits.YAML_INDENT
        val nested = field + LvglLimits.YAML_INDENT

        widget.c.orEmpty().forEachIndexed { index, child ->
            line(tab, "- name: ${quote(labels.getOrElse(index) { "Tab" })}")
            line(field, "widgets:")
            widget(widget = child, indent = nested)
        }
    }

    private fun button(widget: LvglWidget, body: Int) {
        widget.bg?.let { line(body, "bg_color: 0x$it") }
        line(body, "widgets:")
        val item = body + LvglLimits.YAML_INDENT
        line(item, "- label:")
        val label = item + LvglLimits.YAML_INDENT
        line(label, "text: ${quote(widget.text.orEmpty())}")
        widget.color?.let { line(label, "text_color: 0x$it") }
        widget.size?.let { line(label, "text_font: rs_font_$it") }
    }

    private fun leaf(widget: LvglWidget, body: Int) {
        when (widget.k) {
            "text", "icon", "image" -> label(widget, body)
            "switch" -> widget.value?.let { line(body, "checked: ${it == 1}") }
            "slider", "bar", "arc" -> range(widget, body)
            "qr" -> {
                line(body, "text: ${quote(widget.text.orEmpty())}")
                widget.size?.let { line(body, "size: $it") }
            }
            "divider" -> widget.bg?.let { line(body, "bg_color: 0x$it") }
            else -> Unit
        }
    }

    private fun label(widget: LvglWidget, body: Int) {
        if (widget.k == "icon") line(body, "# mdi:${widget.text.orEmpty()}")

        if (widget.k == "image") line(body, "# A JPEG needs an ESPHome image id. This shows its caption.")

        line(body, "text: ${quote(widget.text.orEmpty())}")
        widget.color?.let { line(body, "text_color: 0x$it") }
        widget.size?.let { line(body, "text_font: rs_font_$it") }
        widget.align?.let { line(body, "text_align: $it") }
        widget.bg?.let { line(body, "bg_color: 0x$it") }
    }

    private fun range(widget: LvglWidget, body: Int) {
        line(body, "min_value: ${widget.min ?: 0}")
        line(body, "max_value: ${widget.max ?: LvglLimits.DEFAULT_RANGE}")
        line(body, "value: ${widget.value ?: 0}")

        if (widget.k == "arc") line(body, "adjustable: false")
    }

    private fun click(widget: LvglWidget, body: Int) {
        val then = body + LvglLimits.YAML_INDENT
        val call = then + LvglLimits.YAML_INDENT
        val data = call + LvglLimits.YAML_INDENT
        val field = data + LvglLimits.YAML_INDENT
        line(body, "on_short_click:")
        line(then, "then:")
        line(call, "- homeassistant.service:")
        line(data, "service: remote_screens.panel_tap")
        line(data, "data:")
        line(field, "screen_id: ${quote(screenId)}")
        line(field, "page_id: ${quote(pageId)}")
        line(field, "node_id: ${quote(widget.id)}")
    }

    private fun dimensions(widget: LvglWidget, body: Int) {
        line(body, "width: ${sizeOf(widget.w)}")
        line(body, "height: ${sizeOf(widget.h)}")
        widget.grow?.let { line(body, "flex_grow: $it") }
    }

    private fun chrome(widget: LvglWidget, body: Int) {
        widget.pad?.let { line(body, "pad_all: $it") }
        widget.radius?.let { line(body, "radius: $it") }
        widget.border?.let { line(body, "border_width: $it") }
        widget.edge?.let { line(body, "border_color: 0x$it") }

        if (widget.scroll == true) line(body, "scrollbar_mode: auto")
    }

    private fun yamlId(id: String): String {
        val cleaned = buildString {
            id.forEach { char -> append(if (char.isLetterOrDigit()) char else '_') }
        }.trim('_').ifEmpty { "node" }

        var candidate = "n_$cleaned"
        var suffix = 2

        while (!usedIds.add(candidate)) {
            candidate = "n_${cleaned}_$suffix"
            suffix += 1
        }

        return candidate
    }

    private fun line(indent: Int, text: String) {
        repeat(indent) { yaml.append(' ') }
        yaml.append(text).append('\n')
    }
}
