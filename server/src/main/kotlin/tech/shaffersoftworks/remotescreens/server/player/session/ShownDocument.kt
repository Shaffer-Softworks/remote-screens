package tech.shaffersoftworks.remotescreens.server.player.session

import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.shared.Slot
import tech.shaffersoftworks.remotescreens.shared.VarValue

/** A document shown in [slot], with what was last sent for it. */
internal class ShownDocument(
    val slot: Slot,
    var renderedDoc: RenderedDoc,
    var rebuild: suspend () -> RenderedDoc?,
) {
    var revision = 0
    var sentVars: Map<String, VarValue> = emptyMap()
    /** Source key last successfully sent as a bitmap. */
    val pictureKeys = HashMap<String, String?>()
    /** Wall time of the last successful bitmap send. */
    val pictureSentAtMs = HashMap<String, Long>()
    /** Source key of an in-flight load; cleared when that load finishes. */
    val pictureInflightKeys = HashMap<String, String?>()
    /** Component id to how many times its video was started again after the player said it stopped. */
    val videoRestarts = HashMap<Int, Int>()

    /**
     * Starts a fetch for [sourceKey] when it is not already in flight and either forced, due, or not yet
     * successfully sent. Marks the slot in-flight; call [finishPictureFetch] when the load ends.
     */
    fun beginPictureFetch(
        name: String,
        sourceKey: String?,
        nowMs: Long,
        isForced: Boolean,
        refreshSeconds: Int,
    ): Boolean {
        val lastSentMs = pictureSentAtMs[name] ?: 0L
        val isDue = refreshSeconds > 0 && nowMs - lastSentMs >= refreshSeconds * MS_PER_SECOND
        val shouldSkip = pictureInflightKeys[name] == sourceKey ||
            (!isForced && !isDue && pictureKeys[name] == sourceKey)
        if (!shouldSkip) {
            pictureInflightKeys[name] = sourceKey
        }
        return !shouldSkip
    }

    /** Records a successful send, or clears the in-flight mark after a failure / cancel. */
    fun finishPictureFetch(
        name: String,
        sourceKey: String?,
        nowMs: Long,
        isSuccess: Boolean,
    ) {
        val isCurrent = pictureInflightKeys[name] == sourceKey
        if (isCurrent) {
            pictureInflightKeys.remove(name)
        }
        if (isSuccess && isCurrent) {
            pictureKeys[name] = sourceKey
            pictureSentAtMs[name] = nowMs
        }
    }

    private companion object {
        const val MS_PER_SECOND = 1000L
    }
}
