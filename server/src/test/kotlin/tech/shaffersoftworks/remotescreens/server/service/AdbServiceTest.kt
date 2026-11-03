package tech.shaffersoftworks.remotescreens.server.service

import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tech.shaffersoftworks.remotescreens.server.data.apk.PlayerApkRepository
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdbServiceTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val commands = mutableListOf<Pair<List<String>, String>>()
    private var output = ByteArray(0)

    private fun service() = AdbService(
        temporary.root,
        PlayerApkRepository(null),
        commandRunner = { args, input ->
            commands += args to input
            output
        },
    )

    @Test
    fun pairingUsesStdinAndValidatesBeforeExecuting() = runBlocking {
        val adb = service()
        adb.pair("192.168.1.2:30000", "123456")
        assertEquals(listOf("pair", "192.168.1.2:30000"), commands.single().first)
        assertEquals("123456\n", commands.single().second)
        assertFailsWith<IllegalArgumentException> { adb.pair("192.168.1.2:30000", "123\n456") }
        assertFailsWith<IllegalArgumentException> { adb.connect("-H:1234") }
        assertFailsWith<IllegalArgumentException> { adb.connect("tablet:65536") }
        assertEquals(1, commands.size)
    }

    @Test
    fun deviceOperationsAlwaysTargetOneSerialAndKeepBinaryOutput() = runBlocking {
        val adb = service()
        output = byteArrayOf(0, -1, 13, 10)
        assertContentEquals(output, adb.screenshot("tablet:1234"))
        assertEquals(listOf("-s", "tablet:1234", "exec-out", "screencap", "-p"), commands.single().first)
        adb.restart("tablet:1234")
        assertEquals("force-stop", commands[1].first[4])
        assertEquals("start", commands[2].first[4])
        assertFailsWith<IllegalArgumentException> { adb.logs("-e") }
        assertFailsWith<IllegalArgumentException> { adb.logs("tablet;reboot") }
        assertEquals(3, commands.size)
    }

    @Test
    fun associationsSurviveRestartAndCanBeRemoved() = runBlocking {
        val adb = service()
        adb.associate("player-one", "tablet:1234")
        output = "List of devices attached\ntablet:1234 device model:Tablet\nother offline\n".encodeToByteArray()
        val restored = service().status()
        assertEquals("tablet:1234", restored.associations["player-one"])
        assertEquals(listOf("device", "offline"), restored.devices.map { it.state })
        adb.associate("player-one", "")
        assertEquals(emptyMap(), service().status().associations)
    }

    @Test
    fun missingApkDoesNotRunAnInstall() = runBlocking {
        assertFailsWith<IllegalStateException> { service().install("tablet:1234") }
        assertEquals(emptyList(), commands)
    }
}
