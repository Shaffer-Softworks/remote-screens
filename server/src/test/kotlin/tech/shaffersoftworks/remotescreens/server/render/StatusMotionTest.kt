package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.NamedVariable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.statusIcon
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class StatusMotionTest {
    private fun context(entity: EntityState): Pair<CoreDocument, DocContext> {
        val home = DemoHome()
        val input = RenderInput(
            mapOf(entity.entityId to entity),
            I18n("en", I18n.load("en"), emptyMap(), true),
            home.config.value,
            tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData(),
        )
        val (bytes, context) = DocContext.build(200, 100, "Motion", { input.context(Device(200, 100, 1f)) }) { doc ->
            doc.statusIcon(entity.entityId, "fan", 40f, doc.role(Role.INK))
        }
        val core = CoreDocument().also {
            it.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
        }
        return core to context
    }

    @Test
    fun fanMotionChangesWithoutRebuildingAndStopsWhenUnavailable() {
        val entity = EntityState("fan.test", "off")
        val (core, context) = context(entity)
        val moving = Vars.entity(entity.entityId, "icon_moving")
        val resting = Vars.entity(entity.entityId, "icon_resting")
        val names = core.getNamedVariables(NamedVariable.INT_TYPE).toList()
        assertContains(names, "USER:$moving")
        assertContains(names, "USER:$resting")
        for (state in listOf("off", "on", "unavailable", "unknown")) {
            val values = context.bindings.flatMap { it.compute(mapOf(entity.entityId to entity.copy(state = state))) }
                .associateBy { it.name }
            assertEquals(if (state == "on") Vars.VISIBLE else Vars.GONE, values.getValue(moving).asInt())
            assertEquals(if (state == "on") Vars.GONE else Vars.VISIBLE, values.getValue(resting).asInt())
        }
    }

    @Test
    fun thermostatMotionTracksHvacActionRatherThanSelectedMode() {
        val entity = EntityState("climate.test", "heat")
        val (_, context) = context(entity)
        for (action in listOf("idle", "heating", "preheating", "cooling", "off")) {
            val updated = entity.copy(attributes = JsonObject(mapOf("hvac_action" to JsonPrimitive(action))))
            val values = context.bindings.flatMap { it.compute(mapOf(entity.entityId to updated)) }
                .associateBy { it.name }
            val expected = if (action in setOf("heating", "preheating", "cooling")) Vars.VISIBLE else Vars.GONE
            assertEquals(expected, values.getValue(Vars.entity(entity.entityId, "icon_moving")).asInt())
        }
    }

    @Test
    fun alarmOnlyPulsesDuringTransientOrTriggeredStates() {
        val entity = EntityState("alarm_control_panel.test", "disarmed")
        val (_, context) = context(entity)
        for (state in listOf("disarmed", "armed_home", "arming", "pending", "triggered", "unavailable")) {
            val values = context.bindings.flatMap { it.compute(mapOf(entity.entityId to entity.copy(state = state))) }
                .associateBy { it.name }
            val expected = if (state in setOf("arming", "pending", "triggered")) Vars.VISIBLE else Vars.GONE
            assertEquals(expected, values.getValue(Vars.entity(entity.entityId, "icon_moving")).asInt())
        }
    }
}
