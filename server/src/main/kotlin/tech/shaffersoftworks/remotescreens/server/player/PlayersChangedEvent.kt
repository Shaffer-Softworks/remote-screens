package tech.shaffersoftworks.remotescreens.server.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.FireEvent
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The integration refreshes its tablets when this fires, instead of waiting for its next poll. */
const val PLAYERS_CHANGED_EVENT = "remote_screens_players_changed"
private val PLAYERS_CHANGED_DEBOUNCE = 500.milliseconds

/** A tablet back within this long (an app update, a replaced socket) is not reported offline in between. */
private val DISCONNECT_GRACE = 5.seconds

/**
 * Fires [PLAYERS_CHANGED_EVENT] on Home Assistant when a tablet connects, disconnects, or changes what the
 * integration shows as a state. The integration's 30 s poll still catches up when firing fails.
 */
class PlayersChangedEvent(
    private val homeAssistant: HomeAssistant,
    private val scope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(PlayersChangedEvent::class.java)
    private val changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            changes.debounce(PLAYERS_CHANGED_DEBOUNCE).collect { fire() }
        }
    }

    fun notifyChanged() {
        changes.tryEmit(Unit)
    }

    /** Reports a disconnect only when [isBack] still reads false after [DISCONNECT_GRACE]. */
    fun notifyDisconnected(isBack: () -> Boolean) {
        scope.launch {
            delay(DISCONNECT_GRACE)

            if (!isBack()) notifyChanged()
        }
    }

    private suspend fun fire() {
        runCatching {
            homeAssistant.send(FireEvent(PLAYERS_CHANGED_EVENT))
        }.onFailure { exception ->
            currentCoroutineContext().ensureActive()
            logger.debug("{} not fired: {}", PLAYERS_CHANGED_EVENT, exception.message)
        }
    }
}
