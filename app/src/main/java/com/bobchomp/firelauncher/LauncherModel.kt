package com.bobchomp.firelauncher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-wide launcher state, shared by [MainActivity] and [LauncherOverlay] so both
 * show the same app list and reuse the same decoded banners.
 */
@SuppressLint("StaticFieldLeak") // Only ever holds the application context.
object LauncherModel {

    private lateinit var appContext: Context
    private lateinit var repository: AppRepository
    lateinit var artLoader: AppArtLoader
        private set

    /** Null until the first load finishes. Read from Compose on the main thread. */
    var apps by mutableStateOf<List<LauncherApp>?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let(artLoader::evict)
            reload()
        }
    }

    /** Idempotent; call from the main thread. */
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        repository = AppRepository(appContext)
        artLoader = AppArtLoader(appContext)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(appContext, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch {
            apps = withContext(Dispatchers.IO) { repository.loadApps() }
        }
    }

    fun launch(context: Context, app: LauncherApp) {
        try {
            context.startActivity(repository.launchIntent(app))
        } catch (e: Exception) {
            // ActivityNotFoundException or SecurityException: the app was removed or disabled.
            Toast.makeText(context, context.getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
            reload()
        }
    }

    fun openSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
