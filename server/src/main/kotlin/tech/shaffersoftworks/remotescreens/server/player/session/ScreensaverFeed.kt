package tech.shaffersoftworks.remotescreens.server.player.session

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.screensaver.ScreensaverPhotos
import tech.shaffersoftworks.remotescreens.server.data.screensaver.ScreensaverSlide
import tech.shaffersoftworks.remotescreens.server.render.widgets.weatherIconPng
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.ScreensaverSettings
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.Base64
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private const val WEATHER_POLL_SECONDS = 5
private const val IDLE_POLL_SECONDS = 1
private const val EMPTY_RETRY_SECONDS = 30
private const val PLAYLIST_REFRESH_HOURS = 1

/**
 * Sends screensaver photos and weather for one tablet. One picture is ready while the dashboard is up.
 * While the screensaver is showing, the next picture is sent before the tablet needs it.
 */
class ScreensaverFeed(
    private val photos: ScreensaverPhotos,
    private val screen: () -> Screen?,
    private val widthPx: Int,
    private val heightPx: Int,
    private val send: suspend (ServerMessage) -> Unit,
    private val states: StateFlow<Map<String, EntityState>>,
) {
    private val logger = LoggerFactory.getLogger(ScreensaverFeed::class.java)
    private val showing = MutableStateFlow(false)

    fun setShowing(isShowing: Boolean) {
        showing.value = isShowing
    }

    suspend fun run() {
        coroutineScope {
            launch { watchWeather() }
            launch { watchPhotos() }
        }
    }

    private suspend fun watchWeather() {
        var lastKey = ""

        while (true) {
            val settings = screen()?.settings?.screensaver

            if (settings == null || !settings.enabled) {
                lastKey = ""
            } else {
                val message = weatherMessage(settings = settings, entities = states.value)

                if (message != null) {
                    val key = "${message.condition}|${message.temperature}|${message.humidity}"

                    if (key != lastKey) {
                        lastKey = key
                        send(message)
                    }
                }
            }

            delay(WEATHER_POLL_SECONDS.seconds)
        }
    }

    private suspend fun watchPhotos() {
        var playlist = emptyList<ScreensaverSlide>()
        var index = 0
        var refreshedAt = 0L
        var lastSentId: String? = null

        while (true) {
            val settings = screen()?.settings?.screensaver?.takeIf { it.enabled }

            if (settings == null) {
                playlist = emptyList()
                lastSentId = null
                delay(EMPTY_RETRY_SECONDS.seconds)
            } else {
                val now = System.currentTimeMillis()
                val refreshMs = PLAYLIST_REFRESH_HOURS.hours.inWholeMilliseconds

                if (playlist.isEmpty() || now - refreshedAt >= refreshMs) {
                    playlist = photos.list(settings).shuffled()
                    index = 0
                    refreshedAt = now
                    lastSentId = null
                }

                if (playlist.isEmpty()) {
                    delay(EMPTY_RETRY_SECONDS.seconds)
                } else {
                    val wantIndex = if (showing.value) index + 1 else index
                    val slide = playlist[wantIndex % playlist.size]

                    if (slide.id != lastSentId) {
                        sendPhoto(settings = settings, slide = slide)
                        lastSentId = slide.id
                    }

                    if (showing.value) {
                        delay(settings.seconds.seconds)
                        index = (index + 1) % playlist.size
                    } else {
                        delay(IDLE_POLL_SECONDS.seconds)
                    }
                }
            }
        }
    }

    private suspend fun sendPhoto(settings: ScreensaverSettings, slide: ScreensaverSlide) {
        val jpeg = photos.jpeg(settings = settings, slide = slide, widthPx = widthPx, heightPx = heightPx) ?: return

        send(ServerMessage.ScreensaverPhoto(id = slide.id, data = Base64.getEncoder().encodeToString(jpeg)))
        logger.debug("Screensaver photo {}", slide.id)
    }

    private fun weatherMessage(
        settings: ScreensaverSettings,
        entities: Map<String, EntityState>,
    ): ServerMessage.ScreensaverWeather? {
        val entity = settings.weatherEntity?.let { entities[it] }
        val temperature = entity?.weather?.temperature ?: return null
        val unit = entity.weather.temperatureUnit.orEmpty()
        val text = "${temperature.roundToInt()}$unit"
        val humidity = entity.weather.humidity?.let { "${it.roundToInt()}%" }.orEmpty()
        val iconBytes = weatherIconPng(condition = entity.state)
        val icon = iconBytes?.let { Base64.getEncoder().encodeToString(it) }.orEmpty()

        return ServerMessage.ScreensaverWeather(
            condition = entity.state,
            temperature = text,
            humidity = humidity,
            icon = icon,
        )
    }
}
