package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.remote.player.view.RemoteComposePlayer
import tech.shaffersoftworks.remotescreens.shared.Slot

/**
 * Native video, map, and music-search views stacked on a document player.
 * Kept off [RemoteDocumentView] so that class stays within the function limit.
 */
internal class DocumentOverlays(
    private val parent: RemoteDocumentView,
    private val documentPlayer: (Slot) -> RemoteComposePlayer,
) {
    private val liveVideoView = LiveVideoView(parent.context)
    private val liveMapView = LiveMapView(parent.context)
    private val liveSearchView = LiveSearchView(parent.context)
    private var videoSlot: Slot? = null
    private var mapSlot: Slot? = null
    private var searchSlot: Slot? = null
    private var lastState: RemoteScreenState? = null

    init {
        liveVideoView.onFailed = { video, httpCode ->
            val slot = videoSlot
            stopVideo()
            if (slot != null) parent.onVideoFailed(slot, video, httpCode)
        }
        liveSearchView.onQueryChange = { tileId, query -> parent.onSearchQuery(tileId, query) }
        liveSearchView.onPlayUri = { tileId, uri -> parent.onSearchPlay(tileId, uri) }
        liveSearchView.onEnqueueUri = { tileId, uri -> parent.onSearchEnqueue(tileId, uri) }
    }

    fun renderAll(state: RemoteScreenState) {
        lastState = state
        renderVideo(state)
        renderMap(state)
        renderSearch(state)
    }

    fun placeBeforeDraw() {
        placeOwnedOverlay(videoSlot, liveVideoView, liveVideoView.video?.componentId)
        placeOwnedOverlay(mapSlot, liveMapView, liveMapView.map?.componentId)
        placeSearchOverlay()
    }

    fun stopAll() {
        stopVideo()
        stopMap()
        stopSearch()
    }

    private fun renderVideo(state: RemoteScreenState) {
        val activeVideo = state.playingVideo?.takeIf {
            parent.isVideoAllowed && parent.windowVisibility == View.VISIBLE
        }
        when {
            activeVideo == null -> stopVideo()
            activeVideo.first != videoSlot || activeVideo.second != liveVideoView.video ->
                startVideo(activeVideo.first, activeVideo.second)
        }
    }

    private fun startVideo(slot: Slot, video: SlotVideo) {
        insertAbove(slot, liveVideoView)
        liveVideoView.visibility = View.INVISIBLE
        videoSlot = slot
        liveVideoView.play(video)
        placeOwnedOverlay(slot, liveVideoView, video.componentId)
    }

    private fun stopVideo() {
        liveVideoView.stop()
        videoSlot = null
        (liveVideoView.parent as? ViewGroup)?.removeView(liveVideoView)
    }

    private fun renderMap(state: RemoteScreenState) {
        val activeMap = state.playingMap?.takeIf { parent.windowVisibility == View.VISIBLE }
        when {
            activeMap == null -> stopMap()
            activeMap.first != mapSlot || activeMap.second != liveMapView.map ->
                startMap(activeMap.first, activeMap.second, state.bitmaps)
            else -> liveMapView.show(activeMap.second, state.bitmaps)
        }
    }

    private fun startMap(slot: Slot, map: SlotMap, bitmaps: Map<String, Bitmap>) {
        insertAbove(slot, liveMapView)
        liveMapView.visibility = View.INVISIBLE
        mapSlot = slot
        liveMapView.show(map, bitmaps)
        placeOwnedOverlay(slot, liveMapView, map.componentId)
    }

    private fun stopMap() {
        liveMapView.stop()
        mapSlot = null
        (liveMapView.parent as? ViewGroup)?.removeView(liveMapView)
    }

    private fun renderSearch(state: RemoteScreenState) {
        val activeSearch = state.playingSearch?.takeIf { parent.windowVisibility == View.VISIBLE }
        when {
            activeSearch == null -> stopSearch()
            activeSearch.first != searchSlot ||
                activeSearch.second.componentId != liveSearchView.search?.componentId ||
                activeSearch.second.revision != liveSearchView.search?.revision ->
                startSearch(activeSearch.first, activeSearch.second)
            else -> {
                liveSearchView.setResults(activeSearch.second.tileId, activeSearch.second.rows)
                placeSearchOverlay()
            }
        }
    }

    private fun startSearch(slot: Slot, search: SlotSearch) {
        insertAbove(slot, liveSearchView)
        liveSearchView.visibility = View.INVISIBLE
        searchSlot = slot
        liveSearchView.show(search)
        placeSearchOverlay()
    }

    /** Prefer the rectangle from the add-on. Component bounds stay 0×0 inside the listen column. */
    private fun placeSearchOverlay() {
        val active = lastState?.playingSearch ?: return
        val (slot, search) = active
        val host = documentPlayer(slot)
        if (search.width > 0 && search.height > 0) {
            placeFixedOverlay(liveSearchView, host, search.x, search.y, search.width, search.height)
        } else {
            placeRemoteOverlay(liveSearchView, host, search.componentId)
        }
    }

    private fun stopSearch() {
        liveSearchView.stop()
        searchSlot = null
        (liveSearchView.parent as? ViewGroup)?.removeView(liveSearchView)
    }

    private fun placeOwnedOverlay(slot: Slot?, overlay: View, componentId: Int?) {
        if (slot == null || componentId == null) return
        placeRemoteOverlay(overlay, documentPlayer(slot), componentId)
    }

    /** Just above that slot's player — never bringToFront, or the overlay covers alerts. */
    private fun insertAbove(slot: Slot, overlay: View) {
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        val layoutParams = FrameLayout.LayoutParams(0, 0).apply { gravity = Gravity.TOP or Gravity.START }
        parent.addView(overlay, parent.indexOfChild(documentPlayer(slot)) + 1, layoutParams)
    }
}
