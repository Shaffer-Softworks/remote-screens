package dev.remotescreens.player.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import javax.inject.Singleton

private const val WEB_SOCKET_PING_INTERVAL_MS = 20_000L

@InstallIn(SingletonComponent::class)
@Module
class NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(WebSockets) { pingIntervalMillis = WEB_SOCKET_PING_INTERVAL_MS }
    }
}
