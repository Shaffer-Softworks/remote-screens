package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import android.content.IntentSender
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
    /** Whether to offer leaving battery optimization, so standby keeps its connection to the add-on. */
    val canOfferUnrestrictedBattery: Boolean = false,
    /** Whether to offer the camera permission face wake needs. */
    val canOfferCameraPermission: Boolean = false,
    /** Whether to offer the location permission that sharing the tablet's location needs. */
    val canOfferLocationPermission: Boolean = false,
    /** One-shot note when face wake is on but this tablet cannot run it. */
    @StringRes val wakeOnFaceUnavailableNote: Int? = null,
    /** What the kiosk button does; null hides it (no kiosk, or locked without the PIN entered). */
    val kioskAction: KioskAction? = null,
    /** Offered once the kiosk is unlocked, so the tablet can leave kiosk duty without a factory reset. */
    val canRemoveDeviceOwner: Boolean = false,
    val isCheckingForUpdates: Boolean = false,
    val isForgetConfirmationVisible: Boolean = false,
)

enum class KioskAction { UNLOCK, LOCK_AGAIN }

data class AddressDialogState(val initialAddress: String)

/** One-off things for the UI to do, which are not part of what the screen shows. */
sealed interface PlayerEvent {
    data class ShowMessage(val text: String) : PlayerEvent
    data class ShowMessageResource(@StringRes val messageRes: Int) : PlayerEvent
    data class OpenUrl(val url: String) : PlayerEvent
    data object OpenInstallPermissionSettings : PlayerEvent
    data object OpenOverlayPermissionSettings : PlayerEvent
    data object OpenScreenOffAdminSettings : PlayerEvent
    data object RequestUnrestrictedBattery : PlayerEvent
    data object RequestCameraPermission : PlayerEvent
    data object RequestLocationPermission : PlayerEvent

    data object RequestMicrophonePermission : PlayerEvent

    /** Android's one-tap prompt that turns Location accuracy on. */
    data class RequestLocationAccuracy(val intentSender: IntentSender) : PlayerEvent

    /** Screen pinning would hide the screen where Android asks to confirm an update. */
    data object StopScreenPinning : PlayerEvent

    /** Put screen pinning back after an update was cancelled or failed to download. */
    data object ReapplyKiosk : PlayerEvent
}
