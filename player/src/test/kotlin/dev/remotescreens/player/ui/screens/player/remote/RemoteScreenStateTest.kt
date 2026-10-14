package dev.remotescreens.player.ui.screens.player.remote

import dev.remotescreens.shared.ServerMessage
import dev.remotescreens.shared.Slot
import dev.remotescreens.shared.VarValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteScreenStateTest {
    private val documentBytes = byteArrayOf(1, 2, 3)

    private fun mainDocument(
        pageCount: Int = 4,
        homePage: Int = 0,
        swipePages: List<Int> = emptyList(),
        vars: List<VarValue> = emptyList(),
    ) = ServerMessage.Document(
        slot = Slot.MAIN,
        rev = 1,
        data = "",
        vars = vars,
        homePage = homePage,
        pageCount = pageCount,
        swipePages = swipePages,
    )

    @Test
    fun `main document sets pages and keeps only valid swipe pages in order`() {
        val state = RemoteScreenState().withDocument(mainDocument(swipePages = listOf(3, 9, 1)), documentBytes)

        assertTrue(state.hasMainDocument)
        assertEquals(4, state.pageCount)
        assertEquals(listOf(1, 3), state.swipePages)
    }

    @Test
    fun `empty swipe pages means every page`() {
        val state = RemoteScreenState().withDocument(mainDocument(pageCount = 3), documentBytes)

        assertEquals(listOf(0, 1, 2), state.swipePages)
    }

    @Test
    fun `swipe skips to the nearest swipeable page from a hidden page`() {
        val state = RemoteScreenState()
            .withDocument(mainDocument(pageCount = 5, swipePages = listOf(0, 2, 4)), documentBytes)
            .withPage(3)

        assertEquals(4, state.nextSwipePage)
        assertEquals(2, state.previousSwipePage)
    }

    @Test
    fun `page is kept within the document`() {
        val state = RemoteScreenState().withDocument(mainDocument(pageCount = 2), documentBytes)

        assertEquals(1, state.withPage(7).page)
        assertEquals(0, state.withPage(-1).page)
    }

    @Test
    fun `a new main document with fewer pages goes home`() {
        val state = RemoteScreenState()
            .withDocument(mainDocument(pageCount = 4), documentBytes)
            .withPage(3)
            .withDocument(mainDocument(pageCount = 2, homePage = 1), documentBytes)

        assertEquals(1, state.pageAfterNewDocument)
    }

    @Test
    fun `back closes the alert before the card`() {
        val overlay = ServerMessage.Document(slot = Slot.OVERLAY, rev = 1, data = "")
        val alert = ServerMessage.Document(slot = Slot.ALERT, rev = 1, data = "")
        val state = RemoteScreenState().withDocument(overlay, documentBytes).withDocument(alert, documentBytes)

        assertEquals(Slot.ALERT, state.topOverlay)
        assertEquals(Slot.OVERLAY, state.closed(Slot.ALERT).topOverlay)
        assertNull(state.closed(Slot.ALERT).closed(Slot.OVERLAY).topOverlay)
    }

    @Test
    fun `the main document cannot be closed`() {
        val state = RemoteScreenState().withDocument(mainDocument(), documentBytes)

        assertSame(state, state.closed(Slot.MAIN))
    }

    @Test
    fun `a prediction applies the values the tap will change`() {
        val predicted = VarValue.string("light", "on")
        val document = ServerMessage.Document(
            slot = Slot.MAIN,
            rev = 1,
            data = "",
            vars = listOf(VarValue.string("light", "off")),
            predictions = mapOf("light|toggle|" to listOf(predicted)),
        )
        val state = RemoteScreenState().withDocument(document, documentBytes)

        assertEquals(predicted, state.withPrediction(Slot.MAIN, "light|toggle|")?.values?.get("light"))
        assertNull(state.withPrediction(Slot.MAIN, "other|toggle|"))
    }

    @Test
    fun `vars add predictions to every shown document`() {
        val state = RemoteScreenState()
            .withDocument(mainDocument(), documentBytes)
            .withVars(
                ServerMessage.Vars(
                    vars = listOf(VarValue.int("count", 2)),
                    predictions = mapOf("fan|toggle|" to listOf(VarValue.string("fan", "on"))),
                ),
            )

        assertEquals(VarValue.int("count", 2), state.values["count"])
        assertTrue("fan|toggle|" in state.documents.getValue(Slot.MAIN).predictions)
    }

    @Test
    fun `clearing hides every document and forgets values but keeps the page`() {
        val state = RemoteScreenState()
            .withDocument(mainDocument(vars = listOf(VarValue.int("count", 1))), documentBytes)
            .withPage(2)
            .cleared()

        assertFalse(state.hasMainDocument)
        assertTrue(state.values.isEmpty())
        assertEquals(2, state.page)
    }
}
