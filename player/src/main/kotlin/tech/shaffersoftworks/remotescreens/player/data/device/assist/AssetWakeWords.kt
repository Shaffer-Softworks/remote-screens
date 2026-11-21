package tech.shaffersoftworks.remotescreens.player.data.device.assist

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.shaffersoftworks.remotescreens.player.microwakeword.MicroWakeWord
import tech.shaffersoftworks.remotescreens.shared.AssistWakeWords
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/** Loads a bundled model and the cutoff and window from its own manifest. */
class AssetWakeWords @Inject constructor(
    @ApplicationContext private val context: Context,
) : WakeWordDetectors {
    override fun open(wakeWordId: String): WakeWordDetector {
        val id = AssistWakeWords.validated(wakeWordId)
        val json = context.assets.open("wakewords/$id.json").bufferedReader().use { it.readText() }
        val manifest = AssistWakeWords.manifest(id = id, json = json)
        val bytes = context.assets.open("wakewords/$id.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        model.put(bytes)
        model.flip()

        return MicroWakeWord.open(model = model, manifest = manifest)
    }
}
