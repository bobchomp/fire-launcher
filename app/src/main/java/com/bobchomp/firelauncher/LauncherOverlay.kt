package com.bobchomp.firelauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bobchomp.firelauncher.ui.LauncherScreen
import com.bobchomp.firelauncher.ui.LauncherTheme

/**
 * A full, working copy of the launcher drawn as an overlay window.
 *
 * After Home is pressed, Android holds back activity starts from ordinary apps for
 * about 5 seconds, so [MainActivity] can't come forward right away. Overlay windows
 * aren't held back, so this covers the stock home instantly and stays usable until
 * the real launcher (or an app) takes over. Needs the "draw over other apps" app-op,
 * granted over adb (scripts/deploy.sh).
 */
@SuppressLint("StaticFieldLeak") // The overlay view uses the application context.
object LauncherOverlay {

    private const val TAG = "LauncherOverlay"

    /** Safety net in case the takeover is never seen in the log. */
    private const val MAX_SHOW_MS = 15_000L

    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var owner: OverlayOwner? = null
    private val autoHide = Runnable { hide() }

    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    /** Thread-safe. No-op if already showing or not permitted. */
    fun show(context: Context) {
        main.post { showOnMain(context.applicationContext) }
    }

    /** Thread-safe. No-op if not showing. */
    fun hide() {
        main.post(::hideOnMain)
    }

    private fun showOnMain(context: Context) {
        main.removeCallbacks(autoHide)
        main.postDelayed(autoHide, MAX_SHOW_MS)
        if (root != null || !canShow(context)) return
        LauncherModel.init(context)

        val lifecycleOwner = OverlayOwner().also { it.start() }
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LauncherTheme {
                    LauncherScreen(
                        apps = LauncherModel.apps,
                        artLoader = LauncherModel.artLoader,
                        homePresses = 0,
                        homeRedirectOn = true,
                        onLaunch = { LauncherModel.launch(context, it) },
                        onOpenSettings = { LauncherModel.openSettings(context) },
                    )
                }
            }
        }
        val frame = object : FrameLayout(context) {
            // Like the real launcher, Back does nothing here.
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
        }.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            addView(compose)
        }

        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        @Suppress("DEPRECATION") // FLAG_FULLSCREEN: still what hides the status bar on Android 7.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Focusable (no FLAG_NOT_FOCUSABLE) so the D-pad drives the overlay, not the
            // stock home underneath.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE,
        )
        try {
            wm(context).addView(frame, params)
            root = frame
            owner = lifecycleOwner
            Log.i(TAG, "Shown")
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't show overlay", e)
            lifecycleOwner.destroy()
        }
    }

    private fun hideOnMain() {
        main.removeCallbacks(autoHide)
        val view = root ?: return
        runCatching { wm(view.context).removeView(view) }
        owner?.destroy()
        root = null
        owner = null
        Log.i(TAG, "Hidden")
    }

    private fun wm(context: Context) = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** Minimal lifecycle + saved state so Compose can run outside an activity. */
    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
