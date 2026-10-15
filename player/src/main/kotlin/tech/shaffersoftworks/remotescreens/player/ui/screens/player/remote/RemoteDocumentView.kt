@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import androidx.compose.remote.player.view.RemoteComposePlayer
import tech.shaffersoftworks.remotescreens.shared.Slot
import tech.shaffersoftworks.remotescreens.shared.VarType
import tech.shaffersoftworks.remotescreens.shared.VarValue
import kotlin.math.roundToInt

/** The user variable each main document reads to know which page to draw. */
const val PAGE_VARIABLE = "page"

/**
 * The only door to the Remote Compose player (still alpha). Holds one player per [Slot], stacked, and brings them
 * in line with a [RemoteScreenState]: each new document is loaded once, then only changed values and bitmaps are set.
 */
@SuppressLint("ViewConstructor")
class RemoteDocumentView(context: Context) : SwipeFrame(context) {
    private val players: Map<Slot, RemoteComposePlayer> = Slot.entries.associateWith { RemoteComposePlayer(context) }
    private val loadedDocuments = mutableMapOf<Slot, SlotDocument>()
    private var appliedValues: Map<String, VarValue> = emptyMap()
    private var appliedBitmaps: Map<String, Bitmap> = emptyMap()
    private var appliedPage: Int? = null
    private val liveVideoView = LiveVideoView(context)
    private var videoSlot: Slot? = null
    private var lastState: RemoteScreenState? = null

    /** A tap in a document: its slot, the revision of the document, and the tap's metadata. */
    var onAction: (slot: Slot, revision: Int, metadata: String) -> Unit = { _, _, _ -> }

    /** A camera's video stopped by itself, with the add-on's HTTP status when that was the cause. */
    var onVideoFailed: (slot: Slot, video: SlotVideo, httpCode: Int?) -> Unit = { _, _, _ -> }

    /** Off while the screen is in standby, so no camera is decoded for nobody. */
    var isVideoAllowed = true

    init {
        for (slot in Slot.entries) {
            val player = players.getValue(slot)
            player.visibility = if (slot == Slot.MAIN) View.INVISIBLE else View.GONE
            addView(player, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        liveVideoView.onFailed = { video, httpCode ->
            val slot = videoSlot
            stopVideo()
            if (slot != null) onVideoFailed(slot, video, httpCode)
        }
        // A text that changes while it is measured registers a relayout boundary in the middle of the partial
        // layout pass, which then throws ConcurrentModificationException on every frame (alpha20). A full pass
        // never walks that list, so the partial pass is never left to run.
        viewTreeObserver.addOnPreDrawListener {
            for (slot in loadedDocuments.keys) fullLayoutIfDirty(players.getValue(slot))
            placeVideo()
            true
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        lastState?.let(::renderVideo)
    }

    override fun onDetachedFromWindow() {
        stopVideo()
        super.onDetachedFromWindow()
    }

    fun render(state: RemoteScreenState) {
        lastState = state
        loadedDocuments.keys.retainAll(state.documents.keys)
        val newlyLoaded = loadNewDocuments(state)
        applyChangedValues(state.values, skip = newlyLoaded)
        applyChangedBitmaps(state.bitmaps, skip = newlyLoaded)
        appliedValues = state.values
        appliedBitmaps = state.bitmaps

        if (Slot.MAIN in newlyLoaded || state.page != appliedPage) {
            players.getValue(Slot.MAIN).setUserLocalInt(PAGE_VARIABLE, state.page)
            appliedPage = state.page
        }
        for (slot in Slot.entries) {
            val isShown = slot in state.documents
            players.getValue(slot).visibility = when {
                isShown -> View.VISIBLE
                slot == Slot.MAIN -> View.INVISIBLE
                else -> View.GONE
            }
        }
        // A value set from outside a touch is not drawn until the player is asked to.
        for (slot in loadedDocuments.keys) players.getValue(slot).invalidate()
        renderVideo(state)
    }

    private fun renderVideo(state: RemoteScreenState) {
        val wanted = state.playingVideo?.takeIf { isVideoAllowed && windowVisibility == View.VISIBLE }
        when {
            wanted == null -> stopVideo()
            wanted.first != videoSlot || wanted.second != liveVideoView.video -> startVideo(wanted.first, wanted.second)
        }
    }

    /** Just above its document, so a card's video stays under an alert. */
    private fun startVideo(slot: Slot, video: SlotVideo) {
        removeView(liveVideoView)
        addView(liveVideoView, indexOfChild(players.getValue(slot)) + 1, LayoutParams(0, 0))
        liveVideoView.visibility = View.INVISIBLE
        videoSlot = slot
        liveVideoView.play(video)
        placeVideo()
    }

    private fun stopVideo() {
        liveVideoView.stop()
        videoSlot = null
        removeView(liveVideoView)
    }

    /** Over the image the video belongs to, wherever the document laid it out, scrolled, this frame. */
    private fun placeVideo() {
        val slot = videoSlot ?: return
        val player = players.getValue(slot)
        val bounds = liveVideoView.video?.let { video -> componentBounds(player, video.componentId) }
        if (bounds == null) {
            liveVideoView.visibility = View.INVISIBLE
            return
        }
        bounds.offset(player.left, player.top)
        val params = liveVideoView.layoutParams as LayoutParams
        val placed = Rect(params.leftMargin, params.topMargin, params.leftMargin + params.width, 0)
        placed.bottom = params.topMargin + params.height
        if (placed != bounds) {
            params.leftMargin = bounds.left
            params.topMargin = bounds.top
            params.width = bounds.width()
            params.height = bounds.height()
            liveVideoView.layoutParams = params
        }
        liveVideoView.visibility = View.VISIBLE
    }

    /** Where component [componentId] of [player]'s document is this frame, in the player; null until laid out. */
    private fun componentBounds(player: RemoteComposePlayer, componentId: Int): Rect? {
        val component = runCatching { player.document?.document?.getComponent(componentId) }.getOrNull() ?: return null
        // As the document finds where a touch lands: each parent's offset and scroll, but not the image's own scroll.
        var left = component.x
        var top = component.y
        var parent = component.parent
        while (parent != null) {
            left += parent.x + parent.scrollX
            top += parent.y + parent.scrollY
            parent = parent.parent
        }
        val width = component.width.roundToInt()
        val height = component.height.roundToInt()
        val x = left.roundToInt()
        val y = top.roundToInt()
        return if (width > 0 && height > 0) Rect(x, y, x + width, y + height) else null
    }

    private fun loadNewDocuments(state: RemoteScreenState): Set<Slot> {
        val newlyLoaded = mutableSetOf<Slot>()
        for ((slot, document) in state.documents) {
            if (loadedDocuments[slot] === document) continue
            val player = players.getValue(slot)
            player.setDocument(document.bytes)
            // Action listeners belong to the loaded document, so each new document needs its own.
            player.addIdActionListener { _, metadata ->
                if (metadata != null) onAction(slot, document.revision, metadata)
            }
            for (value in state.values.values) player.applyValue(value)
            for ((name, bitmap) in state.bitmaps) player.setUserLocalBitmap(name, bitmap)
            loadedDocuments[slot] = document
            newlyLoaded += slot
        }
        return newlyLoaded
    }

    private fun applyChangedValues(values: Map<String, VarValue>, skip: Set<Slot>) {
        val changedValues = values.values.filter { value -> appliedValues[value.name] != value }
        if (changedValues.isEmpty()) return
        for (slot in loadedDocuments.keys - skip) {
            val player = players.getValue(slot)
            for (value in changedValues) player.applyValue(value)
        }
    }

    private fun applyChangedBitmaps(bitmaps: Map<String, Bitmap>, skip: Set<Slot>) {
        val changedBitmaps = bitmaps.filter { (name, bitmap) -> appliedBitmaps[name] !== bitmap }
        if (changedBitmaps.isEmpty()) return
        for (slot in loadedDocuments.keys - skip) {
            val player = players.getValue(slot)
            for ((name, bitmap) in changedBitmaps) player.setUserLocalBitmap(name, bitmap)
        }
    }

    private fun fullLayoutIfDirty(player: RemoteComposePlayer) {
        val rootLayout = runCatching { player.document?.document?.rootLayoutComponent }.getOrNull() ?: return
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
}
