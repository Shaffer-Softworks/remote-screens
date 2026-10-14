package dev.remotescreens.player

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.remotescreens.player.ui.screens.player.PlayerScreen
import dev.remotescreens.player.ui.screens.player.PlayerViewModel
import dev.remotescreens.player.ui.theme.RemoteScreensTheme
import dev.remotescreens.player.utils.hideSystemBars
import dev.remotescreens.player.utils.measureDisplay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContent {
            RemoteScreensTheme {
                PlayerScreen(viewModel)
            }
        }
        viewModel.onDisplayMeasured(measureDisplay())
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.post { viewModel.onDisplayMeasured(measureDisplay()) }
    }
}
