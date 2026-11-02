package tech.shaffersoftworks.remotescreens.server.preview

import tech.shaffersoftworks.remotescreens.shared.GridSize
import tech.shaffersoftworks.remotescreens.shared.NavBar
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeBinding
import tech.shaffersoftworks.remotescreens.shared.NodeSize
import tech.shaffersoftworks.remotescreens.shared.NodeTypes
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TopBar

/** Explicit fixtures force new component types to choose appropriate data instead of silently drawing blank. */
internal val nodeEntities = mapOf(
    "column" to null, "row" to null, "box" to null, "flow" to null, "tabs" to null,
    "text" to null, "icon" to "light.living_room", "button" to "light.living_room",
    "spacer" to null, "divider" to null, "slider" to "light.living_room",
    "image" to "camera.front_door", "tile" to "light.living_room",
    "clock" to null, "badge" to "sensor.kitchen_temperature", "avatar" to "person.sam",
    "progress" to "sensor.kitchen_temperature", "graph" to "sensor.power",
    "switch" to "light.living_room", "stepper" to "counter.guests",
    "choices" to "input_select.washing_program", "gauge" to "sensor.power",
    "analog" to null, "iconbutton" to "light.living_room", "keys" to "cover.living_room_curtains",
    "swatches" to "light.living_room", "keypad" to "alarm_control_panel.house",
    "dial" to "climate.living_room", "countdown" to "timer.kitchen", "qr" to null,
    "markdown" to null, "timerkeys" to "timer.kitchen", "todolist" to "todo.shopping",
    "events" to "calendar.family", "mediabar" to "media_player.living_room",
    "forecast" to "weather.home", "presence" to null, "led" to "light.living_room",
    "thermostat" to "climate.living_room", "weather" to "weather.home", "nextevent" to "calendar.family",
)

private fun nodeProps(type: String): Map<String, String> = when (type) {
    "text" -> mapOf("text" to "Living room · 21.5 °C", "fontSize" to "28")
    "markdown" -> mapOf("text" to "**Welcome home**\nYour rooms are comfortable.\n- Lights on\n- Doors locked")
    "qr" -> mapOf("text" to "https://www.home-assistant.io", "size" to "180")
    "button", "badge" -> mapOf("text" to "Living room", "icon" to "lightbulb")
    "avatar" -> mapOf("text" to "Sam", "size" to "96")
    "gauge" -> mapOf("min" to "0", "max" to "2000", "size" to "180")
    "progress" -> mapOf("min" to "0", "max" to "30")
    "tabs" -> mapOf("labels" to "Living room,Kitchen", "gap" to "12")
    "analog" -> mapOf("size" to "240", "numbers" to "all")
    "image" -> mapOf("fit" to "crop")
    else -> mapOf("gap" to "12", "icon" to "lightbulb", "size" to "160")
}

internal fun sampleNode(type: String): Node {
    val props = nodeProps(type)
    val children = if (type in NodeTypes.CONTAINERS) {
        listOf(
            Node("first", "text", props = mapOf("text" to "Living room", "fontSize" to "28")),
            Node("second", "button", props = mapOf("text" to "Lights", "icon" to "lightbulb")),
        )
    } else {
        emptyList()
    }
    return Node(
        "sample",
        type,
        children = children,
        props = props,
        size = NodeSize("fill", if (type == "spacer" || type == "divider") "dp:24" else "fill"),
        bind = nodeEntities.getValue(type)?.let(::NodeBinding),
        tile = if (type == "tile") Tile("embedded", "light.living_room") else null,
    )
}

internal fun previewScreen(page: Page, chrome: Boolean = false, cols: Int = 1, rows: Int = 1) = Screen(
    "preview",
    page.title,
    grid = GridSize(cols, rows),
    pages = listOf(page),
    topBar = TopBar(show = chrome),
    navBar = NavBar(height = if (chrome) null else 0),
)
