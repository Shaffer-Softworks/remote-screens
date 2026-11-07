package tech.shaffersoftworks.remotescreens.player.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import javax.inject.Singleton

private const val WEB_SOCKET_PING_INTERVAL_MS = 20_000L
private const val HTTP_REQUEST_TIMEOUT_MS = 15_000L

@InstallIn(SingletonComponent::class)
@Module
class NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = HTTP_REQUEST_TIMEOUT_MS }
        install(WebSockets) { pingIntervalMillis = WEB_SOCKET_PING_INTERVAL_MS }
    }
}
