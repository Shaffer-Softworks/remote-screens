package tech.shaffersoftworks.remotescreens.player.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    /**
     * The screen's Kiosk lock setting, kept so the lock comes back after a restart before the add-on answers. QR
     * enrollment turns it on before the screen is even added.
     */
    val isKioskEnabled: Flow<Boolean> = dataStore.data.map { preferences -> preferences[KIOSK_ENABLED_KEY] ?: false }

    /** Unlocked on the tablet with the PIN. Kept on disk so a crash does not lock a tablet unlocked on purpose. */
    val isKioskSuppressed: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[KIOSK_SUPPRESSED_KEY] ?: false }

    /** Set by QR enrollment: ask once to leave battery optimization before lock task hides that dialog. */
    val isBatteryPromptPending: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[BATTERY_PROMPT_PENDING_KEY] ?: false }

    /** A random id kept until the user forgets the pairing, so the add-on knows this screen again. */
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
            if (screenName != null) {
                preferences[SCREEN_NAME_KEY] = screenName
            } else {
                preferences.remove(
                    SCREEN_NAME_KEY,
                )
            }
        }
    }

    /** Turning the kiosk on again from Home Assistant also ends an unlock made on the tablet. */
    suspend fun setKioskEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            val wasEnabled = preferences[KIOSK_ENABLED_KEY] ?: false
            preferences[KIOSK_ENABLED_KEY] = isEnabled
            if (isEnabled && !wasEnabled) preferences.remove(KIOSK_SUPPRESSED_KEY)
        }
    }

    suspend fun setKioskSuppressed(isSuppressed: Boolean) {
        dataStore.edit { preferences -> preferences[KIOSK_SUPPRESSED_KEY] = isSuppressed }
    }

    suspend fun setBatteryPromptPending(isPending: Boolean) {
        dataStore.edit { preferences -> preferences[BATTERY_PROMPT_PENDING_KEY] = isPending }
    }

    /**
     * Clear the pairing. A local reset also needs a fresh identity: the server may still require the old
     * identity's token. Keep the identity when acknowledging a server-initiated removal on the current session.
     */
    suspend fun forget(resetDeviceId: Boolean = false) {
        dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(SCREEN_NAME_KEY)
            if (resetDeviceId) preferences[DEVICE_ID_KEY] = UUID.randomUUID().toString()
        }
    }

    private companion object {
        val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        val SERVER_KEY = stringPreferencesKey("server")
        val TOKEN_KEY = stringPreferencesKey("token")
        val SCREEN_NAME_KEY = stringPreferencesKey("screen_name")
        val KIOSK_ENABLED_KEY = booleanPreferencesKey("kiosk_enabled")
        val KIOSK_SUPPRESSED_KEY = booleanPreferencesKey("kiosk_suppressed")
        val BATTERY_PROMPT_PENDING_KEY = booleanPreferencesKey("battery_prompt_pending")
    }
}
