package tech.shaffersoftworks.remotescreens.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class EnergyDashboardProtocolTest {
    @Test
    fun anEnergyDashboardReadsBackAsSent() {
        val message = ServerMessage.EnergyDashboard(
            slot = Slot.MAIN,
            rev = 3,
            page = 2,
            tileId = "ed1",
            componentId = 0x5F_0000,
            periodLabel = "Nov 12",
            isNow = true,
            electricity = EnergyBarSeries(
                title = "Electricity",
                totalKwh = 10.5,
                labels = listOf("8 AM", "9 AM"),
                values = listOf(1.0, 2.5),
            ),
            totals = listOf(EnergyTotalRow(source = "Grid", energyKwh = 10.5, cost = 2.1)),
            lowCarbonPercent = 42.0,
        )

        assertEquals(message, Protocol.decodeServer(Protocol.encode(message)))
    }

    @Test
    fun energyDashboardCapabilityIsNamed() {
        assertEquals("energy_dashboard", Protocol.CAPABILITY_ENERGY_DASHBOARD)
    }
}
