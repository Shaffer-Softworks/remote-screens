@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.content.Context
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.createRcBuffer
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.remote.player.view.RemoteComposePlayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tech.shaffersoftworks.remotescreens.shared.Slot
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.util.ArrayDeque

class RemoteDocumentControllerTest {
    private val posted = ArrayDeque<Runnable>()
    private val controller = RemoteDocumentController(mock<Context>()) {
        mock<RemoteComposePlayer>().also { player ->
            whenever(player.post(any())).doAnswer { invocation ->
                posted.add(invocation.getArgument(0))
                true
            }
        }
    }

    @Test
    fun unchangedDocumentIsNotReloadedAndOnlyChangedValuesAreApplied() {
        val initial = state(document(1)).copy(values = mapOf("title" to VarValue.string("title", "First")))
        controller.render(initial)
        drainFrames()
        val front = controller.primaryPlayer(Slot.MAIN)
        clearInvocations(front)

        controller.render(initial)
        verify(front, never()).setDocument(any<RemoteDocument>())
        verify(front, never()).setUserLocalString(any(), any())

        controller.render(initial.copy(values = mapOf("title" to VarValue.string("title", "Next"))))
        verify(front).setUserLocalString("title", "Next")
        verify(front, never()).setDocument(any<RemoteDocument>())
    }

    @Test
    fun replacementLoadsIntoBackBufferAndSwapsAfterQueuedReveal() {
        controller.render(state(document(1)))
        drainFrames()
        val replacement = state(document(2)).copy(page = 2)
        controller.render(replacement)

        assertTrue(controller.mainFrontIsPrimary)
        assertSame(controller.mainBackPlayer, controller.documentPlayer(Slot.MAIN))
        verify(controller.mainBackPlayer).setDocument(any<RemoteDocument>())
        verify(controller.mainBackPlayer, times(3)).setUserLocalInt(PAGE_VARIABLE, 2)

        drainFrames()
        assertFalse(controller.mainFrontIsPrimary)
        assertSame(controller.mainBackPlayer, controller.documentPlayer(Slot.MAIN))
    }

    @Test
    fun disposedControllerCannotRevealAQueuedReplacement() {
        controller.render(state(document(1)))
        drainFrames()
        controller.render(state(document(2)))
        controller.dispose()
        drainFrames()
        assertTrue(controller.mainFrontIsPrimary)
    }

    @Test
    fun aNewerDocumentSupersedesAnAlreadyQueuedReplacement() {
        controller.render(state(document(1)))
        drainFrames()
        controller.render(state(document(2)))
        controller.render(state(document(3)))
        drainFrames()
        assertFalse(controller.mainFrontIsPrimary)
        verify(controller.mainBackPlayer, times(2)).setDocument(any<RemoteDocument>())
    }

    private fun drainFrames() {
        while (posted.isNotEmpty()) posted.removeFirst().run()
    }

    private fun state(document: SlotDocument) = RemoteScreenState(documents = mapOf(Slot.MAIN to document))

    private fun document(revision: Int): SlotDocument {
        val profiles = RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL
        val profile = Profile(CoreDocument.DOCUMENT_API_LEVEL, profiles, JvmRcPlatformServices()) {
                info, writerProfile, callback ->
            RemoteComposeWriter(info, null, writerProfile, callback)
        }
        val bytes = createRcBuffer(
            RcProfile(profile),
            RemoteComposeWriter.HTag(Header.DOC_WIDTH, 200),
            RemoteComposeWriter.HTag(Header.DOC_HEIGHT, 200),
            RemoteComposeWriter.HTag(Header.DOC_PROFILES, profiles),
        ) { Box(Modifier.size(200f, 200f)) }
        return SlotDocument(revision, bytes, mutableMapOf())
    }
}
