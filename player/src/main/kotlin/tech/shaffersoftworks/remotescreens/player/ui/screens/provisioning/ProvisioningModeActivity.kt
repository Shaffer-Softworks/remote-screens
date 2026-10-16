package tech.shaffersoftworks.remotescreens.player.ui.screens.provisioning

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import timber.log.Timber

/**
 * Asked during QR enrollment how to manage the tablet: as a fully managed kiosk. Only Android 10 and later ask, so
 * the constants of that version are safe here.
 */
@SuppressLint("InlinedApi")
class ProvisioningModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("Provisioning mode asked")
        val provisioningResult = Intent().putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_MODE,
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
        )
        setResult(RESULT_OK, provisioningResult)
        finish()
    }
}
