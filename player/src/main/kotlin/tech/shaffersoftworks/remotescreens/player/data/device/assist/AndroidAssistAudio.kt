package tech.shaffersoftworks.remotescreens.player.data.device.assist

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tech.shaffersoftworks.remotescreens.player.di.IoScope
import tech.shaffersoftworks.remotescreens.player.di.MainDispatcher
import javax.inject.Inject
import kotlin.coroutines.resume

private const val SAMPLE_RATE = 16_000
private const val CHUNK_SAMPLES = 320
private const val CHANNEL_CAPACITY = 8
private const val MIN_BUFFER_CHUNKS = 4

/** 16 kHz mono PCM. Platform echo and noise effects attach when this device offers them. */
class AndroidMicrophone @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoScope private val scope: CoroutineScope,
) : Microphone {
    override val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun open(): MicrophoneSession {
        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBytes.coerceAtLeast(CHUNK_SAMPLES * BYTES_PER_SAMPLE * MIN_BUFFER_CHUNKS),
        )

        val effects = platformEffects(record.audioSessionId)
        record.startRecording()
        val samples = Channel<ShortArray>(capacity = CHANNEL_CAPACITY)
        val job = scope.launch { readLoop(record = record, samples = samples) }

        return object : MicrophoneSession {
            override val samples: ReceiveChannel<ShortArray> = samples

            override fun close() {
                job.cancel()
                samples.close()
                effects.forEach { it.release() }
                runCatching { record.stop() }
                record.release()
            }
        }
    }

    private suspend fun readLoop(record: AudioRecord, samples: Channel<ShortArray>) {
        val buffer = ShortArray(CHUNK_SAMPLES)

        while (coroutineContext.isActive) {
            val read = record.read(buffer, 0, buffer.size)

            if (read > 0) samples.send(buffer.copyOf(read))
        }
    }
}

private const val BYTES_PER_SAMPLE = 2

private fun platformEffects(sessionId: Int): List<android.media.audiofx.AudioEffect> = buildList {
    if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId)?.let(::add)

    if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId)?.let(::add)
}

/** Plays the spoken reply and holds audio focus until it ends. The microphone is already closed. */
@UnstableApi
class Media3SpeechPlayback @Inject constructor(
    @ApplicationContext private val context: Context,
    @MainDispatcher private val main: CoroutineDispatcher,
) : SpeechPlayback {
    override suspend fun play(mimeType: String, audio: ByteArray) {
        if (audio.isEmpty()) return

        withContext(main) { playOnMain(audio) }
    }

    private suspend fun playOnMain(audio: ByteArray) {
        val audioManager = context.getSystemService(AudioManager::class.java)

        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(speechAttributes())
            .build()

        audioManager.requestAudioFocus(focus)
        val player = ExoPlayer.Builder(context).build()

        try {
            val source = ProgressiveMediaSource.Factory(DataSource.Factory { ByteArrayDataSource(audio) })
                .createMediaSource(MediaItem.fromUri(SPEECH_URI))

            player.setMediaSource(source)
            player.prepare()
            player.play()
            awaitEnded(player)
        } finally {
            player.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
    }

    private suspend fun awaitEnded(player: ExoPlayer) = suspendCancellableCoroutine { continuation ->
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && continuation.isActive) continuation.resume(Unit)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }

        player.addListener(listener)
        continuation.invokeOnCancellation { player.removeListener(listener) }
    }

    private companion object {
        const val SPEECH_URI = "memory://assist-reply"
    }
}

private fun speechAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()
