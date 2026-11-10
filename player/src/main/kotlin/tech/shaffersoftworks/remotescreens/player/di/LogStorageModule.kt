package tech.shaffersoftworks.remotescreens.player.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tech.shaffersoftworks.remotescreens.player.data.diagnostics.LogDao
import tech.shaffersoftworks.remotescreens.player.data.diagnostics.LogDatabase

@Module
@InstallIn(SingletonComponent::class)
object LogStorageModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LogDatabase =
        Room.databaseBuilder(context, LogDatabase::class.java, "app-logs.db").build()

    @Provides
    fun logs(database: LogDatabase): LogDao = database.logs()
}
