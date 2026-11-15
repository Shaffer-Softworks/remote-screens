package tech.shaffersoftworks.remotescreens.player.data.image

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.CachePolicy
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

            // Named bitmaps are already held in RemoteScreenState / Remote Compose. Caching by
            // content hash retained every camera still refresh (up to Coil's 12% RAM cap) and
            // drove LMK on low-RAM tablets (ANDROID-43 / BLU M8L).
            val request = ImageRequest.Builder(context)
                .data(bytes)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .allowHardware(false)
                .size(Size.ORIGINAL)
                .build()

            val result = haImageLoaderQualifier.execute(request) as? SuccessResult
                ?: return@withContext null

            result.image.toBitmap()
        }
}
