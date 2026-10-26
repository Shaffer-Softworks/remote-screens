package tech.shaffersoftworks.remotescreens.player.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tech.shaffersoftworks.remotescreens.player.data.device.intercom.AndroidWebRtcAudio
import tech.shaffersoftworks.remotescreens.player.data.device.intercom.KtorSignalingLink
import tech.shaffersoftworks.remotescreens.player.data.device.intercom.SignalingLink
import tech.shaffersoftworks.remotescreens.player.data.device.intercom.WebRtcAudio

@InstallIn(SingletonComponent::class)
@Module
interface IntercomModule {
    @Binds
    fun bindWebRtcAudio(audio: AndroidWebRtcAudio): WebRtcAudio

    @Binds
    fun bindSignalingLink(link: KtorSignalingLink): SignalingLink
}
