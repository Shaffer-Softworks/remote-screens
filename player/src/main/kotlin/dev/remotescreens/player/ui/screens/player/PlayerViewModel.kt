package dev.remotescreens.player.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.remotescreens.player.R
import dev.remotescreens.player.data.connection.ConnectionRepository
import dev.remotescreens.player.data.connection.ConnectionStatus
import dev.remotescreens.player.data.device.DeviceRepository
import dev.remotescreens.player.data.device.DisplayInfo
import dev.remotescreens.player.data.discovery.DiscoveryRepository
import dev.remotescreens.player.data.image.ImageDecoder
import dev.remotescreens.player.data.preferences.PlayerPreferencesRepository
import dev.remotescreens.player.data.update.UpdateRepository
import dev.remotescreens.player.ui.screens.player.remote.RemoteScreenState
import dev.remotescreens.player.utils.decodeBase64OrNull
import dev.remotescreens.shared.isNightAt
import dev.remotescreens.player.utils.normalizeServerAddress
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.PlayerMessage
import dev.remotescreens.shared.ScreenSettings
import dev.remotescreens.shared.ServerMessage
import dev.remotescreens.shared.Slot
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalTime
import javax.inject.Inject

private const val MILLISECONDS_PER_SECOND = 1_000L
private const val MILLISECONDS_PER_MINUTE = 60_000L
private const val NIGHT_CHECK_INTERVAL_MS = 60_000L
private const val MIN_IDENTIFY_SECONDS = 1
private const val MAX_IDENTIFY_SECONDS = 60
private const val PAIRING_CODE_GROUP_SIZE = 3
private val PLAYER_CAPABILITIES = listOf("predictions", "bitmaps", "alerts", "cards", "settings", "deflate")

/**
 * Runs a wall screen: finds and talks to the add-on, keeps the documents it sends, and does what a wall screen does
 * by itself (dim after a while without a touch, follow a night schedule, go back to the home page).
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val preferencesRepository: PlayerPreferencesRepository,
    private val connectionRepository: ConnectionRepository,
    private val discoveryRepository: DiscoveryRepository,
    private val updateRepository: UpdateRepository,
    private val deviceRepository: DeviceRepository,
    private val imageDecoder: ImageDecoder,
) : ViewModel() {

    data class ViewState(
        val infoPanel: InfoPanelState? = null,
        val remoteScreen: RemoteScreenState = RemoteScreenState(),
        val screenSettings: ScreenSettings = ScreenSettings(),
        val isSwipeEnabled: Boolean = true,
        val isAwake: Boolean = true,
        val isNight: Boolean = false,
        /** The brightness being tried in the settings dialog, until it closes. */
        val previewBrightnessPercent: Int? = null,
        /** The name shown big over everything while the add-on asks the screen to identify itself. */
        val identifyScreenName: String? = null,
        val availableUpdate: ServerMessage.UpdateAvailable? = null,
        val settingsDialog: SettingsDialogState? = null,
        val addressDialog: AddressDialogState? = null,
    ) {
        private val scheduledBrightnessPercent: Int
            get() = when {
                !isAwake -> screenSettings.standbyBrightness
                isNight -> screenSettings.nightBrightness
                else -> screenSettings.brightness
            }

        val brightnessPercent: Int get() = previewBrightnessPercent ?: scheduledBrightnessPercent

        /** Standby at brightness 0 covers the screen in black, as the backlight cannot go fully dark. */
        val isBlackout: Boolean get() = !isAwake && scheduledBrightnessPercent == 0

        val isAnyDialogVisible: Boolean
            get() = settingsDialog != null || addressDialog != null || availableUpdate != null
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _events = Channel<PlayerEvent>(Channel.BUFFERED)
    val events: Flow<PlayerEvent> = _events.receiveAsFlow()

    private var displayInfo: DisplayInfo? = null
    private var isPaired = false
    private var discoveryJob: Job? = null
    private var standbyJob: Job? = null
    private var backToHomeJob: Job? = null
    private var alertTimeoutJob: Job? = null
    private var identifyJob: Job? = null

    init {
        observeConnectionStatus()
        observeServerMessages()
        observeNightSchedule()
        onUserInteraction()
    }

    override fun onCleared() {
        connectionRepository.disconnect()
        super.onCleared()
    }

    // region Display and connection

    /** The window was measured. The first time starts the connection; a new size asks for new documents. */
    fun onDisplayMeasured(measuredDisplay: DisplayInfo) {
        val previousDisplay = displayInfo
        displayInfo = measuredDisplay
        when {
            previousDisplay == null -> start()
            !previousDisplay.hasSameSizeAs(measuredDisplay) -> connectionRepository.reconnect()
        }
    }

    private fun start() {
        viewModelScope.launch {
            savedStateHandle.get<String>(EXTRA_SERVER)?.let { server -> preferencesRepository.setServer(server) }
            val server = preferencesRepository.server.first()
            if (server != null) connect(server) else searchForServer()
        }
    }

    private fun searchForServer() {
        _viewState.update { it.copy(infoPanel = InfoPanelState.Searching) }
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            val server = discoveryRepository.discoverServers().first()
            preferencesRepository.setServer(server)
            connect(server)
        }
    }

    private fun connect(server: String) {
        discoveryJob?.cancel()
        connectionRepository.connect(server, ::buildHello)
    }

    private suspend fun buildHello(): PlayerMessage.Hello {
        val display = displayInfo ?: DisplayInfo()
        return PlayerMessage.Hello(
            deviceId = preferencesRepository.deviceId(),
            deviceName = deviceRepository.deviceName,
            token = preferencesRepository.token.first(),
            widthDp = display.widthDp,
            heightDp = display.heightDp,
            density = display.density,
            appVersion = deviceRepository.appVersionCode,
            capabilities = PLAYER_CAPABILITIES,
            darkSystem = display.isSystemDark,
        )
    }

    private fun observeConnectionStatus() {
        viewModelScope.launch {
            connectionRepository.status.collect { status ->
                val hasMainDocument = _viewState.value.remoteScreen.hasMainDocument
                when (status) {
                    ConnectionStatus.CONNECTING -> if (!hasMainDocument && !isPaired) {
                        val server = connectionRepository.server.value.orEmpty()
                        _viewState.update { it.copy(infoPanel = InfoPanelState.Connecting(server)) }
                    }
                    ConnectionStatus.DISCONNECTED -> if (!hasMainDocument) {
                        _viewState.update { it.copy(infoPanel = InfoPanelState.Disconnected) }
                    }
                    ConnectionStatus.IDLE, ConnectionStatus.CONNECTED -> Unit
                }
            }
        }
    }

    private fun send(message: PlayerMessage) {
        viewModelScope.launch { connectionRepository.send(message) }
    }

    private fun sendDeviceState() {
        val currentState = _viewState.value
        val batteryStatus = deviceRepository.batteryStatus()
        send(
            PlayerMessage.DeviceState(
                awake = currentState.isAwake,
                page = currentState.remoteScreen.page,
                battery = batteryStatus.levelPercent,
                charging = batteryStatus.isCharging,
            ),
        )
    }

    // endregion

    // region Messages from the add-on

    private fun observeServerMessages() {
        viewModelScope.launch {
            connectionRepository.messages.collect { message -> handleServerMessage(message) }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun handleServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Welcome -> onWelcome(message)
            is ServerMessage.Document -> onDocument(message)
            is ServerMessage.Vars -> updateRemoteScreen { it.withVars(message) }
            is ServerMessage.Bitmap -> onBitmap(message)
            is ServerMessage.Settings -> {
                applyScreenSettings(message.settings)
                _viewState.update { it.copy(isSwipeEnabled = message.settings.swipe) }
            }
            is ServerMessage.OpenPage -> {
                closeTopOverlay()
                showPage(message.index)
                onUserInteraction()
            }
            is ServerMessage.Close -> closeSlot(message.slot)
            is ServerMessage.Identify -> identify(message.seconds)
            is ServerMessage.Wake -> wake(message.awake)
            is ServerMessage.UpdateAvailable -> onUpdateAvailable(message)
            is ServerMessage.Toast -> _events.send(PlayerEvent.ShowMessage(message.text))
            ServerMessage.Pong -> Unit
        }
    }

    private suspend fun onWelcome(welcome: ServerMessage.Welcome) {
        closeSlot(Slot.OVERLAY)
        closeSlot(Slot.ALERT)
        welcome.token?.let { token -> preferencesRepository.setPairing(token, welcome.screenName) }
        val pairingCode = welcome.pairingCode
        isPaired = pairingCode == null
        if (pairingCode != null) {
            if (welcome.token == null && preferencesRepository.token.first() != null) preferencesRepository.forget()
            val groupedCode = pairingCode.chunked(PAIRING_CODE_GROUP_SIZE).joinToString(" ")
            _viewState.update {
                it.copy(remoteScreen = it.remoteScreen.cleared(), infoPanel = InfoPanelState.Pairing(groupedCode))
            }
        } else if (_viewState.value.remoteScreen.hasMainDocument) {
            _viewState.update { it.copy(infoPanel = null) }
        }
        sendDeviceState()
    }

    private fun onDocument(document: ServerMessage.Document) {
        val bytes = decodeBase64OrNull(document.data)
        if (bytes == null) {
            Timber.w("Bad document data for %s", document.slot)
            return
        }
        updateRemoteScreen { it.withDocument(document, bytes) }
        when (document.slot) {
            Slot.MAIN -> {
                _viewState.update { it.copy(infoPanel = null) }
                showPage(_viewState.value.remoteScreen.pageAfterNewDocument)
            }
            Slot.ALERT -> {
                scheduleAlertTimeout(document.timeout)
                onUserInteraction()
            }
            Slot.OVERLAY -> onUserInteraction()
        }
    }

    private fun onBitmap(message: ServerMessage.Bitmap) {
        viewModelScope.launch {
            val bitmap = imageDecoder.decodeBitmap(message.data) ?: return@launch
            updateRemoteScreen { it.withBitmap(message.name, bitmap) }
        }
    }

    private suspend fun onUpdateAvailable(update: ServerMessage.UpdateAvailable) {
        if (connectionRepository.server.value == null || preferencesRepository.token.first() == null) return
        _viewState.update { it.copy(availableUpdate = update) }
    }

    private fun scheduleAlertTimeout(timeoutSeconds: Int) {
        alertTimeoutJob?.cancel()
        if (timeoutSeconds <= 0) return
        alertTimeoutJob = viewModelScope.launch {
            delay(timeoutSeconds * MILLISECONDS_PER_SECOND)
            closeSlot(Slot.ALERT)
        }
    }

    private fun identify(seconds: Int) {
        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            val screenName = preferencesRepository.screenName.first() ?: deviceRepository.model
            _viewState.update { it.copy(identifyScreenName = screenName) }
            wake(true)
            delay(seconds.coerceIn(MIN_IDENTIFY_SECONDS, MAX_IDENTIFY_SECONDS) * MILLISECONDS_PER_SECOND)
            _viewState.update { it.copy(identifyScreenName = null) }
        }
    }

    // endregion

    // region Documents and pages

    /** A tap in a document. Local verbs are handled here; the rest go to the add-on, shown at once if predicted. */
    fun onDocumentAction(slot: Slot, revision: Int, metadata: String) {
        onUserInteraction()
        val actionRef = ActionRef.decode(metadata) ?: return
        if (actionRef.local) {
            handleLocalAction(slot, actionRef)
            return
        }
        _viewState.value.remoteScreen.withPrediction(slot, metadata)?.let { predicted ->
            _viewState.update { it.copy(remoteScreen = predicted) }
        }
        send(PlayerMessage.Action(slot, revision, metadata))
    }

    private fun handleLocalAction(slot: Slot, actionRef: ActionRef) {
        val remoteScreen = _viewState.value.remoteScreen
        when (actionRef.verb) {
            "page" -> actionRef.arg.toIntOrNull()?.let(::showPage)
            "next" -> remoteScreen.nextSwipePage?.let(::showPage)
            "prev" -> remoteScreen.previousSwipePage?.let(::showPage)
            "back" -> showPage(actionRef.arg.toIntOrNull() ?: remoteScreen.homePage)
            "close" -> closeSlot(slot)
            "settings" -> openSettings()
        }
    }

    fun onSwipe(isNext: Boolean) {
        val remoteScreen = _viewState.value.remoteScreen
        if (remoteScreen.isOverlayShown) return
        val targetPage = if (isNext) remoteScreen.nextSwipePage else remoteScreen.previousSwipePage
        targetPage?.let(::showPage)
    }

    /** Back closes a card first, then goes home. */
    fun onBack() {
        if (closeTopOverlay()) return
        val remoteScreen = _viewState.value.remoteScreen
        if (remoteScreen.page != remoteScreen.homePage) showPage(remoteScreen.homePage)
    }

    private fun showPage(index: Int) {
        updateRemoteScreen { it.withPage(index) }
        send(PlayerMessage.PageShown(_viewState.value.remoteScreen.page))
        sendDeviceState()
    }

    private fun closeSlot(slot: Slot) {
        if (slot == Slot.ALERT) alertTimeoutJob?.cancel()
        updateRemoteScreen { it.closed(slot) }
    }

    private fun closeTopOverlay(): Boolean {
        val topOverlay = _viewState.value.remoteScreen.topOverlay ?: return false
        closeSlot(topOverlay)
        return true
    }

    private inline fun updateRemoteScreen(transform: (RemoteScreenState) -> RemoteScreenState) {
        _viewState.update { it.copy(remoteScreen = transform(it.remoteScreen)) }
    }

    // endregion

    // region Standby, night and back to home

    /** Any touch: wake up, and start the standby and back-to-home clocks again. */
    fun onUserInteraction() {
        if (!_viewState.value.isAwake) setAwake(true)
        standbyJob?.cancel()
        backToHomeJob?.cancel()
        val settings = _viewState.value.screenSettings
        if (settings.standbyMinutes > 0) {
            standbyJob = viewModelScope.launch {
                delay(settings.standbyMinutes * MILLISECONDS_PER_MINUTE)
                setAwake(false)
            }
        }
        if (settings.backToHomeSeconds > 0) {
            backToHomeJob = viewModelScope.launch {
                delay(settings.backToHomeSeconds * MILLISECONDS_PER_SECOND)
                closeTopOverlay()
                showPage(_viewState.value.remoteScreen.homePage)
            }
        }
    }

    private fun wake(isAwake: Boolean) {
        if (isAwake) onUserInteraction() else setAwake(false)
    }

    private fun setAwake(isAwake: Boolean) {
        if (_viewState.value.isAwake == isAwake) return
        _viewState.update { it.copy(isAwake = isAwake) }
        sendDeviceState()
    }

    private fun applyScreenSettings(settings: ScreenSettings) {
        _viewState.update { it.copy(screenSettings = settings, isNight = settings.isNightAt(LocalTime.now())) }
        onUserInteraction()
    }

    private fun observeNightSchedule() {
        viewModelScope.launch {
            while (true) {
                _viewState.update { it.copy(isNight = it.screenSettings.isNightAt(LocalTime.now())) }
                delay(NIGHT_CHECK_INTERVAL_MS)
            }
        }
    }

    // endregion

    // region Settings dialog

    private fun openSettings() {
        _viewState.update {
            it.copy(
                settingsDialog = SettingsDialogState(
                    server = connectionRepository.server.value,
                    canOfferSelfStart = !deviceRepository.canSelfStart(),
                ),
            )
        }
    }

    fun onSettingsBrightnessPreview(brightnessPercent: Int) {
        _viewState.update { it.copy(previewBrightnessPercent = brightnessPercent) }
    }

    fun onSettingsConfirmed(brightnessPercent: Int, isDarkMode: Boolean, standbyMinutes: Int) {
        val currentSettings = _viewState.value.screenSettings
        val changedSettings = currentSettings.copy(
            brightness = brightnessPercent,
            darkMode = isDarkMode,
            standbyMinutes = standbyMinutes,
        )
        if (changedSettings != currentSettings) {
            applyScreenSettings(changedSettings)
            send(PlayerMessage.SettingsChanged(changedSettings))
        }
        onSettingsDismissed()
    }

    fun onSettingsDismissed() {
        _viewState.update { it.copy(settingsDialog = null, previewBrightnessPercent = null) }
        onUserInteraction()
    }

    fun onChangeServerRequested() {
        onSettingsDismissed()
        openAddressDialog()
    }

    fun onForgetRequested() {
        _viewState.update { state ->
            state.copy(settingsDialog = state.settingsDialog?.copy(isForgetConfirmationVisible = true))
        }
    }

    fun onForgetCancelled() {
        _viewState.update { state ->
            state.copy(settingsDialog = state.settingsDialog?.copy(isForgetConfirmationVisible = false))
        }
    }

    fun onForgetConfirmed() {
        onSettingsDismissed()
        viewModelScope.launch {
            preferencesRepository.forget()
            updateRemoteScreen { it.cleared() }
            connectionRepository.reconnect()
        }
    }

    fun onAllowSelfStartRequested() {
        onSettingsDismissed()
        viewModelScope.launch { _events.send(PlayerEvent.OpenOverlayPermissionSettings) }
    }

    // endregion

    // region Address dialog

    fun openAddressDialog() {
        viewModelScope.launch {
            val savedServer = preferencesRepository.server.first().orEmpty()
            _viewState.update { it.copy(addressDialog = AddressDialogState(initialAddress = savedServer)) }
        }
    }

    fun onAddressConfirmed(input: String) {
        onAddressDismissed()
        val server = normalizeServerAddress(input) ?: return
        viewModelScope.launch {
            preferencesRepository.setServer(server)
            updateRemoteScreen { it.cleared() }
            connect(server)
        }
    }

    fun onAddressDismissed() {
        _viewState.update { it.copy(addressDialog = null) }
    }

    // endregion

    // region Updates

    fun onInstallUpdateConfirmed() {
        val update = _viewState.value.availableUpdate ?: return
        onUpdateDismissed()
        viewModelScope.launch {
            val server = connectionRepository.server.value ?: return@launch
            val token = preferencesRepository.token.first() ?: return@launch
            if (!deviceRepository.canInstallPackages()) {
                _events.send(PlayerEvent.ShowMessageResource(R.string.update_permission))
                _events.send(PlayerEvent.OpenInstallPermissionSettings)
                return@launch
            }
            updateRepository.install(server, update.path, token)
        }
    }

    fun onUpdateDismissed() {
        _viewState.update { it.copy(availableUpdate = null) }
    }

    // endregion

    companion object {
        /** `host:port` of the add-on, for setting a screen up over adb: `am start -n … --es server 10.0.0.5:8100`. */
        const val EXTRA_SERVER = "server"
    }
}
