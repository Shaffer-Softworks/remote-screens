package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import tech.shaffersoftworks.remotescreens.shared.ScreenSettings
import tech.shaffersoftworks.remotescreens.shared.ScreensaverSettings
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class ScreensaverClockTest : PlayerViewModelTestBase() {
    @Test
    fun theScreensaverStaysAwakeUntilItsOwnTimeout() {
        receive(screensaverSettings(minutes = 1))
        testDispatcher.scheduler.advanceTimeBy(61_000.milliseconds)

        assertTrue(viewModel.viewState.value.isScreensaver)
        assertTrue(viewModel.viewState.value.isAwake)
        verify(screenPowerRepository, never()).turnScreenOff()

        testDispatcher.scheduler.advanceTimeBy(61_000.milliseconds)

        assertFalse(viewModel.viewState.value.isAwake)
        assertFalse(viewModel.viewState.value.isScreensaver)
        verify(screenPowerRepository).turnScreenOff()
    }

    @Test
    fun aTimeoutOfZeroDoesNotTurnTheScreenOff() {
        receive(screensaverSettings(minutes = 0))
        testDispatcher.scheduler.advanceTimeBy(61_000.milliseconds)

        testDispatcher.scheduler.advanceTimeBy(10.minutes)

        assertTrue(viewModel.viewState.value.isScreensaver)
        assertTrue(viewModel.viewState.value.isAwake)
        verify(screenPowerRepository, never()).turnScreenOff()
    }

    @Test
    fun aTouchLeavesTheScreensaver() {
        receive(screensaverSettings(minutes = 1))
        testDispatcher.scheduler.advanceTimeBy(61_000.milliseconds)

        viewModel.onUserInteraction()

        assertFalse(viewModel.viewState.value.isScreensaver)
        assertTrue(viewModel.viewState.value.isAwake)
    }

    @Test
    fun settingsThatAreNotTheTimersDoNotRestartTheClock() {
        val screensaver = ScreensaverSettings(enabled = true, minutes = 1, weatherEntity = "weather.home")

        receive(ServerMessage.Settings(ScreenSettings(standbyMinutes = 1, screensaver = screensaver)))
        testDispatcher.scheduler.advanceTimeBy(50_000.milliseconds)

        receive(
            ServerMessage.Settings(
                ScreenSettings(
                    standbyMinutes = 1,
                    screensaver = screensaver.copy(weatherEntity = "weather.other"),
                ),
            ),
        )

        testDispatcher.scheduler.advanceTimeBy(11_000.milliseconds)

        assertTrue(viewModel.viewState.value.isScreensaver)
        assertTrue(viewModel.viewState.value.isAwake)
    }

    private fun screensaverSettings(minutes: Int): ServerMessage.Settings = ServerMessage.Settings(
        settings = ScreenSettings(
            standbyMinutes = 1,
            screensaver = ScreensaverSettings(enabled = true, minutes = minutes),
        ),
    )
}
