package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import tech.shaffersoftworks.remotescreens.server.data.renderdata.HomeAssistantResponseParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeAssistantProtocolTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/ha/$name")) { "no fixture $name" }.readText()

    private fun element(name: String): JsonElement = HA_JSON.parseToJsonElement(fixture(name))

    private fun <R> result(request: HaRequest<R>, name: String): R {
        val message = assertIs<HaInbound.Result>(HA_JSON.decodeFromString(HaInbound.serializer(), fixture(name)))

        return HA_JSON.decodeFromJsonElement(request.resultSerializer, checkNotNull(message.result))
    }

    @Test
    fun getStatesDropsAnEntityWithoutAnId() {
        val states = result(GetStates, "get_states_result.json").map(EntityStateDto::toEntityState)

        assertEquals(listOf("light.kitchen", "sensor.outdoor"), states.map { it.entityId })
        assertEquals(180, states.first().light.brightness?.toInt())
        assertEquals("2026-11-17T07:00:00.000000+00:00", states.last().lastChanged)
    }

    @Test
    fun getConfigReadsUnitsAndTheIntegration() {
        val config = result(GetConfig, "get_config_result.json").toConfig("remote_screens")

        assertEquals("nl", config.language)
        assertEquals("°F", config.temperatureUnit)
        assertEquals("Cottage", config.locationName)
        assertTrue(config.isIntegrationLoaded)
    }

    @Test
    fun aStateChangedEventCarriesTheNewState() {
        val event = assertIs<HaInbound.Event>(
            HA_JSON.decodeFromString(HaInbound.serializer(), fixture("state_changed_event.json")),
        ).event

        val change = HA_JSON.decodeFromJsonElement(StateChangedData.serializer(), event.data)

        assertEquals("state_changed", event.eventType)
        assertEquals(CoverState.OPENING, change.newState?.toEntityState()?.coverState)
        assertEquals(40, change.newState?.toEntityState()?.cover?.currentPosition?.toInt())
    }

    @Test
    fun authAndErrorMessagesDecode() {
        fun inbound(text: String) = HA_JSON.decodeFromString(HaInbound.serializer(), text)

        assertEquals(
            "2026.11.2",
            assertIs<HaInbound.AuthRequired>(
                inbound("""{"type":"auth_required","ha_version":"2026.11.2"}"""),
            ).haVersion,
        )

        assertEquals(
            "Invalid password",
            assertIs<HaInbound.AuthInvalid>(
                inbound("""{"type":"auth_invalid","message":"Invalid password"}"""),
            ).message,
        )

        val failed = assertIs<HaInbound.Result>(
            inbound(
                """{"id":9,"type":"result","success":false,"error":{"code":"not_found","message":"Not found"}}""",
            ),
        )

        assertEquals(HaError("not_found", "Not found"), failed.error)
    }

    @Test
    fun entitiesTakeTheirDeviceAreaAndHiddenOnesAreMarked() {
        val registry = HA_JSON.decodeFromJsonElement(
            EntityRegistryListForDisplay.resultSerializer,
            element("entity_registry_list_for_display.json"),
        )

        val areas = registry.entityAreaIds(listOf(DeviceEntry("hall_switch", "hallway"), DeviceEntry("loose")))

        assertEquals(4, registry.entities.size)
        assertEquals("kitchen", areas["light.kitchen"])
        assertEquals("hallway", areas["switch.hall"])
        assertEquals("mdi:ceiling-light", registry.entities[1].icon)

        assertEquals(
            listOf("sensor.signal", "light.hidden"),
            registry.entities.filter { it.isHidden }.map { it.entityId },
        )
    }

    @Test
    fun energyPrefsNameTheGridAndDevices() {
        val preferences = HA_JSON.decodeFromJsonElement(EnergyPreferences.serializer(), element("energy_prefs.json"))
        val prefs = HomeAssistantResponseParser.energy(preferences)

        assertEquals(2, preferences.energySources.size)
        assertEquals(0.31, preferences.energySources.single { it.type == EnergySource.GRID }.numberEnergyPrice)
        assertEquals("sensor.solar_power", prefs.solar)
        assertEquals("sensor.grid_power", prefs.grid)
        assertNull(prefs.battery)
        assertEquals(listOf("Heat pump" to "sensor.heat_pump_power"), prefs.devices)
        assertEquals("sensor.heat_pump_energy", preferences.deviceConsumption.last().includedInStat)
    }

    @Test
    fun historyKeepsNumbersAndReadsBinaryStates() {
        val history = HA_JSON.decodeFromJsonElement(
            HistoryDuringPeriod("", emptyList()).resultSerializer,
            element("history_minimal.json"),
        )

        assertEquals(
            listOf(1_763_366_400_500L to 11.5, 1_763_370_000_000L to 12.0),
            HomeAssistantResponseParser.history(history, "sensor.outdoor"),
        )

        assertEquals(
            listOf(1.0, 0.0),
            HomeAssistantResponseParser.history(history, "binary_sensor.door").map { it.second },
        )
    }

    @Test
    fun serviceResponsesSkipTheEntriesTheyCannotUse() {
        val responses = element("service_responses.json").jsonObject
        val forecasts = HomeAssistantResponseParser.forecasts(responses["forecast"], "weather.home")
        val todos = HomeAssistantResponseParser.todos(responses["todo"], "todo.shopping")

        assertEquals(listOf("rainy", "sunny"), forecasts.map { it.condition })
        assertEquals(80, forecasts.first().rain)
        assertEquals(11.5, forecasts.last().high)
        assertEquals(listOf("a" to "Milk", "Eggs" to "Eggs"), todos.map { it.uid to it.summary })
    }

    @Test
    fun statisticsAndFossilAnswersDecode() {
        val statistics = HA_JSON.decodeFromJsonElement(
            StatisticsDuringPeriod("", "", "hour", emptyList(), emptyList(), emptyMap()).resultSerializer,
            HA_JSON.parseToJsonElement(
                """{"sensor.grid_energy":[{"start":1763366400000,"change":1.25},{"start":1763370000000}]}""",
            ),
        )

        val fossil = HA_JSON.decodeFromJsonElement(
            FossilEnergyConsumption("", "", emptyList(), "hour", "").resultSerializer,
            HA_JSON.parseToJsonElement("""{"2026-11-17T08:00:00+00:00":0.4,"2026-11-17T09:00:00+00:00":null}"""),
        )

        assertEquals(listOf(StatisticRow(1_763_366_400_000L, 1.25)), statistics["sensor.grid_energy"])
        assertEquals(mapOf("2026-11-17T08:00:00+00:00" to 0.4, "2026-11-17T09:00:00+00:00" to null), fossil)
    }

    @Test
    fun requestPayloadsUseHomeAssistantNames() {
        assertEquals(
            buildJsonObject {
                put("entity_id", "camera.door")
                put("format", "hls")
            },
            CameraStream("camera.door").payload(),
        )

        assertEquals(
            buildJsonObject {
                put("entity_id", "calendar.family")
                put("uid", "abc")
            },
            CalendarEventDelete("calendar.family", "abc").payload(),
        )

        val history = HistoryDuringPeriod("2026-11-17T00:00:00Z", listOf("sensor.outdoor")).payload()
        assertEquals(JsonPrimitive(false), history["significant_changes_only"])
        assertEquals(JsonArray(listOf(JsonPrimitive("sensor.outdoor"))), history["entity_ids"])
        assertEquals(buildJsonObject { put("event_type", "state_changed") }, SubscribeEvents("state_changed").payload())
    }

    @Test
    fun aServiceCallMovesItsEntityToTheTarget() {
        val request = VacuumSegmentClean.of(EntityId("vacuum.robot"), listOf("16", "kitchen"))
        val data = request.data()

        val call = CallService(
            domain = request.service.domain.value,
            service = request.service.service,
            serviceData = data.withoutTarget().fields,
            target = ServiceTarget(data.entityIds.map { it.value }),
        ).payload()

        assertEquals(ServiceRef.VACUUM_SEND_COMMAND, request.service)

        assertEquals(
            HA_JSON.parseToJsonElement(
                """{"domain":"vacuum","service":"send_command","service_data":{"params":[16,"kitchen"],
                |"command":"app_segment_clean"},"target":{"entity_id":["vacuum.robot"]}}""".trimMargin(),
            ),
            call,
        )
    }
}
