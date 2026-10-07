package dev.remotescreens.server.ha

import dev.remotescreens.shared.GridSize
import dev.remotescreens.shared.Page
import dev.remotescreens.shared.Screen
import dev.remotescreens.shared.TapAction
import dev.remotescreens.shared.TapType
import dev.remotescreens.shared.Tile
import dev.remotescreens.shared.TopBar

/** A screen over [DemoHome] with a tile of every kind, for demo mode and for tests. */
object DemoLayout {
    fun screen(): Screen = Screen(
        id = "demo",
        name = "Living room tablet",
        grid = GridSize(4, 3),
        topBar = TopBar(temperatureEntity = "sensor.outdoor_temperature"),
        pages = listOf(
            Page("home", "Home", listOf(
                Tile("t1", "light.living_room", w = 2),
                Tile("t2", "light.table_lamp"),
                Tile("t3", "switch.coffee_machine"),
                Tile("t4", "climate.living_room", h = 2),
                Tile("t5", "media_player.living_room", w = 2, h = 2),
                Tile("t6", "weather.home"),
                Tile("t7", "lock.front_door"),
                Tile("t8", "scene.movie_night"),
                Tile("t9", kind = "nav", options = mapOf("page" to "more")),
            )),
            Page("rooms", "Rooms", listOf(
                Tile("r1", "cover.living_room_curtains", w = 2),
                Tile("r2", "fan.bedroom", w = 2),
                Tile("r3", "sensor.kitchen_temperature", w = 2, h = 2, options = mapOf("graph" to "true")),
                Tile("r4", "camera.front_door", w = 2, h = 2),
            )),
            Page("more", "More", hidden = true, tiles = listOf(
                Tile("m1", kind = "clock", w = 2),
                Tile("m2", kind = "energy", w = 2, h = 2),
                Tile("m3", "alarm_control_panel.house"),
                Tile("m4", "input_select.washing_program"),
                Tile("m5", "vacuum.robot"),
                Tile("m6", "person.sam"),
                Tile("m7", "input_boolean.guest_mode"),
                Tile("m8", "script.good_night", tap = TapAction(TapType.ACTION, service = "script.turn_on")),
                Tile("m9", kind = "history", entityId = "sensor.power", w = 2),
                Tile("m10", kind = "favourite", entityId = "media_player.living_room", options = mapOf("content_id" to "playlist:1", "title" to "Sunday Morning")),
                Tile("m11", kind = "map", w = 2, h = 2),
            )),
            Page("night", "Night", listOf(Tile("n1", kind = "bedside", full = true))),
        ),
    )
}
