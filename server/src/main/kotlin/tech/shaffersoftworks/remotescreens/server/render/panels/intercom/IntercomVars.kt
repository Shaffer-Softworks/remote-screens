package tech.shaffersoftworks.remotescreens.server.render.panels.intercom

import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.shared.IntercomStation
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.VarValue

/** What the intercom page is showing for one tablet. Call state lives on that tablet's session, not in render data. */
data class IntercomView(
    val supportsCalls: Boolean,
    val status: String,
    val isBusy: Boolean,
    val connectedId: String?,
    val stations: List<IntercomStation>,
    val isMuted: Boolean = false,
) {
    companion object {
        fun from(report: PlayerMessage.IntercomReport?, supportsCalls: Boolean, i18n: I18n): IntercomView {
            if (!supportsCalls) {
                return IntercomView(
                    supportsCalls = false,
                    status = i18n.t("ui.intercom_update"),
                    isBusy = false,
                    connectedId = null,
                    stations = emptyList(),
                )
            }
            return IntercomView(
                supportsCalls = true,
                status = report?.status ?: i18n.t("ui.intercom_connecting"),
                isBusy = report?.busy == true,
                connectedId = report?.connectedId,
                stations = report?.stations.orEmpty(),
                isMuted = report?.muted == true,
            )
        }
    }
}

internal fun Screen.hasIntercomTile(): Boolean =
    pages.any { page -> page.allTiles().any { it.effectiveKind() == "intercom" } }

/** Names of the variables a call updates without rebuilding the page. */
internal object IntercomVars {
    const val STATUS = "ic.status"
    const val MIC_ON = "ic.mic.on"
    const val MIC_OFF = "ic.mic.off"
    const val HANGUP_LIVE = "ic.hang.live"
    const val HANGUP_IDLE = "ic.hang.idle"

    fun rowStatus(index: Int): String = "ic.r$index.status"

    /** Empty when the tablet cannot place a call: the page then has no variables to update. */
    fun values(view: IntercomView, i18n: I18n): List<VarValue> {
        if (!view.supportsCalls) return emptyList()
        val live = view.connectedId != null
        val rows = view.stations.mapIndexed { index, station ->
            VarValue.string(rowStatus(index), rowLabel(view, station, i18n))
        }
        return listOf(
            VarValue.string(STATUS, view.status),
            VarValue.int(MIC_ON, if (view.isMuted) Vars.GONE else Vars.VISIBLE),
            VarValue.int(MIC_OFF, if (view.isMuted) Vars.VISIBLE else Vars.GONE),
            VarValue.int(HANGUP_LIVE, if (live) Vars.VISIBLE else Vars.GONE),
            VarValue.int(HANGUP_IDLE, if (live) Vars.GONE else Vars.VISIBLE),
        ) + rows
    }

    fun rowLabel(view: IntercomView, station: IntercomStation, i18n: I18n): String = when {
        station.clientId == view.connectedId -> i18n.t("ui.intercom_connected")
        station.inCall -> i18n.t("ui.intercom_in_call")
        else -> i18n.t("ui.intercom_online")
    }
}
