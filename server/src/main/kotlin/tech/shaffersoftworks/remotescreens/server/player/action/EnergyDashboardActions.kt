package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository

private val ENERGY_DASH_VERBS = setOf(
    "energy_dash_prev",
    "energy_dash_next",
    "energy_dash_now",
    "energy_dash_period",
)

/** Period bar verbs for a full Energy dashboard page. */
class EnergyDashboardActions(
    private val renderData: RenderDataRepository?,
) {
    fun handle(verb: String, target: TapTarget): TapOutcome {
        val tile = target.tile
        val store = renderData?.energyDashboardUi
        if (tile == null || store == null || verb !in ENERGY_DASH_VERBS) {
            return TapOutcome.Ignored
        }
        when (verb) {
            "energy_dash_prev" -> store.shiftPeriod(tile.id, delta = -1)
            "energy_dash_next" -> store.shiftPeriod(tile.id, delta = 1)
            "energy_dash_now" -> store.goToNow(tile.id)
            "energy_dash_period" -> store.cyclePeriod(tile.id)
        }
        return TapOutcome.Rebuild
    }
}
