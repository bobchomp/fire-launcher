package com.bobchomp.firelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bobchomp.firelauncher.ui.LauncherScreen
import com.bobchomp.firelauncher.ui.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val repository by lazy { AppRepository(applicationContext) }
    private val artLoader by lazy { AppArtLoader(applicationContext) }

    private var apps by mutableStateOf<List<LauncherApp>?>(null)
    private var homePresses by mutableIntStateOf(0)
    private var homeRedirectOn by mutableStateOf(true)
    private var loadJob: Job? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let(artLoader::evict)
            reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // This is the home screen: Back has nowhere to go.
            BackHandler {}
            LauncherTheme {
                LauncherScreen(
                    apps = apps,
                    artLoader = artLoader,
                    homePresses = homePresses,
                    homeRedirectOn = homeRedirectOn,
                    onLaunch = ::launch,
                    onOpenSettings = ::openSettings,
                )
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        reloadApps()
        HomeWatcherService.start(this)
    }

    override fun onResume() {
        super.onResume()
        homeRedirectOn = HomeWatcherService.canReadLogs(this) || HomeButtonService.isEnabled(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while already home jumps back to the first app, like other launchers.
        // Coming back via Back out of an app keeps focus where it was.
        if (intent.getBooleanExtra(EXTRA_FROM_HOME_KEY, false)) homePresses++
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun reloadApps() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            apps = withContext(Dispatchers.IO) { repository.loadApps() }
        }
    }

    private fun launch(app: LauncherApp) {
        try {
            startActivity(repository.launchIntent(app))
        } catch (e: Exception) {
            // ActivityNotFoundException or SecurityException: the app was removed or disabled.
            Toast.makeText(this, getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
            reloadApps()
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_FROM_HOME_KEY = "com.bobchomp.firelauncher.FROM_HOME_KEY"
    }
}
