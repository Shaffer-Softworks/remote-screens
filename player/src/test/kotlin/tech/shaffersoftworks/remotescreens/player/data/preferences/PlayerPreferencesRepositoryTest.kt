package tech.shaffersoftworks.remotescreens.player.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerPreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `local forget replaces identity and clears pairing while preserving server and kiosk settings`() = runTest {
        val repository = PlayerPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                temporaryFolder.newFolder().resolve("player.preferences_pb")
            },
        )
        val originalId = repository.deviceId()
        repository.setServer("10.0.0.5:8100")
        repository.setPairing("secret-token", "Kitchen")
        repository.setKioskEnabled(true)

        repository.forget(resetDeviceId = true)

        val newId = repository.deviceId()
        assertNotEquals(originalId, newId)
        assertEquals(newId, repository.deviceId())
        assertNull(repository.token.first())
        assertNull(repository.screenName.first())
        assertEquals("10.0.0.5:8100", repository.server.first())
        assertEquals(true, repository.isKioskEnabled.first())

        repository.setPairing("new-token", "Kitchen")
        assertEquals(newId, repository.deviceId())
        assertEquals("new-token", repository.token.first())
    }

    @Test
    fun `server removal clears pairing without changing current session identity`() = runTest {
        val repository = PlayerPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                temporaryFolder.newFolder().resolve("player.preferences_pb")
            },
        )
        val originalId = repository.deviceId()
        repository.setPairing("secret-token", "Kitchen")

        repository.forget()

        assertEquals(originalId, repository.deviceId())
        assertNull(repository.token.first())
        assertNull(repository.screenName.first())
    }
}
