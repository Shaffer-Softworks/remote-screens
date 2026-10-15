package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import timber.log.Timber

/** The card's and the alert's camera images are both rounded by this much. */
private const val CORNER_DP = 16f
private const val LIVE_MAX_SPEED = 8f
private const val MAX_BUFFER_MS = 30_000

/**
 * Plays one camera's live HLS video from the add-on, rounded like the image it covers. It stays see-through until its
 * first frame, so the image's picture shows until then, and it never takes a touch, so taps reach the document.
 */
@SuppressLint("ViewConstructor")
@OptIn(UnstableApi::class)
class LiveVideoView(context: Context) : FrameLayout(context) {
    private val aspectRatioFrame = AspectRatioFrameLayout(context)
    private val textureView = TextureView(context)
    private var player: ExoPlayer? = null

    /** What plays now; null when stopped. */
    var video: SlotVideo? = null
        private set

    /** Playback stopped by itself, with the add-on's HTTP status when that was the cause. */
    var onFailed: (video: SlotVideo, httpCode: Int?) -> Unit = { _, _ -> }

    private val listener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.height > 0) {
                aspectRatioFrame.setAspectRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
            }
        }

        override fun onRenderedFirstFrame() {
            alpha = 1f
        }

        override fun onPlayerError(error: PlaybackException) {
            val failedVideo = video ?: return
            val httpCode = error.httpResponseCode()
            Timber.w("Video stopped (HTTP %s): %s", httpCode, error.message)
            stop()
            onFailed(failedVideo, httpCode)
        }
    }

    init {
        // A TextureView, unlike a SurfaceView, is clipped by the rounded outline.
        aspectRatioFrame.addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(aspectRatioFrame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        val cornerPx = CORNER_DP * resources.displayMetrics.density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
            }
        }
        clipToOutline = true
        alpha = 0f
    }

    fun play(video: SlotVideo) {
        stop()
        this.video = video
        aspectRatioFrame.resizeMode = if (video.crop) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${video.token}"))
        // Start as soon as anything has loaded and catch up quickly, as a camera is only worth watching live.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(0, MAX_BUFFER_MS, 0, 0)
            .build()
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory).setLiveMaxSpeed(LIVE_MAX_SPEED))
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f
                setVideoTextureView(textureView)
                addListener(listener)
                setMediaItem(MediaItem.fromUri(video.url))
                prepare()
                playWhenReady = true
            }
    }

    fun stop() {
        player?.release()
        player = null
        video = null
        alpha = 0f
    }
}

private fun PlaybackException.httpResponseCode(): Int? {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
        cause = cause.cause
    }
    return null
}
