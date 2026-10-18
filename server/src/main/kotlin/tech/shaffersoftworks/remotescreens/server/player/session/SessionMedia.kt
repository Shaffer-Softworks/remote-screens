package tech.shaffersoftworks.remotescreens.server.player.session

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.LiveVideo
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.VarValue
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.Base64

private const val MS_PER_SECOND = 1000L

/** Camera video streams and picture bitmaps for a [SessionHost]. */
internal class SessionMedia(
    private val host: SessionHost,
) {
    private val logger = LoggerFactory.getLogger(SessionMedia::class.java)
    private val context get() = host.context
    private val shownDocuments get() = host.shownDocuments

    /**
     * Starts [video]'s stream and tells the player where it is. A camera card without a stream falls back to its
     * still; an alert keeps its still under the overlay as before.
     */
    fun sendVideo(shown: ShownDocument, video: LiveVideo) {
        context.scope.launch {
            if (!host.supportsVideo) {
                showCameraStillFallback(shown, video)
                return@launch
            }
            val path = context.cameraStreamService.hlsPath(video.entityId)
            if (shownDocuments[shown.slot] !== shown) return@launch
            if (path == null) {
                showCameraStillFallback(shown, video)
                return@launch
            }
            host.send(
                ServerMessage.Video(shown.slot, shown.revision, video.componentId, path, video.crop),
            )
        }
    }

    fun restartVideo(message: PlayerMessage.VideoFailed) {
        val shown = shownDocuments[message.slot]?.takeIf { it.revision == message.rev } ?: return
        val video = shown.renderedDoc.docContext.videos.firstOrNull { it.componentId == message.componentId } ?: return
        val restarts = shown.videoRestarts.getOrDefault(video.componentId, 0)
        if (restarts < MAX_VIDEO_RESTARTS) {
            shown.videoRestarts[video.componentId] = restarts + 1
            sendVideo(shown, video)
        } else {
            logger.info(
                "Video of {} keeps stopping (HTTP {}); falling back to still",
                video.entityId,
                message.httpCode,
            )
            context.scope.launch { showCameraStillFallback(shown, video) }
        }
    }

    /** Hides the camera card's spinner and shows its still; no-op for alerts (they never declare these vars). */
    suspend fun showCameraStillFallback(shown: ShownDocument, video: LiveVideo) {
        val waiting = Vars.entity(video.entityId, Vars.KEY_CAM_WAITING)
        if (!shown.renderedDoc.docContext.declares(waiting)) return
        if (shownDocuments[shown.slot] !== shown) return
        host.send(
            ServerMessage.Vars(
                listOf(
                    VarValue.int(waiting, Vars.GONE),
                    VarValue.int(Vars.entity(video.entityId, Vars.KEY_CAM_STILL), Vars.VISIBLE),
                ),
            ),
        )
    }

    fun sendPictures(shown: ShownDocument, states: Map<String, EntityState>, isForced: Boolean) {
        val nowMs = System.currentTimeMillis()
        for (picture in shown.renderedDoc.docContext.pictures) {
            val sourceKey = context.pictureRepository.sourceKey(picture, states)
            val lastSentMs = shown.pictureSentAtMs[picture.name] ?: 0
            val isDue = picture.refreshSeconds > 0 && nowMs - lastSentMs >= picture.refreshSeconds * MS_PER_SECOND
            if (!isForced && !isDue && shown.pictureKeys[picture.name] == sourceKey) continue
            shown.pictureKeys[picture.name] = sourceKey
            shown.pictureSentAtMs[picture.name] = nowMs
            context.scope.launch {
                val bytes = context.pictureRepository.load(picture, states) ?: return@launch
                if (shownDocuments[shown.slot] === shown) {
                    host.send(
                        ServerMessage.Bitmap(
                            picture.name,
                            Base64.getEncoder().encodeToString(bytes),
                        ),
                    )
                }
            }
        }
    }

    /** Asks Home Assistant for an HLS path in the background so [sendVideo] can reuse a warm stream. */
    fun warmCameraStream(entityId: String? = null, kind: String? = null, camera: String? = null) {
        if (!host.supportsVideo) return
        val id = when {
            !camera.isNullOrBlank() -> camera
            entityId?.startsWith("camera.") == true -> entityId
            kind == "camera" && !entityId.isNullOrBlank() -> entityId
            else -> return
        }
        context.scope.launch {
            runCatching { context.cameraStreamService.hlsPath(id) }
        }
    }

    companion object {
        const val MAX_VIDEO_RESTARTS = 3
    }
}
