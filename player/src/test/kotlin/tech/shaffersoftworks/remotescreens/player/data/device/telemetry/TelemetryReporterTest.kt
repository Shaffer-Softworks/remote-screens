package tech.shaffersoftworks.remotescreens.player.data.device.telemetry

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.shaffersoftworks.remotescreens.shared.DeviceTelemetry
import tech.shaffersoftworks.remotescreens.shared.LocationTelemetry
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryReporterTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val source = FakeTelemetrySource()
    private val reporter = TelemetryReporter(source, testDispatcher)
    private val sent = mutableListOf<PlayerMessage.Telemetry>()

    @After
    fun tearDown() {
        reporter.stop()
    }

    private fun start(isEnabled: Boolean = true, wantsLocation: Boolean = false) {
        reporter.update(testScope, isEnabled, wantsLocation) { message -> sent += message }
    }

    @Test
    fun reportsAtOnceThenEveryInterval() {
        start()
        testScope.runCurrent()
        assertEquals(1, sent.size)

        testScope.advanceTimeBy(REPORT_INTERVAL_MS)
        testScope.runCurrent()

        assertEquals(2, sent.size)
    }

    @Test
    fun aChangeReportsBeforeTheInterval() {
        start()
        testScope.runCurrent()
        testScope.advanceTimeBy(MIN_REPORT_GAP_MS + 1)

        source.changes.tryEmit(Unit)
        testScope.runCurrent()

        assertEquals(2, sent.size)
    }

    @Test
    fun quickChangesAreFoldedIntoOneReport() {
        start()
        testScope.runCurrent()

        repeat(times = 5) { source.changes.tryEmit(Unit) }
        testScope.runCurrent()
        assertEquals(1, sent.size)

        testScope.advanceTimeBy(MIN_REPORT_GAP_MS + 1)
        testScope.runCurrent()
        assertEquals(2, sent.size)
    }

    @Test
    fun disabledReportsNothingAndStopsTheSensors() {
        start()
        testScope.runCurrent()

        start(isEnabled = false)
        testScope.advanceTimeBy(REPORT_INTERVAL_MS * 2)
        testScope.runCurrent()

        assertEquals(1, sent.size)
        assertFalse(reporter.isRunning)
        assertFalse(source.isStarted)
    }

    @Test
    fun locationNeedsBothTheSettingAndThePermission() {
        source.hasPermission = false
        start(wantsLocation = true)
        testScope.runCurrent()
        assertEquals(null, sent.last().telemetry.location)

        source.hasPermission = true
        start(wantsLocation = true)
        testScope.runCurrent()

        assertTrue(source.isLocationShared)
        assertEquals(52.0, sent.last().telemetry.location?.latitude)
    }

    @Test
    fun permissionGrantedWhileRunningIsPickedUpOnTheNextReport() {
        source.hasPermission = false
        start(wantsLocation = true)
        testScope.runCurrent()
        assertEquals(null, sent.last().telemetry.location)

        source.hasPermission = true
        testScope.advanceTimeBy(REPORT_INTERVAL_MS)
        testScope.runCurrent()

        assertTrue(source.isLocationShared)
        assertEquals(52.0, sent.last().telemetry.location?.latitude)
    }

    private class FakeTelemetrySource : TelemetrySource {
        override val changes = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        var hasPermission = true
        var isStarted = false
        var isLocationShared = false

        override fun start(isLocationShared: Boolean) {
            isStarted = true
            this.isLocationShared = isLocationShared
        }

        override fun stop() {
            isStarted = false
            isLocationShared = false
        }

        override fun hasLocationPermission(): Boolean = hasPermission

        override fun snapshot(isLocationShared: Boolean): DeviceTelemetry = DeviceTelemetry(
            location = LocationTelemetry(latitude = 52.0, longitude = 4.0, fixedAt = 0L).takeIf { isLocationShared },
        )
    }
}
