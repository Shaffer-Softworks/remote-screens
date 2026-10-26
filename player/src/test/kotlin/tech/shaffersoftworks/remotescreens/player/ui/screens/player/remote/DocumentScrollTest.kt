package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.remote.core.operations.layout.modifiers.ComponentModifiers
import androidx.compose.remote.core.operations.layout.modifiers.ScrollModifierOperation
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.createRcBuffer
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.profile.Profile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class DocumentScrollTest {
    @Test
    fun aRebuiltListComesBackToTheSameOffset() {
        val written = mutableListOf<Float>()
        val primed = parse(scrollDocument())
        DocumentScroll.restore(
            primed,
            listOf(DocumentScroll.Mark(LIST_ID, isVertical = true, offset = SCROLLED)),
        ) { _, value ->
            written += value
        }
        assertEquals(listOf(-SCROLLED), written)
        assertEquals(
            listOf(DocumentScroll.Mark(LIST_ID, isVertical = true, offset = SCROLLED)),
            DocumentScroll.capture(primed),
        )

        val fresh = parse(scrollDocument())
        DocumentScroll.restore(fresh, DocumentScroll.capture(primed)) { _, _ -> }
        assertEquals(SCROLLED, scrollers(fresh).single().scrollY, 0f)
    }

    private fun parse(bytes: ByteArray): Component {
        val document = CoreDocument()
        document.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
        return checkNotNull(document.rootLayoutComponent)
    }

    private fun scrollers(root: Component): List<Component> {
        val found = mutableListOf<Component>()
        fun walk(component: Component) {
            if (component.ownsVerticalScroll()) found += component
            for (operation in component.mList) {
                val nested = operation as? Component ?: continue
                walk(nested)
            }
            val laidOut = (component as? LayoutComponent)?.childrenComponents ?: return
            for (child in laidOut) walk(child)
        }
        walk(root)
        return found
    }

    private fun Component.ownsVerticalScroll(): Boolean {
        for (operation in mList) {
            val modifiers = operation as? ComponentModifiers ?: continue
            for (modifier in modifiers.list) {
                if (modifier is ScrollModifierOperation && modifier.isVerticalScroll()) return true
            }
        }
        return false
    }

    private fun scrollDocument(): ByteArray = createRcBuffer(
        RcProfile(profile),
        RemoteComposeWriter.HTag(Header.DOC_WIDTH, WIDTH),
        RemoteComposeWriter.HTag(Header.DOC_HEIGHT, HEIGHT),
        RemoteComposeWriter.HTag(Header.DOC_CONTENT_DESCRIPTION, "scroll"),
        RemoteComposeWriter.HTag(Header.DOC_PROFILES, PROFILES),
    ) {
        Column(Modifier.size(LIST_SIZE, LIST_SIZE).componentId(LIST_ID).verticalScroll()) {
            Box(Modifier.size(LIST_SIZE, LIST_SIZE * TALL))
        }
    }

    private companion object {
        const val WIDTH = 200
        const val HEIGHT = 200
        const val LIST_SIZE = 80f
        const val TALL = 4
        const val LIST_ID = 0x60_1000
        const val SCROLLED = -180f
        const val PROFILES = RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL

        val profile: Profile = Profile(
            CoreDocument.DOCUMENT_API_LEVEL,
            PROFILES,
            JvmRcPlatformServices(),
        ) { info, writerProfile, callback ->
            RemoteComposeWriter(info, null, writerProfile, callback)
        }
    }
}
