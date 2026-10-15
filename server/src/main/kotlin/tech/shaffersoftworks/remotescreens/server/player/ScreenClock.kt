package tech.shaffersoftworks.remotescreens.server.player

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.i18n.LanguageRepository
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.shared.ScreenSettings
import tech.shaffersoftworks.remotescreens.shared.VarValue
import tech.shaffersoftworks.remotescreens.shared.isDarkAt
import java.time.ZoneId
import java.time.ZonedDateTime

/** Time as the screens show it: in Home Assistant's time zone and the screens' language. */
class ScreenClock(
    private val homeAssistant: HomeAssistant,
    private val languageRepository: LanguageRepository,
) {
    fun zone(): ZoneId =
        runCatching { ZoneId.of(homeAssistant.config.value.timeZone) }.getOrDefault(ZoneId.systemDefault())

    fun now(): ZonedDateTime = ZonedDateTime.now(zone())

    fun clockVars(): List<VarValue> {
        val now = now()
        val i18n = languageRepository.current()
        return listOf(
            VarValue.string(Vars.TIME, i18n.time(now)),
            VarValue.string(Vars.DATE, i18n.date(now)),
            VarValue.string(Vars.DAY, i18n.day(now)),
        )
    }

    /** The theme a screen wears now: its dark one in dark mode or on a dark night, else its day one. */
    fun themeIdFor(settings: ScreenSettings): String =
        if (settings.isDarkAt(now().toLocalTime())) settings.darkTheme else settings.theme
}
