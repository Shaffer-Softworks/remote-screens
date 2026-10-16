package tech.shaffersoftworks.remotescreens.server.render

/**
 * Splits constrained markdown into lines the free-form renderer can draw: plain text with optional bold or italic
 * for a whole line (a line that is wrapped in `**…**` or `*…*`). No HTML, links or nested markup.
 */
object MarkdownLines {
    data class Line(val text: String, val bold: Boolean, val italic: Boolean)

    fun parse(source: String): List<Line> =
        source.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { raw ->
            when {
                raw.startsWith("**") && raw.endsWith("**") && raw.length >= 4 ->
                    Line(raw.removeSurrounding("**"), bold = true, italic = false)
                raw.startsWith("*") && raw.endsWith("*") && raw.length >= 2 && !raw.startsWith("**") ->
                    Line(raw.removeSurrounding("*"), bold = false, italic = true)
                else -> Line(raw, bold = false, italic = false)
            }
        }
}
