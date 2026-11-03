package tech.shaffersoftworks.remotescreens.server.preview

import java.io.File
import kotlinx.coroutines.runBlocking
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoLayout
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.NodeTypes
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Tile

/** Runs only in the JVM preview source set, never inside the add-on or Android player. */
fun main(args: Array<String>) = runBlocking {
    require(nodeEntities.keys == NodeTypes.ALL) {
        "Update PreviewFixtures.nodeEntities for new or removed free-form components: " +
            (NodeTypes.ALL - nodeEntities.keys) + (nodeEntities.keys - NodeTypes.ALL)
    }
    val output = PreviewOutput(File(args.first()), args.getOrElse(1) { "" })
    val home = DemoHome()
    val demo = DemoLayout.screen()
    val extraPages = listOf(
        DemoLayout.alarmPage(),
        DemoLayout.lightsPage(),
        DemoLayout.camerasPage(),
        DemoLayout.intercomPage(),
        Page("calendar", "Calendar", listOf(Tile("calendar", "calendar.family", full = true))),
    )
    val repository = RenderDataRepository(home)
    val extras = extraPages.map { repository.forScreen(previewScreen(it)) }
    val data = repository.forScreen(demo).copy(
        lightsRooms = extras.flatMap { it.lightsRooms },
        agenda = extras.mapNotNull { it.agenda }.firstOrNull(),
    )
    val environment = PreviewEnvironment(
        originalStates = home.states.value,
        config = home.config.value,
        data = data,
        demo = demo,
        extraPages = extraPages,
    )
    for ((themeName, palette) in listOf("Dark" to Themes.RPI, "Light" to Themes.LIGHT)) {
        val session = PreviewSession(
            output = output,
            themeName = themeName,
            palette = palette,
            environment = environment,
        )
        session.screens()
        session.catalogue()
        session.components()
        session.alerts()
        session.panels()
        session.people()
    }
    output.finish()
}

internal fun validateCoverage(covered: Set<String>) {
    val expected = Catalogue.types.flatMap { (kind, spec) -> spec.sizes.map { "tile:$kind:$it" } }.toSet() +
        NodeTypes.ALL.map { "node:$it" } +
        Catalogue.types.filterValues { it.card != "none" && it.needsEntity }.keys.map { "card:$it" }
    check(covered.containsAll(expected)) { "Missing previews: ${expected - covered}" }
}
