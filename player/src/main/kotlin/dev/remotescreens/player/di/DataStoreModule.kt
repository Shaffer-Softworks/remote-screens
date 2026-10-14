package dev.remotescreens.player.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** Players installed before DataStore kept their identity and token in these SharedPreferences. */
private const val LEGACY_SHARED_PREFERENCES_NAME = "player"
private const val PREFERENCES_DATA_STORE_NAME = "player_preferences"

@InstallIn(SingletonComponent::class)
@Module
class DataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @IoScope ioScope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, LEGACY_SHARED_PREFERENCES_NAME)),
        scope = ioScope,
        produceFile = { context.preferencesDataStoreFile(PREFERENCES_DATA_STORE_NAME) },
    )
}
