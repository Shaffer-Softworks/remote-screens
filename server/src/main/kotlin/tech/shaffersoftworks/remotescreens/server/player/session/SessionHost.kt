package tech.shaffersoftworks.remotescreens.server.player.session

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.player.PlayerSessionContext
import tech.shaffersoftworks.remotescreens.server.render.Alert
import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.theme.Palette
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Slot
import tech.shaffersoftworks.remotescreens.shared.VarValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared mutable state and hooks that [SessionDocument], [SessionMedia], and [SessionLifecycle] use.
 * Implemented by [tech.shaffersoftworks.remotescreens.server.player.PlayerSession].
 */
internal interface SessionHost {
    val context: PlayerSessionContext
    val hello: PlayerMessage.Hello
    val device: Device
    val supportsDeflate: Boolean
    val supportsVideo: Boolean
    val supportsMap: Boolean
    val supportsMusicSearch: Boolean
    val supportsCalendarDialogs: Boolean
    val buildMutex: Mutex
    val shownDocuments: ConcurrentHashMap<Slot, ShownDocument>
    val jobs: MutableList<Job>
    val newPairingCode: () -> String
    val features: List<String>

    var lastRevision: Int
    var themeId: String
    var palette: Palette
    var pairingCode: String?
    var screenId: String?
    var previewScreen: Screen?
    var previewJob: Job?
    var currentAlert: Alert?
    /** The page the tablet last reported, baked into rebuilt main documents so they do not flash home. */
    val page: Int

    fun screen(): Screen?
    suspend fun send(message: ServerMessage)
    fun connectedVar(): VarValue
    fun deviceVars(): List<VarValue>
    fun foresee(ref: ActionRef, entity: EntityState): EntityState?
}
