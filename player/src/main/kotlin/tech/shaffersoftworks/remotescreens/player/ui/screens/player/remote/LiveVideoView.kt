package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlin.math.roundToInt
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import timber.log.Timber


/** The card's and the alert's camera images are both rounded by this much. */
private const val CORNER_DP = 16f
private const val LIVE_MAX_SPEED = 8f
private const val MAX_BUFFER_MS = 30_000
private const val CONNECT_TIMEOUT_MS = 3_000
private const val READ_TIMEOUT_MS = 5_000
/** Stay near the live edge so the first playable parts are enough to start. */
private const val LIVE_TARGET_OFFSET_MS = 2_000L
private const val LIVE_MIN_OFFSET_MS = 500L
private const val LIVE_MAX_OFFSET_MS = 5_000L

/** Compose owns clipping, aspect ratio, and lifetime; only the texture is a native view. */
@Composable
@OptIn(UnstableApi::class)
internal fun LiveVideoView(video: SlotVideo, onFailed: (SlotVideo, Int?) -> Unit) {
    if (LocalInspectionMode.current) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(CORNER_DP.dp)).background(Color.Black))
        return
    }
    val context = LocalContext.current
    val texture = remember { TextureView(context) }
    var aspectRatio by remember(video) { mutableStateOf(1f) }
    var ready by remember(video) { mutableStateOf(false) }
    val currentOnFailed by rememberUpdatedState(onFailed)
    DisposableEffect(video, texture) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    aspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() { ready = true }

            override fun onPlayerError(error: PlaybackException) {
                Timber.w("Video stopped (HTTP %s): %s", error.httpResponseCode(), error.message)
                ready = false
                currentOnFailed(video, error.httpResponseCode())
            }
        }
        val player = createVideoPlayer(context, video)
        player.addListener(listener)
        player.setVideoTextureView(texture)
        onDispose {
            player.removeListener(listener)
            player.clearVideoTextureView(texture)
            player.release()
        }
    }
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(CORNER_DP.dp))
            .alpha(if (ready) 1f else 0f).background(Color.Black),
    ) {
        Layout(
            content = { AndroidView(factory = { texture }) },
            modifier = Modifier.fillMaxSize().clipToBounds(),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val fillWidth = (width.toFloat() / height > aspectRatio) == video.crop
            val videoWidth = if (fillWidth) width else (height * aspectRatio).roundToInt()
            val videoHeight = if (fillWidth) (width / aspectRatio).roundToInt() else height
            val surface = measurables.single().measure(
                Constraints.fixed(videoWidth.coerceAtLeast(1), videoHeight.coerceAtLeast(1)),
            )
            layout(width, height) { surface.place((width - videoWidth) / 2, (height - videoHeight) / 2) }
        }
    }
}

@OptIn(UnstableApi::class)
private fun createVideoPlayer(context: android.content.Context, video: SlotVideo): ExoPlayer {
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${video.token}"))
        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
        .setReadTimeoutMs(READ_TIMEOUT_MS)
        .setAllowCrossProtocolRedirects(true)
    // Start as soon as anything has loaded and catch up quickly, as a camera is only worth watching live.
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(0, MAX_BUFFER_MS, 0, 0)
        .build()
    val mediaItem = MediaItem.Builder()
        .setUri(video.url)
        .setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                .setMinOffsetMs(LIVE_MIN_OFFSET_MS)
                .setMaxOffsetMs(LIVE_MAX_OFFSET_MS)
                .setMaxPlaybackSpeed(LIVE_MAX_SPEED)
                .build(),
        )
        .build()
    val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(httpDataSourceFactory).setLiveMaxSpeed(LIVE_MAX_SPEED),
        )
        .setLoadControl(loadControl)
        .build()
        .apply {
            volume = 0f
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }

    return player
}

private fun PlaybackException.httpResponseCode(): Int? {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
        cause = cause.cause
    }
    return null
}

@Preview(name = "Video surface", widthDp = 800, heightDp = 480)
@Composable
private fun LiveVideoViewPreview() {
    RemoteScreensTheme {
        LiveVideoView(SlotVideo(1, 1, "", "", false)) { _, _ -> }
    }
}
