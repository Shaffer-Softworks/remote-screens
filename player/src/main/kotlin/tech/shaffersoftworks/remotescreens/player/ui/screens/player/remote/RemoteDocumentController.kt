@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.remote.core.CalendarSystemClock
import androidx.compose.remote.core.RemoteClock
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.remote.player.view.RemoteComposePlayer
import tech.shaffersoftworks.remotescreens.shared.Slot
import tech.shaffersoftworks.remotescreens.shared.VarType
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.io.ByteArrayInputStream
import java.time.ZoneId
import java.util.TimeZone

/** The user variable each main document reads to know which page to draw. */
const val PAGE_VARIABLE = "page"

/**
 * Owns the Remote Compose renderers and brings them
 * in line with a [RemoteScreenState]: each new document is loaded once, then only changed values and bitmaps are set.
 *
 * Main uses a front/back pair so a rebuild can load off-screen (with PAGE set) and swap — otherwise the tablet
 * blanks to the theme page colour or flashes home until PAGE sticks.
 */
internal class RemoteDocumentController(
    context: Context,
    createPlayer: (Context) -> RemoteComposePlayer = { RemoteComposePlayer(it) },
) {
    private val players: Map<Slot, RemoteComposePlayer> = Slot.entries.associateWith { createPlayer(
        context,
    ) }
    /** Inactive MAIN buffer; [mainFrontIsPrimary] says whether [players] MAIN or this view is showing. */
    val mainBackPlayer = createPlayer(context)
    var mainFrontIsPrimary by mutableStateOf(true)
        private set
    private val loadedDocuments = mutableMapOf<Slot, SlotDocument>()
    private var appliedValues: Map<String, VarValue> = emptyMap()
    private var appliedBitmaps: Map<String, Bitmap> = emptyMap()
    private var appliedPage: Int? = null
    private var appliedTimeZone: String? = null
    /** After a MAIN rebuild into the back buffer, swap once PAGE has been drawn off-screen. */
    private var pendingMainSwap: SlotDocument? = null
    /** Scroll offsets copied off the document a rebuild is about to replace. */
    private var pendingMainScroll: List<DocumentScroll.Mark> = emptyList()
    /** First MAIN document: draw once at alpha 0 so PAGE sticks before it is shown. */
    private var pendingMainFirstReveal: SlotDocument? = null
    private var mainSwapPosted = false
    private var mainFirstPosted = false

    /** A tap in a document: its slot, the revision of the document, and the tap's metadata. */
    var onAction: (slot: Slot, revision: Int, metadata: String) -> Unit = { _, _, _ -> }

    init {
        players.forEach { (slot, player) ->
            player.visibility = if (slot == Slot.MAIN) View.INVISIBLE else View.GONE
        }
        mainBackPlayer.visibility = View.GONE
    }

    fun primaryPlayer(slot: Slot): RemoteComposePlayer = players.getValue(slot)

    fun beforeDraw() {
        for (slot in loadedDocuments.keys) fullLayoutIfDirty(documentPlayer(slot))
    }

    fun dispose() {
        // Invalidate queued reveal callbacks when this screen leaves composition.
        loadedDocuments.clear()
        pendingMainSwap = null
        pendingMainFirstReveal = null
    }

    fun render(state: RemoteScreenState) {
        loadedDocuments.keys.retainAll(state.documents.keys)
        if (Slot.MAIN !in state.documents) {
            pendingMainSwap = null
            pendingMainFirstReveal = null
            pendingMainScroll = emptyList()
        }
        val newlyLoaded = loadNewDocuments(state)
        applyChangedValues(state.values, excludeSlots = newlyLoaded)
        applyChangedBitmaps(state.bitmaps, excludeSlots = newlyLoaded)
        appliedValues = state.values
        appliedBitmaps = state.bitmaps

        syncMainPage(state, newlyLoaded)
        showSlots(state)
        invalidateDocuments()
        pendingMainSwap?.let { document -> revealMainSwap(document) }
        pendingMainFirstReveal?.let { document -> revealMainFirst(document) }
    }

    private fun syncMainPage(state: RemoteScreenState, newlyLoaded: Set<Slot>) {
        val mainTarget = if (pendingMainSwap != null) mainInactive() else player(Slot.MAIN)
        if (Slot.MAIN in newlyLoaded || state.page != appliedPage) {
            mainTarget.setUserLocalInt(PAGE_VARIABLE, state.page)
            appliedPage = state.page
        }
        if (Slot.MAIN !in loadedDocuments) return
        for ((name, value) in state.localInts) {
            mainTarget.setUserLocalInt(name, value)
        }
    }

    private fun showSlots(state: RemoteScreenState) {
        for (slot in Slot.entries) {
            val pendingMain = slot == Slot.MAIN && pendingMainSwap != null
            val pendingFirst = slot == Slot.MAIN && pendingMainFirstReveal != null
            if (pendingMain || pendingFirst) continue
            val isShown = slot in state.documents
            val slotPlayer = player(slot)
            slotPlayer.visibility = when {
                isShown -> View.VISIBLE
                slot == Slot.MAIN -> View.INVISIBLE
                else -> View.GONE
            }
            // A card loads at alpha 0 so its values land before the first frame. Left there, the
            // full-screen player is invisible and still takes every tap, so the page looks normal
            // and ignores touches. The main page stays at 0 until its own reveal.
            if (isShown && slot != Slot.MAIN) slotPlayer.alpha = 1f
        }
        if (pendingMainSwap == null) mainInactive().visibility = View.GONE
    }

    /** A value set from outside a touch is not drawn until the player is asked to. */
    private fun invalidateDocuments() {
        for (slot in loadedDocuments.keys) {
            if (slot == Slot.MAIN && pendingMainSwap != null) {
                mainInactive().invalidate()
            } else {
                player(slot).invalidate()
            }
        }
    }

    /** Active document player for [slot] (MAIN may be either buffer). */
    private fun player(slot: Slot): RemoteComposePlayer = when (slot) {
        Slot.MAIN -> if (mainFrontIsPrimary) players.getValue(Slot.MAIN) else mainBackPlayer
        else -> players.getValue(slot)
    }

    private fun mainInactive(): RemoteComposePlayer =
        if (mainFrontIsPrimary) mainBackPlayer else players.getValue(Slot.MAIN)

    /** Player that holds the current document bytes. During a swap that is the back buffer. */
    fun documentPlayer(slot: Slot): RemoteComposePlayer =
        if (slot == Slot.MAIN && pendingMainSwap != null) mainInactive() else player(slot)

    /**
     * A hidden player never applies `page`, so the first visible frame is home.
     * Draw the new document at alpha 0 (under the current page) for a frame, then show it.
     */
    private fun revealMainSwap(document: SlotDocument) {
        if (mainSwapPosted) return
        mainSwapPosted = true
        val newFront = mainInactive()
        val oldFront = player(Slot.MAIN)
        prepareHiddenDraw(newFront)
        newFront.post {
            if (!swapStillPending(document)) {
                mainSwapPosted = false
                pendingMainSwap?.takeIf(::swapStillPending)?.let(::revealMainSwap)
                return@post
            }
            newFront.setUserLocalInt(PAGE_VARIABLE, appliedPage ?: 0)
            newFront.invalidate()
            newFront.post {
                if (!swapStillPending(document)) {
                    mainSwapPosted = false
                    pendingMainSwap?.takeIf(::swapStillPending)?.let(::revealMainSwap)
                    return@post
                }
                DocumentScroll.restore(newFront, pendingMainScroll)
                pendingMainScroll = emptyList()
                newFront.alpha = 1f
                oldFront.visibility = View.GONE
                oldFront.alpha = 1f
                mainFrontIsPrimary = !mainFrontIsPrimary
                pendingMainSwap = null
                mainSwapPosted = false
                newFront.invalidate()
            }
        }
    }

    private fun revealMainFirst(document: SlotDocument) {
        if (mainFirstPosted) return
        mainFirstPosted = true
        val main = player(Slot.MAIN)
        prepareHiddenDraw(main)
        main.post {
            if (loadedDocuments[Slot.MAIN] !== document || pendingMainFirstReveal !== document) {
                mainFirstPosted = false
                pendingMainFirstReveal?.takeIf { loadedDocuments[Slot.MAIN] === it }?.let(::revealMainFirst)
                return@post
            }
            main.setUserLocalInt(PAGE_VARIABLE, appliedPage ?: 0)
            main.invalidate()
            main.post {
                if (loadedDocuments[Slot.MAIN] !== document || pendingMainFirstReveal !== document) {
                    mainFirstPosted = false
                    pendingMainFirstReveal?.takeIf { loadedDocuments[Slot.MAIN] === it }?.let(::revealMainFirst)
                    return@post
                }
                pendingMainFirstReveal = null
                mainFirstPosted = false
                main.alpha = 1f
                main.invalidate()
            }
        }
    }

    private fun prepareHiddenDraw(player: RemoteComposePlayer) {
        player.alpha = 0f
        player.visibility = View.VISIBLE
        player.setUserLocalInt(PAGE_VARIABLE, appliedPage ?: 0)
        player.invalidate()
    }

    private fun swapStillPending(document: SlotDocument): Boolean =
        loadedDocuments[Slot.MAIN] === document && pendingMainSwap === document

    private fun loadNewDocuments(state: RemoteScreenState): Set<Slot> {
        if (state.timeZone != appliedTimeZone) {
            loadedDocuments.clear()
            pendingMainSwap = null
            pendingMainFirstReveal = null
            pendingMainScroll = emptyList()
            mainSwapPosted = false
            mainFirstPosted = false
            appliedTimeZone = state.timeZone
        }
        val newlyLoaded = mutableSetOf<Slot>()
        for ((slot, document) in state.documents) {
            if (loadedDocuments[slot] === document) continue
            loadSlot(state, slot, document)
            newlyLoaded += slot
        }
        return newlyLoaded
    }

    /** Loads [document] into [slot], keeping list positions from the document it replaces. */
    private fun loadSlot(state: RemoteScreenState, slot: Slot, document: SlotDocument) {
        val hadMain = slot == Slot.MAIN && Slot.MAIN in loadedDocuments
        val marks = when {
            slot !in loadedDocuments -> emptyList()
            slot == Slot.MAIN -> DocumentScroll.capture(player(Slot.MAIN))
            else -> DocumentScroll.capture(player(slot))
        }
        if (slot == Slot.MAIN) pendingMainScroll = marks
        // Rebuilds load under the current page. Alpha 0 still draws, so PAGE can stick before reveal.
        val target = if (hadMain) mainInactive() else player(slot)
        target.alpha = 0f
        target.visibility = View.INVISIBLE
        // Load with Home Assistant's zone so analog hands match the digital TIME/DATE vars.
        // Each document needs its own clock: CalendarSystemClock's calendar is not thread-safe.
        target.setDocument(
            RemoteDocument(ByteArrayInputStream(document.bytes), clockFor(state.timeZone)),
        )
        if (marks.isNotEmpty()) DocumentScroll.restore(target, marks)
        // Action listeners belong to the loaded document, so each new document needs its own.
        target.addIdActionListener { _, metadata ->
            if (metadata != null) onAction(slot, document.revision, metadata)
        }
        for (value in state.values.values) target.applyValue(value)
        for ((name, bitmap) in state.bitmaps) target.setUserLocalBitmap(name, bitmap)
        // PAGE after vars: must stick before the buffer is revealed.
        if (slot == Slot.MAIN) {
            target.setUserLocalInt(PAGE_VARIABLE, state.page)
            appliedPage = state.page
            if (hadMain) {
                pendingMainSwap = document
                pendingMainFirstReveal = null
            } else {
                pendingMainFirstReveal = document
                pendingMainSwap = null
            }
        }
        loadedDocuments[slot] = document
    }

    private fun applyChangedValues(values: Map<String, VarValue>, excludeSlots: Set<Slot>) {
        val changedValues = values.values.filter { value -> appliedValues[value.name] != value }
        if (changedValues.isEmpty()) return
        for (slot in loadedDocuments.keys - excludeSlots) {
            val target = player(slot)
            for (value in changedValues) target.applyValue(value)
        }
    }

    private fun applyChangedBitmaps(bitmaps: Map<String, Bitmap>, excludeSlots: Set<Slot>) {
        val changedBitmaps = bitmaps.filter { (name, bitmap) -> appliedBitmaps[name] !== bitmap }
        if (changedBitmaps.isEmpty()) return
        for (slot in loadedDocuments.keys - excludeSlots) {
            val target = player(slot)
            for ((name, bitmap) in changedBitmaps) target.setUserLocalBitmap(name, bitmap)
        }
    }

}

private fun clockFor(timeZone: String?): RemoteClock {
    if (timeZone.isNullOrEmpty()) return RemoteClock.SYSTEM
    return runCatching {
        CalendarSystemClock(TimeZone.getTimeZone(ZoneId.of(timeZone)))
    }.getOrDefault(RemoteClock.SYSTEM)
}

private fun fullLayoutIfDirty(player: RemoteComposePlayer) {
    val rootLayout = runCatching { player.document.document.rootLayoutComponent }.getOrNull() ?: return
    if (rootLayout.needsMeasure()) {
        rootLayout.clearDirtyBoundaries()
        rootLayout.invalidateMeasure()
    }
}

private fun RemoteComposePlayer.applyValue(value: VarValue) {
    when (value.type) {
        VarType.STRING -> setUserLocalString(value.name, value.value)
        VarType.INT -> setUserLocalInt(value.name, value.asInt())
        VarType.FLOAT -> setUserLocalFloat(value.name, value.asFloat())
        VarType.COLOR -> setUserLocalColor(value.name, value.asColor())
    }
}
