package tech.shaffersoftworks.remotescreens.server.render

import io.ktor.server.plugins.BadRequestException
import tech.shaffersoftworks.remotescreens.server.service.validation.ThemeValidator
import tech.shaffersoftworks.remotescreens.shared.Theme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemesTest {
    @Test
    fun theDefaultIsTheRpiPanel() {
        assertEquals(0xFF333333.toInt(), Themes.RPI[Role.CARD])
        assertEquals(0xFF14E1BA.toInt(), Themes.RPI[Pastel.TEAL])
        assertTrue(Themes.RPI.dark)
        assertFalse(Themes.LIGHT.dark)
    }

    @Test
    fun everyBuiltInThemeHasEveryColour() {
        for ((id, v) in Themes.BUILTIN) {
            val all = Themes.all(emptyList()).first { it.id == id }
            assertEquals(Role.entries.size, all.roles.size, id)
            assertEquals(Pastel.entries.size, all.pastels.size, id)
            assertEquals(Themes.hex(v.second[Role.INK]), all.roles["ink"])
        }
    }

    @Test
    fun aCustomThemeLaysItsColoursOverItsBase() {
        val mine = Theme("mine", "Mine", base = "light", roles = mapOf("card" to "#123456", "nope" to "#FFFFFF"), pastels = mapOf("teal" to "#80FF0000"))
        val p = Themes.resolve("mine", listOf(mine))
        assertEquals(0xFF123456.toInt(), p[Role.CARD])
        assertEquals(0x80FF0000.toInt(), p[Pastel.TEAL])
        assertEquals(Themes.LIGHT[Role.INK], p[Role.INK])
        assertEquals("#80FF0000", Themes.all(listOf(mine)).last().pastels["teal"])
    }

    @Test
    fun anUnknownThemeIsTheDefault() {
        assertEquals(Themes.RPI[Role.PAGE], Themes.resolve("gone", emptyList())[Role.PAGE])
        assertEquals(Themes.RPI[Role.PAGE], Themes.resolve(null, emptyList())[Role.PAGE])
    }

    @Test
    fun hexRoundTrips() {
        assertEquals(0xFFABCDEF.toInt(), Themes.parse("#abcdef"))
        assertEquals("#ABCDEF", Themes.hex(0xFFABCDEF.toInt()))
        assertEquals("#CC000000", Themes.hex(Themes.RPI[Role.SCRIM]))
        assertEquals(null, Themes.parse("red"))
    }

    @Test
    fun aStoredThemeIsChecked() {
        assertFailsWith<BadRequestException> { ThemeValidator.validate(Theme("rpi", "Mine"), emptyList()) }
        assertFailsWith<BadRequestException> { ThemeValidator.validate(Theme("Bad Id", "Mine"), emptyList()) }
        assertFailsWith<BadRequestException> { ThemeValidator.validate(Theme("mine", "Mine", base = "mine"), emptyList()) }
        assertFailsWith<BadRequestException> { ThemeValidator.validate(Theme("mine", "Mine", roles = mapOf("card" to "blue")), emptyList()) }
        val full = (1..Theme.MAX_THEMES).map { Theme("t$it", "T$it") }
        assertFailsWith<BadRequestException> { ThemeValidator.validate(Theme("mine", "Mine"), full) }
        ThemeValidator.validate(Theme("t1", "Again"), full)
        val ok = ThemeValidator.validate(Theme("mine", "  ", roles = mapOf("CARD" to "#abcdef", "nope" to "#000000")), emptyList())
        assertEquals("mine", ok.name)
        assertEquals(mapOf("card" to "#ABCDEF"), ok.roles)
    }

    @Test
    fun onlyThemesHoldsHexColours() {
        val dir = File("src/main/kotlin/tech/shaffersoftworks/remotescreens/server/render")
        // An opaque mask such as `argb or 0xFF000000.toInt()` is arithmetic, not a colour.
        val hex = Regex("0x[0-9A-Fa-f]{8}(?!\\.toInt\\(\\))")
        val hexInventory = setOf("BuiltinThemes.kt", "Themes.kt")
        val offenders = dir.listFiles()!!.filter {
            it.name !in hexInventory && it.name.endsWith(".kt") && hex.containsMatchIn(it.readText())
        }
        assertEquals(emptyList(), offenders.map { it.name })
    }
}
