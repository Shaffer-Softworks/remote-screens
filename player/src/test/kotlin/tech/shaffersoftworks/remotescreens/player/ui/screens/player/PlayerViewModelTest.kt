package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionRepository
import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionStatus
import tech.shaffersoftworks.remotescreens.player.data.device.BatteryStatus
import tech.shaffersoftworks.remotescreens.player.data.device.DeviceRepository
import tech.shaffersoftworks.remotescreens.player.data.device.DisplayInfo
import tech.shaffersoftworks.remotescreens.player.data.device.FaceWakeMonitor
import tech.shaffersoftworks.remotescreens.player.data.device.ProximityWakeMonitor
import tech.shaffersoftworks.remotescreens.player.data.device.ScreenPowerRepository
import tech.shaffersoftworks.remotescreens.player.data.device.intercom.IntercomCoordinator
import tech.shaffersoftworks.remotescreens.player.data.device.telemetry.LocationAccuracy
import tech.shaffersoftworks.remotescreens.player.data.device.telemetry.TelemetryReporter
import tech.shaffersoftworks.remotescreens.player.data.discovery.DiscoveryRepository
import tech.shaffersoftworks.remotescreens.player.data.image.ImageDecoder
import tech.shaffersoftworks.remotescreens.player.data.kiosk.KioskManager
import tech.shaffersoftworks.remotescreens.player.data.kiosk.KioskState
import tech.shaffersoftworks.remotescreens.player.data.preferences.PlayerPreferencesRepository
import tech.shaffersoftworks.remotescreens.player.data.update.UpdateRepository
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.Protocol
import tech.shaffersoftworks.remotescreens.shared.ScreenSettings
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.milliseconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    private val consecutiveFailures = MutableStateFlow(0)
    private val serverMessages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
    private val connectedServer = MutableStateFlow<String?>("10.0.0.5:8100")

    private val connectionRepository: ConnectionRepository = mock {
        on { status } doReturn connectionStatus
        on { messages } doReturn serverMessages
        on { server } doReturn connectedServer
        on { consecutiveFailures } doReturn consecutiveFailures
    }
    private val isKioskEnabled = MutableStateFlow(false)
    private val isKioskSuppressed = MutableStateFlow(false)
    private val preferencesRepository: PlayerPreferencesRepository = mock {
        on { server } doReturn flowOf("10.0.0.5:8100")
        on { token } doReturn flowOf("token")
        on { screenName } doReturn flowOf("Kitchen")
        on { deviceId() } doReturn "tablet-1"
        on { isKioskEnabled } doReturn isKioskEnabled
        on { isKioskSuppressed } doReturn isKioskSuppressed
    }
    private val kioskManager: KioskManager = mock {
        on { lockTaskState() } doReturn KioskState.OFF
        on { isDeviceOwner() } doReturn false
    }
    private val intercomCoordinator: IntercomCoordinator = mock {
        on { hasMicrophone() } doReturn false
    }
    private val deviceRepository: DeviceRepository = mock {
        on { batteryStatus() } doReturn BatteryStatus(levelPercent = 50, isCharging = true)
    }
    private val discoveryRepository: DiscoveryRepository = mock {
        on { discoverServers() } doReturn emptyFlow()
    }
    private val screenPowerRepository: ScreenPowerRepository = mock()
    private val proximityWakeMonitor: ProximityWakeMonitor = mock {
        on { isUsable() } doReturn false
    }
    private val faceWakeMonitor: FaceWakeMonitor = mock {
        on { isUsable() } doReturn false
        on { hasCameraPermission() } doReturn false
        on { hasFrontCamera() } doReturn true
        on { isLowRamDevice() } doReturn false
    }

    private val telemetryReporter: TelemetryReporter = mock {
        on { hasLocationPermission() } doReturn false
    }
    private val locationAccuracy: LocationAccuracy = mock()

    private val updateRepository: UpdateRepository = mock()

    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(),
            preferencesRepository = preferencesRepository,
            connectionRepository = connectionRepository,
            discoveryRepository = discoveryRepository,
            updateRepository = updateRepository,
            deviceRepository = deviceRepository,
            screenPowerRepository = screenPowerRepository,
            proximityWakeMonitor = proximityWakeMonitor,
            faceWakeMonitor = faceWakeMonitor,
            kioskManager = kioskManager,
            imageDecoder = mock<ImageDecoder>(),
            telemetryReporter = telemetryReporter,
            locationAccuracy = locationAccuracy,
            intercomCoordinator = intercomCoordinator,
            haImageLoaderQualifier = mock(),
        )
    }

    private fun runCurrent() = testDispatcher.scheduler.runCurrent()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mainDocument(pageCount: Int = 3, homePage: Int = 0) = ServerMessage.Document(
        slot = Slot.MAIN,
        rev = 7,
        data = "AAAA",
        homePage = homePage,
        pageCount = pageCount,
    )

    private fun receive(message: ServerMessage) {
        runCurrent()
        serverMessages.tryEmit(message)
        runCurrent()
    }

    @Test
    fun `first measure connects to the saved server`() {
        viewModel.onDisplayMeasured(DisplayInfo(widthDp = 800, heightDp = 480))
        runCurrent()

        verify(connectionRepository).connect(eq("10.0.0.5:8100"), any())
    }

    @Test
    fun `forget resets identity before reconnecting and clears the old screen`() {
        receive(mainDocument())

        viewModel.onForgetConfirmed()
        runCurrent()

        inOrder(preferencesRepository, connectionRepository) {
            runBlocking { verify(preferencesRepository).forget(resetDeviceId = true) }
            verify(connectionRepository).reconnect()
        }
        assertFalse(viewModel.viewState.value.remoteScreen.hasMainDocument)
    }

    @Test
    fun `a new size asks for new documents`() {
        viewModel.onDisplayMeasured(DisplayInfo(widthDp = 800, heightDp = 480))
        viewModel.onDisplayMeasured(DisplayInfo(widthDp = 480, heightDp = 800))

        verify(connectionRepository).reconnect()
    }

    @Test
    fun `the same size again does not reconnect`() {
        viewModel.onDisplayMeasured(DisplayInfo(widthDp = 800, heightDp = 480))
        viewModel.onDisplayMeasured(DisplayInfo(widthDp = 800, heightDp = 480))

        verify(connectionRepository, never()).reconnect()
    }

    @Test
    fun `an empty measure is ignored`() {
        viewModel.onDisplayMeasured(DisplayInfo(widthDp = 0, heightDp = 0))
        runCurrent()

        verify(connectionRepository, never()).connect(any(), any())
    }

    @Test
    fun `repeated connection failures look for the add-on again`() {
        val discovery = MutableSharedFlow<String>(extraBufferCapacity = 1)
        whenever(discoveryRepository.discoverServers()).thenReturn(discovery)
        viewModel.onDisplayMeasured(DisplayInfo(widthDp = 800, heightDp = 480))
        runCurrent()

        consecutiveFailures.value = 5
        runCurrent()
        discovery.tryEmit("10.0.0.9:8100")
        runCurrent()

        verify(connectionRepository).connect(eq("10.0.0.9:8100"), any())
        verifyBlocking(preferencesRepository) { setServer("10.0.0.9:8100") }
    }

    @Test
    fun `connecting without a document shows the server`() {
        runCurrent()
        connectionStatus.value = ConnectionStatus.CONNECTING
        runCurrent()

        assertEquals(
            InfoPanelState.Connecting("10.0.0.5:8100"),
            viewModel.viewState.value.infoPanel,
        )
    }

    @Test
    fun `telemetry is reported only to a paired add-on that accepts it`() {
        receive(ServerMessage.Welcome(pairingCode = "123456", features = listOf(Protocol.CAPABILITY_TELEMETRY)))
        verify(telemetryReporter).update(any(), eq(false), eq(false), any())

        receive(ServerMessage.Welcome(features = listOf(Protocol.CAPABILITY_TELEMETRY)))
        verify(telemetryReporter).update(any(), eq(true), eq(false), any())
    }

    @Test
    fun `turning location sharing on updates the reporter`() {
        receive(ServerMessage.Settings(ScreenSettings(shareLocation = true)))

        verify(telemetryReporter).update(any(), any(), eq(true), any())
        verify(locationAccuracy).enableLocationServices()
        verify(kioskManager, never()).setLocationAccuracyDialogAllowed(true)
    }

    @Test
    fun `location accuracy is offered once permission is already granted`() {
        val sender = mock<IntentSender>()
        whenever(telemetryReporter.hasLocationPermission()).thenReturn(true)
        whenever { locationAccuracy.findAccuracyPrompt() } doReturn sender

        receive(ServerMessage.Settings(ScreenSettings(shareLocation = true)))

        verify(locationAccuracy).enableLocationServices()
        verify(kioskManager).setLocationAccuracyDialogAllowed(true)
    }

    @Test
    fun `pairing shows the code in groups of three`() {
        receive(ServerMessage.Welcome(pairingCode = "123456"))

        assertEquals(InfoPanelState.Pairing("123 456"), viewModel.viewState.value.infoPanel)
    }

    @Test
    fun `a main document hides the info panel and reports the page`() {
        receive(ServerMessage.Welcome(pairingCode = "123456"))
        receive(mainDocument())

        assertNull(viewModel.viewState.value.infoPanel)
        assertTrue(viewModel.viewState.value.remoteScreen.hasMainDocument)
        verifyBlocking(connectionRepository) { send(PlayerMessage.PageShown(0)) }
    }

    @Test
    fun `a local page action changes page without asking the add-on`() {
        receive(mainDocument())

        viewModel.onDocumentAction(
            Slot.MAIN,
            revision = 7,
            metadata = ActionRef("_", "page", "2").encode(),
        )
        runCurrent()

        assertEquals(2, viewModel.viewState.value.remoteScreen.page)
        verifyBlocking(connectionRepository) { send(PlayerMessage.PageShown(2)) }
    }

    @Test
    fun `other actions go to the add-on with the document revision`() {
        receive(mainDocument())
        val metadata = ActionRef("light.kitchen", "toggle").encode()

        viewModel.onDocumentAction(Slot.MAIN, revision = 7, metadata = metadata)
        runCurrent()

        verifyBlocking(connectionRepository) {
            send(PlayerMessage.Action(Slot.MAIN, 7, metadata, page = 0))
        }
    }

    @Test
    fun `back closes the card before going home`() {
        receive(mainDocument())
        receive(ServerMessage.OpenPage(2))
        receive(ServerMessage.Document(slot = Slot.OVERLAY, rev = 1, data = "AAAA"))

        viewModel.onBack()
        assertFalse(viewModel.viewState.value.remoteScreen.isOverlayShown)
        assertEquals(2, viewModel.viewState.value.remoteScreen.page)

        viewModel.onBack()
        assertEquals(0, viewModel.viewState.value.remoteScreen.page)
    }

    @Test
    fun `a wake message to sleep dims to standby brightness`() {
        receive(ServerMessage.Wake(awake = false))

        val viewState = viewModel.viewState.value
        assertFalse(viewState.isAwake)
        assertTrue(viewState.isBlackout)
    }

    @Test
    fun `a touch wakes the screen again`() {
        receive(ServerMessage.Wake(awake = false))

        viewModel.onUserInteraction()

        assertTrue(viewModel.viewState.value.isAwake)
    }

    @Test
    fun `settings sent again do not restart the standby clock`() {
        val settings = ScreenSettings(standbyMinutes = 1)
        receive(ServerMessage.Settings(settings))
        testDispatcher.scheduler.advanceTimeBy(50_000.milliseconds)

        receive(ServerMessage.Settings(settings))
        testDispatcher.scheduler.advanceTimeBy(11_000.milliseconds)

        assertFalse(viewModel.viewState.value.isAwake)
    }

    @Test
    fun `settings from the add-on do not wake the screen`() {
        receive(ServerMessage.Wake(awake = false))

        receive(ServerMessage.Settings(ScreenSettings(standbyMinutes = 5)))

        assertFalse(viewModel.viewState.value.isAwake)
    }

    @Test
    fun `turning standby off wakes the screen`() {
        receive(ServerMessage.Wake(awake = false))

        receive(ServerMessage.Settings(ScreenSettings(standbyMinutes = 0)))

        assertTrue(viewModel.viewState.value.isAwake)
    }

    @Test
    fun `standby turns the screen off when the screen asks for it`() {
        receive(ServerMessage.Settings(ScreenSettings(standbyScreenOff = true)))

        receive(ServerMessage.Wake(awake = false))

        verify(screenPowerRepository).turnScreenOff()
    }

    @Test
    fun `standby only dims when the screen does not ask to turn off`() {
        receive(ServerMessage.Wake(awake = false))

        verify(screenPowerRepository, never()).turnScreenOff()
    }

    @Test
    fun `face wake skips turning the screen off so the camera can run`() {
        whenever(faceWakeMonitor.isUsable()).thenReturn(true)
        receive(ServerMessage.Settings(ScreenSettings(standbyScreenOff = true, wakeOnFace = true)))

        receive(ServerMessage.Wake(awake = false))

        verify(screenPowerRepository, never()).turnScreenOff()
        verify(faceWakeMonitor).start(any())
    }

    @Test
    fun `proximity and face wake can both run when both are enabled`() {
        whenever(proximityWakeMonitor.isUsable()).thenReturn(true)
        whenever(faceWakeMonitor.isUsable()).thenReturn(true)
        receive(
            ServerMessage.Settings(
                ScreenSettings(standbyScreenOff = true, wakeOnProximity = true, wakeOnFace = true),
            ),
        )

        receive(ServerMessage.Wake(awake = false))

        // Face needs the panel on (blackout), even when proximity is also listening.
        verify(screenPowerRepository, never()).turnScreenOff()
        verify(proximityWakeMonitor, atLeastOnce()).start(any())
        verify(faceWakeMonitor).start(any())
    }

    @Test
    fun `waking leaves proximity listening`() {
        whenever(proximityWakeMonitor.isUsable()).thenReturn(true)
        receive(
            ServerMessage.Settings(
                ScreenSettings(standbyScreenOff = true, wakeOnProximity = true),
            ),
        )

        receive(ServerMessage.Wake(awake = false))
        viewModel.onUserInteraction()

        verify(proximityWakeMonitor, never()).stop()
        verify(proximityWakeMonitor, atLeastOnce()).start(any())
        verify(screenPowerRepository, atLeastOnce()).turnScreenOn()
    }

    @Test
    fun `face wake without a usable camera still turns the screen off`() {
        whenever(faceWakeMonitor.isUsable()).thenReturn(false)
        receive(ServerMessage.Settings(ScreenSettings(standbyScreenOff = true, wakeOnFace = true)))

        receive(ServerMessage.Wake(awake = false))

        verify(screenPowerRepository).turnScreenOff()
        verify(faceWakeMonitor, never()).start(any())
    }

    @Test
    fun `waking stops wake monitors`() {
        whenever(faceWakeMonitor.isUsable()).thenReturn(true)
        receive(ServerMessage.Settings(ScreenSettings(wakeOnFace = true)))
        receive(ServerMessage.Wake(awake = false))

        viewModel.onUserInteraction()

        verify(faceWakeMonitor, atLeastOnce()).stop()
        verify(proximityWakeMonitor, atLeastOnce()).stop()
    }

    @Test
    fun `waking turns the screen on again`() {
        receive(ServerMessage.Settings(ScreenSettings(standbyScreenOff = true)))
        receive(ServerMessage.Wake(awake = false))

        receive(ServerMessage.Wake(awake = true))

        inOrder(screenPowerRepository) {
            verify(screenPowerRepository).turnScreenOff()
            verify(screenPowerRepository).turnScreenOn()
        }
    }

    @Test
    fun `identify shows the saved screen name`() {
        receive(ServerMessage.Identify(seconds = 5))

        assertEquals("Kitchen", viewModel.viewState.value.identifyScreenName)
    }

    private fun lockKiosk(kioskPin: String? = "1234") {
        isKioskEnabled.value = true
        receive(ServerMessage.Settings(ScreenSettings(kiosk = true, kioskPin = kioskPin)))
    }

    private fun openSettings() {
        viewModel.onDocumentAction(
            Slot.MAIN,
            revision = 7,
            metadata = ActionRef("_", "settings").encode(),
        )
        runCurrent()
    }

    @Test
    fun `the kiosk is not applied before the saved setting is read`() {
        assertNull(viewModel.viewState.value.isKioskWanted)

        runCurrent()

        assertEquals(false, viewModel.viewState.value.isKioskWanted)
    }

    @Test
    fun `the kiosk setting from the add-on is saved`() {
        receive(ServerMessage.Settings(ScreenSettings(kiosk = true)))

        verifyBlocking(preferencesRepository) { setKioskEnabled(true) }
    }

    @Test
    fun `a locked kiosk with a pin asks for it before settings`() {
        lockKiosk()

        openSettings()

        assertTrue(viewModel.viewState.value.isKioskPinDialogVisible)
        assertNull(viewModel.viewState.value.settingsDialog)
    }

    @Test
    fun `the right pin opens settings with the unlock button`() {
        lockKiosk()
        openSettings()

        viewModel.onKioskPinEntered("1234")
        runCurrent()

        val settingsDialog = viewModel.viewState.value.settingsDialog
        assertEquals(KioskAction.UNLOCK, settingsDialog?.kioskAction)
        assertFalse(settingsDialog?.canOfferSelfStart ?: true)
        assertFalse(viewModel.viewState.value.isKioskPinDialogVisible)
    }

    @Test
    fun `a wrong pin keeps settings closed`() {
        lockKiosk()
        openSettings()

        viewModel.onKioskPinEntered("9999")
        runCurrent()

        assertNull(viewModel.viewState.value.settingsDialog)
        assertFalse(viewModel.viewState.value.isKioskPinDialogVisible)
    }

    @Test
    fun `a kiosk without a pin opens settings with no way to unlock`() {
        lockKiosk(kioskPin = null)

        openSettings()

        assertNull(viewModel.viewState.value.settingsDialog?.kioskAction)
        assertFalse(viewModel.viewState.value.isKioskPinDialogVisible)
    }

    @Test
    fun `unlocking on the tablet is saved and lets the player out`() {
        lockKiosk()
        assertEquals(true, viewModel.viewState.value.isKioskWanted)

        viewModel.onUnlockKioskRequested()
        runCurrent()
        verifyBlocking(preferencesRepository) { setKioskSuppressed(true) }

        isKioskSuppressed.value = true
        runCurrent()
        assertEquals(false, viewModel.viewState.value.isKioskWanted)
    }

    @Test
    fun `an unlocked kiosk offers to lock again without the pin`() {
        lockKiosk()
        isKioskSuppressed.value = true
        runCurrent()

        openSettings()

        assertEquals(KioskAction.LOCK_AGAIN, viewModel.viewState.value.settingsDialog?.kioskAction)
        viewModel.onLockKioskAgainRequested()
        runCurrent()
        verifyBlocking(preferencesRepository) { setKioskSuppressed(false) }
    }

    @Test
    fun `intercom asks for the microphone when the tablet is not the device owner`() {
        receive(intercomConfig())

        assertEquals(listOf(PlayerEvent.RequestMicrophonePermission), takeEvents())
        verify(intercomCoordinator, never()).start(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `intercom starts when the microphone is already granted`() {
        whenever(intercomCoordinator.hasMicrophone()).thenReturn(true)
        whenever { preferencesRepository.deviceId() } doReturn "device-1"

        receive(intercomConfig())

        verify(intercomCoordinator).start(any(), any(), any(), any(), any(), any())
        assertFalse(takeEvents().contains(PlayerEvent.RequestMicrophonePermission))
    }

    @Test
    fun `a device owner is not asked for the microphone`() {
        whenever(kioskManager.isDeviceOwner()).thenReturn(true)

        receive(intercomConfig())

        assertFalse(takeEvents().contains(PlayerEvent.RequestMicrophonePermission))
        verify(intercomCoordinator, never()).start(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `opening the intercom page asks again after the microphone was denied`() {
        receive(mainDocument())
        receive(intercomConfig())
        takeEvents()
        viewModel.onMicrophonePermissionResult(granted = false)
        runCurrent()
        takeEvents()

        receive(ServerMessage.OpenPage(2))

        assertTrue(takeEvents().contains(PlayerEvent.RequestMicrophonePermission))
    }

    @Test
    fun `opening another page does not ask for the microphone`() {
        receive(mainDocument())
        receive(intercomConfig())
        takeEvents()
        viewModel.onMicrophonePermissionResult(granted = false)
        runCurrent()
        takeEvents()

        receive(ServerMessage.OpenPage(1))

        assertFalse(takeEvents().contains(PlayerEvent.RequestMicrophonePermission))
    }

    private fun intercomConfig(): ServerMessage.IntercomConfig = ServerMessage.IntercomConfig(
        enabled = true,
        displayName = "Office keypad",
        pageIndexes = listOf(2),
    )

    @Test
    fun `manual check reopens a dismissed update without device owner`() {
        val update = ServerMessage.UpdateAvailable(142, "0.1.42", "/player/apk")
        receive(update)
        viewModel.onUpdateDismissed()
        openSettings()
        runBlocking { whenever(updateRepository.check(any(), any())).thenReturn(update) }

        viewModel.onCheckForUpdatesRequested()
        runCurrent()

        assertEquals(update, viewModel.viewState.value.availableUpdate)
        assertNull(viewModel.viewState.value.settingsDialog)
        verifyBlocking(updateRepository, never()) { install(any(), any(), any(), any()) }
    }

    @Test
    fun `manual check reports current version without offering a downgrade`() {
        whenever(deviceRepository.appVersionCode).thenReturn(142)
        runBlocking {
            whenever(updateRepository.check(any(), any()))
                .thenReturn(ServerMessage.UpdateAvailable(141, "0.1.41", "/player/apk"))
        }
        openSettings()

        viewModel.onCheckForUpdatesRequested()
        runCurrent()

        assertNull(viewModel.viewState.value.availableUpdate)
        assertEquals(false, viewModel.viewState.value.settingsDialog?.isCheckingForUpdates)
        assertTrue(takeEvents().contains(PlayerEvent.ShowMessageResource(
            tech.shaffersoftworks.remotescreens.player.R.string.update_current,
        )))
    }

    @Test
    fun `failed update check allows retry and reports failure`() {
        runBlocking { whenever(updateRepository.check(any(), any())).thenThrow(IllegalStateException("offline")) }
        openSettings()

        viewModel.onCheckForUpdatesRequested()
        runCurrent()

        assertEquals(false, viewModel.viewState.value.settingsDialog?.isCheckingForUpdates)
        assertTrue(takeEvents().contains(PlayerEvent.ShowMessageResource(
            tech.shaffersoftworks.remotescreens.player.R.string.update_check_failed,
        )))
    }

    @Test
    fun `a silent banner does not wake a sleeping tablet`() {
        receive(ServerMessage.Wake(false))
        receive(ServerMessage.NotificationBanner("one", "Laundry", timeout = 10))
        assertFalse(viewModel.viewState.value.isAwake)
        assertEquals("one", viewModel.viewState.value.notificationBanner?.id)
    }

    @Test
    fun `replacing a banner cancels its old expiry`() {
        receive(ServerMessage.NotificationBanner("one", "First", timeout = 1))
        testDispatcher.scheduler.advanceTimeBy(500)
        receive(ServerMessage.NotificationBanner("two", "Second", timeout = 10))
        testDispatcher.scheduler.advanceTimeBy(600)
        runCurrent()
        assertEquals("two", viewModel.viewState.value.notificationBanner?.id)
        testDispatcher.scheduler.advanceTimeBy(10_000)
        runCurrent()
        assertNull(viewModel.viewState.value.notificationBanner)
    }

    @Test
    fun `banner action sends once and clears local state`() {
        receive(ServerMessage.NotificationBanner("one", "Laundry", buttons = listOf("Done")))
        viewModel.dismissNotificationBanner(0)
        viewModel.dismissNotificationBanner(0)
        runCurrent()
        verifyBlocking(connectionRepository) { send(PlayerMessage.NotificationAction("one", 0)) }
        assertNull(viewModel.viewState.value.notificationBanner)
    }

    @Test
    fun `banner expiry continues behind an alert`() {
        receive(ServerMessage.NotificationBanner("one", "Laundry", timeout = 1))
        receive(ServerMessage.Document(Slot.ALERT, 10, "AAAA", timeout = 0))
        testDispatcher.scheduler.advanceTimeBy(1_001)
        runCurrent()
        assertNull(viewModel.viewState.value.notificationBanner)
        assertTrue(Slot.ALERT in viewModel.viewState.value.remoteScreen.documents)
    }

    @Test
    fun `silent alert document does not wake and explicit wake does`() {
        receive(ServerMessage.Wake(false))
        receive(ServerMessage.Document(Slot.ALERT, 10, "AAAA", wakeOnShow = false))
        assertFalse(viewModel.viewState.value.isAwake)
        receive(ServerMessage.Wake(true))
        assertTrue(viewModel.viewState.value.isAwake)
    }

    @Test
    fun `back dismisses a banner without invoking a button`() {
        receive(ServerMessage.NotificationBanner("one", "Laundry"))
        viewModel.onBack()
        runCurrent()
        assertNull(viewModel.viewState.value.notificationBanner)
        verifyBlocking(connectionRepository) { send(PlayerMessage.NotificationAction("one")) }
    }

    @Test
    fun `reconnect clears the previous banner`() {
        receive(ServerMessage.NotificationBanner("one", "Laundry"))
        receive(ServerMessage.Welcome(screenId = "home"))
        assertNull(viewModel.viewState.value.notificationBanner)
    }

    @Test
    fun `alert rebuild preserves the original expiry`() {
        receive(ServerMessage.Document(Slot.ALERT, 10, "AAAA", timeout = 1))
        testDispatcher.scheduler.advanceTimeBy(500)
        receive(ServerMessage.Document(Slot.ALERT, 11, "AAAA", timeout = 0, restartTimeout = false))
        testDispatcher.scheduler.advanceTimeBy(600)
        runCurrent()
        assertFalse(Slot.ALERT in viewModel.viewState.value.remoteScreen.documents)
    }

    @Test
    fun `back to home clock does not auto-dismiss a persistent alert`() {
        receive(mainDocument())
        receive(ServerMessage.Settings(ScreenSettings(backToHomeSeconds = 1)))
        receive(ServerMessage.Document(Slot.ALERT, 10, "AAAA", timeout = 0))
        testDispatcher.scheduler.advanceTimeBy(1_100)
        runCurrent()
        assertTrue(Slot.ALERT in viewModel.viewState.value.remoteScreen.documents)
    }

    private fun takeEvents(): List<PlayerEvent> {
        val collected = mutableListOf<PlayerEvent>()
        val job = CoroutineScope(testDispatcher).launch {
            viewModel.events.collect { event -> collected += event }
        }
        runCurrent()
        job.cancel()
        runCurrent()
        return collected
    }
}
