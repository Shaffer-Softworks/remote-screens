package tech.shaffersoftworks.remotescreens.player.microwakeword

import tech.shaffersoftworks.remotescreens.player.data.device.assist.WakeWordDetector
import tech.shaffersoftworks.remotescreens.shared.WakeWordManifest
import java.nio.ByteBuffer

/**
 * JNI microWakeWord. The audio frontend matches the model training, so a raw TensorFlow interpreter is not used.
 * Adapted from the Home Assistant Android companion, Apache License 2.0.
 * The capture coroutine is the only caller.
 */
class MicroWakeWord private constructor(
    private val handle: Long,
    override val phrase: String,
) : WakeWordDetector {
    override val isReady: Boolean = handle != 0L

    override fun processAudio(samples: ShortArray): Boolean =
        handle != 0L && nativeProcessAudio(handle = handle, audio = samples)

    override fun reset() {
        if (handle != 0L) nativeReset(handle)
    }

    override fun close() {
        if (handle != 0L) nativeDestroy(handle)
    }

    companion object {
        private const val SAMPLE_RATE = 16_000

        private val isLibraryLoaded: Boolean = runCatching { System.loadLibrary("microwakeword") }.isSuccess

        fun open(model: ByteBuffer, manifest: WakeWordManifest): WakeWordDetector {
            if (!isLibraryLoaded || !model.isDirect) return UnavailableWakeWord(manifest.phrase)

            val handle = nativeCreate(
                model = model,
                sampleRate = SAMPLE_RATE,
                featureStepSizeMs = manifest.featureStepSizeMs,
                probabilityCutoff = manifest.probabilityCutoff,
                slidingWindowSize = manifest.slidingWindowSize,
            )

            return if (handle == 0L) UnavailableWakeWord(manifest.phrase) else MicroWakeWord(handle, manifest.phrase)
        }

        @JvmStatic
        private external fun nativeCreate(
            model: ByteBuffer,
            sampleRate: Int,
            featureStepSizeMs: Int,
            probabilityCutoff: Float,
            slidingWindowSize: Int,
        ): Long

        @JvmStatic
        private external fun nativeProcessAudio(handle: Long, audio: ShortArray): Boolean

        @JvmStatic
        private external fun nativeReset(handle: Long)

        @JvmStatic
        private external fun nativeDestroy(handle: Long)
    }
}

class UnavailableWakeWord(override val phrase: String) : WakeWordDetector {
    override val isReady: Boolean = false
    override fun processAudio(samples: ShortArray): Boolean = false
    override fun reset() = Unit
    override fun close() = Unit
}
