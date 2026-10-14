package dev.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.DrawTextAnchored
import androidx.compose.remote.core.operations.NamedVariable
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.operations.layout.animation.AnimationSpec
import androidx.compose.remote.core.operations.layout.managers.BoxLayout
import dev.remotescreens.server.BadRequest
import dev.remotescreens.server.DataFetcher
import dev.remotescreens.server.I18n
import dev.remotescreens.server.ha.DemoHome
import dev.remotescreens.server.ha.DemoLayout
import dev.remotescreens.server.validate
import dev.remotescreens.shared.MAX_NODE_DEPTH
import dev.remotescreens.shared.Node
import dev.remotescreens.shared.NodeBinding
import dev.remotescreens.shared.NodeSize
import dev.remotescreens.shared.NodeStyle
import dev.remotescreens.shared.NodeTypes
import dev.remotescreens.shared.Page
import dev.remotescreens.shared.Screen
import dev.remotescreens.shared.TapAction
import dev.remotescreens.shared.TapType
import dev.remotescreens.shared.Tile
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import dev.remotescreens.shared.ActionRef
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FreeFormTest {
    private val ha = DemoHome()
    private val i18n = I18n("en", I18n.load("en"), emptyMap(), true)
    private val tablet = Device(1280, 800, 1.5f)

    private fun input() = RenderInput(ha.states.value, i18n, ha.config.value, RenderData(), false)

    private fun parse(bytes: ByteArray) = CoreDocument().also { it.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes))) }

    private fun screen(root: Node) = Screen("s", "Free", pages = listOf(Page("home", "Home", root = root), Page("two", "Two")))

    private val everyType = Node(
        "root", "column", size = NodeSize("fill", "fill"), style = NodeStyle(padding = 16f), props = mapOf("gap" to "8"),
        children = listOf(
            Node("title", "text", props = mapOf("text" to "Hello", "fontSize" to "28", "weight" to "700", "color" to "accent")),
            Node("temp", "text", bind = NodeBinding("sensor.kitchen_temperature", "value", prefix = "Kitchen ")),
            Node("row", "row", size = NodeSize("fill", "weight:1"), props = mapOf("arrangement" to "spaceBetween", "alignment" to "center"), children = listOf(
                Node("bulb", "icon", bind = NodeBinding("light.table_lamp"), props = mapOf("iconOn" to "lightbulb-on")),
                Node("go", "button", props = mapOf("text" to "Page two", "icon" to "arrow-right"), tap = TapAction(TapType.PAGE, page = "two")),
                Node("sp", "spacer", size = NodeSize("weight:1", "wrap")),
                Node("card", "tile", size = NodeSize("dp:240", "fill"), tile = Tile("t1", entityId = "light.kitchen")),
            )),
            Node("div", "divider"),
            Node("box", "box", size = NodeSize("fill", "dp:80"), style = NodeStyle(background = "card", corner = 12f), props = mapOf("alignment" to "center"), children = listOf(
                Node("level", "slider", size = NodeSize("dp:300", "wrap"), bind = NodeBinding("light.living_room")),
            )),
            Node("cam", "image", size = NodeSize("dp:160", "dp:90"), bind = NodeBinding("camera.front_door")),
            Node("more", "row", size = NodeSize("fill", "wrap"), props = mapOf("gap" to "8"), children = listOf(
                Node("clk", "clock", props = mapOf("show" to "both", "fontSize" to "32")),
                Node("pill", "badge", bind = NodeBinding("light.kitchen")),
                Node("who", "avatar", bind = NodeBinding("person.alex")),
                Node("sw", "switch", bind = NodeBinding("switch.coffee_machine")),
            )),
            Node("bar", "progress", size = NodeSize("fill", "wrap"), bind = NodeBinding("sensor.kitchen_temperature"), props = mapOf("min" to "0", "max" to "40")),
            Node("chart", "graph", size = NodeSize("fill", "dp:100"), bind = NodeBinding("sensor.kitchen_temperature"), props = mapOf("hours" to "12")),
            Node("heat", "stepper", bind = NodeBinding("climate.living_room")),
            Node("modes", "choices", size = NodeSize("fill", "wrap"), bind = NodeBinding("climate.living_room")),
            Node("wash", "choices", size = NodeSize("fill", "wrap"), bind = NodeBinding("input_select.washing_program")),
            Node("chips", "flow", size = NodeSize("fill", "wrap"), props = mapOf("gap" to "8"), style = NodeStyle(border = 2f, borderColor = "accent", corner = 12f), children = listOf(
                Node("ring", "gauge", bind = NodeBinding("light.living_room"), props = mapOf("size" to "120", "caption" to "@name")),
                Node("face", "analog", props = mapOf("seconds" to "true", "numbers" to "all")),
                Node("dial", "gauge", props = mapOf("caption" to "Humidity")),
                Node("lamp", "iconbutton", bind = NodeBinding("light.table_lamp"), props = mapOf("iconOn" to "lightbulb-on")),
                Node("onlyOn", "text", bind = NodeBinding("light.table_lamp"), props = mapOf("showWhen" to "on", "animate" to "up")),
            )),
            Node("list", "column", size = NodeSize("fill", "dp:120"), props = mapOf("scroll" to "true"), children = listOf(
                Node("ticker", "text", size = NodeSize("dp:120", "wrap"), props = mapOf("text" to "A long line that glides along", "marquee" to "true")),
                Node("remote", "keys", size = NodeSize("fill", "wrap"), bind = NodeBinding("media_player.living_room")),
                Node("door", "keys", bind = NodeBinding("lock.front_door")),
                Node("hues", "swatches", size = NodeSize("fill", "wrap"), bind = NodeBinding("light.kitchen")),
                Node("whites", "swatches", bind = NodeBinding("light.kitchen"), props = mapOf("show" to "white")),
                Node("pad", "keypad", bind = NodeBinding("alarm_control_panel.house")),
                Node("arm", "keys", bind = NodeBinding("alarm_control_panel.house")),
            )),
        ),
    )

    @Test
    fun everyComponentTypeRenders() {
        val used = everyType.walk().map { it.type }.toSet()
        assertEquals(NodeTypes.ALL, used, "the test covers every component type")
        val doc = ScreenRenderer.render(screen(everyType), tablet, input())
        val core = parse(doc.bytes)
        assertContains(core.getNamedVariables(NamedVariable.INT_TYPE).toList(), "USER:page")
        assertTrue(doc.ctx.pictures.any { it.name == "img.cam" && it.source == "/api/camera_proxy/camera.front_door" })
    }

    private fun ops(list: List<Operation>): List<Operation> = list.flatMap { op -> listOf(op) + if (op is Container) ops(op.list) else emptyList() }

    @Test
    fun cardsSideBySideThatFillTheirRowsHeightAreAsTallAsTheTallest() {
        fun card(id: String, withIcon: Boolean) = Node(id, "column", size = NodeSize("fill", "fill"), style = NodeStyle(padding = 20f), props = mapOf("gap" to "12"), children = listOfNotNull(
            if (withIcon) Node("$id.head", "row", children = listOf(Node("$id.i", "icon"), Node("$id.t", "text", props = mapOf("fontSize" to "16")))) else Node("$id.t", "text", props = mapOf("fontSize" to "16")),
            Node("$id.sw", "switch"),
        ))
        val row = Node("row", "row", size = NodeSize("fill", "wrap"), props = mapOf("gap" to "12"), children = listOf(card("a", true), card("b", false)))
        // The icon's 32 dp, the gap, the switch's 30 dp and 20 dp of padding top and bottom.
        assertEquals(32f + 12f + 30f + 40f, content(row).h)
        val doc = ScreenRenderer.render(screen(Node("root", "column", children = listOf(row))), tablet, input())
        assertTrue(doc.bytes.isNotEmpty())
    }

    @Test
    fun aComponentAlignsOnItsOwnWithinItsParent() {
        fun boxes(place: String?): List<Pair<Int, Int>> {
            val card = Node("card", "column", size = NodeSize("fill", "wrap"), children = listOf(
                Node("name", "text", props = mapOf("text" to "Ceiling fan")),
                Node("sw", "switch", bind = NodeBinding("light.kitchen"), props = listOfNotNull(place?.let { "place" to it }).toMap()),
            ))
            val field = { name: String -> BoxLayout::class.java.getDeclaredField(name).apply { isAccessible = true } }
            return ops(parse(ScreenRenderer.render(screen(card), tablet, input()).bytes).operations)
                .filterIsInstance<BoxLayout>().map { field("mHorizontalPositioning").getInt(it) to field("mVerticalPositioning").getInt(it) }
        }
        val plain = boxes(null)
        val right = boxes("end")
        assertEquals(plain.size + 1, right.size, "the switch sits in a box of its own")
        assertEquals(1, right.count { it == BoxLayout.END to BoxLayout.TOP } - plain.count { it == BoxLayout.END to BoxLayout.TOP })
    }

    @Test
    fun clockNumbersAndGaugeCaptionsAreDrawnAndAComponentSlidesInAndOut() {
        val dials = Node("root", "column", children = listOf(
            Node("face", "analog", props = mapOf("numbers" to "all")),
            Node("ring", "gauge", bind = NodeBinding("light.living_room"), props = mapOf("caption" to "@name")),
            Node("dial", "gauge", props = mapOf("caption" to "Humidity")),
        ))
        // Twelve numbers round the face, one caption under each of the two gauges.
        assertEquals(14, ops(parse(ScreenRenderer.render(screen(dials), tablet, input()).bytes).operations).count { it is DrawTextAnchored })
        val all = ops(parse(ScreenRenderer.render(screen(everyType), tablet, input()).bytes).operations)
        val slides = all.filterIsInstance<Component>().map { it.mAnimationSpec }.filter { it.enterAnimation == AnimationSpec.ANIMATION.SLIDE_TOP }
        assertEquals(1, slides.size)
        assertEquals(AnimationSpec.ANIMATION.SLIDE_TOP, slides.single().exitAnimation)
    }

    @Test
    fun boundTextIsAVariableThatFollowsTheEntity() {
        val doc = ScreenRenderer.render(screen(everyType), tablet, input())
        val states = ha.states.value
        val value = doc.vars(states)[nodeVar("temp")]?.value
        assertNotNull(value)
        assertTrue(value.toString().startsWith("Kitchen "), "prefixed: $value")
        assertContains(doc.entities, "sensor.kitchen_temperature")
        val core = parse(doc.bytes)
        assertContains(core.getNamedVariables(NamedVariable.STRING_TYPE).toList(), "USER:" + nodeVar("temp"))
    }

    @Test
    fun aBoundIconLightsUpWithItsEntity() {
        val doc = ScreenRenderer.render(screen(everyType), tablet, input())
        val states = ha.states.value
        val on = doc.vars(states + ("light.table_lamp" to states.getValue("light.table_lamp").with("on")))
        val off = doc.vars(states + ("light.table_lamp" to states.getValue("light.table_lamp").with("off")))
        assertEquals(Pastel.YELLOW.argb, on["t.bulb.icon"]?.asColor())
        assertEquals(Role.INK_DIM.argb, off["t.bulb.icon"]?.asColor())
    }

    @Test
    fun componentsAreTappableThroughTheRouter() {
        val page = screen(everyType).pages.first()
        val ids = page.allTiles().map { it.id }.toSet()
        assertTrue(setOf("temp", "bulb", "go", "t1", "level", "cam").all { it in ids }, "tappable: $ids")
        val doc = ScreenRenderer.render(screen(everyType), tablet, input())
        assertTrue("bulb|toggle|" in doc.ctx.predicted, "a bound light icon toggles, and the toggle is foreseen")
    }

    @Test
    fun aSwitchTogglesAndItsKnobFollowsTheEntity() {
        val page = screen(everyType).pages.first()
        assertEquals(TapType.TOGGLE, page.allTiles().first { it.id == "sw" }.tap.type)
        val doc = ScreenRenderer.render(screen(everyType), tablet, input())
        assertTrue("sw|toggle|" in doc.ctx.predicted)
        val states = ha.states.value
        val on = doc.vars(states + ("switch.coffee_machine" to states.getValue("switch.coffee_machine").with("on")))
        assertEquals(V.VISIBLE.toString(), on["e.switch.coffee_machine.on"]?.value)
        assertEquals(V.GONE.toString(), on["e.switch.coffee_machine.off"]?.value)
    }

    @Test
    fun aStepperStepsTheThermostatThroughTheRouter() {
        val doc = ScreenRenderer.render(screen(everyType), tablet, input())
        assertTrue(doc.ctx.predicted.keys.any { it.startsWith("heat|temperature|+") }, "predicted: ${doc.ctx.predicted.keys}")
        assertTrue(doc.ctx.predicted.keys.any { it.startsWith("heat|temperature|-") })
        val tile = screen(everyType).pages.first().allTiles().first { it.id == "heat" }
        assertEquals("climate", tile.effectiveKind())
    }

    @Test
    fun choicesLightTheChosenOne() {
        val doc = ScreenRenderer.render(screen(everyType), tablet, input())
        val states = ha.states.value
        val vars = doc.vars(states)
        val wash = states.getValue("input_select.washing_program")
        val i = wash.list("options").take(4).indexOf(wash.state)
        assertTrue(i >= 0, "the demo's option is among the first four")
        assertEquals(Role.KEY_ON.argb, vars["o.wash.c.$i.bg"]?.asColor())
        val mode = states.getValue("climate.living_room").let { it.list("hvac_modes").take(4).indexOf(it.state) }
        assertEquals(Role.KEY_ON.argb, vars["o.modes.c.$mode.bg"]?.asColor())
    }

    @Test
    fun aProgressBarFillsWithTheValueBetweenItsLimits() {
        val doc = ScreenRenderer.render(screen(everyType), tablet, input())
        val states = ha.states.value
        val at20 = doc.vars(states + ("sensor.kitchen_temperature" to states.getValue("sensor.kitchen_temperature").with("20")))
        assertEquals(0.5f, at20[nodeLevel("bar")]?.value?.toString()?.toFloat()!!, 0.001f)
        val at99 = doc.vars(states + ("sensor.kitchen_temperature" to states.getValue("sensor.kitchen_temperature").with("99")))
        assertEquals(1f, at99[nodeLevel("bar")]?.value?.toString()?.toFloat()!!, 0.001f)
    }

    @Test
    fun aGraphHasItsHistoryFetched() {
        val s = screen(everyType)
        val tile = s.pages.first().allTiles().first { it.id == "chart" }
        assertEquals("history", tile.effectiveKind())
        assertEquals("12", tile.options["hours"])
        val data = runBlocking { DataFetcher(ha).forScreen(s) }
        assertTrue("sensor.kitchen_temperature" in data.history.keys)
    }

    @Test
    fun aGaugeFillsWithTheLevelAndAComponentCanShowOnlyWhileOn() {
        val doc = ScreenRenderer.render(screen(everyType), tablet, input())
        val states = ha.states.value
        val lamp = states.getValue("light.living_room")
        val half = doc.vars(states + ("light.living_room" to lamp.with("on", mapOf("brightness" to kotlinx.serialization.json.JsonPrimitive(128)))))
        assertEquals(0.5f, half[nodeLevel("ring")]?.value?.toString()?.toFloat()!!, 0.01f)
        val off = doc.vars(states + ("light.table_lamp" to states.getValue("light.table_lamp").with("off")))
        assertEquals(V.GONE.toString(), off["e.light.table_lamp.on"]?.value, "the text bound with showWhen=on is hidden")
    }

    @Test
    fun keysKeypadAndSwatchesTapThroughTheRouter() {
        val s = screen(everyType)
        val router = dev.remotescreens.server.ActionRouter(ha) { i18n }
        val memory = dev.remotescreens.server.TapMemory()
        fun tap(ref: ActionRef) = runBlocking { router.handle(ref, s, memory) }
        tap(ActionRef("remote", "next"))
        assertEquals("media_player" to "media_next_track", ha.calls.last().let { it.first to it.second })
        tap(ActionRef("hues", "hue", "120"))
        assertEquals("light" to "turn_on", ha.calls.last().let { it.first to it.second })
        tap(ActionRef("pad", "code_digit", "1"))
        tap(ActionRef("pad", "code_digit", "2"))
        assertIs<dev.remotescreens.server.Outcome.Code>(tap(ActionRef("pad", "code_digit", "3")))
        tap(ActionRef("arm", "arm_away"))
        val (domain, service, data) = ha.calls.last()
        assertEquals("alarm_control_panel" to "alarm_arm_away", domain to service)
        assertEquals("123", (data["code"] as kotlinx.serialization.json.JsonPrimitive).content, "the code typed on the keypad goes with the arm")
        val doc = ScreenRenderer.render(s, tablet, input())
        val declared = parse(doc.bytes).getNamedVariables(NamedVariable.STRING_TYPE).toList()
        assertContains(declared, "USER:t.e:alarm_control_panel.house.code")
        assertContains(declared, "USER:t.door.confirm")
    }

    @Test
    fun everyDemoCardRendersAsAComponent() {
        val demo = DemoLayout.screen()
        val pages = demo.pages.map { p ->
            val cards = p.tiles.map { t -> Node("n.${t.id}", "tile", size = NodeSize("weight:1", "fill"), tile = t) }
            p.copy(tiles = emptyList(), root = Node("root.${p.id}", "row", size = NodeSize("fill", "fill"), children = cards))
        }
        val screen = validate(demo.copy(pages = pages))
        val data = runBlocking { DataFetcher(ha).forScreen(screen) }
        val doc = ScreenRenderer.render(screen, tablet, input().let { RenderInput(it.states, it.i18n, it.ha, data, false) })
        val vars = doc.vars(ha.states.value)
        assertEquals("Ceiling light", vars["e.light.living_room.name"]?.value)
        val declared = parse(doc.bytes).let { core ->
            core.getNamedVariables(NamedVariable.STRING_TYPE).toSet() + core.getNamedVariables(NamedVariable.INT_TYPE) +
                core.getNamedVariables(NamedVariable.FLOAT_TYPE) + core.getNamedVariables(NamedVariable.COLOR_TYPE)
        }
        val unread = vars.keys.filter { "USER:$it" !in declared && !it.startsWith("sys.") }
        assertTrue(unread.isEmpty(), "values sent but never declared: $unread")
    }

    @Test
    fun aValidTreePasses() {
        val s = validate(screen(everyType))
        assertEquals(everyType, s.pages.first().root)
    }

    @Test
    fun invalidTreesAreRejected() {
        fun rejects(root: Node) = assertFailsWith<BadRequest> { validate(screen(root)) }
        rejects(Node("a", "marquee"))
        rejects(Node("a", "text", children = listOf(Node("b", "text"))))
        rejects(Node("a", "column", children = listOf(Node("a", "text"))))
        rejects(Node("a", "tile"))
        rejects(Node("a", "text", size = NodeSize("huge", "wrap")))
        var deep = Node("leaf", "text")
        repeat(MAX_NODE_DEPTH) { deep = Node("c$it", "column", children = listOf(deep)) }
        rejects(deep)
        rejects(Node("a", "column", children = List(500) { Node("n$it", "spacer") }))
    }

    @Test
    fun aTileIdMustDifferFromEveryComponentId() {
        val clash = Node("a", "column", children = listOf(Node("x", "tile", tile = Tile("dup", kind = "clock")), Node("dup", "text", bind = NodeBinding("light.kitchen"))))
        assertFailsWith<BadRequest> { validate(screen(clash)) }
    }
}
