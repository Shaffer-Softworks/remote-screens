package tech.shaffersoftworks.remotescreens.player

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import tech.shaffersoftworks.remotescreens.player.data.kiosk.KioskManager
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.PlayerScreen
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.PlayerViewModel
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.player.utils.hideSystemBars
import tech.shaffersoftworks.remotescreens.player.utils.measureDisplay
import tech.shaffersoftworks.remotescreens.player.utils.showOverLockScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    @Inject
    lateinit var kioskManager: KioskManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showOverLockScreen()
        hideSystemBars()
        setContent {
            RemoteScreensTheme {
                PlayerScreen(viewModel)
            }
        }
        viewModel.onDisplayMeasured(measureDisplay())
        observeKiosk()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        viewModel.onResumed()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.post { viewModel.onDisplayMeasured(measureDisplay()) }
    }

    /**
     * Lock task can only start while the activity is in front. Collecting again on every resume also locks again
     * after someone unpinned the screen with Back and Overview.
     */
    private fun observeKiosk() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.viewState
                    .map { viewState -> viewState.isKioskWanted }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { isKioskWanted ->
                        kioskManager.applyKiosk(this@MainActivity, isKioskWanted)
                        viewModel.onKioskStateChanged()
                    }
            }
        }
    }
}
