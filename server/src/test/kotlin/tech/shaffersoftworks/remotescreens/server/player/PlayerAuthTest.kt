package tech.shaffersoftworks.remotescreens.server.player

import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.PlayerRecord
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerAuthTest {
    private val token = "tablet-token"
    private val paired = PlayerRecord(
        deviceId = "tablet",
        name = "Hallway",
        screenId = "home",
        tokenHash = PlayerTokens.hash(token),
    )

    @Test
    fun aMatchingTokenIsTrusted() {
        assertTrue(PlayerAuth.isTrusted(token, paired.tokenHash))
        assertFalse(PlayerAuth.isTrusted(null, paired.tokenHash))
        assertFalse(PlayerAuth.isTrusted("wrong", paired.tokenHash))
        assertFalse(PlayerAuth.isTrusted(token, null))
    }

    @Test
    fun aPairedDeviceMustPresentItsToken() {
        assertTrue(PlayerAuth.mayConnect(hello(token), paired))
        assertFalse(PlayerAuth.mayConnect(hello(null), paired))
        assertFalse(PlayerAuth.mayConnect(hello("wrong"), paired))
        assertTrue(PlayerAuth.mayConnect(hello(null), null))
        assertTrue(PlayerAuth.mayConnect(hello(null), PlayerRecord("new", "New tablet")))
    }

    private fun hello(token: String?): PlayerMessage.Hello = PlayerMessage.Hello(
        deviceId = "tablet",
        deviceName = "Hallway",
        token = token,
        widthDp = 800,
        heightDp = 480,
        density = 1f,
        appVersion = 1,
    )
}
