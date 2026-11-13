package tech.shaffersoftworks.remotescreens.server.data.energy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.ZoneOffset

class EnergyDashboardBuilderTest {
    @Test
    fun buildSumsGridAndDevicesAndAppliesFossilShare() {
        val config = EnergyDashboardConfig(
            gridEnergyFrom = "sensor.grid_energy",
            gridPrice = 0.2,
            gridName = "Grid",
            homeName = "Demo home",
            devices = listOf(
                EnergyDashboardDevice(name = "Heat pump", statConsumption = "sensor.heat_pump_energy"),
            ),
        )
        val ui = EnergyDashboardUiState()
        val snapshot = EnergyDashboardBuilder.build(
            config = config,
            ui = ui,
            zone = ZoneOffset.UTC,
            gridPoints = listOf(
                EnergyStatPoint(0L, 2.0),
                EnergyStatPoint(1L, 3.0),
            ),
            devicePoints = mapOf(
                "sensor.heat_pump_energy" to listOf(
                    EnergyStatPoint(0L, 1.0),
                    EnergyStatPoint(1L, 1.5),
                ),
            ),
            fossilFraction = 0.6,
            nowMs = 0L,
        )

        assertEquals(5.0, snapshot.electricity.totalKwh, 0.001)
        assertEquals(1.0, snapshot.totals.first().cost ?: -1.0, 0.001)
        assertEquals(40.0, snapshot.lowCarbonPercent, 0.001)
        assertEquals(2.0, snapshot.distribution.lowCarbonKwh, 0.001)
        assertTrue(snapshot.devicesRank.any { it.name == "Heat pump" && it.kwh == 2.5 })
        assertTrue(snapshot.sankey.links.any { it.toId == "untracked" })
    }
}
