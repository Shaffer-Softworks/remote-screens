package dev.remotescreens.server.player

import dev.remotescreens.server.data.homeassistant.HomeAssistant
import dev.remotescreens.server.i18n.LanguageRepository
import dev.remotescreens.server.render.V
import dev.remotescreens.shared.ScreenSettings
import dev.remotescreens.shared.VarValue
import dev.remotescreens.shared.isDarkAt
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
            VarValue.string(V.TIME, i18n.time(now)),
            VarValue.string(V.DATE, i18n.date(now)),
            VarValue.string(V.DAY, i18n.day(now)),
        )
    }

    /** The theme a screen wears now: its dark one in dark mode or on a dark night, else its day one. */
    fun themeIdFor(settings: ScreenSettings): String =
        if (settings.isDarkAt(now().toLocalTime())) settings.darkTheme else settings.theme
}
