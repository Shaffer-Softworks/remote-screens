package dev.remotescreens.player.ui.screens.player.remote

import android.graphics.Bitmap
import dev.remotescreens.shared.ServerMessage
import dev.remotescreens.shared.Slot
import dev.remotescreens.shared.VarValue

/**
 * A document shown in a slot. Compared by identity, so the renderer loads each one into its player exactly once.
 * [predictions] maps an action's metadata to the values it will change.
 */
class SlotDocument(
    val revision: Int,
    val bytes: ByteArray,
    val predictions: Map<String, List<VarValue>>,
)

/**
 * Everything the Remote Compose players show: the pages of the screen, a card over them, an alert over both.
 * Values go to every shown document; each document takes the ones it names.
 */
data class RemoteScreenState(
    /** The shown documents. A slot without a document is hidden. */
    val documents: Map<Slot, SlotDocument> = emptyMap(),
    val values: Map<String, VarValue> = emptyMap(),
    val bitmaps: Map<String, Bitmap> = emptyMap(),
    val page: Int = 0,
    val homePage: Int = 0,
    val pageCount: Int = 1,
    /** The pages a swipe moves through, in order. */
    val swipePages: List<Int> = listOf(0),
) {
    val hasMainDocument: Boolean get() = Slot.MAIN in documents

    val isOverlayShown: Boolean get() = Slot.OVERLAY in documents || Slot.ALERT in documents

    /** The topmost shown overlay, which Back closes first. */
    val topOverlay: Slot?
        get() = when {
            Slot.ALERT in documents -> Slot.ALERT
            Slot.OVERLAY in documents -> Slot.OVERLAY
            else -> null
        }

    /** The page to show after a new main document: the same one if it still exists, otherwise home. */
    val pageAfterNewDocument: Int get() = if (page < pageCount) page else homePage

    /** From a hidden page, a swipe goes to the nearest swipeable page in that direction. */
    val nextSwipePage: Int? get() = swipePages.firstOrNull { it > page }

    val previousSwipePage: Int? get() = swipePages.lastOrNull { it < page }

    fun withDocument(message: ServerMessage.Document, bytes: ByteArray): RemoteScreenState {
        val document = SlotDocument(message.rev, bytes, message.predictions)
        val updated = copy(
            documents = documents + (message.slot to document),
            values = values + message.vars.associateBy(VarValue::name),
        )
        if (message.slot != Slot.MAIN) return updated
        val newPageCount = message.pageCount.coerceAtLeast(1)
        return updated.copy(
            homePage = message.homePage,
            pageCount = newPageCount,
            swipePages = message.swipePages
                .filter { it in 0 until newPageCount }
                .sorted()
                .ifEmpty { (0 until newPageCount).toList() },
        )
    }

    fun withVars(message: ServerMessage.Vars): RemoteScreenState {
        val updatedDocuments = if (message.predictions.isEmpty()) {
            documents
        } else {
            documents.mapValues { (_, document) ->
                SlotDocument(document.revision, document.bytes, document.predictions + message.predictions)
            }
        }
        return copy(documents = updatedDocuments, values = values + message.vars.associateBy(VarValue::name))
    }

    /** What a tap will change, applied before Home Assistant answers. Null when the tap predicts nothing. */
    fun withPrediction(slot: Slot, metadata: String): RemoteScreenState? {
        val predictedValues = documents[slot]?.predictions?.get(metadata) ?: return null
        return copy(values = values + predictedValues.associateBy(VarValue::name))
    }

    fun withBitmap(name: String, bitmap: Bitmap): RemoteScreenState = copy(bitmaps = bitmaps + (name to bitmap))

    fun withPage(index: Int): RemoteScreenState = copy(page = index.coerceIn(0, pageCount - 1))

    /** The main document cannot be closed, only replaced or cleared. */
    fun closed(slot: Slot): RemoteScreenState = if (slot == Slot.MAIN) this else copy(documents = documents - slot)

    /** Hides every document and forgets the values, as when the screen must be paired again. */
    fun cleared(): RemoteScreenState = copy(documents = emptyMap(), values = emptyMap())
}
