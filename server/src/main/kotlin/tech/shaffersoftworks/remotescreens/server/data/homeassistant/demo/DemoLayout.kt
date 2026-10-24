// Demo fixture data: the numbers are made-up entity values and the long lines are one entity each.
@file:Suppress("MagicNumber", "MaxLineLength")

package tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo

import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.ALARM_ZONES_OPTION
import tech.shaffersoftworks.remotescreens.shared.GridSize
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeBinding
import tech.shaffersoftworks.remotescreens.shared.NodeSize
import tech.shaffersoftworks.remotescreens.shared.NodeStyle
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.TapType
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TopBar

/** A screen over [DemoHome] with a tile of every kind, for demo mode and for tests. */
object DemoLayout {
    @Suppress("LongMethod")
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
                Tile(
                    "r3",
                    "sensor.kitchen_temperature",
                    col = 0,
                    row = 1,
                    w = 2,
                    h = 2,
                    options = mapOf("graph" to "true"),
                ),
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
                Tile(
                    "m8",
                    "script.good_night",
                    col = 3,
                    row = 2,
                    tap = TapAction(TapType.ACTION, service = "script.turn_on"),
                ),
                Tile(
                    "m10",
                    kind = "favourite",
                    entityId = "media_player.living_room",
                    col = 4,
                    row = 2,
                    options = mapOf("content_id" to "playlist:1", "title" to "Sunday Morning"),
                ),
            )),
            Page("extras", "Extras", icon = "puzzle", tiles = listOf(
                Tile("x1", "calendar.family", col = 0, row = 0, w = 2),
                Tile("x2", "todo.shopping", col = 2, row = 0),
                Tile("x3", "timer.kitchen", col = 3, row = 0),
                Tile("x4", "counter.guests", col = 4, row = 0),
                Tile("x5", "water_heater.tank", col = 0, row = 1, w = 2),
                Tile("x6", "valve.garden", col = 2, row = 1),
                Tile("x7", "siren.alarm", col = 3, row = 1),
                Tile("x8", "lawn_mower.yard", col = 4, row = 1),
                Tile("x9", "humidifier.bedroom", col = 0, row = 2),
                Tile("x10", "input_text.note", col = 1, row = 2, w = 2),
                Tile("x11", "input_datetime.quiet_hours", col = 3, row = 2),
                Tile("x12", "sun.sun", kind = "generic", col = 4, row = 2),
            )),
            Page(
                "compose",
                "Compose",
                icon = "view-dashboard-edit",
                root = Node(
                    "root",
                    "column",
                    size = NodeSize("fill", "fill"),
                    style = NodeStyle(padding = 16f),
                    props = mapOf("gap" to "12", "scroll" to "true"),
                    children = listOf(
                        Node(
                            "title",
                            "text",
                            props = mapOf(
                                "text" to "Free-form",
                                "fontSize" to "28",
                                "weight" to "700",
                            ),
                        ),
                        Node(
                            "row",
                            "row",
                            size = NodeSize("fill", "wrap"),
                            props = mapOf("gap" to "16", "alignment" to "center"),
                            children = listOf(
                                Node(
                                    "heat",
                                    "dial",
                                    bind = NodeBinding("climate.living_room"),
                                    props = mapOf("size" to "140"),
                                ),
                                Node(
                                    "left",
                                    "countdown",
                                    bind = NodeBinding("timer.kitchen"),
                                    props = mapOf("fontSize" to "36"),
                                ),
                                Node(
                                    "code",
                                    "qr",
                                    props = mapOf(
                                        "text" to "https://www.home-assistant.io",
                                        "size" to "120",
                                    ),
                                ),
                            ),
                        ),
                        Node(
                            "tabs",
                            "tabs",
                            size = NodeSize("fill", "dp:160"),
                            props = mapOf("labels" to "Note,Light", "gap" to "8"),
                            children = listOf(
                                Node(
                                    "md",
                                    "markdown",
                                    props = mapOf(
                                        "text" to "**Welcome**\nDrag components in the editor.",
                                    ),
                                ),
                                Node("sw", "switch", bind = NodeBinding("light.living_room")),
                            ),
                        ),
                        Node(
                            "now",
                            "mediabar",
                            size = NodeSize("fill", "wrap"),
                            bind = NodeBinding("media_player.living_room"),
                        ),
                        Node(
                            "timerRow",
                            "timerkeys",
                            size = NodeSize("fill", "wrap"),
                            bind = NodeBinding("timer.kitchen"),
                        ),
                        Node(
                            "days",
                            "forecast",
                            size = NodeSize("fill", "wrap"),
                            bind = NodeBinding("weather.home"),
                            props = mapOf("days" to "5"),
                        ),
                        Node("who", "presence", size = NodeSize("fill", "wrap")),
                        Node(
                            "ev",
                            "events",
                            size = NodeSize("fill", "wrap"),
                            bind = NodeBinding("calendar.family"),
                        ),
                        Node(
                            "todos",
                            "todolist",
                            size = NodeSize("fill", "wrap"),
                            bind = NodeBinding("todo.shopping"),
                        ),
                        Node(
                            "ledRow",
                            "row",
                            size = NodeSize("fill", "wrap"),
                            props = mapOf("gap" to "12", "alignment" to "center"),
                            children = listOf(
                                Node("ledLabel", "text", props = mapOf("text" to "Living room")),
                                Node(
                                    "dot",
                                    "led",
                                    bind = NodeBinding("light.living_room"),
                                    props = mapOf("size" to "18"),
                                ),
                            ),
                        ),
                        Node(
                            "climate",
                            "thermostat",
                            size = NodeSize("fill", "dp:420"),
                            bind = NodeBinding("climate.living_room"),
                        ),
                    ),
                ),
            ),
            Page(
                "weather",
                "Weather",
                listOf(Tile("w1", "weather.home", full = true)),
                icon = "weather-partly-cloudy",
            ),
            Page(
                "thermostat",
                "Thermostat",
                listOf(Tile("c1", "climate.living_room", full = true)),
                icon = "thermostat",
            ),
            Page(
                "music",
                "Music",
                listOf(
                    Tile(
                        "ma1",
                        "media_player.living_room",
                        kind = "music",
                        full = true,
                        options = mapOf("config_entry_id" to "demo_music_assistant"),
                    ),
                ),
                icon = "music",
            ),
        ),
    )

    /**
     * RPI's alarm page over the house panel and its three Alarmo zones. The demo screen already has
     * [tech.shaffersoftworks.remotescreens.shared.MAX_PAGES] pages, so this one is added where it is wanted.
     */
    fun alarmPage(): Page = Page(
        "alarm",
        "Alarm",
        listOf(
            Tile(
                "a1",
                "alarm_control_panel.house",
                full = true,
                options = mapOf(
                    ALARM_ZONES_OPTION to listOf(
                        "alarm_control_panel.basement=Basement",
                        "alarm_control_panel.upstairs=Upstairs",
                        "alarm_control_panel.garage=Garage",
                    ).joinToString(";"),
                ),
            ),
        ),
        icon = "shield-home",
    )
}
