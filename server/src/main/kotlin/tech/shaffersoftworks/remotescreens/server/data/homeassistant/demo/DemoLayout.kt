// Demo fixture data: the numbers are made-up entity values and the long lines are one entity each.
@file:Suppress("MagicNumber", "MaxLineLength")

package tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo

import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.ALARM_ZONES_OPTION
import tech.shaffersoftworks.remotescreens.server.render.panels.cameras.CAMERAS_OPTION
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
            Page(id = "home", title = "Home", tiles = listOf(Tile("h1", kind = "home", full = true)), icon = "home"),
            Page(id = "living", title = "Living room", icon = "sofa", tiles = listOf(
                Tile(id = "t1", entityId = "light.living_room", col = 0, row = 0, w = 2),
                Tile(id = "t2", entityId = "light.table_lamp", col = 2, row = 0),
                Tile(id = "t3", entityId = "switch.coffee_machine", col = 3, row = 0),
                Tile(id = "t6", entityId = "weather.home", col = 4, row = 0, h = 2),
                Tile(id = "t4", entityId = "climate.living_room", col = 0, row = 1, h = 2),
                Tile(id = "t5", entityId = "media_player.living_room", col = 1, row = 1, w = 2, h = 2),
                Tile(id = "t7", entityId = "lock.front_door", col = 3, row = 1),
                Tile(id = "t8", entityId = "scene.movie_night", col = 3, row = 2),
                Tile(id = "t9", kind = "nav", col = 4, row = 2, options = mapOf("page" to "more")),
            )),
            Page(id = "rooms", title = "Rooms", icon = "floor-plan", tiles = listOf(
                Tile(id = "r1", entityId = "cover.living_room_curtains", col = 0, row = 0, w = 2),
                Tile(id = "r2", entityId = "fan.bedroom", col = 2, row = 0, w = 2),
                Tile(
                    id = "r3",
                    entityId = "sensor.kitchen_temperature",
                    col = 0,
                    row = 1,
                    w = 2,
                    h = 2,
                    options = mapOf("graph" to "true"),
                ),
                Tile(id = "r4", entityId = "camera.front_door", col = 2, row = 1, w = 2, h = 2),
                Tile(id = "r5", kind = "map", col = 4, row = 0, h = 3),
            )),
            Page(id = "more", title = "More", hidden = true, tiles = listOf(
                Tile(id = "m1", kind = "clock", col = 0, row = 0, w = 2),
                Tile(id = "m9", kind = "history", entityId = "sensor.power", col = 0, row = 1, w = 2),
                Tile(id = "m2", kind = "energy", col = 2, row = 0, w = 2, h = 2),
                Tile(id = "m3", entityId = "alarm_control_panel.house", col = 4, row = 0),
                Tile(id = "m4", entityId = "input_select.washing_program", col = 4, row = 1),
                Tile(id = "m5", entityId = "vacuum.robot", col = 0, row = 2),
                Tile(id = "m6", entityId = "person.sam", col = 1, row = 2),
                Tile(id = "m7", entityId = "input_boolean.guest_mode", col = 2, row = 2),
                Tile(
                    id = "m8",
                    entityId = "script.good_night",
                    col = 3,
                    row = 2,
                    tap = TapAction(TapType.ACTION, service = "script.turn_on"),
                ),
                Tile(
                    id = "m10",
                    kind = "favourite",
                    entityId = "media_player.living_room",
                    col = 4,
                    row = 2,
                    options = mapOf("content_id" to "playlist:1", "title" to "Sunday Morning"),
                ),
            )),
            Page(id = "extras", title = "Extras", icon = "puzzle", tiles = listOf(
                Tile(id = "x1", entityId = "calendar.family", col = 0, row = 0, w = 2),
                Tile(id = "x2", entityId = "todo.shopping", col = 2, row = 0),
                Tile(id = "x3", entityId = "timer.kitchen", col = 3, row = 0),
                Tile(id = "x4", entityId = "counter.guests", col = 4, row = 0),
                Tile(id = "x5", entityId = "water_heater.tank", col = 0, row = 1, w = 2),
                Tile(id = "x6", entityId = "valve.garden", col = 2, row = 1),
                Tile(id = "x7", entityId = "siren.alarm", col = 3, row = 1),
                Tile(id = "x8", entityId = "lawn_mower.yard", col = 4, row = 1),
                Tile(id = "x9", entityId = "humidifier.bedroom", col = 0, row = 2),
                Tile(id = "x10", entityId = "input_text.note", col = 1, row = 2, w = 2),
                Tile(id = "x11", entityId = "input_datetime.quiet_hours", col = 3, row = 2),
                Tile(id = "x12", entityId = "sun.sun", kind = "generic", col = 4, row = 2),
            )),
            Page(
                id = "compose",
                title = "Compose",
                icon = "view-dashboard-edit",
                root = Node(
                    id = "root",
                    type = "column",
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
                            id = "row",
                            type = "row",
                            size = NodeSize("fill", "wrap"),
                            props = mapOf("gap" to "16", "alignment" to "center"),
                            children = listOf(
                                Node(
                                    id = "heat",
                                    type = "dial",
                                    bind = NodeBinding("climate.living_room"),
                                    props = mapOf("size" to "140"),
                                ),
                                Node(
                                    id = "left",
                                    type = "countdown",
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
                            id = "tabs",
                            type = "tabs",
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
                            id = "now",
                            type = "mediabar",
                            size = NodeSize("fill", "wrap"),
                            bind = NodeBinding("media_player.living_room"),
                        ),
                        Node(
                            id = "timerRow",
                            type = "timerkeys",
                            size = NodeSize("fill", "wrap"),
                            bind = NodeBinding("timer.kitchen"),
                        ),
                        Node(
                            id = "days",
                            type = "forecast",
                            size = NodeSize("fill", "wrap"),
                            bind = NodeBinding("weather.home"),
                            props = mapOf("days" to "5"),
                        ),
                        Node("who", "presence", size = NodeSize("fill", "wrap")),
                        Node(
                            id = "ev",
                            type = "events",
                            size = NodeSize("fill", "wrap"),
                            bind = NodeBinding("calendar.family"),
                        ),
                        Node(
                            id = "todos",
                            type = "todolist",
                            size = NodeSize("fill", "wrap"),
                            bind = NodeBinding("todo.shopping"),
                        ),
                        Node(
                            id = "ledRow",
                            type = "row",
                            size = NodeSize("fill", "wrap"),
                            props = mapOf("gap" to "12", "alignment" to "center"),
                            children = listOf(
                                Node("ledLabel", "text", props = mapOf("text" to "Living room")),
                                Node(
                                    id = "dot",
                                    type = "led",
                                    bind = NodeBinding("light.living_room"),
                                    props = mapOf("size" to "18"),
                                ),
                            ),
                        ),
                        Node(
                            id = "climate",
                            type = "thermostat",
                            size = NodeSize("fill", "dp:420"),
                            bind = NodeBinding("climate.living_room"),
                        ),
                    ),
                ),
            ),
            Page(
                id = "weather",
                title = "Weather",
                tiles = listOf(Tile("w1", "weather.home", full = true)),
                icon = "weather-partly-cloudy",
            ),
            Page(
                id = "thermostat",
                title = "Thermostat",
                tiles = listOf(Tile("c1", "climate.living_room", full = true)),
                icon = "thermostat",
            ),
            Page(
                id = "music",
                title = "Music",
                tiles = listOf(
                    Tile(
                        id = "ma1",
                        entityId = "media_player.living_room",
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
        id = "alarm",
        title = "Alarm",
        tiles = listOf(
            Tile(
                id = "a1",
                entityId = "alarm_control_panel.house",
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

    /** RPI's lights page over every demo area. Added where it is wanted, like [alarmPage]. */
    fun lightsPage(): Page = Page(
        id = "lights",
        title = "Lights",
        tiles = listOf(Tile("li1", kind = "lights", full = true)),
        icon = "lightbulb-group",
    )

    /** RPI's cameras page over the demo cameras. Added where it is wanted, like [alarmPage]. */
    fun camerasPage(): Page = Page(
        id = "cameras",
        title = "Cameras",
        tiles = listOf(
            Tile(
                id = "cm1",
                kind = "cameras",
                full = true,
                options = mapOf(CAMERAS_OPTION to "camera.front_door=Front Door;camera.driveway"),
            ),
        ),
        icon = "cctv",
    )

    /** RPI's intercom page. Added where it is wanted, like [camerasPage]. */
    fun intercomPage(): Page = Page(
        id = "intercom",
        title = "Intercom",
        tiles = listOf(Tile("ic1", kind = "intercom", full = true)),
        icon = "phone",
    )
}
