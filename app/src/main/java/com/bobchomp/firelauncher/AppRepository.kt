package com.bobchomp.firelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import java.text.Collator

/** One launchable app shown in the grid. */
class LauncherApp(
    val label: String,
    val component: ComponentName,
    /** True when found via LEANBACK_LAUNCHER (a TV-ready app), false for phone-style LAUNCHER apps. */
    val isLeanback: Boolean,
    val activityInfo: ActivityInfo,
) {
    val key: String = component.flattenToString()
}

/** Reads installed launchable apps from PackageManager. Call [loadApps] off the main thread. */
class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    fun loadApps(): List<LauncherApp> {
        val byPackage = LinkedHashMap<String, LauncherApp>()

        // TV entry points first, so an app that has both keeps its TV activity and banner.
        // Sideloaded phone apps often only declare LAUNCHER, so include those too.
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, isLeanback = true, into = byPackage)
        collect(Intent.CATEGORY_LAUNCHER, isLeanback = false, into = byPackage)

        val collator = Collator.getInstance()
        return byPackage.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun collect(category: String, isLeanback: Boolean, into: MutableMap<String, LauncherApp>) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (info in pm.queryIntentActivities(intent, 0)) {
            val activity = info.activityInfo ?: continue
            val pkg = activity.packageName
            if (pkg == context.packageName || pkg in into) continue
            into[pkg] = LauncherApp(
                label = info.loadLabel(pm).toString().trim(),
                component = ComponentName(pkg, activity.name),
                isLeanback = isLeanback,
                activityInfo = activity,
            )
        }
    }

    fun launchIntent(app: LauncherApp): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}
