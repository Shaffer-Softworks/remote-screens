package tech.shaffersoftworks.remotescreens.player.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import tech.shaffersoftworks.remotescreens.player.di.DefaultDispatcher
import tech.shaffersoftworks.remotescreens.player.utils.decodeBase64OrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Decodes the base64 pictures the add-on sends for named bitmaps. */
@Singleton
class ImageDecoder @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    /** A PNG or JPEG, decoded off the main thread. Null when the data is not a picture. */
    suspend fun decodeBitmap(base64Data: String): Bitmap? = withContext(defaultDispatcher) {
        val bytes = decodeBase64OrNull(base64Data) ?: return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
