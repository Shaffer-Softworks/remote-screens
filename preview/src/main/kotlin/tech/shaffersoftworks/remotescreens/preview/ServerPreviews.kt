@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.preview

import androidx.compose.remote.tooling.preview.RemoteDocumentPreview
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.viewinterop.AndroidView

internal data class PreviewCase(
    val group: String,
    val label: String,
    val asset: String,
    val width: Int,
    val height: Int,
) {
    override fun toString() = label
}

internal abstract class Cases(private val group: String) : PreviewParameterProvider<PreviewCase> {
    override val values: Sequence<PreviewCase>
        get() = generatedPreviews.asSequence().filter { it.group == group }

    override fun getDisplayName(index: Int): String? = values.elementAt(index).label
}

@Composable
private fun documentBytes(sample: PreviewCase): ByteArray {
    val context = LocalContext.current
    return remember(sample.asset) {
        context.assets.open(sample.asset).use { it.readBytes() }
    }
}

/** Use the production player's renderer; the alpha tooling renderer drops canvas paths. */
@Composable
internal fun ServerDocument(sample: PreviewCase) {
    val bytes = documentBytes(sample)
    val document = remember(bytes) { RemoteDocument(bytes) }
    key(sample.asset) {
        // Loading in factory also initializes canvas paths correctly in Layoutlib.
        AndroidView(
            factory = { RemoteComposePlayer(it).apply { setDocument(document) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal class ScreensCases : Cases("Screens")

@Preview(name = "Screens", widthDp = 800, heightDp = 480, device = "spec:width=800dp,height=480dp,dpi=160")
@Composable
private fun ScreensPreview(@PreviewParameter(ScreensCases::class) sample: PreviewCase) {
    ServerDocument(sample)
}

/** Keep the official tooling entry point available to compare behavior after AndroidX upgrades. */
@Preview(name = "AndroidX tooling comparison", widthDp = 800, heightDp = 480,
    device = "spec:width=800dp,height=480dp,dpi=160")
@Composable
private fun ToolingLibraryPreview() {
    val sample = generatedPreviews.firstOrNull { it.group == "Screens" && it.label.startsWith("Living room") }
        ?: generatedPreviews.first()
    RemoteDocumentPreview(documentBytes(sample))
}
