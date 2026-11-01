package com.bobchomp.firelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the Home watcher at boot and after this app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> HomeWatcherService.start(context)
        }
    }
}
