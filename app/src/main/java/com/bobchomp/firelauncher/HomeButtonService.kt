package com.bobchomp.firelauncher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Fire OS won't let you change the default home app, so this service brings
 * [MainActivity] to the front instead. It has two ways to do that:
 *
 * 1. Key filtering: swallow the Home key and open the launcher directly, so the
 *    stock home screen never shows.
 * 2. Fallback: if the stock home activity still appears (some Fire OS builds handle
 *    Home before accessibility sees it, and Back out of an app goes there too),
 *    cover it with the launcher right away.
 *
 * At boot the stock home is usually up before this service connects, so connecting
 * shortly after boot also brings the launcher up.
 */
class HomeButtonService : AccessibilityService() {

    /** Activity class names (and alias targets) of every other app that handles HOME. */
    private var stockHomeActivities: Set<String> = emptySet()
    private var stockHomePackages: Set<String> = emptySet()
    private var lastLaunchAt = 0L

    override fun onServiceConnected() {
        refreshStockHome()
        Log.i(TAG, "Connected; stock home activities: $stockHomeActivities")
        // Only right after boot, so a service restart mid-video doesn't interrupt anything.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) bringLauncherToFront(fromHomeKey = false)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        // Consume both down and up so the system never sees half a press.
        // Long-pressing Home (the Fire TV quick menu) is consumed too.
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            bringLauncherToFront(fromHomeKey = true)
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in stockHomePackages) return
        val cls = event.className?.toString()
        if (cls in stockHomeActivities) {
            bringLauncherToFront(fromHomeKey = false)
        } else {
            // Other screens from the stock launcher package are left alone. Logged so a
            // Fire OS build that uses a different home class can be spotted with logcat.
            Log.d(TAG, "Ignoring window $pkg/$cls")
        }
    }

    override fun onInterrupt() = Unit

    private fun refreshStockHome() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val activities = packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != packageName && !it.name.endsWith("FallbackHome") }
        stockHomePackages = activities.mapTo(HashSet()) { it.packageName }
        stockHomeActivities = activities.flatMapTo(HashSet()) {
            listOfNotNull(it.name, it.targetActivity)
        }
    }

    private fun bringLauncherToFront(fromHomeKey: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunchAt < DEBOUNCE_MS) return
        lastLaunchAt = now
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_FROM_HOME_KEY, fromHomeKey)
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Couldn't bring launcher to front", it) }
    }

    companion object {
        private const val TAG = "HomeButtonService"
        private const val DEBOUNCE_MS = 500L
        private const val BOOT_WINDOW_MS = 3 * 60_000L

        fun component(context: Context) = ComponentName(context, HomeButtonService::class.java)

        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val me = component(context)
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val si = info.resolveInfo?.serviceInfo ?: return@any false
                    si.packageName == me.packageName && si.name == me.className
                }
        }
    }
}
