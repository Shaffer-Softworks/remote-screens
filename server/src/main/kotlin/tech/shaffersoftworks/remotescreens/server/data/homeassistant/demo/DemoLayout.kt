// Demo fixture data: the numbers are made-up entity values and the long lines are one entity each.
@file:Suppress("MagicNumber", "MaxLineLength")

package tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo

import tech.shaffersoftworks.remotescreens.shared.GridSize
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.TapType
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TopBar

/** A screen over [DemoHome] with a tile of every kind, for demo mode and for tests. */
object DemoLayout {
    fun screen(): Screen = Screen(
        id = "demo",
        name = "Living room tablet",
        grid = GridSize(5, 3),
        topBar = TopBar(temperatureEntity = "sensor.outdoor_temperature"),
        pages = listOf(
            Page("home", "Home", listOf(Tile("h1", kind = "home", full = true)), icon = "home"),
            Page("living", "Living room", icon = "sofa", tiles = listOf(
                Tile("t1", "light.living_room", col = 0, row = 0, w = 2),
                Tile("t2", "light.table_lamp", col = 2, row = 0),
                Tile("t3", "switch.coffee_machine", col = 3, row = 0),
                Tile("t6", "weather.home", col = 4, row = 0, h = 2),
                Tile("t4", "climate.living_room", col = 0, row = 1, h = 2),
                Tile("t5", "media_player.living_room", col = 1, row = 1, w = 2, h = 2),
                Tile("t7", "lock.front_door", col = 3, row = 1),
                Tile("t8", "scene.movie_night", col = 3, row = 2),
                Tile("t9", kind = "nav", col = 4, row = 2, options = mapOf("page" to "more")),
            )),
            Page("rooms", "Rooms", icon = "floor-plan", tiles = listOf(
                Tile("r1", "cover.living_room_curtains", col = 0, row = 0, w = 2),
                Tile("r2", "fan.bedroom", col = 2, row = 0, w = 2),
                Tile("r3", "sensor.kitchen_temperature", col = 0, row = 1, w = 2, h = 2, options = mapOf("graph" to "true")),
                Tile("r4", "camera.front_door", col = 2, row = 1, w = 2, h = 2),
                Tile("r5", kind = "map", col = 4, row = 0, h = 3),
            )),
            Page("more", "More", hidden = true, tiles = listOf(
                Tile("m1", kind = "clock", col = 0, row = 0, w = 2),
                Tile("m9", kind = "history", entityId = "sensor.power", col = 0, row = 1, w = 2),
                Tile("m2", kind = "energy", col = 2, row = 0, w = 2, h = 2),
                Tile("m3", "alarm_control_panel.house", col = 4, row = 0),
                Tile("m4", "input_select.washing_program", col = 4, row = 1),
                Tile("m5", "vacuum.robot", col = 0, row = 2),
                Tile("m6", "person.sam", col = 1, row = 2),
                Tile("m7", "input_boolean.guest_mode", col = 2, row = 2),
                Tile("m8", "script.good_night", col = 3, row = 2, tap = TapAction(TapType.ACTION, service = "script.turn_on")),
                Tile("m10", kind = "favourite", entityId = "media_player.living_room", col = 4, row = 2,
                    options = mapOf("content_id" to "playlist:1", "title" to "Sunday Morning")),
            )),
            Page("weather", "Weather", listOf(Tile("w1", "weather.home", full = true)), icon = "weather-partly-cloudy"),
            Page("thermostat", "Thermostat", listOf(Tile("c1", "climate.living_room", full = true)), icon = "thermostat"),
            Page("night", "Night", listOf(Tile("n1", kind = "bedside", full = true)), icon = "weather-night"),
        ),
    )
}
