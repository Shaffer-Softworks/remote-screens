package tech.shaffersoftworks.remotescreens.player.di

import android.content.Context
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    @HaImageLoaderQualifier
    fun provideHaImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        Timber.tag(LOG_TAG).i("provideHaImageLoader() — building image loader")
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(MEMORY_CACHE_MAX_SIZE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(DISK_CACHE_DIR))
                    .maxSizeBytes(DISK_CACHE_MAX_SIZE_BYTES)
                    .build()
            }
            .eventListener(object : EventListener {
                override fun onStart(request: ImageRequest) {
                    Timber.tag(LOG_TAG).d("onStart data=%s", request.data)
                }

                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    Timber.tag(LOG_TAG).d("onSuccess data=%s ds=%s", request.data, result.dataSource)
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    Timber.tag(LOG_TAG).w(result.throwable, "onError data=%s", request.data)
                }
            })
            .okHttpClient {
                Timber.tag(LOG_TAG).i("okHttpClient factory invoked")
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        try {
                            val response = chain.proceed(request)
                            if (!response.isSuccessful) {
                                Timber.tag(LOG_TAG).w(
                                    "image %d %s -> %s",
                                    response.code,
                                    response.message,
                                    request.url
                                )
                            } else {
                                Timber.tag(LOG_TAG).v(
                                    "image %d %s",
                                    response.code,
                                    request.url
                                )
                            }
                            response
                        } catch (e: Exception) {
                            Timber.tag(LOG_TAG).w(e, "image FAIL %s", request.url)
                            throw e
                        }
                    }
                    .build()
            }
            .build()
            .also { Coil.setImageLoader(it) }
    }

    private const val LOG_TAG = "RPI/IMG"
    private const val MEMORY_CACHE_MAX_SIZE_PERCENT = 0.12
    private const val DISK_CACHE_DIR = "image_cache"
    private const val DISK_CACHE_MAX_SIZE_BYTES = 64L * 1024L * 1024L
}
