package dev.remotescreens.player.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import dev.remotescreens.player.data.connection.ConnectionRepository
import dev.remotescreens.player.data.connection.ConnectionStatus
import dev.remotescreens.player.data.device.BatteryStatus
import dev.remotescreens.player.data.device.DeviceRepository
import dev.remotescreens.player.data.device.DisplayInfo
import dev.remotescreens.player.data.device.ScreenPowerRepository
import dev.remotescreens.player.data.discovery.DiscoveryRepository
import dev.remotescreens.player.data.image.ImageDecoder
import dev.remotescreens.player.data.preferences.PlayerPreferencesRepository
import dev.remotescreens.player.data.update.UpdateRepository
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.PlayerMessage
import dev.remotescreens.shared.ScreenSettings
import dev.remotescreens.shared.ServerMessage
import dev.remotescreens.shared.Slot
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    private val serverMessages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 16)
    private val connectedServer = MutableStateFlow<String?>("10.0.0.5:8100")

    private val connectionRepository: ConnectionRepository = mock {
        on { status } doReturn connectionStatus
        on { messages } doReturn serverMessages
        on { server } doReturn connectedServer
    }
    private val preferencesRepository: PlayerPreferencesRepository = mock {
        on { server } doReturn flowOf("10.0.0.5:8100")
        on { token } doReturn flowOf("token")
        on { screenName } doReturn flowOf("Kitchen")
    }
    private val deviceRepository: DeviceRepository = mock {
        on { batteryStatus() } doReturn BatteryStatus(levelPercent = 50, isCharging = true)
    }
    private val discoveryRepository: DiscoveryRepository = mock {
        on { discoverServers() } doReturn emptyFlow()
    }
    private val screenPowerRepository: ScreenPowerRepository = mock()

    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(),
            preferencesRepository = preferencesRepository,
            connectionRepository = connectionRepository,
            discoveryRepository = discoveryRepository,
            updateRepository = mock<UpdateRepository>(),
            deviceRepository = deviceRepository,
            screenPowerRepository = screenPowerRepository,
            imageDecoder = mock<ImageDecoder>(),
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
    fun `a new size asks for new documents`() {
        viewModel.onDisplayMeasured(DisplayInfo(widthDp = 800, heightDp = 480))
        viewModel.onDisplayMeasured(DisplayInfo(widthDp = 480, heightDp = 800))

        verify(connectionRepository).reconnect()
    }

    @Test
    fun `connecting without a document shows the server`() {
        runCurrent()
        connectionStatus.value = ConnectionStatus.CONNECTING
        runCurrent()

        assertEquals(InfoPanelState.Connecting("10.0.0.5:8100"), viewModel.viewState.value.infoPanel)
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

        viewModel.onDocumentAction(Slot.MAIN, revision = 7, metadata = ActionRef("_", "page", "2").encode())
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

        verifyBlocking(connectionRepository) { send(PlayerMessage.Action(Slot.MAIN, 7, metadata)) }
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
    fun `swipes are ignored while a card is open`() {
        receive(mainDocument())
        receive(ServerMessage.Document(slot = Slot.OVERLAY, rev = 1, data = "AAAA"))

        viewModel.onSwipe(isNext = true)

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
        testDispatcher.scheduler.advanceTimeBy(50_000)

        receive(ServerMessage.Settings(settings))
        testDispatcher.scheduler.advanceTimeBy(11_000)

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
}
