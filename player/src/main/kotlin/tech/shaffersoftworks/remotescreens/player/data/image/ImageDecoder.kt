package tech.shaffersoftworks.remotescreens.player.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tech.shaffersoftworks.remotescreens.player.di.DefaultDispatcher
import tech.shaffersoftworks.remotescreens.player.di.HaImageLoader
import tech.shaffersoftworks.remotescreens.player.utils.decodeBase64OrNull
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Decodes the base64 pictures the add-on sends for named bitmaps. */
@Singleton
class ImageDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @HaImageLoader val haImageLoader: ImageLoader
) {
    suspend fun decodeBitmap(base64Data: String): Bitmap? =
        withContext(defaultDispatcher) {
            val bytes = decodeBase64OrNull(base64Data)
                ?: return@withContext null

            val contentHash = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }

            val request = ImageRequest.Builder(context)
                .data(ByteBuffer.wrap(bytes))
                .memoryCacheKey("remote-bitmap:$contentHash")
                .allowHardware(false)
                .size(Size.ORIGINAL)
                .build()

            val result = haImageLoader.execute(request) as? SuccessResult
                ?: return@withContext null

            (result.drawable as? BitmapDrawable)?.bitmap
        }
}
