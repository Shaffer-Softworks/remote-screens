@file:Suppress("RestrictedApi")

package dev.remotescreens.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.remote.player.view.RemoteComposePlayer
import dev.remotescreens.shared.ServerMessage
import dev.remotescreens.shared.Slot
import dev.remotescreens.shared.VarType
import dev.remotescreens.shared.VarValue

/**
 * The only door to the Remote Compose player (still alpha). Three players stacked: the pages of the screen, a card
 * over them, an alert over both. Values the add-on sends go to all three; each document takes the ones it names.
 */
class RcHost(
    context: Context,
    /** A tap in a document: its slot, the revision of the document, and the tap's metadata. */
    private val onAction: (Slot, Int, String) -> Unit,
    private val onPage: (Int) -> Unit,
) {
    val view = FrameLayout(context)
    private val main = SlotView(Slot.MAIN, RemoteComposePlayer(context))
    private val overlay = SlotView(Slot.OVERLAY, RemoteComposePlayer(context))
    private val alert = SlotView(Slot.ALERT, RemoteComposePlayer(context))
    private val slots = listOf(main, overlay, alert)
    private val values = HashMap<String, VarValue>()
    private val bitmaps = HashMap<String, Bitmap>()
    private val handler = Handler(Looper.getMainLooper())
    private val closeAlert = Runnable { close(Slot.ALERT) }

    var page = 0
        private set
    var homePage = 0
        private set
    var pageCount = 1
        private set
    private var swipePages: List<Int> = listOf(0)

    private inner class SlotView(val slot: Slot, val player: RemoteComposePlayer) {
        var rev = 0
        var predictions: Map<String, List<VarValue>> = emptyMap()
        var shown = false
    }

    init {
        for (s in slots) {
            s.player.visibility = if (s.slot == Slot.MAIN) View.VISIBLE else View.GONE
            view.addView(s.player, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    val hasDocument: Boolean get() = main.shown
    val overlayShown: Boolean get() = overlay.shown || alert.shown

    fun show(doc: ServerMessage.Document) {
        val s = slot(doc.slot)
        val bytes = runCatching { Base64.decode(doc.data, Base64.DEFAULT) }.getOrElse {
            Log.w(TAG, "Bad document data")
            return
        }
        s.player.setDocument(bytes)
        s.rev = doc.rev
        s.predictions = doc.predictions
        s.shown = true
        s.player.addIdActionListener { _, metadata -> if (metadata != null) onAction(s.slot, s.rev, metadata) }
        for (v in doc.vars) values[v.name] = v
        for (v in values.values) apply(s.player, v)
        for ((name, b) in bitmaps) s.player.setUserLocalBitmap(name, b)
        s.player.visibility = View.VISIBLE
        s.player.invalidate()
        if (doc.slot == Slot.MAIN) {
            homePage = doc.homePage
            pageCount = doc.pageCount.coerceAtLeast(1)
            swipePages = doc.swipePages.filter { it in 0 until pageCount }.sorted().ifEmpty { (0 until pageCount).toList() }
            showPage(if (page < pageCount) page else homePage)
        }
        if (doc.slot == Slot.ALERT) {
            handler.removeCallbacks(closeAlert)
            if (doc.timeout > 0) handler.postDelayed(closeAlert, doc.timeout * 1000L)
        }
    }

    fun vars(msg: ServerMessage.Vars) {
        for (v in msg.vars) {
            values[v.name] = v
            for (s in slots) if (s.shown) apply(s.player, v)
        }
        redraw()
        if (msg.predictions.isNotEmpty()) for (s in slots) if (s.shown) s.predictions = s.predictions + msg.predictions
    }

    /** Shows at once what a tap will change, before Home Assistant answers. */
    fun predict(slot: Slot, metadata: String) {
        val change = slot(slot).predictions[metadata] ?: return
        for (v in change) {
            values[v.name] = v
            for (s in slots) if (s.shown) apply(s.player, v)
        }
        redraw()
    }

    fun bitmap(name: String, b: Bitmap) {
        bitmaps.put(name, b)?.takeIf { it != b }?.let { old -> handler.postDelayed({ if (!old.isRecycled && old !in bitmaps.values) old.recycle() }, 2_000) }
        for (s in slots) if (s.shown) s.player.setUserLocalBitmap(name, b)
        redraw()
    }

    fun showPage(index: Int) {
        val i = index.coerceIn(0, pageCount - 1)
        page = i
        main.player.setUserLocalInt(PAGE, i)
        main.player.invalidate()
        onPage(i)
    }

    /** From a hidden page, a swipe goes to the nearest swipeable page in that direction. */
    fun next() = swipePages.firstOrNull { it > page }?.let { showPage(it) }
    fun previous() = swipePages.lastOrNull { it < page }?.let { showPage(it) }

    fun close(slot: Slot) {
        val s = slot(slot)
        if (slot == Slot.MAIN) return
        s.shown = false
        s.player.visibility = View.GONE
        if (slot == Slot.ALERT) handler.removeCallbacks(closeAlert)
    }

    /** The topmost overlay, for Back. */
    fun closeTop(): Boolean = when {
        alert.shown -> { close(Slot.ALERT); true }
        overlay.shown -> { close(Slot.OVERLAY); true }
        else -> false
    }

    fun clear() {
        close(Slot.OVERLAY)
        close(Slot.ALERT)
        main.shown = false
        main.player.visibility = View.INVISIBLE
        values.clear()
    }

    fun revealMain() {
        if (main.shown) main.player.visibility = View.VISIBLE
    }

    /** A value set from outside a touch is not drawn until the player is asked to. */
    private fun redraw() {
        for (s in slots) if (s.shown) s.player.invalidate()
    }

    private fun slot(slot: Slot) = when (slot) {
        Slot.MAIN -> main
        Slot.OVERLAY -> overlay
        Slot.ALERT -> alert
    }

    private fun apply(p: RemoteComposePlayer, v: VarValue) {
        when (v.type) {
            VarType.STRING -> p.setUserLocalString(v.name, v.value)
            VarType.INT -> p.setUserLocalInt(v.name, v.asInt())
            VarType.FLOAT -> p.setUserLocalFloat(v.name, v.asFloat())
            VarType.COLOR -> p.setUserLocalColor(v.name, v.asColor())
        }
    }

    companion object {
        private const val TAG = "RcHost"
        const val PAGE = "page"
    }
}
