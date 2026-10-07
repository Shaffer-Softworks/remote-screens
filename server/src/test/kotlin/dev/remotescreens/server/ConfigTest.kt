package dev.remotescreens.server

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigTest {
    @Test
    fun mapTilesNeedEveryPlaceholder() {
        assertEquals(Config.OSM_TILES, Config.mapTiles(null))
        assertEquals(Config.OSM_TILES, Config.mapTiles(""))
        assertEquals(Config.OSM_TILES, Config.mapTiles("https://tiles.example/{z}/{x}.png"))
        assertEquals(Config.OSM_TILES, Config.mapTiles("file:///{z}/{x}/{y}"))
        assertEquals("https://t.example/{z}/{x}/{y}.png?key=k", Config.mapTiles(" https://t.example/{z}/{x}/{y}.png?key=k "))
    }

    @Test
    fun mapTilesComeFromTheAddonOptions() {
        val dir = Files.createTempDirectory("config").toFile()
        File(dir, "options.json").writeText("""{"map_tiles": "https://t.example/{z}/{x}/{y}.png"}""")
        val env = mapOf("REMOTE_SCREENS_DEMO" to "1", "REMOTE_SCREENS_DATA" to dir.path)
        assertEquals("https://t.example/{z}/{x}/{y}.png", Config.fromEnv(env).mapTiles)
        assertEquals("https://e.example/{z}/{x}/{y}", Config.fromEnv(env + ("REMOTE_SCREENS_MAP_TILES" to "https://e.example/{z}/{x}/{y}")).mapTiles)
    }
}
