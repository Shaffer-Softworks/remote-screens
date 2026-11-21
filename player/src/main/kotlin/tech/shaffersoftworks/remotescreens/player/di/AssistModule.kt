package tech.shaffersoftworks.remotescreens.player.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tech.shaffersoftworks.remotescreens.player.data.device.assist.AndroidAssistForeground
import tech.shaffersoftworks.remotescreens.player.data.device.assist.AndroidMicrophone
import tech.shaffersoftworks.remotescreens.player.data.device.assist.AssetWakeWords
import tech.shaffersoftworks.remotescreens.player.data.device.assist.AssistForeground
import tech.shaffersoftworks.remotescreens.player.data.device.assist.AssistTurnClient
import tech.shaffersoftworks.remotescreens.player.data.device.assist.KtorAssistTurnClient
import tech.shaffersoftworks.remotescreens.player.data.device.assist.Media3SpeechPlayback
import tech.shaffersoftworks.remotescreens.player.data.device.assist.Microphone
import tech.shaffersoftworks.remotescreens.player.data.device.assist.SpeechPlayback
import tech.shaffersoftworks.remotescreens.player.data.device.assist.WakeWordDetectors

@InstallIn(SingletonComponent::class)
@Module
interface AssistModule {
    @Binds
    fun bindMicrophone(microphone: AndroidMicrophone): Microphone

    @Binds
    fun bindWakeWords(wakeWords: AssetWakeWords): WakeWordDetectors

    @Binds
    fun bindTurns(turns: KtorAssistTurnClient): AssistTurnClient

    @Binds
    fun bindPlayback(playback: Media3SpeechPlayback): SpeechPlayback

    @Binds
    fun bindForeground(foreground: AndroidAssistForeground): AssistForeground
}
