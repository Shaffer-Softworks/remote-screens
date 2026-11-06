package tech.shaffersoftworks.remotescreens.player.data.image

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import coil3.size.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tech.shaffersoftworks.remotescreens.player.di.DefaultDispatcher
import tech.shaffersoftworks.remotescreens.player.di.HaImageLoaderQualifier
import tech.shaffersoftworks.remotescreens.player.utils.decodeBase64OrNull
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Decodes the base64 pictures the add-on sends for named bitmaps. */
@Singleton
class ImageDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @HaImageLoaderQualifier val haImageLoaderQualifier: ImageLoader,
) {
    suspend fun decodeBitmap(base64Data: String): Bitmap? =
        withContext(defaultDispatcher) {
            val bytes = decodeBase64OrNull(base64Data)
                ?: return@withContext null

            val contentHash = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }

            val request = ImageRequest.Builder(context)
                .data(bytes)
                .memoryCacheKey("remote-bitmap:$contentHash")
                .allowHardware(false)
                .size(Size.ORIGINAL)
                .build()

            val result = haImageLoaderQualifier.execute(request) as? SuccessResult
                ?: return@withContext null

            result.image.toBitmap()
        }
}
