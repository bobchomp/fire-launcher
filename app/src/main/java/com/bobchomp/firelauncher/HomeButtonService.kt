package com.bobchomp.firelauncher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Accessibility-based Home redirect, for Fire OS builds that still allow third-party
 * accessibility services (Fire OS 6.7.x wipes the setting; there [HomeWatcherService]
 * does the job instead). It has two ways to spot the stock home:
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

    private var stockHome = HomeRedirect.StockHome(emptySet(), emptySet(), emptySet())

    override fun onServiceConnected() {
        stockHome = HomeRedirect.findStockHome(this)
        Log.i(TAG, "Connected; stock home activities: ${stockHome.activityClasses}")
        // Only right after boot, so a service restart mid-video doesn't interrupt anything.
        if (HomeRedirect.isShortlyAfterBoot()) HomeRedirect.bringLauncherToFront(this, fromHomeKey = false)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        // Consume both down and up so the system never sees half a press.
        // Long-pressing Home (the Fire TV quick menu) is consumed too.
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            HomeRedirect.bringLauncherToFront(this, fromHomeKey = true)
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in stockHome.packages) return
        val cls = event.className?.toString()
        if (cls in stockHome.activityClasses) {
            HomeRedirect.bringLauncherToFront(this, fromHomeKey = false)
        } else {
            // Other screens from the stock launcher package are left alone. Logged so a
            // Fire OS build that uses a different home class can be spotted with logcat.
            Log.d(TAG, "Ignoring window $pkg/$cls")
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "HomeButtonService"

        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val me = ComponentName(context, HomeButtonService::class.java)
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val si = info.resolveInfo?.serviceInfo ?: return@any false
                    si.packageName == me.packageName && si.name == me.className
                }
        }
    }
}
