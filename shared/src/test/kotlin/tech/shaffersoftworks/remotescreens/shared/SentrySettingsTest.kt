package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SentrySettingsTest {
    @Test
    fun reportingIsOffForOldLayoutsAndSettingsMessages() {
        assertFalse(Json.decodeFromString<Preferences>("{}").sentry.enabled)
        val message = Protocol.decodeServer("""{"type":"settings","settings":{}}""") as ServerMessage.Settings
        assertFalse(message.sentry.enabled)
    }

    @Test
    fun settingsRoundTripThroughThePlayerProtocol() {
        val settings = SentrySettings(true, "https://key@sentry.example/123", "wall-tablets", false)
        val message = ServerMessage.Settings(ScreenSettings(), settings)
        assertEquals(message, Protocol.decodeServer(Protocol.encode(message)))
    }

    @Test
    fun publicAndSelfHostedDsnsAreAcceptedAndTrimmed() {
        val settings = SentrySettings(true, " https://key@sentry.example/prefix/123 ", " staging ")
        assertEquals("https://key@sentry.example/prefix/123", settings.validated().dsn)
        assertEquals("staging", settings.validated().environment)
        SentrySettings(true, "http://key@192.168.1.2:9000/123").validated()
    }

    @Test
    fun malformedOrPrivateDsnsAndMissingEnabledDsnsAreRejected() {
        for (dsn in listOf("", "not a uri", "https://host/123", "https://key:secret@host/123",
            "ftp://key@host/123", "https://key@host/abc", "https://key@host/123?token=secret")) {
            assertFailsWith<IllegalArgumentException>(dsn) { SentrySettings(true, dsn).validated() }
        }
        assertFailsWith<IllegalArgumentException> {
            SentrySettings(environment = "has spaces").validated()
        }
    }
}
