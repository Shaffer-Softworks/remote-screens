package dev.remotescreens.player.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    @Test
    fun `adds the add-on port when none is given`() {
        assertEquals("192.168.1.10:8100", normalizeServerAddress("192.168.1.10"))
    }

    @Test
    fun `keeps a given port and strips scheme and slash`() {
        assertEquals("homeassistant.local:9000", normalizeServerAddress("  http://homeassistant.local:9000/ "))
        assertEquals("10.0.0.5:8100", normalizeServerAddress("ws://10.0.0.5:8100"))
    }

    @Test
    fun `nothing typed is no address`() {
        assertNull(normalizeServerAddress("   "))
        assertNull(normalizeServerAddress("http://"))
    }
}
