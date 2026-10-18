package tech.shaffersoftworks.remotescreens.server.config

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerConfigTest {
    @Test
    fun mapTilesNeedEveryPlaceholder() {
        val default = ServerConfig.OPENSTREETMAP_TILE_URL
        assertEquals(default, ServerConfig.mapTileUrlOrDefault(null))
        assertEquals(default, ServerConfig.mapTileUrlOrDefault(""))
        assertEquals(default, ServerConfig.mapTileUrlOrDefault("https://tiles.example/{z}/{x}.png"))
        assertEquals(default, ServerConfig.mapTileUrlOrDefault("file:///{z}/{x}/{y}"))
        assertEquals(
            "https://t.example/{z}/{x}/{y}.png?key=k",
            ServerConfig.mapTileUrlOrDefault(" https://t.example/{z}/{x}/{y}.png?key=k "),
        )
    }

    @Test
    fun mapTilesComeFromTheAddonOptions() {
        val dir = Files.createTempDirectory("config").toFile()
        File(
            dir,
            "options.json",
        ).writeText("""{"map_tiles": "https://t.example/{z}/{x}/{y}.png"}""")
        val env = mapOf("REMOTE_SCREENS_DEMO" to "1", "REMOTE_SCREENS_DATA" to dir.path)
        val override = env + ("REMOTE_SCREENS_MAP_TILES" to "https://e.example/{z}/{x}/{y}")

        assertEquals(
            "https://t.example/{z}/{x}/{y}.png",
            ServerConfig.fromEnvironment(env).mapTileUrl,
        )
        assertEquals(
            "https://e.example/{z}/{x}/{y}",
            ServerConfig.fromEnvironment(override).mapTileUrl,
        )
    }

    @Test
    fun ingressComesFromTheSupervisorNetwork() {
        listOf("172.30.32.2", "172.30.32.1", "172.30.33.5", "::ffff:172.30.32.2")
            .forEach { address -> assertTrue(ServerConfig.isIngressPeer(address), address) }
        listOf("10.11.0.5", "172.30.34.1", "172.17.0.1", "127.0.0.1", "", "localhost")
            .forEach { address -> assertFalse(ServerConfig.isIngressPeer(address), address) }
    }
}
