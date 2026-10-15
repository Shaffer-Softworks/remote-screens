package tech.shaffersoftworks.remotescreens.player.ui.screens.provisioning

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import tech.shaffersoftworks.remotescreens.player.data.kiosk.KioskManager
import timber.log.Timber
import javax.inject.Inject

/** The last step of QR enrollment (Android 10 and later): the player is now the device owner. */
@AndroidEntryPoint
class PolicyComplianceActivity : ComponentActivity() {
    @Inject
    lateinit var kioskManager: KioskManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("Provisioning finished (device owner: %b)", kioskManager.isDeviceOwner())
        kioskManager.onProvisioned(intent)
        setResult(RESULT_OK)
        finish()
    }
}
