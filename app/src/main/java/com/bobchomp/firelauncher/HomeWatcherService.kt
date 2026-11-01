package com.bobchomp.firelauncher

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Home-button redirect for Fire OS builds that block third-party accessibility
 * services (e.g. Fire OS 6.7.x).
 *
 * The system logs every start/resume of an activity. With READ_LOGS (granted once
 * over adb: see scripts/deploy.sh) this service follows those lines and brings the
 * launcher forward whenever the stock home activity is started (Home pressed) or
 * resumed (Back out of an app, boot). The stock home shows briefly before ours covers it.
 *
 * Runs as a foreground service so Fire OS doesn't kill it while other apps are open.
 */
class HomeWatcherService : Service() {

    @Volatile private var running = false
    @Volatile private var logcat: Process? = null
    private lateinit var stockHome: HomeRedirect.StockHome

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        stockHome = HomeRedirect.findStockHome(this)
        Log.i(TAG, "Watching for stock home: ${stockHome.components}")
        running = true
        Thread(::watchLoop, "HomeWatcher").apply { isDaemon = true }.start()
        // At boot the stock home is usually up before we start.
        if (HomeRedirect.isShortlyAfterBoot()) HomeRedirect.bringLauncherToFront(this, fromHomeKey = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        logcat?.destroy()
        super.onDestroy()
    }

    private fun watchLoop() {
        while (running) {
            val startedAt = System.currentTimeMillis()
            try {
                val process = ProcessBuilder(
                    "logcat", "-b", "main", "-b", "events", "-v", "time", "-T", "1",
                    "ActivityManager:I", "am_resume_activity:I", "*:S",
                ).redirectErrorStream(true).start()
                logcat = process
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!running) break
                        handleLine(line, startedAt)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "logcat stopped", e)
            }
            // logcat exited (or never started): retry, but don't spin.
            if (running) Thread.sleep(RETRY_MS)
        }
    }

    private fun handleLine(line: String, notBefore: Long) {
        val homeStart = line.contains("START u0") && line.contains("android.intent.category.HOME")
        val resumed = line.contains("am_resume_activity")
        if (!homeStart && !resumed) return
        // -T 1 replays the newest existing line; ignore anything older than this logcat run.
        val at = parseTime(line) ?: return
        if (at < notBefore - CLOCK_SLACK_MS) return
        val hitsStockHome = COMPONENT.findAll(line).any { it.value in stockHome.components }
        if (hitsStockHome) {
            Log.i(TAG, "Stock home ${if (homeStart) "started" else "resumed"}; bringing launcher forward")
            HomeRedirect.bringLauncherToFront(this, fromHomeKey = homeStart)
        }
    }

    /** Parses the "MM-dd HH:mm:ss.SSS" prefix of `-v time` output (this year, local time). */
    private fun parseTime(line: String): Long? {
        if (line.length < 18) return null
        val parsed = runCatching { TIME_FORMAT.get()!!.parse(line.substring(0, 18)) }.getOrNull() ?: return null
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        cal.time = parsed
        cal.set(Calendar.YEAR, year)
        return cal.timeInMillis
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.watcher_channel), NotificationManager.IMPORTANCE_MIN),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.watcher_running))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val TAG = "HomeWatcher"
        private const val CHANNEL_ID = "home_watcher"
        private const val NOTIFICATION_ID = 1
        private const val RETRY_MS = 5_000L
        private const val CLOCK_SLACK_MS = 1_000L
        private val COMPONENT = Regex("""[\w.]+/[\w.$]+""")
        private val TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
        }

        fun canReadLogs(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_LOGS) ==
                PackageManager.PERMISSION_GRANTED

        /** Starts the watcher if it can do anything (READ_LOGS granted). Safe to call repeatedly. */
        fun start(context: Context) {
            if (!canReadLogs(context)) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, HomeWatcherService::class.java))
            }.onFailure { Log.w(TAG, "Couldn't start watcher", it) }
        }
    }
}
