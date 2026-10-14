package dev.remotescreens.player.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What the player remembers between starts: who it is, where the add-on is, and the token it was given. */
@Singleton
class PlayerPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** `host:port` of the add-on, found over mDNS or typed in. */
    val server: Flow<String?> = dataStore.data.map { preferences -> preferences[SERVER_KEY] }

    val token: Flow<String?> = dataStore.data.map { preferences -> preferences[TOKEN_KEY] }

    val screenName: Flow<String?> = dataStore.data.map { preferences -> preferences[SCREEN_NAME_KEY] }

    /** A random id made on first use and kept for good, so the add-on knows this screen again. */
    suspend fun deviceId(): String {
        val updatedPreferences = dataStore.edit { preferences ->
            if (preferences[DEVICE_ID_KEY] == null) preferences[DEVICE_ID_KEY] = UUID.randomUUID().toString()
        }
        return checkNotNull(updatedPreferences[DEVICE_ID_KEY])
    }

    suspend fun setServer(server: String) {
        dataStore.edit { preferences -> preferences[SERVER_KEY] = server }
    }

    suspend fun setPairing(token: String, screenName: String?) {
        dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
            if (screenName != null) preferences[SCREEN_NAME_KEY] = screenName else preferences.remove(SCREEN_NAME_KEY)
        }
    }

    suspend fun forget() {
        dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(SCREEN_NAME_KEY)
        }
    }

    private companion object {
        val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        val SERVER_KEY = stringPreferencesKey("server")
        val TOKEN_KEY = stringPreferencesKey("token")
        val SCREEN_NAME_KEY = stringPreferencesKey("screen_name")
    }
}
