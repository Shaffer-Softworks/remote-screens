package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import androidx.annotation.StringRes

/** The full-screen panel shown while there is nothing to draw yet. */
sealed interface InfoPanelState {
    data object Searching : InfoPanelState
    data class Connecting(val server: String) : InfoPanelState
    data object Disconnected : InfoPanelState

    /** An unadopted screen shows the code to enter in Home Assistant, in groups of three. */
    data class Pairing(val code: String) : InfoPanelState
}

data class SettingsDialogState(
    val server: String?,
    /** Whether to offer the permission that lets the app open itself after a restart or an update. */
    val canOfferSelfStart: Boolean,
    /** Whether to offer the device admin that standby needs to turn the screen off. */
    val canOfferScreenOff: Boolean = false,
    /** What the kiosk button does; null hides it (no kiosk, or locked without the PIN entered). */
    val kioskAction: KioskAction? = null,
    /** Offered once the kiosk is unlocked, so the tablet can leave kiosk duty without a factory reset. */
    val canRemoveDeviceOwner: Boolean = false,
    val isForgetConfirmationVisible: Boolean = false,
)

enum class KioskAction { UNLOCK, LOCK_AGAIN }

data class AddressDialogState(val initialAddress: String)

/** One-off things for the UI to do, which are not part of what the screen shows. */
sealed interface PlayerEvent {
    data class ShowMessage(val text: String) : PlayerEvent
    data class ShowMessageResource(@StringRes val messageRes: Int) : PlayerEvent
    data object OpenInstallPermissionSettings : PlayerEvent
    data object OpenOverlayPermissionSettings : PlayerEvent
    data object OpenScreenOffAdminSettings : PlayerEvent

    /** Screen pinning would hide the screen where Android asks to confirm an update. */
    data object StopScreenPinning : PlayerEvent
}
