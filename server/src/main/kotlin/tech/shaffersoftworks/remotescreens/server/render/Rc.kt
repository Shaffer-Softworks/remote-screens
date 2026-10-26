@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.RemotePathBase
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.createRcBuffer
import androidx.compose.remote.creation.profile.Profile

/**
 * The only door to the Remote Compose creation library (still alpha). Everything else builds through [document],
 * so an upgrade of the library touches this file and the tile renderers' DSL calls, nothing else.
 */
object Rc {
    /** The prefix the player's `setUserLocal*` puts before a name. */
    const val USER = "USER:"

    private const val ERROR_SNIPPET_LENGTH = 40

    /**
     * AndroidX operations plus the experimental ones: long press (`MODIFIER_MULTI_CLICK`) and `Flow` are still only
     * there in alpha22. The player reads the same profile from the header.
     */
    const val PROFILES = RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL

    val profile: Profile = Profile(
        CoreDocument.DOCUMENT_API_LEVEL,
        PROFILES,
        Platform,
    ) { info, p, callback ->
        RemoteComposeWriter(info, null, p, callback)
    }

    fun document(widthPx: Int, heightPx: Int, description: String, content: RcScope.() -> Unit): ByteArray =
        createRcBuffer(
            RcProfile(profile),
            RemoteComposeWriter.HTag(Header.DOC_WIDTH, widthPx),
            RemoteComposeWriter.HTag(Header.DOC_HEIGHT, heightPx),
            RemoteComposeWriter.HTag(Header.DOC_CONTENT_DESCRIPTION, description),
            RemoteComposeWriter.HTag(Header.DOC_PROFILES, PROFILES),
        ) { content() }

    /** The player capability that says it reads documents [compress]ed (alpha22 players do). */
    const val DEFLATE = "deflate"

    /** [document] with everything after its header deflated, for players with the [DEFLATE] capability. */
    fun compress(document: ByteArray): ByteArray = Header.compressDocument(document, document.size)

    /**
     * A path from `M`, `L`, `C` and `Z` with absolute coordinates, as `tools/generate_icons.py` writes them.
     * The library's own parser reads neither relative commands nor `V`, so icons never go through it.
     *
     * [scale] multiplies every coordinate. Icons are authored on a small grid; scaling them with the canvas
     * makes the player rasterise that grid and stretch the bitmap. Scaling the coordinates keeps the path sharp.
     */
    fun path(commands: String, scale: Float = 1f): RemotePathBase {
        val remotePath = RemotePathBase()
        val tokens = commands.split(' ')
        var i = 0
        fun nextNumber() = tokens[i++].toFloat() * scale
        while (i < tokens.size) {
            when (tokens[i++]) {
                "M" -> remotePath.moveTo(nextNumber(), nextNumber())
                "L" -> remotePath.lineTo(nextNumber(), nextNumber())
                "C" -> remotePath.cubicTo(
                    nextNumber(),
                    nextNumber(),
                    nextNumber(),
                    nextNumber(),
                    nextNumber(),
                    nextNumber(),
                )
                "Z" -> remotePath.close()
                "" -> Unit
                else -> throw IllegalArgumentException(
                    "Unexpected path command at $i in ${commands.take(ERROR_SNIPPET_LENGTH)}",
                )
            }
        }
        return remotePath
    }

    /** The JVM services, except that any path that can write itself out is taken (alpha20 takes only its own class). */
    private object Platform : RcPlatformServices by JvmRcPlatformServices() {
        override fun pathToFloatArray(path: Any): FloatArray? =
            (path as? RcPlatformServices.RcPathArrayCreator)?.createFloatArray()
    }
}
