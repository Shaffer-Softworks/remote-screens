package tech.shaffersoftworks.remotescreens.server.preview

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tech.shaffersoftworks.remotescreens.shared.GridSize
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Tile

/** Exercise the photo branch separately from DemoHome's initials-only person fixtures. */
internal fun PreviewSession.people() {
    val id = "person.sam"
    val person = originalStates.getValue(id)
    val photo = person.copy(
        attributes = JsonObject(person.attributes + ("entity_picture" to JsonPrimitive("/preview/person/sam"))),
    )
    val photoInput = input(originalStates + (id to photo))
    val home = demo.pages.first { it.id == "home" }
    page(
        group = "Panels",
        label = "Home · person photo",
        page = home,
        renderInput = photoInput,
        canvas = PageCanvas(chrome = true),
    )
    for ((label, renderInput) in listOf("Photo" to photoInput, "Initials" to input())) {
        for ((width, height) in listOf(1 to 1, 2 to 1, 2 to 2)) {
            val tile = Tile(id = "person", entityId = id, w = width, h = height)
            page(
                group = "People",
                label = "$label · ${width}x$height",
                page = Page("person", "Person", listOf(tile)),
                renderInput = renderInput,
                canvas = PageCanvas(grid = GridSize(GRID_COLS, GRID_ROWS)),
            )
        }
    }
}
