package tech.shaffersoftworks.remotescreens.player.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tech.shaffersoftworks.remotescreens.player.data.device.telemetry.DeviceTelemetrySource
import tech.shaffersoftworks.remotescreens.player.data.device.telemetry.GoogleLocationAccuracy
import tech.shaffersoftworks.remotescreens.player.data.device.telemetry.LocationAccuracy
import tech.shaffersoftworks.remotescreens.player.data.device.telemetry.TelemetrySource

@InstallIn(SingletonComponent::class)
@Module
interface TelemetryModule {
    @Binds
    fun bindTelemetrySource(source: DeviceTelemetrySource): TelemetrySource

    @Binds
    fun bindLocationAccuracy(accuracy: GoogleLocationAccuracy): LocationAccuracy
}
