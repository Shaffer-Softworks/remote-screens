package tech.shaffersoftworks.remotescreens.player.di

import android.content.Context
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    @HaImageLoaderQualifier
    fun provideHaImageLoader(
        @ApplicationContext context: Context,
        httpClient: HttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, MEMORY_CACHE_MAX_SIZE_PERCENT)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(DISK_CACHE_DIR).toOkioPath())
                .maxSizeBytes(DISK_CACHE_MAX_SIZE_BYTES)
                .build()
        }
        .components {
            add(KtorNetworkFetcherFactory(
                httpClient = { httpClient },
                cacheStrategy = { CacheControlCacheStrategy() },
            ))
        }
        .eventListener(object : EventListener() {
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
        .build()
        .also { imageLoader -> SingletonImageLoader.setSafe { imageLoader } }

    private const val LOG_TAG = "RPI/IMG"
    private const val MEMORY_CACHE_MAX_SIZE_PERCENT = 0.12
    private const val DISK_CACHE_DIR = "image_cache"
    private const val DISK_CACHE_MAX_SIZE_BYTES = 64L * 1024L * 1024L
}
