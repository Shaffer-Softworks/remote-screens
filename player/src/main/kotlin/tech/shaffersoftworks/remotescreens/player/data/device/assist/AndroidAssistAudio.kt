package tech.shaffersoftworks.remotescreens.player.data.device.assist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tech.shaffersoftworks.remotescreens.player.R
import tech.shaffersoftworks.remotescreens.player.di.IoScope
import tech.shaffersoftworks.remotescreens.player.di.MainDispatcher
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
            PackageManager.PERMISSION_GRANTED

    override fun open(): MicrophoneSession {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO is not granted")
        }

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
        val isClosed = AtomicBoolean(false)

        return object : MicrophoneSession {
            override val samples: ReceiveChannel<ShortArray> = samples

            override fun close() {
                if (!isClosed.compareAndSet(false, true)) return

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

            if (read <= 0) continue

            samples.send(buffer.copyOf(read))
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

    override suspend fun playListeningChime() {
        runCatching { playCue() }
            .onFailure { error -> Log.w(CHIME_LOG_TAG, "Listening chime failed", error) }
    }

    /** The keypad never opens an AudioTrack for the FLAC. This is the same recording as PCM. */
    private suspend fun playCue() {
        val pcm = context.resources.openRawResource(R.raw.wake_word_triggered_cue).use { stream -> stream.readBytes() }

        if (pcm.isEmpty()) return

        val audioManager = context.getSystemService(AudioManager::class.java)
        val attributes = cueAttributes()

        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()

        audioManager.requestAudioFocus(focus)
        val track = cueTrack(pcm = pcm, attributes = attributes)

        try {
            val written = track.write(pcm, 0, pcm.size)

            if (written <= 0) return

            track.play()
            delay(cueLength(byteCount = written))
        } finally {
            runCatching { track.stop() }
            track.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
    }

    private suspend fun playOnMain(audio: ByteArray) {
        val source = ProgressiveMediaSource.Factory { ByteArrayDataSource(audio) }
            .createMediaSource(MediaItem.fromUri(SPEECH_URI))

        playItem(
            item = MediaItem.fromUri(SPEECH_URI),
            source = source,
            focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
        )
    }

    private suspend fun playItem(
        item: MediaItem,
        source: MediaSource? = null,
        focusGain: Int = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
    ) {
        val audioManager = context.getSystemService(AudioManager::class.java)

        val focus = AudioFocusRequest.Builder(focusGain)
            .setAudioAttributes(speechAttributes())
            .build()

        audioManager.requestAudioFocus(focus)
        val player = ExoPlayer.Builder(context).build()

        try {
            if (source != null) player.setMediaSource(source) else player.setMediaItem(item)

            player.prepare()
            player.play()
            awaitPlayback(player)
        } finally {
            player.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
    }

    /** Returns when the clip has been heard. A short file often ends in a buffer stall the player never closes. */
    private suspend fun awaitPlayback(player: ExoPlayer) = coroutineScope {
        val done = CompletableDeferred<Unit>()
        val listener = PlaybackEnd(done = done, scope = this)
        player.addListener(listener)

        try {
            if (player.playbackState == Player.STATE_ENDED) done.complete(Unit)

            withTimeoutOrNull(PLAYBACK_LIMIT) { done.await() }
        } finally {
            player.removeListener(listener)
            coroutineContext.cancelChildren()
        }
    }

    private companion object {
        const val SPEECH_URI = "memory://assist-reply"
    }
}

private fun speechAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

private const val CHIME_LOG_TAG = "AssistChime"
private const val CUE_SAMPLE_RATE = 48_000
private const val CUE_BYTES_PER_FRAME = 2
private const val CUE_TAIL_MS = 100
private const val MILLISECONDS_PER_SECOND = 1_000
private const val PLAYBACK_LIMIT_SEC = 60
private val PLAYBACK_LIMIT = PLAYBACK_LIMIT_SEC.seconds
private val PLAYBACK_TAIL = 1.seconds

private fun cueAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

private fun cueTrack(pcm: ByteArray, attributes: AudioAttributes): AudioTrack {
    val minBytes = AudioTrack.getMinBufferSize(
        CUE_SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )

    return AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(CUE_SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setBufferSizeInBytes(pcm.size.coerceAtLeast(minBytes))
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
}

private fun cueLength(byteCount: Int) =
    (byteCount / CUE_BYTES_PER_FRAME * MILLISECONDS_PER_SECOND / CUE_SAMPLE_RATE + CUE_TAIL_MS).milliseconds

/**
 * Finishes when the audio stops. ExoPlayer keeps a short Home Assistant clip in BUFFERING
 * and reports a duration much longer than the sound, so the reply bar used to wait that out.
 */
private class PlaybackEnd(
    private val done: CompletableDeferred<Unit>,
    private val scope: CoroutineScope,
) : Player.Listener {
    private var hasPlayed: Boolean = false
    private var tail: Job? = null

    override fun onPlaybackStateChanged(playbackState: Int) {
        val gaveUp = playbackState == Player.STATE_IDLE && hasPlayed

        if (playbackState == Player.STATE_ENDED || gaveUp) done.complete(Unit)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            hasPlayed = true
            tail?.cancel()
            tail = null

            return
        }

        if (!hasPlayed || tail != null) return

        tail = scope.launch {
            delay(PLAYBACK_TAIL)
            done.complete(Unit)
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        done.complete(Unit)
    }
}
