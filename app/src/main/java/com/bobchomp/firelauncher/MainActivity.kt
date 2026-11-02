package com.bobchomp.firelauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bobchomp.firelauncher.ui.LauncherScreen
import com.bobchomp.firelauncher.ui.LauncherTheme

class MainActivity : ComponentActivity() {

    private var homePresses by mutableIntStateOf(0)
    private var homeRedirectOn by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherModel.init(this)

        setContent {
            // This is the home screen: Back has nowhere to go.
            BackHandler {}
            LauncherTheme {
                LauncherScreen(
                    apps = LauncherModel.apps,
                    artLoader = LauncherModel.artLoader,
                    homePresses = homePresses,
                    homeRedirectOn = homeRedirectOn,
                    onLaunch = { LauncherModel.launch(this, it) },
                    onOpenSettings = { LauncherModel.openSettings(this) },
                )
            }
        }

        HomeWatcherService.start(this)
    }

    override fun onResume() {
        super.onResume()
        // The real launcher is up: the stand-in overlay (if any) can go.
        LauncherOverlay.hide()
        homeRedirectOn = HomeWatcherService.canReadLogs(this) || HomeButtonService.isEnabled(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while already home jumps back to the first app, like other launchers.
        // Coming back via Back out of an app keeps focus where it was.
        if (intent.getBooleanExtra(EXTRA_FROM_HOME_KEY, false)) homePresses++
    }

    companion object {
        const val EXTRA_FROM_HOME_KEY = "com.bobchomp.firelauncher.FROM_HOME_KEY"
    }
}
