package tech.shaffersoftworks.remotescreens.server.preview

import tech.shaffersoftworks.remotescreens.server.data.alarm.AlarmNotice
import tech.shaffersoftworks.remotescreens.server.data.alarm.AlarmUiState
import tech.shaffersoftworks.remotescreens.server.data.cameras.CamerasUiState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoLayout
import tech.shaffersoftworks.remotescreens.server.data.lights.LightDetailMode
import tech.shaffersoftworks.remotescreens.server.data.lights.LightsUiState
import tech.shaffersoftworks.remotescreens.server.data.music.MusicUiState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaSnapshot
import tech.shaffersoftworks.remotescreens.server.render.panels.intercom.IntercomView
import tech.shaffersoftworks.remotescreens.shared.IntercomStation

internal fun PreviewSession.panels() {
    lightRooms()
    alarmStates()
    intercomStates()
    lightDialogs()
    noticesAndCamera()
    musicStates()
    calendarStates()
}

private fun PreviewSession.lightRooms() {
    for ((areaId, name) in data.lightsRooms) {
        val renderData = data.copy(lightsUi = mapOf("li1" to LightsUiState(selectedAreaId = areaId)))
        page(
            group = "Panels",
            label = "Lights · $name",
            page = DemoLayout.lightsPage(),
            renderInput = input(renderData = renderData),
        )
    }
}

private fun PreviewSession.alarmStates() {
    for (status in listOf("armed_home", "armed_away", "triggered", "pending")) {
        val id = "alarm_control_panel.house"
        val states = originalStates + (id to originalStates.getValue(id).copy(state = status))
        page(group = "Panels", label = "Alarm · $status", page = DemoLayout.alarmPage(), renderInput = input(states))
    }
}

private fun PreviewSession.intercomStates() {
    for (muted in listOf(false, true)) {
        val view = IntercomView(
            supportsCalls = true,
            status = "Connected to Kitchen",
            isBusy = true,
            connectedId = "kitchen",
            stations = listOf(IntercomStation("kitchen", "Kitchen", true), IntercomStation("bedroom", "Bedroom")),
            isMuted = muted,
        )
        page(
            group = "Panels",
            label = "Intercom · connected · muted=$muted",
            page = DemoLayout.intercomPage(),
            renderInput = input(intercom = view),
        )
    }
}

private fun PreviewSession.lightDialogs() {
    for (mode in LightDetailMode.entries) {
        val detail = data.copy(
            lightsUi = mapOf("li1" to LightsUiState(detailEntityId = "light.living_room", detailMode = mode)),
        )
        page(
            group = "Dialogs",
            label = "Light · ${mode.key}",
            page = DemoLayout.lightsPage(),
            renderInput = input(renderData = detail),
        )
    }
    val effects = data.copy(
        lightsUi = mapOf("li1" to LightsUiState(detailEntityId = "light.living_room", isEffectMenuOpen = true)),
    )
    page(
        group = "Dialogs",
        label = "Light · effects",
        page = DemoLayout.lightsPage(),
        renderInput = input(renderData = effects),
    )
}

private fun PreviewSession.noticesAndCamera() {
    val notice = AlarmNotice("Unable to arm", "Close the kitchen window.")
    val alarm = data.copy(alarmUi = mapOf("a1" to AlarmUiState(notice = notice)))
    page(
        group = "Dialogs",
        label = "Alarm notice",
        page = DemoLayout.alarmPage(),
        renderInput = input(renderData = alarm),
    )
    val cameras = data.copy(camerasUi = mapOf("cm1" to CamerasUiState("camera.driveway")))
    page(
        group = "Panels",
        label = "Selected camera",
        page = DemoLayout.camerasPage(),
        renderInput = input(renderData = cameras),
    )
}

private fun PreviewSession.musicStates() {
    val musicPage = demo.pages.first { it.id == "music" }
    for (section in listOf(MusicUiState.PRIMARY_BROWSE, MusicUiState.PRIMARY_LISTEN)) {
        for (mode in listOf(MusicUiState.LISTEN_SEARCH, MusicUiState.LISTEN_QUEUE)) {
            val music = data.copy(
                musicUi = mapOf("ma1" to MusicUiState(primarySection = section, listenMode = mode)),
            )
            page(
                group = "Panels",
                label = "Music · $section · $mode",
                page = musicPage,
                renderInput = input(renderData = music),
            )
        }
    }
}

private fun PreviewSession.calendarStates() {
    val loading = data.copy(agenda = AgendaSnapshot(isLoading = true))
    page(
        group = "States",
        label = "Calendar loading",
        page = extraPages.last(),
        renderInput = input(renderData = loading),
    )
    val error = data.copy(agenda = AgendaSnapshot(errorMessage = "Calendar could not be loaded."))
    page(
        group = "States",
        label = "Calendar error",
        page = extraPages.last(),
        renderInput = input(renderData = error),
    )
}
