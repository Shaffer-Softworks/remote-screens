package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionRepository
import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionStatus
import tech.shaffersoftworks.remotescreens.player.data.device.BatteryStatus
import tech.shaffersoftworks.remotescreens.player.data.device.DeviceRepository
import tech.shaffersoftworks.remotescreens.player.data.device.FaceWakeMonitor
import tech.shaffersoftworks.remotescreens.player.data.device.ProximityWakeMonitor
import tech.shaffersoftworks.remotescreens.player.data.device.ScreenPowerRepository
import tech.shaffersoftworks.remotescreens.player.data.device.intercom.IntercomCoordinator
import tech.shaffersoftworks.remotescreens.player.data.device.telemetry.LocationAccuracy
import tech.shaffersoftworks.remotescreens.player.data.device.telemetry.TelemetryReporter
import tech.shaffersoftworks.remotescreens.player.data.diagnostics.AppLog
import tech.shaffersoftworks.remotescreens.player.data.diagnostics.CrashReporter
import tech.shaffersoftworks.remotescreens.player.data.discovery.DiscoveryRepository
import tech.shaffersoftworks.remotescreens.player.data.image.ImageDecoder
import tech.shaffersoftworks.remotescreens.player.data.kiosk.KioskManager
import tech.shaffersoftworks.remotescreens.player.data.kiosk.KioskState
import tech.shaffersoftworks.remotescreens.player.data.preferences.PlayerPreferencesRepository
import tech.shaffersoftworks.remotescreens.player.data.update.UpdateRepository
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/** Mocks and a [PlayerViewModel] shared by the view model test classes. Messages arrive on connection 1. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class PlayerViewModelTestBase {
    protected val testDispatcher = StandardTestDispatcher()
    protected val connectionState = MutableStateFlow(ConnectionRepository.ConnectionState(null, ConnectionStatus.IDLE))
    protected val consecutiveFailures = MutableStateFlow(0)
    protected val serverMessages = MutableSharedFlow<ConnectionRepository.ReceivedMessage>(extraBufferCapacity = 16)
    protected val connectedServer = MutableStateFlow<String?>("10.0.0.5:8100")

    protected val connectionRepository: ConnectionRepository = mock {
        on { connectionState } doReturn connectionState
        on { receivedMessages } doReturn serverMessages
        on { server } doReturn connectedServer
        on { consecutiveFailures } doReturn consecutiveFailures
    }
    protected val isKioskEnabled = MutableStateFlow(false)
    protected val isKioskSuppressed = MutableStateFlow(false)
    protected val preferencesRepository: PlayerPreferencesRepository = mock {
        on { server } doReturn flowOf("10.0.0.5:8100")
        on { token } doReturn flowOf("token")
        on { screenName } doReturn flowOf("Kitchen")
        on { deviceId() } doReturn "tablet-1"
        on { isKioskEnabled } doReturn isKioskEnabled
        on { isKioskSuppressed } doReturn isKioskSuppressed
    }
    protected val kioskManager: KioskManager = mock {
        on { lockTaskState() } doReturn KioskState.OFF
        on { isDeviceOwner() } doReturn false
    }
    protected val intercomCoordinator: IntercomCoordinator = mock {
        on { hasMicrophone() } doReturn false
    }
    protected val deviceRepository: DeviceRepository = mock {
        on { batteryStatus() } doReturn BatteryStatus(levelPercent = 50, isCharging = true)
    }
    protected val discoveryRepository: DiscoveryRepository = mock {
        on { discoverServers() } doReturn emptyFlow()
    }
    protected val screenPowerRepository: ScreenPowerRepository = mock()
    protected val proximityWakeMonitor: ProximityWakeMonitor = mock {
        on { isUsable() } doReturn false
    }
    protected val faceWakeMonitor: FaceWakeMonitor = mock {
        on { isUsable() } doReturn false
        on { hasCameraPermission() } doReturn false
        on { hasFrontCamera() } doReturn true
        on { isLowRamDevice() } doReturn false
    }

    protected val telemetryReporter: TelemetryReporter = mock {
        on { hasLocationPermission() } doReturn false
    }
    protected val locationAccuracy: LocationAccuracy = mock()

    protected val updateRepository: UpdateRepository = mock()
    protected val crashReporter: CrashReporter = mock()
    protected val appLog: AppLog = mock()

    protected lateinit var viewModel: PlayerViewModel

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
            crashReporter = crashReporter,
            appLog = appLog,
            locationAccuracy = locationAccuracy,
            intercomCoordinator = intercomCoordinator,
            haImageLoaderQualifier = mock(),
        )
    }

    protected fun runCurrent() = testDispatcher.scheduler.runCurrent()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected fun receive(message: ServerMessage, connectionId: Long = 1L) {
        runCurrent()
        serverMessages.tryEmit(ConnectionRepository.ReceivedMessage(connectionId, message))
        runCurrent()
    }
}
