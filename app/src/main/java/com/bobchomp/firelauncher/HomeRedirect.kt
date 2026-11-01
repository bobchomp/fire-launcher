package com.bobchomp.firelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Shared by the two ways of spotting the stock home screen: [HomeWatcherService]
 * (system log, used on Fire OS) and [HomeButtonService] (accessibility, where allowed).
 */
object HomeRedirect {

    private const val TAG = "HomeRedirect"
    private const val DEBOUNCE_MS = 500L
    private const val BOOT_WINDOW_MS = 3 * 60_000L

    @Volatile private var lastLaunchAt = 0L

    /** The other home screens on the device (on Fire TV, Amazon's), as component strings. */
    class StockHome(val packages: Set<String>, val activityClasses: Set<String>, val components: Set<String>)

    fun findStockHome(context: Context): StockHome {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val activities = context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName && !it.name.endsWith("FallbackHome") }
        val classes = HashSet<String>()
        val components = HashSet<String>()
        for (info in activities) {
            // Include alias targets: logs report the real activity class.
            for (cls in listOfNotNull(info.name, info.targetActivity)) {
                classes += cls
                val cn = ComponentName(info.packageName, cls)
                components += cn.flattenToString()
                components += cn.flattenToShortString()
            }
        }
        return StockHome(activities.mapTo(HashSet()) { it.packageName }, classes, components)
    }

    fun isShortlyAfterBoot() = SystemClock.elapsedRealtime() < BOOT_WINDOW_MS

    fun bringLauncherToFront(context: Context, fromHomeKey: Boolean) {
        synchronized(this) {
            val now = SystemClock.uptimeMillis()
            if (now - lastLaunchAt < DEBOUNCE_MS) return
            lastLaunchAt = now
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_FROM_HOME_KEY, fromHomeKey)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Couldn't bring launcher to front", it) }
    }
}
